package com.omniai.sync;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.catalog.Product;
import com.omniai.catalog.ProductRequest;
import com.omniai.catalog.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Khung đồng bộ chiều đi (T-49.1) trên Spring + Postgres thật. Connector SHOPEE giả, chạy theo kịch bản lỗi/thành
 * công. Worker tự chạy bị tắt ({@code omniai.sync.enabled=false}), test gọi {@link ProductSyncWorker#runOnce()} để
 * kiểm từng lượt. Sản phẩm tạo qua {@link ProductService} thật, nên đi đúng đường sự kiện ProductChanged.
 */
@SpringBootTest(properties = {"omniai.sync.enabled=false", "omniai.sync.max-attempts=4",
        "omniai.sync.retry-backoff=1ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, ProductSyncIT.Connectors.class})
class ProductSyncIT {

    /** Connector giả: mỗi lần push lấy 1 kết quả trong {@code script} (null = thành công), ghi lại mọi lần gọi. */
    static class ScriptedConnector implements ProductChannelConnector {
        final List<ProductSnapshot> pushes = Collections.synchronizedList(new ArrayList<>());
        final List<String> externalIds = Collections.synchronizedList(new ArrayList<>());
        final Deque<RuntimeException> script = new ArrayDeque<>();
        RuntimeException always;

        @Override
        public String channel() {
            return "SHOPEE";
        }

        @Override
        public synchronized String push(ProductSnapshot product, String externalId) {
            pushes.add(product);
            externalIds.add(externalId);
            if (always != null) {
                throw always;
            }
            RuntimeException next = script.poll();
            if (next != null) {
                throw next;
            }
            return externalId != null ? externalId : "item-" + product.productId();
        }

        synchronized void reset() {
            pushes.clear();
            externalIds.clear();
            script.clear();
            always = null;
        }
    }

    @TestConfiguration
    static class Connectors {
        @Bean
        ScriptedConnector shopeeConnector() {
            return new ScriptedConnector();
        }
    }

    @Autowired
    ProductService productService;
    @Autowired
    ProductSyncWorker worker;
    @Autowired
    ProductSyncRepository states;
    @Autowired
    ScriptedConnector shopee;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @MockitoBean
    BrainClient brainClient;

    private final List<Long> created = new ArrayList<>();
    private final Random random = new Random();

    @BeforeEach
    void setUp() {
        shopee.reset();
        // Dòng còn sót của test khác (nếu có) không được chen vào lượt quét.
        jdbc.update("UPDATE product_channel_sync SET status = 'SYNCED' WHERE status = 'PENDING'");
    }

    @AfterEach
    void cleanUp() {
        created.forEach(id -> jdbc.update("DELETE FROM product WHERE id = ?", id));
        created.clear();
    }

    // ---------- helper ----------

    private ProductRequest request(String sku, String name, Boolean active) {
        Map<String, Long> prices = new LinkedHashMap<>(Map.of("WEB", 250_000L, "SHOPEE", 260_000L, "TIKTOK", 243_000L));
        return new ProductRequest(sku, name, "CeraVe", "serum", "oily,combination", "pores", "am,pm",
                "Serum mỏng nhẹ giúp da trông mịn hơn.", "Aqua, Niacinamide, Glycerin", 30, 12, prices, 10, active);
    }

    private String newSku() {
        StringBuilder sb = new StringBuilder("SYNC-");
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        return sb.append("-0").append(random.nextInt(10)).toString();
    }

    private Product create(String name) {
        Product p = productService.create(request(newSku(), name, null));
        created.add(p.getId());
        return p;
    }

    private ProductSyncState state(Product p, String channel) {
        return states.findById(new ProductSyncState.Key(p.getId(), channel)).orElse(null);
    }

    /** Chạy worker tới khi không còn gì đến hạn (tối đa {@code max} lượt). Backoff test là 1 ms. */
    private void drain(int max) throws InterruptedException {
        for (int i = 0; i < max; i++) {
            Thread.sleep(20);
            if (worker.runOnce() == 0) {
                return;
            }
        }
    }

    // ---------- ghi thay đổi ----------

    @Test
    void creatingProductQueuesOnlyChannelsWithConnector() {
        Product p = create("Serum đồng bộ A");

        ProductSyncState shopeeState = state(p, "SHOPEE");
        assertThat(shopeeState).isNotNull();
        assertThat(shopeeState.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(shopeeState.getAttempts()).isZero();
        // WEB là kênh của shop, TIKTOK chưa có connector (US-50)
        assertThat(state(p, "WEB")).isNull();
        assertThat(state(p, "TIKTOK")).isNull();
    }

    @Test
    void hiddenProductThatWasNeverSyncedIsNotQueued() {
        Product p = productService.create(request(newSku(), "Serum ẩn", false));
        created.add(p.getId());
        assertThat(state(p, "SHOPEE")).isNull();
    }

    // ---------- tiêu chí T-49.1: lỗi 2 lần rồi thành công ----------

    @Test
    void failsTwiceThenSucceeds() throws Exception {
        Product p = create("Serum đồng bộ B");
        shopee.script.add(new IllegalStateException("Shopee 500"));
        shopee.script.add(new IllegalStateException("timeout"));

        assertThat(worker.runOnce()).isEqualTo(1);
        ProductSyncState afterFirst = state(p, "SHOPEE");
        assertThat(afterFirst.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).isEqualTo("Shopee 500");
        assertThat(afterFirst.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(5));

        drain(5);

        ProductSyncState done = state(p, "SHOPEE");
        assertThat(shopee.pushes).hasSize(3);
        assertThat(done.getStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(done.getAttempts()).isEqualTo(3);
        assertThat(done.getLastError()).isNull();
        assertThat(done.getExternalId()).isEqualTo("item-" + p.getId());
        assertThat(done.getSyncedAt()).isNotNull();
        assertThat(shopee.pushes.get(2).name()).isEqualTo("Serum đồng bộ B");
        assertThat(shopee.pushes.get(2).price()).isEqualTo(260_000L);
    }

    // ---------- tiêu chí T-49.1: trùng sự kiện không gửi 2 lần ----------

    @Test
    void duplicateEventDoesNotSendTwice() throws Exception {
        Product p = create("Serum đồng bộ C");
        drain(3);
        assertThat(shopee.pushes).hasSize(1);

        // Lưu lại đúng nội dung cũ (vd. admin bấm Lưu mà không đổi gì, hoặc sự kiện lặp lại)
        productService.update(p.getId(), request(p.getSku(), "Serum đồng bộ C", null));
        productService.update(p.getId(), request(p.getSku(), "Serum đồng bộ C", null));
        assertThat(state(p, "SHOPEE").getStatus()).isEqualTo(SyncStatus.SYNCED);
        drain(3);
        assertThat(shopee.pushes).hasSize(1);

        // Đổi giá, tồn không đẩy lại cả sản phẩm (US-50 lo giá/tồn)
        ProductRequest priceOnly = request(p.getSku(), "Serum đồng bộ C", null);
        priceOnly.prices().put("SHOPEE", 199_000L);
        productService.update(p.getId(), priceOnly);
        drain(3);
        assertThat(shopee.pushes).hasSize(1);

        // Đổi nội dung thật thì gửi bản sửa kèm mã sản phẩm phía sàn
        productService.update(p.getId(), request(p.getSku(), "Serum đồng bộ C mới", null));
        drain(3);
        assertThat(shopee.pushes).hasSize(2);
        assertThat(shopee.externalIds.get(1)).isEqualTo("item-" + p.getId());
        assertThat(shopee.pushes.get(1).name()).isEqualTo("Serum đồng bộ C mới");
    }

    @Test
    void severalEditsBeforeSendingAreSentOnceWithLatestContent() throws Exception {
        Product p = create("Bản 1");
        productService.update(p.getId(), request(p.getSku(), "Bản 2", null));
        productService.update(p.getId(), request(p.getSku(), "Bản 3", null));

        drain(3);

        assertThat(shopee.pushes).hasSize(1);
        assertThat(shopee.pushes.get(0).name()).isEqualTo("Bản 3");
        assertThat(state(p, "SHOPEE").getStatus()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    void hidingASyncedProductPushesDeactivation() throws Exception {
        Product p = create("Serum sẽ ẩn");
        drain(3);
        productService.setActive(p.getId(), false);
        drain(3);

        assertThat(shopee.pushes).hasSize(2);
        assertThat(shopee.pushes.get(1).active()).isFalse();
    }

    // ---------- hết lượt, lỗi không thử lại, admin ----------

    @Test
    void exhaustedRetriesBecomeFailedAndAdminCanRetry() throws Exception {
        Product p = create("Serum lỗi mãi");
        shopee.always = new IllegalStateException("Shopee sập");

        drain(10);

        ProductSyncState failed = state(p, "SHOPEE");
        assertThat(shopee.pushes).hasSize(4);
        assertThat(failed.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(4);
        assertThat(failed.getLastError()).isEqualTo("Shopee sập");

        mvc.perform(get("/api/admin/sync/products").param("status", "failed").param("channel", "shopee")
                        .param("size", "100").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.productId == " + p.getId() + ")].lastError").value("Shopee sập"));

        shopee.always = null;
        mvc.perform(post("/api/admin/sync/products/" + p.getId() + "/shopee/retry").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        drain(3);
        assertThat(state(p, "SHOPEE").getStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(shopee.pushes).hasSize(5);
    }

    @Test
    void nonRetryableErrorFailsAtOnce() throws Exception {
        Product p = create("Serum bị từ chối");
        shopee.script.add(new SyncException("Shopee từ chối: thiếu category_id", false));

        drain(5);

        assertThat(shopee.pushes).hasSize(1);
        assertThat(state(p, "SHOPEE").getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(state(p, "SHOPEE").getAttempts()).isEqualTo(1);
    }

    @Test
    void adminApiNeedsAuthAndReportsStatesByProduct() throws Exception {
        Product p = create("Serum trạng thái");
        mvc.perform(get("/api/admin/sync/products/by-ids").param("ids", p.getId().toString()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/sync/products/by-ids").param("ids", p.getId().toString())
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].channel").value("SHOPEE"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(get("/api/admin/sync/channels").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("SHOPEE"));
        mvc.perform(post("/api/admin/sync/products/999999999/SHOPEE/retry").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/sync/products/resync").param("channel", "TIKTOK").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sync/products").param("status", "DONE").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
    }
}

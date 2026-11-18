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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
        "omniai.sync.retry-backoff=1ms", "omniai.tiktok.products.enabled=false",
        "omniai.channels.shopee.sync-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, ProductSyncIT.Connectors.class})
class ProductSyncIT {

    /** Connector giả: mỗi lần push lấy 1 kết quả trong {@code script} (null = thành công), ghi lại mọi lần gọi. */
    static class ScriptedConnector implements ProductChannelConnector {
        final String channel;
        final List<ProductSnapshot> pushes = Collections.synchronizedList(new ArrayList<>());
        final List<String> externalIds = Collections.synchronizedList(new ArrayList<>());
        final List<String> prices = Collections.synchronizedList(new ArrayList<>());
        final List<String> stocks = Collections.synchronizedList(new ArrayList<>());
        final Deque<RuntimeException> script = new ArrayDeque<>();
        RuntimeException always;
        volatile boolean ready = true;
        volatile Set<SyncAspect> aspects = ALL;

        static final Set<SyncAspect> ALL = Set.of(SyncAspect.PRODUCT, SyncAspect.PRICE, SyncAspect.STOCK);

        ScriptedConnector(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public Set<SyncAspect> aspects() {
            return aspects;
        }

        @Override
        public boolean ready() {
            return ready;
        }

        /** Ghi "externalId=giá" để kiểm đúng sản phẩm, đúng giá của kênh. */
        @Override
        public synchronized void pushPrice(ProductSnapshot product, String externalId) {
            prices.add(externalId + "=" + product.price());
        }

        @Override
        public synchronized void pushStock(ProductSnapshot product, String externalId) {
            stocks.add(externalId + "=" + product.stock());
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
            prices.clear();
            stocks.clear();
            script.clear();
            always = null;
            ready = true;
            aspects = ALL;
        }
    }

    @TestConfiguration
    static class Connectors {
        @Bean
        ScriptedConnector shopeeConnector() {
            return new ScriptedConnector("SHOPEE");
        }

        @Bean
        ScriptedConnector tiktokConnector() {
            return new ScriptedConnector("TIKTOK");
        }
    }

    @Autowired
    ProductService productService;
    @Autowired
    ProductSyncWorker worker;
    @Autowired
    ProductSyncRepository states;
    @Autowired
    @Qualifier("shopeeConnector")
    ScriptedConnector shopee;
    @Autowired
    @Qualifier("tiktokConnector")
    ScriptedConnector tiktok;
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
        tiktok.reset();
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
        return state(p, channel, SyncAspect.PRODUCT);
    }

    private ProductSyncState state(Product p, String channel, SyncAspect aspect) {
        return states.findById(new ProductSyncState.Key(p.getId(), channel, aspect)).orElse(null);
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
        tiktok.ready = false; // vd. shop TikTok chưa ủy quyền
        Product p = create("Serum đồng bộ A");

        ProductSyncState shopeeState = state(p, "SHOPEE");
        assertThat(shopeeState).isNotNull();
        assertThat(shopeeState.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(shopeeState.getAttempts()).isZero();
        // WEB là kênh của shop; TIKTOK có connector nhưng chưa sẵn sàng. Giá/tồn chưa xếp vì sàn chưa có sản phẩm
        assertThat(state(p, "WEB")).isNull();
        assertThat(state(p, "TIKTOK")).isNull();
        assertThat(state(p, "SHOPEE", SyncAspect.PRICE)).isNull();
        assertThat(state(p, "SHOPEE", SyncAspect.STOCK)).isNull();
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
        tiktok.ready = false; // chỉ đếm lượt của Shopee
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

    // ---------- T-50.2: giá theo kênh, tồn kho mọi kênh ----------

    @Test
    void priceChangeGoesOnlyToItsChannelAndStockChangeToEveryChannel() throws Exception {
        Product p = create("Serum giá tồn");
        drain(5);
        // Lần tạo đã mang giá, tồn: không gửi riêng nữa
        assertThat(shopee.pushes).hasSize(1);
        assertThat(tiktok.pushes).hasSize(1);
        assertThat(shopee.prices).isEmpty();
        assertThat(tiktok.stocks).isEmpty();
        assertThat(state(p, "SHOPEE", SyncAspect.PRICE).getStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(state(p, "TIKTOK", SyncAspect.STOCK).getStatus()).isEqualTo(SyncStatus.SYNCED);

        // Đổi giá Shopee: chỉ Shopee nhận, đúng giá Shopee, không đẩy lại nội dung
        ProductRequest shopeePrice = request(p.getSku(), "Serum giá tồn", null);
        shopeePrice.prices().put("SHOPEE", 199_000L);
        productService.update(p.getId(), shopeePrice);
        drain(5);
        assertThat(shopee.prices).containsExactly("item-" + p.getId() + "=199000");
        assertThat(tiktok.prices).isEmpty();
        assertThat(shopee.pushes).hasSize(1);
        assertThat(tiktok.pushes).hasSize(1);

        // Đổi tồn: cả hai sàn nhận
        ProductRequest stock = request(p.getSku(), "Serum giá tồn", null);
        stock.prices().put("SHOPEE", 199_000L);
        productService.update(p.getId(), new ProductRequest(stock.sku(), stock.name(), stock.brand(), stock.category(),
                stock.skinTypes(), stock.skinConcerns(), stock.usageTime(), stock.description(), stock.ingredientsInci(),
                stock.sizeMl(), stock.paoMonths(), stock.prices(), 3, null));
        drain(5);
        assertThat(shopee.stocks).containsExactly("item-" + p.getId() + "=3");
        assertThat(tiktok.stocks).containsExactly("item-" + p.getId() + "=3");
        assertThat(shopee.prices).hasSize(1);
        assertThat(tiktok.prices).isEmpty();
    }

    @Test
    void channelWithoutProductApiSendsPriceStockOnlyAfterAdminLinksListing() throws Exception {
        shopee.aspects = Set.of(SyncAspect.PRICE, SyncAspect.STOCK); // như Shopee thật lúc này: chưa có add_item
        tiktok.ready = false;
        Product p = create("Serum đã đăng tay trên Shopee");
        assertThat(state(p, "SHOPEE")).isNull();
        assertThat(state(p, "SHOPEE", SyncAspect.PRICE)).isNull();

        mvc.perform(post("/api/admin/sync/products/" + p.getId() + "/shopee/link").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"880011\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.aspect == 'PRODUCT')].externalId").value("880011"))
                .andExpect(jsonPath("$[?(@.aspect == 'PRICE')].status").value("PENDING"));
        drain(5);
        assertThat(shopee.prices).containsExactly("880011=260000");
        assertThat(shopee.stocks).containsExactly("880011=10");
        assertThat(shopee.pushes).isEmpty();

        ProductRequest cheaper = request(p.getSku(), "Serum đã đăng tay trên Shopee", null);
        cheaper.prices().put("SHOPEE", 255_000L);
        productService.update(p.getId(), cheaper);
        drain(5);
        assertThat(shopee.prices).containsExactly("880011=260000", "880011=255000");

        mvc.perform(post("/api/admin/sync/products/" + p.getId() + "/web/link").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"1\"}"))
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].aspect", everyItem(is("PRODUCT"))))
                .andExpect(jsonPath("$[*].status", everyItem(is("PENDING"))));
        mvc.perform(get("/api/admin/sync/channels").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("SHOPEE", "TIKTOK")));
        mvc.perform(post("/api/admin/sync/products/999999999/SHOPEE/retry").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/sync/products/resync").param("channel", "WEB").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/sync/products").param("status", "DONE").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
    }
}

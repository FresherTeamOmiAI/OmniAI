package com.omniai.promotion;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Khuyến mãi theo kênh (T-33.1, US-33 AC, UC-21, decisions #53): voucher giảm số tiền cố định, flash sale giá sale
 * theo SKU, trạng thái tính lúc đọc. Giờ "đã qua" giả bằng SQL. Mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
// Giống AdminProductApiTest để dùng chung Spring context.
@TestPropertySource(properties = {"omniai.images.base-url=https://test.supabase.co/", "omniai.sync.enabled=false"})
@Transactional
@RecordApplicationEvents
class PromotionApiTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin");
    private static final String SKU = "CERA-CLE-01";
    private static final String OTHER_SKU = "CERA-MOI-01";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @PersistenceContext
    EntityManager em;

    @MockitoBean
    BrainClient brainClient;

    private final Instant now = Instant.now();
    private long skuId;
    private long shopeePrice;

    @BeforeEach
    void setUp() {
        skuId = jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, SKU);
        shopeePrice = price(SKU, "SHOPEE");
    }

    // --- AC US-33 ---

    @Test
    void voucherOfShopeeIsReturnedOnlyForShopee() throws Exception {
        create(voucher("SHOPEE", "sale1212", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voucherCode").value("SALE1212"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.minOrderAmount").value(0));

        mvc.perform(get("/api/promotions?channel=SHOPEE")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].voucherCode").value(contains("SALE1212")))
                .andExpect(jsonPath("$[0].discountAmount").value(30_000))
                .andExpect(jsonPath("$[0].items").value(empty()));
        mvc.perform(get("/api/promotions?channel=tiktok")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].voucherCode").value(not(hasItem("SALE1212"))));
    }

    @Test
    void expiredVoucherIsNotReturnedButAdminStillSeesIt() throws Exception {
        long id = id(create(voucher("SHOPEE", "HETHAN", now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)))));
        jdbc.update("UPDATE promotion SET starts_at = ?, ends_at = ? WHERE id = ?",
                ts(now.minus(Duration.ofDays(3))), ts(now.minus(Duration.ofMinutes(1))), id);
        em.clear(); // test và API chung 1 transaction: bỏ bản entity cũ trong bộ nhớ để đọc giờ mới từ DB

        mvc.perform(get("/api/promotions?channel=SHOPEE&upcomingDays=30"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
        mvc.perform(get("/api/admin/promotions/" + id).with(ADMIN))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/admin/promotions?status=EXPIRED&channel=SHOPEE").with(ADMIN))
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) id)));
        mvc.perform(get("/api/admin/promotions?status=ACTIVE").with(ADMIN))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem((int) id))));
    }

    @Test
    void upcomingPromotionsNeedUpcomingDays() throws Exception {
        long soon = id(create(voucher("SHOPEE", "SAPTOI", now.plus(Duration.ofDays(6)), now.plus(Duration.ofDays(7)))));
        long later = id(create(voucher("SHOPEE", "XAHON", now.plus(Duration.ofDays(8)), now.plus(Duration.ofDays(9)))));

        mvc.perform(get("/api/promotions?channel=SHOPEE"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) soon))));
        mvc.perform(get("/api/promotions?channel=SHOPEE&upcomingDays=7"))
                .andExpect(jsonPath("$[*].id").value(hasItem((int) soon)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) later))))
                .andExpect(jsonPath("$[?(@.id == " + soon + ")].status").value(contains("SCHEDULED")));
        mvc.perform(get("/api/promotions?channel=SHOPEE&upcomingDays=31")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("upcomingDays"));
    }

    @Test
    void disabledPromotionIsHiddenAndCanBeEnabledAgain() throws Exception {
        long id = id(create(voucher("SHOPEE", "TATBAT", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)))));
        mvc.perform(admin(patch("/api/admin/promotions/" + id + "/enabled"), Map.of("enabled", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get("/api/promotions?channel=SHOPEE")).andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));

        mvc.perform(admin(patch("/api/admin/promotions/" + id + "/enabled"), Map.of("enabled", true)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/promotions?channel=SHOPEE")).andExpect(jsonPath("$[*].id").value(hasItem((int) id)));
    }

    // --- Flash sale ---

    @Test
    void flashSaleCarriesSalePriceAndDiscountPercentPerSku() throws Exception {
        long sale = shopeePrice * 85 / 100;
        long id = id(create(flashSale("SHOPEE", now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)),
                item(SKU, sale), item(OTHER_SKU, price(OTHER_SKU, "SHOPEE") - 1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voucherCode").doesNotExist()));

        mvc.perform(get("/api/promotions?channel=SHOPEE&sku=" + SKU.toLowerCase()))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].items[*].sku").value(contains(SKU)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].items[0].listPrice").value(contains((int) shopeePrice)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].items[0].salePrice").value(contains((int) sale)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].items[0].discountPercent")
                        .value(contains((int) ((shopeePrice - sale) * 100 / shopeePrice))));
        mvc.perform(get("/api/promotions?channel=SHOPEE&sku=KHONG-CO-99")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    @Test
    void hiddenProductsAreDroppedFromPublicFlashSales() throws Exception {
        long id = id(create(flashSale("SHOPEE", now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)),
                item(SKU, shopeePrice - 1000))));
        jdbc.update("UPDATE product SET active = FALSE WHERE id = ?", skuId);

        mvc.perform(get("/api/promotions?channel=SHOPEE")).andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
        mvc.perform(get("/api/admin/promotions/" + id).with(ADMIN))
                .andExpect(jsonPath("$.items[*].sku").value(contains(SKU)));
    }

    @Test
    void flashSalePriceMustBeBelowListPriceAndSkusMustExist() throws Exception {
        create(flashSale("SHOPEE", now, now.plus(Duration.ofHours(2)),
                item(SKU, shopeePrice), item("KHONG-CO-99", 1000L), item(SKU, 1000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
                        "items[0].salePrice", "items[1].sku", "items[2].sku")));
        create(flashSale("SHOPEE", now, now.plus(Duration.ofHours(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("items")));
    }

    @Test
    void sameProductCannotBeInTwoOverlappingFlashSalesOfOneChannel() throws Exception {
        Instant start = now.plus(Duration.ofDays(1));
        long first = id(create(flashSale("SHOPEE", start, start.plus(Duration.ofHours(3)), item(SKU, shopeePrice - 1000))));

        create(flashSale("SHOPEE", start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(5)),
                item(OTHER_SKU, price(OTHER_SKU, "SHOPEE") - 1000), item(SKU, shopeePrice - 2000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[1].sku"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("#" + first)));
        // Kênh khác, hoặc nối tiếp (không giao), thì được.
        create(flashSale("TIKTOK", start, start.plus(Duration.ofHours(3)), item(SKU, price(SKU, "TIKTOK") - 1000)))
                .andExpect(status().isCreated());
        long next = id(create(flashSale("SHOPEE", start.plus(Duration.ofHours(3)), start.plus(Duration.ofHours(4)),
                item(SKU, shopeePrice - 2000))));

        // Tắt bản đầu thì bản giao giờ tạo được; bật lại bản đầu thì bị chặn.
        mvc.perform(admin(patch("/api/admin/promotions/" + first + "/enabled"), Map.of("enabled", false)));
        long overlapping = id(create(flashSale("SHOPEE", start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(2)),
                item(SKU, shopeePrice - 3000))));
        mvc.perform(admin(patch("/api/admin/promotions/" + first + "/enabled"), Map.of("enabled", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("#" + overlapping)));
        assertThat(next).isPositive();
    }

    // --- Kiểm dữ liệu ---

    @Test
    void invalidRequestsReturnFieldErrors() throws Exception {
        Map<String, Object> bad = voucher("LAZADA", "x", now.plus(Duration.ofDays(1)), now);
        bad.put("type", "COMBO");
        bad.put("name", " ");
        create(bad).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("channel", "type", "name", "endsAt")));

        Map<String, Object> voucher = voucher("SHOPEE", "x!", now, now.plus(Duration.ofDays(1)));
        voucher.put("discountAmount", 0);
        voucher.put("minOrderAmount", -1);
        voucher.put("items", List.of(item(SKU, 1000L)));
        create(voucher).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
                        "voucherCode", "discountAmount", "minOrderAmount", "items")));

        create(voucher("SHOPEE", "QUAKHU", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endsAt"));

        Map<String, Object> badTime = voucher("SHOPEE", "GIOSAI", now, now.plus(Duration.ofDays(1)));
        badTime.put("startsAt", "12/12/2026");
        create(badTime).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startsAt"));

        mvc.perform(get("/api/promotions")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("channel"));
        mvc.perform(get("/api/admin/promotions?status=XYZ").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateVoucherCodeInSameChannelIsConflict() throws Exception {
        create(voucher("SHOPEE", "TRUNG", now, now.plus(Duration.ofDays(1)))).andExpect(status().isCreated());
        create(voucher("SHOPEE", "trung", now, now.plus(Duration.ofDays(1)))).andExpect(status().isConflict());
        create(voucher("TIKTOK", "TRUNG", now, now.plus(Duration.ofDays(1)))).andExpect(status().isCreated());
    }

    @Test
    void timesWithoutOffsetAreVietnamTimeAndResponsesUsePlus7() throws Exception {
        Map<String, Object> v = voucher("SHOPEE", "GIOVN", now, now.plus(Duration.ofDays(1)));
        v.put("startsAt", "2099-12-12T00:00");
        v.put("endsAt", "2099-12-12T23:59:59+07:00");
        long id = id(create(v).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value("2099-12-12T00:00:00+07:00"))
                .andExpect(jsonPath("$.endsAt").value(endsWith("+07:00"))));

        Timestamp stored = jdbc.queryForObject("SELECT starts_at FROM promotion WHERE id = ?", Timestamp.class, id);
        assertThat(stored.toInstant()).isEqualTo(OffsetDateTime.of(2099, 12, 11, 17, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    // --- Quyền, sửa, xoá, sự kiện ---

    @Test
    void adminApiNeedsBasicAuthButPublicListDoesNot() throws Exception {
        mvc.perform(get("/api/admin/promotions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/promotions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(voucher("SHOPEE", "NOAUTH", now, now.plus(Duration.ofDays(1))))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/promotions?channel=WEB")).andExpect(status().isOk());
    }

    @Test
    void updateAndDeletePublishPromotionChanged() throws Exception {
        long id = id(create(voucher("SHOPEE", "SUAXOA", now, now.plus(Duration.ofDays(1)))));
        Map<String, Object> edited = voucher("SHOPEE", "SUAXOA", now, now.plus(Duration.ofDays(3)));
        edited.put("discountAmount", 50_000);
        mvc.perform(admin(put("/api/admin/promotions/" + id), edited)).andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(50_000));

        mvc.perform(delete("/api/admin/promotions/" + id).with(ADMIN)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/promotions/" + id).with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/promotions/" + id).with(ADMIN)).andExpect(status().isNotFound());

        assertThat(events.stream(PromotionChanged.class).filter(e -> e.promotionId() == id))
                .extracting(PromotionChanged::deleted)
                .containsExactly(false, false, true);
    }

    // --- Tiện ích ---

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(admin(post("/api/admin/promotions"), body));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long price(String sku, String channel) {
        return jdbc.queryForObject("""
                SELECT pc.price FROM product_channel pc JOIN product p ON p.id = pc.product_id
                WHERE p.sku = ? AND pc.channel_code = ?""", Long.class, sku, channel);
    }

    private static Map<String, Object> voucher(String channel, String code, Instant starts, Instant ends) {
        Map<String, Object> v = base(channel, "VOUCHER", "Voucher " + code, starts, ends);
        v.put("voucherCode", code);
        v.put("discountAmount", 30_000);
        return v;
    }

    @SafeVarargs
    private static Map<String, Object> flashSale(String channel, Instant starts, Instant ends, Map<String, Object>... items) {
        Map<String, Object> f = base(channel, "FLASH_SALE", "Flash sale " + channel, starts, ends);
        f.put("items", List.of(items));
        return f;
    }

    private static Map<String, Object> base(String channel, String type, String name, Instant starts, Instant ends) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", channel);
        m.put("type", type);
        m.put("name", name);
        m.put("startsAt", starts.truncatedTo(ChronoUnit.SECONDS).toString());
        m.put("endsAt", ends.truncatedTo(ChronoUnit.SECONDS).toString());
        return m;
    }

    private static Map<String, Object> item(String sku, Long salePrice) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", sku);
        m.put("salePrice", salePrice);
        return m;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}

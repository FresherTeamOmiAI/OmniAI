package com.omniai.order;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import com.omniai.identity.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lịch sử đơn mọi kênh (T-23.1, US-23, decisions #52): đơn WEB theo {@code customer_id}, đơn sàn chỉ hiện khi danh
 * tính sàn của người mua đang liên kết với khách. ai-brain đọc qua /api/internal/... (X-Internal-Token), người mua qua
 * /api/me/orders (JWT). Đơn sàn seed bằng SQL vì T-51.3 chưa nhận đơn thật. Mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "omniai.internal.token=test-internal-token")
@Transactional
class OrderHistoryApiTest {

    private static final String INTERNAL = "test-internal-token";
    private static final String LAN_SHOPEE = "shopee-buyer-23101";
    private static final String LAN_TIKTOK = "tiktok-buyer-23102";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerRepository customers;

    @Autowired
    TokenService tokens;

    @MockitoBean
    BrainClient brainClient;

    private final Instant now = Instant.now();
    private long lan;
    private long minh;
    private long productId;

    @BeforeEach
    void setUp() {
        lan = customer("history-lan@example.com");
        minh = customer("history-minh@example.com");
        productId = jdbc.queryForObject("SELECT id FROM product WHERE sku = 'CERA-CLE-01'", Long.class);
        identity("WEB", "history-lan@example.com", lan);
    }

    @Test
    void listsWebAndLinkedMarketplaceOrdersNewestFirst() throws Exception {
        identity("SHOPEE", LAN_SHOPEE, lan);
        long web = webOrder(lan, 3);
        long shopee = marketOrder("SHOPEE", "SP-230001", LAN_SHOPEE, 1);

        internal(lan).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].id").value(contains((int) shopee, (int) web)))
                .andExpect(jsonPath("$.orders[0].channel").value("SHOPEE"))
                .andExpect(jsonPath("$.orders[0].externalOrderId").value("SP-230001"))
                .andExpect(jsonPath("$.orders[0].items[0].sku").value("CERA-CLE-01"))
                .andExpect(jsonPath("$.orders[0].total").value(150_000))
                .andExpect(jsonPath("$.orders[1].channel").value("WEB"))
                .andExpect(jsonPath("$.orders[1].externalOrderId").value(nullValue()))
                .andExpect(jsonPath("$.linkedChannels").value(contains("SHOPEE", "WEB")))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void neverShowsOrdersOfUnlinkedOrForeignIdentities() throws Exception {
        long web = webOrder(lan, 1);
        marketOrder("TIKTOK", "TT-230001", LAN_TIKTOK, 2);          // danh tính TikTok chưa liên kết
        identity("SHOPEE", "shopee-buyer-minh", minh);
        marketOrder("SHOPEE", "SP-230002", "shopee-buyer-minh", 3); // danh tính Shopee của khách khác
        webOrder(minh, 4);                                          // đơn web của khách khác

        internal(lan).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].id").value(contains((int) web)))
                .andExpect(jsonPath("$.linkedChannels").value(contains("WEB")));
    }

    @Test
    void marketplaceOrderWithCustomerIdButUnlinkedIdentityStaysHidden() throws Exception {
        long web = webOrder(lan, 1);
        long tiktok = marketOrder("TIKTOK", "TT-230003", LAN_TIKTOK, 2);
        jdbc.update("UPDATE customer_order SET customer_id = ? WHERE id = ?", lan, tiktok);

        internal(lan).andExpect(jsonPath("$.orders[*].id").value(contains((int) web)));
    }

    @Test
    void linkingLaterShowsOlderOrdersAndUnlinkingHidesThem() throws Exception {
        long shopee = marketOrder("SHOPEE", "SP-230004", LAN_SHOPEE, 5);
        identity("SHOPEE", LAN_SHOPEE, null);
        internal(lan).andExpect(jsonPath("$.orders").value(hasSize(0)));

        jdbc.update("UPDATE channel_identity SET customer_id = ? WHERE channel = 'SHOPEE' AND external_id = ?", lan, LAN_SHOPEE);
        internal(lan).andExpect(jsonPath("$.orders[*].id").value(contains((int) shopee)));

        jdbc.update("UPDATE channel_identity SET customer_id = NULL WHERE channel = 'SHOPEE' AND external_id = ?", LAN_SHOPEE);
        internal(lan).andExpect(jsonPath("$.orders").value(hasSize(0)))
                .andExpect(jsonPath("$.linkedChannels").value(contains("WEB")));
    }

    @Test
    void meEndpointReturnsTheSameHistoryForTheTokenOwner() throws Exception {
        identity("SHOPEE", LAN_SHOPEE, lan);
        long web = webOrder(lan, 2);
        long shopee = marketOrder("SHOPEE", "SP-230005", LAN_SHOPEE, 1);
        webOrder(minh, 3);

        mvc.perform(get("/api/me/orders").header("Authorization", bearer(lan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].id").value(contains((int) shopee, (int) web)))
                .andExpect(jsonPath("$.linkedChannels").value(contains("SHOPEE", "WEB")));
        mvc.perform(get("/api/me/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void limitPagesAndIsValidated() throws Exception {
        long newest = webOrder(lan, 1);
        webOrder(lan, 2);

        internal(lan, "?limit=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].id").value(contains((int) newest)))
                .andExpect(jsonPath("$.hasMore").value(true));
        internal(lan, "?limit=2").andExpect(jsonPath("$.hasMore").value(false));
        internal(lan, "?limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        internal(lan, "?limit=51").andExpect(status().isBadRequest());
        internal(lan, "?limit=abc").andExpect(status().isBadRequest());
    }

    @Test
    void internalApiNeedsTokenAndKnownCustomer() throws Exception {
        mvc.perform(get("/api/internal/customers/{id}/orders", lan)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/customers/{id}/orders", lan).header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/customers/{id}/orders", 999_999_999L).header("X-Internal-Token", INTERNAL))
                .andExpect(status().isNotFound());
    }

    @Test
    void webOrdersCannotCarryAMarketplaceBuyer() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer_order (channel_code, buyer_external_id, customer_id, status, total_amount)
                VALUES ('WEB', 'someone', ?, 'DEMO', 0)""", lan))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customer_order_buyer");
    }

    private long customer(String email) {
        Customer c = new Customer();
        c.setEmail(email);
        return customers.saveAndFlush(c).getId();
    }

    /** {@code customerId} null: danh tính sàn đã biết nhưng chưa liên kết. */
    private void identity(String channel, String externalId, Long customerId) {
        jdbc.update("INSERT INTO channel_identity (customer_id, channel, external_id, created_at) VALUES (?, ?, ?, ?)",
                customerId, channel, externalId, Timestamp.from(now));
    }

    /** Đơn web của {@code customerId}, đặt cách đây {@code daysAgo} ngày. */
    private long webOrder(long customerId, int daysAgo) {
        return order("WEB", null, null, customerId, daysAgo);
    }

    /** Đơn sàn như T-51.3 sẽ ghi: có người mua phía sàn, không có customer_id. */
    private long marketOrder(String channel, String externalOrderId, String buyer, int daysAgo) {
        return order(channel, externalOrderId, buyer, null, daysAgo);
    }

    private long order(String channel, String externalOrderId, String buyer, Long customerId, int daysAgo) {
        long id = jdbc.queryForObject("""
                INSERT INTO customer_order (channel_code, external_order_id, buyer_external_id, customer_id, status,
                                            total_amount, created_at)
                VALUES (?, ?, ?, ?, 'DEMO', 150000, ?) RETURNING id""", Long.class,
                channel, externalOrderId, buyer, customerId, Timestamp.from(now.minus(Duration.ofDays(daysAgo))));
        jdbc.update("""
                INSERT INTO customer_order_item (order_id, line_no, product_id, sku, product_name, unit_price, quantity, line_total)
                VALUES (?, 1, ?, 'CERA-CLE-01', 'Sữa rửa mặt CeraVe', 150000, 1, 150000)""", id, productId);
        return id;
    }

    private ResultActions internal(long customerId) throws Exception {
        return internal(customerId, "");
    }

    private ResultActions internal(long customerId, String query) throws Exception {
        return mvc.perform(get("/api/internal/customers/" + customerId + "/orders" + query)
                .header("X-Internal-Token", INTERNAL));
    }

    private String bearer(long customerId) {
        return "Bearer " + tokens.issue(customers.findById(customerId).orElseThrow()).token();
    }
}

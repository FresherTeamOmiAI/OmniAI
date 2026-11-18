package com.omniai.identity;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mã liên kết OMNI-XXXXXX (T-22.1, US-22, SRS UC-08, NFR-08, decisions #49): lấy mã trên web, gõ mã trong
 * chat sàn qua API nội bộ, xác nhận + consent trên web. Đồng hồ chỉnh được, mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, OtpAuthApiTest.ClockConfig.class})
@TestPropertySource(properties = "omniai.internal.token=test-internal-token")
@Transactional
class LinkCodeApiTest {

    private static final String INTERNAL = "test-internal-token";
    private static final String SHOPEE_BUYER = "shopee-buyer-777001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerRepository customers;

    @Autowired
    TokenService tokens;

    @Autowired
    EntityManager em;

    @Autowired
    OtpAuthApiTest.MutableClock clock;

    @MockitoBean
    BrainClient brainClient;

    private final JsonMapper json = JsonMapper.builder().build();

    private Customer lan;
    private String lanBearer;

    @BeforeEach
    void setUp() {
        clock.reset();
        lan = newCustomer("link-lan@example.com");
        lanBearer = bearer(lan);
    }

    private Customer newCustomer(String email) {
        Customer c = new Customer();
        c.setEmail(email);
        return customers.saveAndFlush(c);
    }

    private String bearer(Customer c) {
        return "Bearer " + tokens.issue(c).token();
    }

    private ResultActions issue(String bearer) throws Exception {
        return mvc.perform(post("/api/me/link-codes").header("Authorization", bearer));
    }

    private String newCode(String bearer) throws Exception {
        String body = issue(bearer).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("code").asString();
    }

    private ResultActions claim(String channel, String user, String code) throws Exception {
        return mvc.perform(post("/api/internal/link-codes/claim")
                .header("X-Internal-Token", INTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("channel", channel, "channelUserId", user, "code", code))));
    }

    private ResultActions requests(String bearer) throws Exception {
        return mvc.perform(get("/api/me/link-requests").header("Authorization", bearer));
    }

    private long firstRequestId(String bearer) throws Exception {
        String body = requests(bearer).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get(0).get("id").asLong();
    }

    private ResultActions confirm(String bearer, long id, String body) throws Exception {
        return mvc.perform(post("/api/me/link-requests/" + id + "/confirm")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> identity(String channel, String externalId) {
        em.flush();
        return jdbc.queryForMap("SELECT customer_id, linked_by, confidence, consent_at FROM channel_identity "
                + "WHERE channel = ? AND external_id = ?", channel, externalId);
    }

    private int count(String sql, Object... args) {
        em.flush();
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void codeIsTypedInChatThenConfirmedWithConsentOnWeb() throws Exception {
        String code = newCode(lanBearer);
        assertThat(code).matches("OMNI-[A-HJ-NP-Z2-9]{6}");
        assertThat(count("SELECT count(*) FROM link_code WHERE customer_id = ? AND code_hash <> ?", lan.getId(), code))
                .as("chỉ lưu hash").isEqualTo(1);
        requests(lanBearer).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Khách gõ thường, bỏ tiền tố: vẫn nhận.
        claim("SHOPEE", SHOPEE_BUYER, code.substring(5).toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.confirmBefore").value(clock.instant().plus(Duration.ofMinutes(15)).toString()));

        requests(lanBearer)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].channel").value("SHOPEE"))
                .andExpect(jsonPath("$[0].channelUser").value("***001"));
        long id = firstRequestId(lanBearer);

        confirm(lanBearer, id, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));
        confirm(lanBearer, id, "{\"consent\": false}").andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM channel_identity WHERE external_id = ?", SHOPEE_BUYER)).isZero();

        clock.advance(Duration.ofMinutes(5));
        confirm(lanBearer, id, "{\"consent\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("SHOPEE"))
                .andExpect(jsonPath("$.channelUser").value("***001"));

        Map<String, Object> linked = identity("SHOPEE", SHOPEE_BUYER);
        assertThat(((Number) linked.get("customer_id")).longValue()).isEqualTo(lan.getId());
        assertThat(linked.get("linked_by")).isEqualTo("LINK_CODE");
        assertThat(((Number) linked.get("confidence")).doubleValue()).isEqualTo(1.0);
        assertThat(linked.get("consent_at")).isNotNull();

        requests(lanBearer).andExpect(jsonPath("$.length()").value(0));
        confirm(lanBearer, id, "{\"consent\": true}").andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("link_code_expired"));
        // Danh tính đã gắn: gõ mã nữa thì báo đã liên kết, không tính lần sai.
        claim("SHOPEE", SHOPEE_BUYER, newCode(lanBearer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("identity_already_linked"));
    }

    @Test
    void codeIsSingleUseExpiresAndIsRevokedByNewerCode() throws Exception {
        String first = newCode(lanBearer);
        String second = newCode(lanBearer);
        claim("SHOPEE", SHOPEE_BUYER, first).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("link_code_expired"))
                .andExpect(jsonPath("$.attemptsLeft").value(4));

        claim("SHOPEE", SHOPEE_BUYER, second).andExpect(status().isOk());
        // Mã chỉ dùng 1 lần.
        claim("TIKTOK", "tiktok-buyer-1", second).andExpect(status().isGone());

        String late = newCode(lanBearer);
        clock.advance(Duration.ofMinutes(15));
        claim("TIKTOK", "tiktok-buyer-2", late).andExpect(status().isGone());
    }

    @Test
    void confirmationWindowIsFifteenMinutesFromClaim() throws Exception {
        String code = newCode(lanBearer);
        clock.advance(Duration.ofMinutes(14));
        claim("SHOPEE", SHOPEE_BUYER, code).andExpect(status().isOk());
        long id = firstRequestId(lanBearer);

        clock.advance(Duration.ofMinutes(10));
        requests(lanBearer).andExpect(jsonPath("$.length()").value(1));
        clock.advance(Duration.ofMinutes(5));
        requests(lanBearer).andExpect(jsonPath("$.length()").value(0));
        confirm(lanBearer, id, "{\"consent\": true}").andExpect(status().isGone());
    }

    @Test
    void fiveFailedAttemptsInAnHourLockTheChannelIdentity() throws Exception {
        String code = newCode(lanBearer);
        for (int left = 4; left >= 1; left--) {
            claim("SHOPEE", SHOPEE_BUYER, "OMNI-ZZZZZZ").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("link_code_invalid"))
                    .andExpect(jsonPath("$.attemptsLeft").value(left));
            clock.advance(Duration.ofMinutes(1));
        }
        claim("SHOPEE", SHOPEE_BUYER, "not-a-code").andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("link_locked"))
                .andExpect(header().exists("Retry-After"));
        // Đang khoá: mã đúng cũng bị chặn, danh tính khác thì không ảnh hưởng.
        claim("SHOPEE", SHOPEE_BUYER, code).andExpect(status().isLocked())
                .andExpect(jsonPath("$.retryAfterSeconds").value(56 * 60));
        claim("TIKTOK", SHOPEE_BUYER, code).andExpect(status().isOk());

        // Cửa sổ trượt: lần sai đầu ra khỏi 1 giờ thì nhập được, nhưng 4 lần sai sau vẫn còn tính.
        clock.advance(Duration.ofMinutes(56));
        claim("SHOPEE", SHOPEE_BUYER, "OMNI-ZZZZZZ").andExpect(status().isLocked());
        clock.advance(Duration.ofMinutes(61));
        claim("SHOPEE", SHOPEE_BUYER, "OMNI-ZZZZZZ").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.attemptsLeft").value(4));
    }

    @Test
    void identityLinkedToAnotherCustomerIsNeverMerged() throws Exception {
        Customer minh = newCustomer("link-minh@example.com");
        String minhBearer = bearer(minh);

        // Cả 2 mã cùng được 1 người mua gõ trước khi ai xác nhận.
        claim("SHOPEE", SHOPEE_BUYER, newCode(lanBearer)).andExpect(status().isOk());
        claim("SHOPEE", SHOPEE_BUYER, newCode(minhBearer)).andExpect(status().isOk());

        confirm(lanBearer, firstRequestId(lanBearer), "{\"consent\": true}").andExpect(status().isOk());
        confirm(minhBearer, firstRequestId(minhBearer), "{\"consent\": true}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("identity_already_linked"));
        assertThat(((Number) identity("SHOPEE", SHOPEE_BUYER).get("customer_id")).longValue()).isEqualTo(lan.getId());
    }

    @Test
    void customerCanRejectAndCannotTouchOthersRequests() throws Exception {
        Customer minh = newCustomer("link-minh@example.com");
        claim("SHOPEE", SHOPEE_BUYER, newCode(lanBearer)).andExpect(status().isOk());
        long id = firstRequestId(lanBearer);

        confirm(bearer(minh), id, "{\"consent\": true}").andExpect(status().isNotFound());
        mvc.perform(post("/api/me/link-requests/" + id + "/reject").header("Authorization", bearer(minh)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/me/link-requests/" + id + "/reject").header("Authorization", lanBearer))
                .andExpect(status().isNoContent());
        confirm(lanBearer, id, "{\"consent\": true}").andExpect(status().isGone());
        assertThat(count("SELECT count(*) FROM channel_identity WHERE external_id = ?", SHOPEE_BUYER)).isZero();
    }

    @Test
    void claimValidatesBody() throws Exception {
        claim("WEB", "123", newCode(lanBearer)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("channel"));
        claim("ZALO", "123", "OMNI-ABC234").andExpect(status().isBadRequest());
        mvc.perform(post("/api/internal/link-codes/claim").header("X-Internal-Token", INTERNAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\": \"SHOPEE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void endpointsNeedTheRightCredentials() throws Exception {
        mvc.perform(post("/api/me/link-codes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/link-requests")).andExpect(status().isUnauthorized());

        String body = "{\"channel\": \"SHOPEE\", \"channelUserId\": \"x\", \"code\": \"OMNI-ABC234\"}";
        mvc.perform(post("/api/internal/link-codes/claim").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/link-codes/claim").header("X-Internal-Token", "sai")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/link-codes/claim").header("Authorization", lanBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedCustomerLosesCodesAndCannotIssue() throws Exception {
        newCode(lanBearer);
        em.flush();
        jdbc.update("DELETE FROM channel_identity WHERE customer_id = ?", lan.getId());
        jdbc.update("DELETE FROM customer WHERE id = ?", lan.getId());
        em.clear();

        assertThat(count("SELECT count(*) FROM link_code WHERE customer_id = ?", lan.getId())).isZero();
        issue(lanBearer).andExpect(status().isUnauthorized());
    }
}

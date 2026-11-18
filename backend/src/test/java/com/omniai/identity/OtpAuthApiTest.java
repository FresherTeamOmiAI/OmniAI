package com.omniai.identity;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập email + OTP (T-13.1, SRS UC-07, NFR-08): hết hạn 5 phút, sai 5 lần khoá 15 phút,
 * gửi lại >= 60 s và <= 5 lần/giờ, gắn danh tính WEB, JWT cho /api/me. Đồng hồ chỉnh được, mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, OtpAuthApiTest.ClockConfig.class})
@Transactional
class OtpAuthApiTest {

    /** Đồng hồ test: mặc định là giờ thật, {@link #advance} để giả thời gian trôi. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.now();

        void reset() {
            now = Instant.now();
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    private static final String EMAIL = "Lan.Nguyen@Example.com";
    private static final String NORMALIZED = "lan.nguyen@example.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @MockitoBean
    BrainClient brainClient;

    @MockitoBean
    OtpSender sender;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void resetClock() {
        clock.reset();
        clearInvocations(sender);
    }

    @Test
    void newCustomerLogsInLinksWebIdentityAndCallsMe() throws Exception {
        requestOtp(EMAIL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
        String code = lastCode(NORMALIZED);
        assertThat(code).matches("\\d{6}");
        assertThat(jdbc.queryForObject("SELECT code_hash FROM otp_challenge WHERE email = ?", String.class, NORMALIZED))
                .isNotEqualTo(code);

        JsonNode login = body(verifyOtp(EMAIL, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCustomer").value(true))
                .andExpect(jsonPath("$.customer.email").value(NORMALIZED)));
        long customerId = login.get("customer").get("id").asLong();
        String token = login.get("token").asString();

        Map<String, Object> identity = jdbc.queryForMap(
                "SELECT external_id, linked_by, confidence, consent_at FROM channel_identity WHERE customer_id = ? AND channel = 'WEB'",
                customerId);
        assertThat(identity.get("external_id")).isEqualTo(Long.toString(customerId));
        assertThat(identity.get("linked_by")).isEqualTo("OTP");
        assertThat(identity.get("confidence")).isEqualTo(1.0);
        assertThat(identity.get("consent_at")).isNotNull();

        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.get("sub").asString()).isEqualTo(Long.toString(customerId));
        assertThat(claims.get("iss").asString()).isEqualTo("omniai");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.email").value(NORMALIZED));
    }

    @Test
    void returningCustomerKeepsSameAccountAndIdentity() throws Exception {
        long first = login(EMAIL).get("customer").get("id").asLong();
        clock.advance(Duration.ofMinutes(2));

        JsonNode second = login("LAN.NGUYEN@example.COM");
        assertThat(second.get("customer").get("id").asLong()).isEqualTo(first);
        assertThat(second.get("newCustomer").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE email = ?", Long.class, NORMALIZED)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM channel_identity WHERE customer_id = ?", Long.class, first)).isEqualTo(1);
    }

    @Test
    void rejectsMissingConsentAndBadInput() throws Exception {
        post("/api/auth/otp", "{\"email\": \"a@example.com\", \"consent\": false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));
        post("/api/auth/otp", "{\"email\": \"a@example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("consent"));
        post("/api/auth/otp", "{\"email\": \"khong-phai-email\", \"consent\": true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        post("/api/auth/otp/verify", "{\"email\": \"a@example.com\", \"code\": \"12ab\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
        post("/api/auth/otp", "{không phải json")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_challenge", Long.class)).isZero();
    }

    @Test
    void codeExpiresAfterFiveMinutes() throws Exception {
        requestOtp(EMAIL).andExpect(status().isAccepted());
        String code = lastCode(NORMALIZED);
        clock.advance(Duration.ofMinutes(5));

        verifyOtp(EMAIL, code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("otp_expired"));
    }

    @Test
    void verifyWithoutRequestIsExpired() throws Exception {
        verifyOtp("chua-gui@example.com", "123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("otp_expired"));
    }

    @Test
    void fiveWrongCodesLockForFifteenMinutes() throws Exception {
        requestOtp(EMAIL).andExpect(status().isAccepted());
        String code = lastCode(NORMALIZED);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int left = 4; left >= 1; left--) {
            verifyOtp(EMAIL, wrong)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("otp_invalid"))
                    .andExpect(jsonPath("$.attemptsLeft").value(left));
        }
        verifyOtp(EMAIL, wrong)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("otp_locked"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(header().string("Retry-After", "900"));

        // Đang khoá: mã đúng cũng bị từ chối, không xin được mã mới.
        verifyOtp(EMAIL, code).andExpect(status().isLocked());
        clock.advance(Duration.ofMinutes(14));
        requestOtp(EMAIL)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        clock.advance(Duration.ofMinutes(1));
        requestOtp(EMAIL).andExpect(status().isAccepted());
        verifyOtp(EMAIL, lastCode(NORMALIZED)).andExpect(status().isOk());
    }

    @Test
    void resendNeedsSixtySecondsAndAtMostFivePerHour() throws Exception {
        requestOtp(EMAIL).andExpect(status().isAccepted());
        clock.advance(Duration.ofSeconds(30));
        requestOtp(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("otp_rate_limited"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30))
                .andExpect(header().string("Retry-After", "30"));

        for (int i = 2; i <= 5; i++) {
            clock.advance(Duration.ofSeconds(60));
            requestOtp(EMAIL).andExpect(status().isAccepted());
        }
        clock.advance(Duration.ofSeconds(60));
        // 5 mã đã gửi ở t = 0, 90, 150, 210, 270 s; bây giờ t = 330 s, mã đầu ra khỏi cửa sổ 1 giờ sau 3270 s.
        requestOtp(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").value(3270));

        clock.advance(Duration.ofSeconds(3270));
        requestOtp(EMAIL).andExpect(status().isAccepted());
    }

    @Test
    void newCodeReplacesOldOneAndCodeIsSingleUse() throws Exception {
        requestOtp(EMAIL).andExpect(status().isAccepted());
        String oldCode = lastCode(NORMALIZED);
        clock.advance(Duration.ofSeconds(61));
        clearInvocations(sender);
        requestOtp(EMAIL).andExpect(status().isAccepted());
        String newCode = lastCode(NORMALIZED);

        if (!oldCode.equals(newCode)) {
            verifyOtp(EMAIL, oldCode).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("otp_invalid"));
        }
        verifyOtp(EMAIL, newCode).andExpect(status().isOk());
        verifyOtp(EMAIL, newCode).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("otp_expired"));
    }

    @Test
    void meRequiresValidToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer khong.phai.token")).andExpect(status().isUnauthorized());

        // Token cấp từ 8 ngày trước (thời hạn 7 ngày) đã hết hạn.
        clock.set(Instant.now().minus(Duration.ofDays(8)));
        String expired = login(EMAIL).get("token").asString();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenCannotOpenAdminAndAdminBasicStillWorks() throws Exception {
        String token = login(EMAIL).get("token").asString();
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String basic = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        mvc.perform(get("/api/admin/products").param("size", "1").header("Authorization", "Basic " + basic))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Basic " + basic))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email) throws Exception {
        clearInvocations(sender);
        requestOtp(email).andExpect(status().isAccepted());
        return body(verifyOtp(email, lastCode(OtpService.normalizeEmail(email))).andExpect(status().isOk()));
    }

    private String lastCode(String normalizedEmail) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).send(eq(normalizedEmail), code.capture(), any());
        return code.getValue();
    }

    private ResultActions requestOtp(String email) throws Exception {
        return post("/api/auth/otp", "{\"email\": \"%s\", \"consent\": true}".formatted(email));
    }

    private ResultActions verifyOtp(String email, String code) throws Exception {
        return post("/api/auth/otp/verify", "{\"email\": \"%s\", \"code\": \"%s\"}".formatted(email, code));
    }

    private ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

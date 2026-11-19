package com.omniai.identity;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Job dọn dữ liệu xác thực cũ (nợ kỹ thuật US-13, US-22, decisions #51): xoá OTP, mã liên kết chưa CONFIRMED và lần
 * gõ sai cũ hơn 7 ngày; dòng mới và mã CONFIRMED giữ nguyên. Đồng hồ chỉnh được, mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, OtpAuthApiTest.ClockConfig.class})
@Transactional
class IdentityCleanupTest {

    private static final Duration OLD = Duration.ofDays(8);
    private static final Duration RECENT = Duration.ofDays(6);

    @Autowired
    IdentityCleanup cleanup;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerRepository customers;

    @Autowired
    OtpAuthApiTest.MutableClock clock;

    @MockitoBean
    BrainClient brainClient;

    @MockitoBean
    OtpSender sender;

    private Instant now;
    private long customerId;
    private int hashSeq;

    @BeforeEach
    void setUp() {
        clock.reset();
        clearInvocations(sender);
        now = clock.instant();
    }

    @Test
    void removesOnlyRowsOlderThanRetentionAndKeepsConfirmedLinkCodes() {
        Customer c = new Customer();
        c.setEmail("cleanup-lan@example.com");
        customerId = customers.saveAndFlush(c).getId();
        otp("old@example.com", OLD);
        otp("old@example.com", OLD.minusMinutes(1));
        otp("recent@example.com", RECENT);

        long unclaimed = linkCode(OLD, false, false, null);
        long revoked = linkCode(OLD, true, false, null);
        long rejected = linkCode(OLD, false, true, "REJECTED");
        long expiredClaim = linkCode(OLD, false, true, null);
        long confirmed = linkCode(OLD, false, true, "CONFIRMED");
        long recent = linkCode(RECENT, false, false, null);

        attempt(OLD);
        attempt(Duration.ofHours(1));

        IdentityCleanup.Removed removed = cleanup.run();

        assertThat(removed).isEqualTo(new IdentityCleanup.Removed(2, 4, 1));
        assertThat(jdbc.queryForList("SELECT email FROM otp_challenge WHERE email LIKE '%@example.com'", String.class))
                .containsExactly("recent@example.com");
        assertThat(linkCodeIds()).containsExactlyInAnyOrder(confirmed, recent)
                .doesNotContain(unclaimed, revoked, rejected, expiredClaim);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM link_code_attempt WHERE external_id = 'cleanup-buyer'",
                Integer.class)).isEqualTo(1);

        assertThat(cleanup.run().total()).as("chạy lần 2 không còn gì để xoá").isZero();
    }

    @Test
    void loginStillWorksAfterOldChallengesAreRemoved() throws Exception {
        String email = "cleanup-login@example.com";
        requestOtp(email).andExpect(status().isAccepted());

        clock.advance(OLD);
        clearInvocations(sender);
        requestOtp(email).andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(email), code.capture(), any());

        assertThat(cleanup.run().otpChallenges()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_challenge WHERE email = ?", Integer.class, email))
                .as("mã vừa gửi còn nguyên").isEqualTo(1);

        post("/api/auth/otp/verify", "{\"email\": \"%s\", \"code\": \"%s\"}".formatted(email, code.getValue()))
                .andExpect(status().isOk());
    }

    /** Đúng đường lịch chạy: không có transaction bọc ngoài như các test khác (từng lỗi "No active transaction"). */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void scheduledRunDeletesWithoutCallerTransaction() {
        String email = "cleanup-scheduled@example.com";
        try {
            otp(email, OLD);
            cleanup.scheduled();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_challenge WHERE email = ?", Integer.class, email))
                    .isZero();
        } finally {
            jdbc.update("DELETE FROM otp_challenge WHERE email = ?", email);
        }
    }

    @Test
    void rejectsRetentionShorterThanOneDay() {
        assertThatThrownBy(() -> new IdentityCleanup(null, null, null, clock,
                        Duration.ofHours(2), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 ngày");
    }

    private void otp(String email, Duration age) {
        Instant created = now.minus(age);
        jdbc.update("INSERT INTO otp_challenge (email, code_hash, created_at, expires_at) VALUES (?, 'x', ?, ?)",
                email, ts(created), ts(created.plus(Duration.ofMinutes(5))));
    }

    /** {@code claimed}: đã có danh tính sàn gõ mã; {@code resolution} chỉ đặt khi đã gõ. */
    private long linkCode(Duration age, boolean revoked, boolean claimed, String resolution) {
        Instant created = now.minus(age);
        Instant claimedAt = claimed ? created.plus(Duration.ofMinutes(5)) : null;
        String hash = "%064d".formatted(++hashSeq);
        return jdbc.queryForObject("""
                INSERT INTO link_code (customer_id, code_hash, created_at, expires_at, revoked_at,
                                       claimed_channel, claimed_external_id, claimed_at, resolved_at, resolution)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                customerId, hash, ts(created), ts(created.plus(Duration.ofMinutes(15))),
                revoked ? ts(created.plus(Duration.ofMinutes(1))) : null,
                claimed ? "SHOPEE" : null, claimed ? "shopee-cleanup-" + hashSeq : null, ts(claimedAt),
                resolution == null ? null : ts(claimedAt.plus(Duration.ofMinutes(2))), resolution);
    }

    private void attempt(Duration age) {
        jdbc.update("INSERT INTO link_code_attempt (channel, external_id, attempted_at) VALUES ('SHOPEE', 'cleanup-buyer', ?)",
                ts(now.minus(age)));
    }

    private List<Long> linkCodeIds() {
        return jdbc.queryForList("SELECT id FROM link_code WHERE customer_id = ?", Long.class, customerId);
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private ResultActions requestOtp(String email) throws Exception {
        return post("/api/auth/otp", "{\"email\": \"%s\", \"consent\": true}".formatted(email));
    }

    private ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

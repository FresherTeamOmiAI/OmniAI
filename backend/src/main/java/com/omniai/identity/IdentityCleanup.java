package com.omniai.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Dọn dữ liệu xác thực hết dùng (nợ kỹ thuật US-13, US-22, decisions #51): mỗi lần gửi OTP, lấy mã liên kết hay gõ
 * sai mã đều thêm 1 dòng. Dòng cũ hơn {@code omniai.identity.cleanup.retention} (mặc định 7 ngày) không còn tác dụng:
 * OTP chỉ dùng dòng mới nhất và cửa sổ gửi lại 1 giờ (khoá muộn nhất 20 phút sau khi gửi), mã liên kết chỉ dùng được
 * 15 phút để gõ + 15 phút để xác nhận, lần gõ sai chỉ đếm trong 1 giờ. Giữ {@code link_code} CONFIRMED.
 *
 * <p>Chạy hằng đêm lúc 03:00 giờ Việt Nam. {@code @EnableScheduling} đã bật cho cả app ở {@code sync.SyncConfig}.
 * Nhiều máy dùng chung 1 DB cùng chạy cũng không sao: chỉ là các câu DELETE theo mốc thời gian.
 */
@Slf4j
@Component
public class IdentityCleanup {

    /** Số dòng đã xoá mỗi bảng trong 1 lần chạy. */
    public record Removed(int otpChallenges, int linkCodes, int linkCodeAttempts) {
        public int total() {
            return otpChallenges + linkCodes + linkCodeAttempts;
        }
    }

    private final OtpChallengeRepository challenges;
    private final LinkCodeRepository codes;
    private final LinkCodeAttemptRepository attempts;
    private final Clock clock;
    private final Duration retention;
    private final TransactionTemplate tx;

    IdentityCleanup(OtpChallengeRepository challenges, LinkCodeRepository codes, LinkCodeAttemptRepository attempts,
                    Clock clock, @Value("${omniai.identity.cleanup.retention:7d}") Duration retention,
                    PlatformTransactionManager transactionManager) {
        if (retention.compareTo(Duration.ofDays(1)) < 0) {
            // Ngắn hơn thì có thể xoá cả mã đang khoá hoặc lần gõ sai còn trong cửa sổ 1 giờ.
            throw new IllegalStateException("omniai.identity.cleanup.retention phải từ 1 ngày trở lên");
        }
        this.challenges = challenges;
        this.codes = codes;
        this.attempts = attempts;
        this.clock = clock;
        this.retention = retention;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${omniai.identity.cleanup.cron:0 0 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    void scheduled() {
        try {
            run();
        } catch (RuntimeException e) {
            // Lần sau chạy lại; dữ liệu cũ chỉ chiếm chỗ, không làm sai đăng nhập hay liên kết.
            log.warn("Dọn dữ liệu xác thực cũ thất bại: {}", e.toString());
        }
    }

    /**
     * Xoá trong 1 transaction. Dùng {@link TransactionTemplate} chứ không {@code @Transactional}: {@link #scheduled()}
     * gọi thẳng {@code run()} trong cùng bean nên không qua proxy, chạy thật sẽ lỗi "No active transaction".
     */
    public Removed run() {
        Instant cutoff = clock.instant().minus(retention);
        Removed removed = tx.execute(status -> new Removed(
                challenges.deleteCreatedBefore(cutoff),
                codes.deleteFinishedBefore(cutoff, LinkCode.Resolution.CONFIRMED),
                attempts.deleteAttemptedBefore(cutoff)));
        if (removed.total() > 0) {
            log.info("Dọn dữ liệu xác thực trước {}: {} OTP, {} mã liên kết, {} lần gõ sai",
                    cutoff, removed.otpChallenges(), removed.linkCodes(), removed.linkCodeAttempts());
        }
        return removed;
    }
}

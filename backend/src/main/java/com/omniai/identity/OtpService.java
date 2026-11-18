package com.omniai.identity;

import com.omniai.customer.Customer;
import com.omniai.identity.AuthErrors.OtpExpired;
import com.omniai.identity.AuthErrors.OtpInvalid;
import com.omniai.identity.AuthErrors.OtpLocked;
import com.omniai.identity.AuthErrors.OtpRateLimited;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Đăng ký = đăng nhập bằng email + OTP (US-13, SRS UC-07, NFR-08, G-02).
 * <ul>
 *   <li>Mã 6 số, hết hạn sau 5 phút. Gửi mã mới thì mã cũ mất hiệu lực.</li>
 *   <li>Gửi lại cách nhau >= 60 s, tối đa 5 lần trong 1 giờ.</li>
 *   <li>Sai 5 lần thì khoá email 15 phút: không gửi mã, không xác thực.</li>
 * </ul>
 */
@Service
public class OtpService {

    private static final Duration HOUR = Duration.ofHours(1);

    public record OtpIssued(long expiresInSeconds, long resendAfterSeconds) {
    }

    public record LoginResult(Customer customer, boolean newCustomer, TokenService.IssuedToken token) {
    }

    private final OtpChallengeRepository challenges;
    private final OtpSender sender;
    private final PasswordEncoder encoder;
    private final WebIdentityLinker linker;
    private final TokenService tokens;
    private final OtpProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository challenges, OtpSender sender, PasswordEncoder encoder,
                      WebIdentityLinker linker, TokenService tokens, OtpProperties props, Clock clock) {
        this.challenges = challenges;
        this.sender = sender;
        this.encoder = encoder;
        this.linker = linker;
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Gửi lỗi (SMTP) thì rollback, không tính vào giới hạn gửi lại. */
    @Transactional
    public OtpIssued request(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Instant now = clock.instant();

        Optional<OtpChallenge> latest = challenges.findFirstByEmailOrderByCreatedAtDescIdDesc(email);
        if (latest.isPresent()) {
            OtpChallenge last = latest.get();
            if (last.isLockedAt(now)) {
                throw new OtpLocked(secondsUntil(now, last.getLockedUntil()));
            }
            Instant nextAllowed = last.getCreatedAt().plus(props.resendInterval());
            if (nextAllowed.isAfter(now)) {
                throw new OtpRateLimited(secondsUntil(now, nextAllowed), "Vừa gửi mã, hãy chờ trước khi gửi lại");
            }
        }
        Instant windowStart = now.minus(HOUR);
        if (challenges.countByEmailAndCreatedAtAfter(email, windowStart) >= props.maxPerHour()) {
            // Được gửi lại khi mã cũ nhất trong cửa sổ 1 giờ trôi ra ngoài.
            Instant oldest = challenges.findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc(email, windowStart)
                    .map(OtpChallenge::getCreatedAt).orElse(now);
            throw new OtpRateLimited(secondsUntil(now, oldest.plus(HOUR)),
                    "Đã gửi " + props.maxPerHour() + " mã trong 1 giờ, hãy thử lại sau");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.setEmail(email);
        challenge.setCodeHash(encoder.encode(code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(props.ttl()));
        challenges.save(challenge);

        sender.send(email, code, props.ttl());
        return new OtpIssued(props.ttl().toSeconds(), props.resendInterval().toSeconds());
    }

    /** Sai mã vẫn commit để lưu số lần sai và mốc khoá. */
    @Transactional(noRollbackFor = {OtpInvalid.class, OtpLocked.class})
    public LoginResult verify(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        Instant now = clock.instant();

        OtpChallenge challenge = challenges.findFirstByEmailOrderByCreatedAtDescIdDesc(email)
                .orElseThrow(OtpExpired::new);
        if (challenge.isLockedAt(now)) {
            throw new OtpLocked(secondsUntil(now, challenge.getLockedUntil()));
        }
        if (!challenge.isUsableAt(now) || challenge.getAttempts() >= props.maxAttempts()) {
            throw new OtpExpired();
        }
        if (!encoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= props.maxAttempts()) {
                challenge.setLockedUntil(now.plus(props.lock()));
                throw new OtpLocked(props.lock().toSeconds());
            }
            throw new OtpInvalid(props.maxAttempts() - challenge.getAttempts());
        }

        challenge.setConsumedAt(now);
        WebIdentityLinker.Linked linked = linker.link(email, now);
        return new LoginResult(linked.customer(), linked.newCustomer(), tokens.issue(linked.customer()));
    }

    private static long secondsUntil(Instant now, Instant until) {
        return Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
    }
}

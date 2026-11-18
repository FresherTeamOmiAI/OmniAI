package com.omniai.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình `omniai.otp.*` (SRS NFR-08, G-02).
 *
 * @param ttl            mã hết hạn sau khoảng này
 * @param maxAttempts    sai đủ số lần này thì khoá email
 * @param lock           thời gian khoá
 * @param resendInterval khoảng cách tối thiểu giữa 2 lần gửi mã
 * @param maxPerHour     số lần gửi tối đa trong 1 giờ
 * @param sender         {@code log} (dev, in mã ra log) hoặc {@code smtp} (SRS Q-02)
 * @param mailFrom       địa chỉ người gửi khi dùng SMTP
 */
@ConfigurationProperties(prefix = "omniai.otp")
public record OtpProperties(
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration lock,
        @DefaultValue("60s") Duration resendInterval,
        @DefaultValue("5") int maxPerHour,
        @DefaultValue("log") String sender,
        @DefaultValue("no-reply@omniai.local") String mailFrom) {
}

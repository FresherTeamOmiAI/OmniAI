package com.omniai.identity;

/** Gửi mã OTP tới email. Chọn bằng {@code omniai.otp.sender}: {@code log} (mặc định) hoặc {@code smtp}. */
public interface OtpSender {

    void send(String email, String code, java.time.Duration ttl);
}

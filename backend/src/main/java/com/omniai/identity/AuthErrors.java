package com.omniai.identity;

/** Lỗi nghiệp vụ của đăng nhập OTP. {@link AuthExceptionHandler} đổi sang ProblemDetail có {@code code}. */
public final class AuthErrors {

    private AuthErrors() {
    }

    public abstract static class AuthError extends RuntimeException {
        AuthError(String message) {
            super(message);
        }

        public abstract String code();
    }

    /** Gửi lại quá sớm (< 60 s) hoặc quá 5 lần/giờ: 429. */
    public static final class OtpRateLimited extends AuthError {
        private final long retryAfterSeconds;

        OtpRateLimited(long retryAfterSeconds, String message) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public String code() {
            return "otp_rate_limited";
        }
    }

    /** Sai đủ 5 lần, đang khoá 15 phút: 423. */
    public static final class OtpLocked extends AuthError {
        private final long retryAfterSeconds;

        OtpLocked(long retryAfterSeconds) {
            super("Nhập sai mã quá nhiều lần, email tạm khoá");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public String code() {
            return "otp_locked";
        }
    }

    /** Sai mã, còn lượt: 401. */
    public static final class OtpInvalid extends AuthError {
        private final int attemptsLeft;

        OtpInvalid(int attemptsLeft) {
            super("Mã không đúng");
            this.attemptsLeft = attemptsLeft;
        }

        public int attemptsLeft() {
            return attemptsLeft;
        }

        @Override
        public String code() {
            return "otp_invalid";
        }
    }

    /** Chưa gửi mã, mã đã dùng hoặc đã hết hạn: 401, khách phải xin mã mới. */
    public static final class OtpExpired extends AuthError {
        OtpExpired() {
            super("Mã đã hết hạn hoặc đã được dùng, hãy lấy mã mới");
        }

        @Override
        public String code() {
            return "otp_expired";
        }
    }
}

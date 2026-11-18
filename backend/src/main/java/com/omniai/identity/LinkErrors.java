package com.omniai.identity;

/** Lỗi nghiệp vụ của mã liên kết OMNI. {@link LinkExceptionHandler} đổi sang ProblemDetail có {@code code}. */
public final class LinkErrors {

    private LinkErrors() {
    }

    public abstract static class LinkError extends RuntimeException {
        LinkError(String message) {
            super(message);
        }

        public abstract String code();
    }

    /** Không có mã này (gõ sai): 404, kèm số lượt còn lại trước khi bị khoá. */
    public static final class LinkCodeInvalid extends LinkError {
        private final int attemptsLeft;

        LinkCodeInvalid(int attemptsLeft) {
            super("Mã liên kết không đúng");
            this.attemptsLeft = attemptsLeft;
        }

        public int attemptsLeft() {
            return attemptsLeft;
        }

        @Override
        public String code() {
            return "link_code_invalid";
        }
    }

    /** Mã hết hạn, đã thu hồi, đã dùng; hoặc yêu cầu liên kết đã xử lý/quá hạn xác nhận: 410. */
    public static final class LinkCodeExpired extends LinkError {
        private final Integer attemptsLeft;

        LinkCodeExpired(String message, Integer attemptsLeft) {
            super(message);
            this.attemptsLeft = attemptsLeft;
        }

        /** Chỉ có khi lỗi đến từ lần gõ mã trong chat (null với API xác nhận). */
        public Integer attemptsLeft() {
            return attemptsLeft;
        }

        @Override
        public String code() {
            return "link_code_expired";
        }
    }

    /** Danh tính kênh gõ sai 5 lần trong 1 giờ: 423. */
    public static final class LinkLocked extends LinkError {
        private final long retryAfterSeconds;

        LinkLocked(long retryAfterSeconds) {
            super("Gõ sai mã quá nhiều lần, tạm khoá nhập mã");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public String code() {
            return "link_locked";
        }
    }

    /** Danh tính kênh đã gắn với một khách (không tự gộp, decisions #49): 409. */
    public static final class IdentityAlreadyLinked extends LinkError {
        IdentityAlreadyLinked() {
            super("Tài khoản trên kênh này đã được liên kết");
        }

        @Override
        public String code() {
            return "identity_already_linked";
        }
    }

    /** Không có yêu cầu liên kết này của khách đang đăng nhập: 404. */
    public static final class LinkRequestNotFound extends RuntimeException {
        LinkRequestNotFound(long id) {
            super("Không có yêu cầu liên kết id=" + id);
        }
    }

    /** Token hợp lệ nhưng khách đã bị xoá: 401. */
    public static final class CustomerGone extends RuntimeException {
        CustomerGone() {
            super("Tài khoản không còn, hãy đăng nhập lại");
        }
    }
}

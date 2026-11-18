package com.omniai.sync;

/** Lỗi khi gửi ra sàn. {@code retryable=false}: không thử lại, chuyển FAILED ngay để admin xử lý. */
public class SyncException extends RuntimeException {

    private final boolean retryable;

    public SyncException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public SyncException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}

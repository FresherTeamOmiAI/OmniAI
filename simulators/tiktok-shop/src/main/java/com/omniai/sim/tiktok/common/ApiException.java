package com.omniai.sim.tiktok.common;

/** Ném ở controller/store để trả envelope lỗi theo {@link TiktokErrorCode}. */
public class ApiException extends RuntimeException {

    private final TiktokErrorCode error;
    private final String detail;

    public ApiException(TiktokErrorCode error) {
        this(error, null);
    }

    public ApiException(TiktokErrorCode error, String detail) {
        super(detail == null ? error.message() : error.message() + " " + detail);
        this.error = error;
        this.detail = detail;
    }

    public TiktokErrorCode error() {
        return error;
    }

    public String detail() {
        return detail;
    }
}

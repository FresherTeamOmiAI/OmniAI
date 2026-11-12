package com.omniai.channel.tiktok;

import java.util.Optional;

/** TikTok trả code khác 0 (hoặc HTTP 429). Giữ request_id để tra log phía sàn. */
public class TiktokApiException extends RuntimeException {

    private final int code;
    private final String requestId;
    private final int httpStatus;

    public TiktokApiException(int code, String message, String requestId, int httpStatus) {
        super("TikTok API lỗi " + code + ": " + message + " (request_id=" + requestId + ")");
        this.code = code;
        this.requestId = requestId;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String requestId() {
        return requestId;
    }

    public Optional<TiktokErrorCode> error() {
        return TiktokErrorCode.of(code);
    }

    public boolean is(TiktokErrorCode error) {
        return code == error.code();
    }

    /** Access token sai/hết hạn: refresh rồi gọi lại 1 lần. */
    public boolean isTokenError() {
        return is(TiktokErrorCode.ACCESS_TOKEN_INVALID) || is(TiktokErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    public boolean isRateLimited() {
        return httpStatus == 429 || is(TiktokErrorCode.TOO_MANY_REQUESTS);
    }
}

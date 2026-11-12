package com.omniai.sim.tiktok.common;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi simulator trả về. Giữ đồng bộ với backend {@code com.omniai.channel.tiktok.TiktokErrorCode}.
 * <p>
 * Nhóm "docv2" lấy từ mục Error Code của từng trang API (lưu 11/11/2026).
 * Nhóm "chưa đối chiếu" là giá trị giả định vì thư mục docs không có trang Common error codes / Get Access Token;
 * khi có trang thật thì chỉ sửa số ở đây và ở backend (docs/channel-api/tiktok.md mục 1, 9).
 */
public enum TiktokErrorCode {

    // --- docv2 ---
    INTERNAL_ERROR(36009003, "Internal error. Please try again."),
    CS_INTERNAL_ERROR(45101001, "Internal error. Please try again later or contact the technical support team for help."),
    CS_INVALID_PARAMS(45101002, "Invalid params."),
    CS_RECORD_NOT_FOUND(45101003, "Record not found. Please check the parameters."),
    CS_DAILY_QUOTA_REACHED(45101004, "The query quota has been reached (10000 request per day). Please try again tomorrow."),
    CS_SENSITIVE_CONTENT(45101006, "You cannot send the message containing sensitive content. Please check the message."),
    CS_NO_PERMISSION(45102007, "No permission to access this conversation."),
    CS_CONVERSATION_RULES(45109001, "Unable to send messages due to conversation rules."),
    CS_CANNOT_CREATE_CONVERSATION(45109003, "Unable to create a conversation as you did not meet the criteria for initiating conversations."),

    // --- chưa đối chiếu ---
    INVALID_PARAMS(36009004, "Invalid params."),
    TOO_MANY_REQUESTS(36009006, "Too many requests. Please retry later.", HttpStatus.TOO_MANY_REQUESTS),
    ACCESS_TOKEN_INVALID(105001, "Invalid access token."),
    ACCESS_TOKEN_EXPIRED(105002, "Access token is expired."),
    REFRESH_TOKEN_EXPIRED(105003, "Refresh token is invalid or expired. Please re-authorize."),
    AUTH_CODE_INVALID(105004, "Auth code is invalid or has been used."),
    SCOPE_NOT_AUTHORIZED(105005, "The API scope is not authorized by the seller."),
    INVALID_SIGN(106001, "Invalid app_key or sign."),
    INVALID_TIMESTAMP(106002, "Invalid timestamp."),
    INVALID_SHOP_CIPHER(106011, "Invalid shop_cipher.");

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    TiktokErrorCode(int code, String message) {
        this(code, message, HttpStatus.OK);
    }

    TiktokErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** TikTok trả lỗi nghiệp vụ bằng HTTP 200 + code khác 0; riêng rate limit dùng 429. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}

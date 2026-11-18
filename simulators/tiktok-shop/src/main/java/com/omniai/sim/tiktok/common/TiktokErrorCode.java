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
    // Products (docv2: Create/Edit Product, Update Price, Update Inventory, Upload Product Image, US-50)
    PRODUCT_DESCRIPTION_INVALID(12019006, "product description is invalid"),
    PRODUCT_PACKAGE_WEIGHT_INVALID(12019011, "product package weight is invalid"),
    PRODUCT_INVENTORY_INVALID(12019024, "The inventory quantity is invalid."),
    PRODUCT_NOT_EXIST(12052032, "The product does not exist"),
    PRODUCT_SKU_IDS_REQUIRED(12052036, "Provide a valid SKU ID for every SKU in the request."),
    PRODUCT_CATEGORY_NOT_EXIST(12052023, "Category does not exist"),
    PRODUCT_MAIN_IMAGE_REQUIRED(12052028, "Main product image is required"),
    PRODUCT_INVENTORY_OUT_OF_RANGE(12052055, "The inventory quantity for a SKU at a warehouse is outside the allowed range."),
    PRODUCT_PRICE_INVALID(12052073, "The product price is invalid"),
    PRODUCT_CURRENCY_NOT_AVAILABLE(12052084, "The provided currency is not available for this shop/region."),
    PRODUCT_WAREHOUSE_NOT_EXIST(12052097, "The warehouse does not exist"),
    PRODUCT_ID_NOT_EXIST(12052260, "The product ID does not exist"),
    PRODUCT_MAIN_IMAGE_URI_ILLEGAL(12052300, "product main image uri illegal"),
    PRODUCT_SKU_NOT_EXIST(12052556, "The SKU id not exist."),
    PRODUCT_SKU_LIST_EMPTY(12052902, "The SKU list is empty."),
    IMAGE_INVALID_PARAM(11050001, "invalid param"),
    IMAGE_INVALID_FILE(12038002, "Invalid upload file."),

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

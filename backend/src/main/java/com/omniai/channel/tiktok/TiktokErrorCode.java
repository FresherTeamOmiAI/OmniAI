package com.omniai.channel.tiktok;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mã lỗi TikTok Shop mà hub phân biệt để xử lý. Giữ đồng bộ với simulator {@code com.omniai.sim.tiktok.common.TiktokErrorCode}.
 * Nhóm "docv2" lấy từ trang API; nhóm "chưa đối chiếu" là giá trị giả định (docs/channel-api/tiktok.md mục 9).
 */
public enum TiktokErrorCode {
    // docv2
    INTERNAL_ERROR(36009003),
    CS_INVALID_PARAMS(45101002),
    CS_RECORD_NOT_FOUND(45101003),
    CS_DAILY_QUOTA_REACHED(45101004),
    CS_SENSITIVE_CONTENT(45101006),
    CS_NO_PERMISSION(45102007),
    CS_CONVERSATION_RULES(45109001),
    // chưa đối chiếu
    INVALID_PARAMS(36009004),
    TOO_MANY_REQUESTS(36009006),
    ACCESS_TOKEN_INVALID(105001),
    ACCESS_TOKEN_EXPIRED(105002),
    REFRESH_TOKEN_EXPIRED(105003),
    AUTH_CODE_INVALID(105004),
    SCOPE_NOT_AUTHORIZED(105005),
    INVALID_SIGN(106001),
    INVALID_TIMESTAMP(106002),
    INVALID_SHOP_CIPHER(106011);

    private final int code;

    TiktokErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<TiktokErrorCode> of(int code) {
        return Arrays.stream(values()).filter(e -> e.code == code).findFirst();
    }
}

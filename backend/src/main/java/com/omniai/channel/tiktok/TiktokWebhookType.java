package com.omniai.channel.tiktok;

/**
 * Số `type` trong envelope webhook mà hub xử lý. CHƯA ĐỐI CHIẾU, giữ trùng với simulator {@code WebhookType}
 * (docs/channel-api/tiktok.md mục 5).
 */
public final class TiktokWebhookType {

    public static final int ORDER_STATUS_CHANGE = 1;
    public static final int SELLER_DEAUTHORIZATION = 6;
    public static final int UPCOMING_AUTHORIZATION_EXPIRATION = 7;
    public static final int NEW_CONVERSATION = 13;
    public static final int NEW_MESSAGE = 14;

    /** event_type hub đăng ký qua Update Shop Webhook sau khi seller ủy quyền. */
    public static final java.util.List<String> SUBSCRIBED_EVENTS = java.util.List.of(
            "NEW_MESSAGE", "NEW_CONVERSATION", "SELLER_DEAUTHORIZATION", "UPCOMING_AUTHORIZATION_EXPIRATION");

    private TiktokWebhookType() {
    }
}

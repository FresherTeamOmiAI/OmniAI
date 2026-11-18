package com.omniai.sim.tiktok.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * event_type đăng ký qua Update Shop Webhook (tên lấy từ docv2, 17 loại) và số `type` trong envelope webhook.
 * Số `type` CHƯA ĐỐI CHIẾU (thư mục docs không có trang Webhooks overview); backend dùng cùng bảng số.
 */
public enum WebhookType {
    ORDER_STATUS_CHANGE(1),
    REVERSE_STATUS_UPDATE(2),
    RECIPIENT_ADDRESS_UPDATE(3),
    PACKAGE_UPDATE(4),
    PRODUCT_STATUS_CHANGE(5),
    SELLER_DEAUTHORIZATION(6),
    UPCOMING_AUTHORIZATION_EXPIRATION(7),
    CANCELLATION_STATUS_CHANGE(11),
    RETURN_STATUS_CHANGE(12),
    NEW_CONVERSATION(13),
    NEW_MESSAGE(14),
    PRODUCT_INFORMATION_CHANGE(15),
    PRODUCT_CREATION(16),
    PRODUCT_CATEGORY_CHANGE(17),
    NEW_MESSAGE_LISTENER(33),
    INVOICE_STATUS_CHANGE(34),
    PRODUCT_AUDIT_STATUS_CHANGE(35);

    private final int type;

    WebhookType(int type) {
        this.type = type;
    }

    public int type() {
        return type;
    }

    public static Optional<WebhookType> parse(String eventType) {
        return Arrays.stream(values()).filter(t -> t.name().equals(eventType)).findFirst();
    }
}

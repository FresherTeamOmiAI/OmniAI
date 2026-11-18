package com.omniai.common;

/**
 * Các kênh hệ thống hỗ trợ. OWNED = kênh shop sở hữu, được phép so sánh kênh và gửi link.
 * Telegram và Messenger đã bỏ ngày 11/11/2026 (decisions #22); Shopee, TikTok Shop chạy bằng simulator.
 */
public enum ChannelType {
    WEB(true),
    SHOPEE(false),
    TIKTOK(false);

    private final boolean owned;

    ChannelType(boolean owned) {
        this.owned = owned;
    }

    public boolean isOwned() {
        return owned;
    }
}

package com.omniai.common;

/** Các kênh hệ thống hỗ trợ. OWNED = kênh shop sở hữu, được phép so sánh kênh và gửi link. */
public enum ChannelType {
    WEB(true),
    TELEGRAM(true),
    MESSENGER(true),
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

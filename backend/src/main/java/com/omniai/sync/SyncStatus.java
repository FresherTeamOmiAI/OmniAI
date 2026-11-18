package com.omniai.sync;

/** Trạng thái đồng bộ 1 sản phẩm ra 1 kênh (cột status của product_channel_sync, V18). */
public enum SyncStatus {
    /** Chờ gửi: vừa đổi, hoặc gửi lỗi và đang chờ lượt thử lại. */
    PENDING,
    /** Sàn đã nhận bản mới nhất. */
    SYNCED,
    /** Hết lượt thử hoặc lỗi không thử lại được. Admin xem lỗi và bấm thử lại. */
    FAILED
}

package com.omniai.sync;

/**
 * Phần dữ liệu của 1 sản phẩm được đồng bộ riêng ra 1 kênh (cột aspect, V19).
 * PRODUCT: nội dung (tên, mô tả, ảnh, INCI, ẩn/hiện), giữ mã sản phẩm phía sàn. PRICE: giá niêm yết của đúng kênh đó
 * (US-50). STOCK: tồn kho, chung mọi kênh.
 */
public enum SyncAspect {
    PRODUCT, PRICE, STOCK
}

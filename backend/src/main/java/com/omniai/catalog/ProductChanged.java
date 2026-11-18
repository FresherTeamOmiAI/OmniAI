package com.omniai.catalog;

/**
 * Sự kiện: admin vừa tạo, sửa hoặc ẩn/hiện 1 sản phẩm. Phát trong transaction của {@link ProductService}, nên
 * listener chạy đồng bộ (@EventListener) ghi được hàng đợi đồng bộ ra sàn cùng transaction (US-49, module sync).
 * Catalog không biết ai nghe.
 */
public record ProductChanged(Long productId) {
}

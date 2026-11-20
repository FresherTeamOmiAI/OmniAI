package com.omniai.catalog;

import java.util.List;

/**
 * Sự kiện: giá niêm yết của 1 sản phẩm vừa đổi ở 1 hoặc nhiều kênh (tạo mới cũng tính, giá cũ null). Phát trong
 * transaction của {@link ProductService}; module promotion nghe để ghi lịch sử giá (US-33). Kênh không đổi giá thì
 * không có trong {@code changes}.
 */
public record ProductPricesChanged(Long productId, List<Change> changes) {

    public record Change(String channelCode, Long oldPrice, long newPrice) {
    }
}

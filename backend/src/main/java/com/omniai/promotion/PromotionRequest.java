package com.omniai.promotion;

import java.util.List;

/**
 * Body tạo/sửa khuyến mãi (admin, T-33.1). Kiểu dữ liệu để rộng (String) để mọi lỗi về trong {@code errors[]} theo
 * field thay vì 1 lỗi "JSON sai". {@code startsAt}/{@code endsAt}: ISO-8601 có offset
 * ({@code 2026-12-12T00:00:00+07:00}) hoặc không offset ({@code 2026-12-12T00:00}, hiểu là giờ Asia/Ho_Chi_Minh).
 *
 * @param type           VOUCHER hoặc FLASH_SALE
 * @param voucherCode    chỉ VOUCHER: 3–20 chữ/số, tự viết hoa, không trùng trong kênh
 * @param discountAmount chỉ VOUCHER: số tiền giảm (VND) &gt; 0
 * @param minOrderAmount chỉ VOUCHER: đơn tối thiểu (VND), mặc định 0
 * @param enabled        mặc định true
 * @param items          chỉ FLASH_SALE: ít nhất 1 dòng, giá sale thấp hơn giá niêm yết ở kênh đó
 */
public record PromotionRequest(String channel, String type, String name, String voucherCode, Long discountAmount,
                               Long minOrderAmount, String startsAt, String endsAt, Boolean enabled,
                               List<Item> items) {

    public record Item(String sku, Long salePrice) {
    }
}

package com.omniai.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body của POST /api/orders (T-09.3, SRS UC-06): khách chọn sản phẩm và số lượng.
 * Không có thông tin thanh toán, địa chỉ hay khách hàng: S2 là đơn DEMO, không cần đăng nhập (G-04).
 */
public record OrderRequest(
        @NotEmpty(message = "cần ít nhất 1 sản phẩm")
        @Size(max = 20, message = "tối đa 20 dòng")
        List<@Valid @NotNull(message = "bắt buộc") Line> items) {

    public record Line(
            @NotNull(message = "bắt buộc") Long productId,
            @NotNull(message = "bắt buộc")
            @Min(value = 1, message = "phải >= 1")
            @Max(value = OrderService.MAX_QUANTITY, message = "tối đa " + OrderService.MAX_QUANTITY) Integer quantity) {
    }
}

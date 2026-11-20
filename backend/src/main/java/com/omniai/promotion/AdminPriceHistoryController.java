package com.omniai.promotion;

import com.omniai.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lịch sử giá niêm yết của 1 sản phẩm cho admin (US-33). Cần HTTP Basic admin (chain {@code /api/admin/**}). */
@RestController
public class AdminPriceHistoryController {

    private final PriceHistoryService service;

    public AdminPriceHistoryController(PriceHistoryService service) {
        this.service = service;
    }

    /** Mới nhất trước. {@code channel} để trống = mọi kênh. */
    @GetMapping("/api/admin/products/{id}/price-history")
    public PageResponse<PriceHistoryService.Entry> history(@PathVariable Long id,
                                                           @RequestParam(required = false) String channel,
                                                           @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(service.history(id, channel, pageable));
    }
}

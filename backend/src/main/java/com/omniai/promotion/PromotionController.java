package com.omniai.promotion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khuyến mãi đang chạy theo kênh, công khai (US-33 AC; store và tool {@code get_promotions} của ai-brain, US-34).
 * Voucher vốn hiện công khai trên sàn nên không cần đăng nhập, giống {@code GET /api/products/sku/{sku}}.
 */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    /**
     * {@code channel} bắt buộc (WEB, SHOPEE, TIKTOK...). {@code upcomingDays} 0–30 (mặc định 0): thêm khuyến mãi bắt
     * đầu trong số ngày đó. {@code sku}: chỉ flash sale có SKU đó và voucher của kênh.
     */
    @GetMapping
    public List<PromotionView> list(@RequestParam(required = false) String channel,
                                    @RequestParam(required = false) String sku,
                                    @RequestParam(required = false) Integer upcomingDays) {
        return service.visible(channel, sku, upcomingDays);
    }
}

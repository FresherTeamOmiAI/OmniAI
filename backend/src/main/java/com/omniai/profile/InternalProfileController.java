package com.omniai.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hồ sơ da cho ai-brain (US-19): T-19.2 {@code get_customer_profile} đọc, T-19.1 ghi sau khi khách xác nhận và đồng ý
 * trong chat. Dưới /api/internal/** nên cần header {@code X-Internal-Token} (SecurityConfig, decisions #48).
 * {@code customerId} lấy từ {@code ChatRequest.customer_id} (chỉ có khi danh tính kênh đã liên kết). Owner: LucVT.
 */
@RestController
@RequestMapping("/api/internal/customers/{customerId}/profile")
public class InternalProfileController {

    private final SkinProfileService service;

    public InternalProfileController(SkinProfileService service) {
        this.service = service;
    }

    @GetMapping
    public SkinProfileView get(@PathVariable long customerId) {
        return service.get(customerId);
    }

    @PutMapping
    public SkinProfileView save(@PathVariable long customerId, @RequestBody SkinProfileRequest request) {
        return service.save(customerId, request, SkinProfile.Source.CHAT);
    }
}

package com.omniai.profile;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Khách tự xem, khai, xoá hồ sơ da trên web (US-19, UC-13 bước 1 "qua form"). Nằm dưới /api/me/** nên cần JWT người
 * mua (SecurityConfig.customerFilterChain); khách = {@code sub} của token. Owner: LucVT.
 */
@RestController
@RequestMapping("/api/me/profile")
public class MeProfileController {

    private final SkinProfileService service;

    public MeProfileController(SkinProfileService service) {
        this.service = service;
    }

    @GetMapping
    public SkinProfileView get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(customerId(jwt));
    }

    @PutMapping
    public SkinProfileView save(@AuthenticationPrincipal Jwt jwt, @RequestBody SkinProfileRequest request) {
        return service.save(customerId(jwt), request, SkinProfile.Source.FORM);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        service.delete(customerId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static long customerId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

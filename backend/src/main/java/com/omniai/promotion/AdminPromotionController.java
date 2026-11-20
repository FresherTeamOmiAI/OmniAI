package com.omniai.promotion;

import com.omniai.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** API quản trị khuyến mãi (US-33, UC-21). Cần HTTP Basic tài khoản admin (chain {@code /api/admin/**}). */
@RestController
@RequestMapping("/api/admin/promotions")
public class AdminPromotionController {

    private final PromotionService service;

    public AdminPromotionController(PromotionService service) {
        this.service = service;
    }

    /** {@code channel}, {@code status} (SCHEDULED, ACTIVE, EXPIRED, DISABLED) để trống = tất cả. Mới bắt đầu trước. */
    @GetMapping
    public PageResponse<PromotionView> list(@RequestParam(required = false) String channel,
                                            @RequestParam(required = false) Promotion.Status status,
                                            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.DESC)
                                            Pageable pageable) {
        return PageResponse.of(service.search(channel, status, pageable));
    }

    @GetMapping("/{id}")
    public PromotionView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PromotionView> create(@RequestBody PromotionRequest request) {
        PromotionView created = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/promotions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public PromotionView update(@PathVariable Long id, @RequestBody PromotionRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public PromotionView setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return service.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }
}

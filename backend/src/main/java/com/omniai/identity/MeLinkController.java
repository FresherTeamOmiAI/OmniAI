package com.omniai.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mã liên kết OMNI phía web (US-22, T-22.1): lấy mã (T-22.2) và xác nhận/từ chối yêu cầu liên kết (T-22.4).
 * Nằm dưới /api/me nên cần JWT của khách; {@code customerId} = {@code sub}.
 */
@RestController
public class MeLinkController {

    public record ConfirmRequest(
            @NotNull(message = "Cần đồng ý xử lý dữ liệu cá nhân")
            @AssertTrue(message = "Cần đồng ý xử lý dữ liệu cá nhân") Boolean consent) {
    }

    private final LinkCodeService links;

    public MeLinkController(LinkCodeService links) {
        this.links = links;
    }

    @PostMapping("/api/me/link-codes")
    public ResponseEntity<LinkCodeService.LinkCodeIssued> issue(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(links.issue(customerId(jwt)));
    }

    @GetMapping("/api/me/link-requests")
    public List<LinkCodeService.LinkRequestView> pending(@AuthenticationPrincipal Jwt jwt) {
        return links.pending(customerId(jwt));
    }

    @PostMapping("/api/me/link-requests/{id}/confirm")
    public LinkCodeService.Linked confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                          @Valid @RequestBody ConfirmRequest request) {
        return links.confirm(customerId(jwt), id);
    }

    @PostMapping("/api/me/link-requests/{id}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        links.reject(customerId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static long customerId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

package com.omniai.sim.tiktok.event;

import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.ApiResponse;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.gateway.GatewayFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Event 202309: Get / Update / Delete Shop Webhooks (docv2). */
@RestController
@RequestMapping("/event/202309/webhooks")
public class EventController {

    private final WebhookRegistry registry;

    public EventController(WebhookRegistry registry) {
        this.registry = registry;
    }

    public record Webhooks(List<WebhookRegistry.Registration> webhooks, int totalCount) {
    }

    public record UpdateWebhookRequest(@NotBlank @Size(max = 255) String address, @NotBlank String eventType) {
    }

    public record DeleteWebhookRequest(@NotBlank String eventType) {
    }

    @GetMapping
    public ApiResponse<Webhooks> list(HttpServletRequest request) {
        List<WebhookRegistry.Registration> list = registry.list(GatewayFilter.grant(request).shopId());
        return ApiResponse.ok(new Webhooks(list, list.size()));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@Valid @RequestBody UpdateWebhookRequest body,
                                                   HttpServletRequest request) {
        if (!body.address().matches("(?i)^https?://\\S+$")) {
            throw new ApiException(TiktokErrorCode.INVALID_PARAMS, "address must be an HTTP or HTTPS URL.");
        }
        registry.put(GatewayFilter.grant(request).shopId(), parse(body.eventType()), body.address());
        return ApiResponse.okEmpty();
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> delete(@Valid @RequestBody DeleteWebhookRequest body,
                                                   HttpServletRequest request) {
        registry.remove(GatewayFilter.grant(request).shopId(), parse(body.eventType()));
        return ApiResponse.okEmpty();
    }

    private static WebhookType parse(String eventType) {
        return WebhookType.parse(eventType)
                .orElseThrow(() -> new ApiException(TiktokErrorCode.INVALID_PARAMS, "Unknown event_type " + eventType + "."));
    }
}

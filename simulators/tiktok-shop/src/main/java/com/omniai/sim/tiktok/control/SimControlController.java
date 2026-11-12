package com.omniai.sim.tiktok.control;

import com.omniai.sim.tiktok.auth.TokenStore;
import com.omniai.sim.tiktok.cs.CsModels.Message;
import com.omniai.sim.tiktok.cs.CustomerService;
import com.omniai.sim.tiktok.event.WebhookDispatcher;
import com.omniai.sim.tiktok.event.WebhookRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API điều khiển chỉ có ở simulator (/sim/*): đóng vai buyer, bật lỗi, xem log webhook, reset.
 * Không có trên sàn thật, không qua GatewayFilter. JSON vẫn snake_case cho đồng bộ.
 */
@RestController
@RequestMapping("/sim")
public class SimControlController {

    private final CustomerService cs;
    private final WebhookDispatcher webhooks;
    private final WebhookRegistry registry;
    private final TokenStore tokens;
    private final Faults faults;

    public SimControlController(CustomerService cs, WebhookDispatcher webhooks, WebhookRegistry registry,
                                TokenStore tokens, Faults faults) {
        this.cs = cs;
        this.webhooks = webhooks;
        this.registry = registry;
        this.tokens = tokens;
        this.faults = faults;
    }

    /** wait_ms > 0: chờ shop trả lời trong hội thoại đó tối đa wait_ms (bench dùng để đo độ trễ đầu-cuối). */
    public record BuyerMessageRequest(@NotBlank String buyerUserId, String nickname,
                                      @NotBlank @Size(max = 2000) String text,
                                      @Min(0) @Max(60_000) Long waitMs) {
    }

    public record BuyerMessageResponse(String conversationId, String messageId, String ttsNotificationId,
                                       boolean newConversation, Message reply, Long replyLatencyMs) {
    }

    @PostMapping("/buyer/messages")
    public BuyerMessageResponse buyerMessage(@Valid @RequestBody BuyerMessageRequest body) {
        long started = System.nanoTime();
        var result = cs.buyerSays(body.buyerUserId(), body.nickname(), body.text());
        long waitMs = body.waitMs() == null ? 0 : body.waitMs();
        CompletableFuture<Message> reply = waitMs > 0 ? cs.expectReply(result.conversationId()) : null;
        String notificationId = cs.notifyBuyerMessage(result.conversationId(), result.message());
        Message got = reply == null ? null : CustomerService.await(reply, Duration.ofMillis(waitMs)).orElse(null);
        Long latency = got == null ? null : (System.nanoTime() - started) / 1_000_000;
        return new BuyerMessageResponse(result.conversationId(), result.message().id(), notificationId,
                result.newConversation(), got, latency);
    }

    @GetMapping("/faults")
    public Faults.Snapshot faults() {
        return faults.snapshot();
    }

    @PostMapping("/faults")
    public Faults.Snapshot updateFaults(@RequestBody Faults.Update update) {
        return faults.apply(update);
    }

    /** Cho token hết hạn ngay. kind=access: chỉ access token; kind=refresh: cả refresh token (phải ủy quyền lại). */
    @PostMapping("/tokens/expire")
    public Map<String, Object> expireTokens(@RequestParam(defaultValue = "access") String kind) {
        if (!kind.equals("access") && !kind.equals("refresh")) {
            throw new IllegalArgumentException("kind phải là access hoặc refresh");
        }
        return Map.of("expired", tokens.expireAll(kind.equals("refresh")), "kind", kind);
    }

    @GetMapping("/deliveries")
    public List<WebhookDispatcher.Delivery> deliveries() {
        return webhooks.deliveries();
    }

    /** Xóa hội thoại, token, webhook đã đăng ký, log giao và tắt mọi fault. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        cs.clear();
        tokens.clear();
        registry.clear();
        webhooks.clear();
        faults.reset();
        return Map.of("reset", true);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "fields",
                e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + " " + f.getDefaultMessage()).toList()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", e.getMessage()));
    }
}

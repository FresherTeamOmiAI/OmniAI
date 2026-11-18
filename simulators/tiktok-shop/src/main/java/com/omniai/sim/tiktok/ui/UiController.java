package com.omniai.sim.tiktok.ui;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.auth.TokenStore;
import com.omniai.sim.tiktok.cs.CustomerService;
import com.omniai.sim.tiktok.event.WebhookDispatcher;
import com.omniai.sim.tiktok.event.WebhookRegistry;
import com.omniai.sim.tiktok.product.ProductCatalog;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu cho trang kiểm tra trực quan /ui (chỉ có ở simulator): hub đã gửi gì lên sàn (sản phẩm, tin trả lời,
 * lời gọi API) và sàn đã bắn gì về hub (webhook). Không qua GatewayFilter. Thao tác (buyer nhắn, fault, token,
 * reset) dùng lại các API /sim/* sẵn có.
 */
@RestController
public class UiController {

    private final SimProperties props;
    private final CustomerService cs;
    private final ProductCatalog products;
    private final TokenStore tokens;
    private final WebhookRegistry registry;
    private final WebhookDispatcher webhooks;
    private final ApiCallLog calls;
    private final Clock clock;

    public UiController(SimProperties props, CustomerService cs, ProductCatalog products, TokenStore tokens,
                        WebhookRegistry registry, WebhookDispatcher webhooks, ApiCallLog calls, Clock clock) {
        this.props = props;
        this.cs = cs;
        this.products = products;
        this.tokens = tokens;
        this.registry = registry;
        this.webhooks = webhooks;
        this.calls = calls;
        this.clock = clock;
    }

    @GetMapping({"/", "/ui", "/ui/"})
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/ui/index.html").build();
    }

    /** Token chỉ hiện 8 ký tự đầu. Thời gian hết hạn tính bằng giây epoch. */
    public record TokenView(String shopId, String accessToken, long accessExpiresAt, boolean accessExpired,
                            long refreshExpiresAt, boolean refreshExpired, List<String> scopes) {
    }

    public record Counts(int conversations, int messages, int products, int deliveries, int failedDeliveries,
                         int apiCalls, int apiErrors) {
    }

    public record Overview(SimProperties.Shop shop, String appKey, String defaultWebhookAddress,
                           List<WebhookRegistry.Registration> webhooks, List<TokenView> tokens, Counts counts,
                           long serverTime) {
    }

    @GetMapping("/sim/ui/overview")
    public Overview overview() {
        Instant now = clock.instant();
        List<TokenView> grants = tokens.grants().stream()
                .map(g -> new TokenView(g.shopId(), mask(g.accessToken()), g.accessExpiresAt().getEpochSecond(),
                        !g.accessExpiresAt().isAfter(now), g.refreshExpiresAt().getEpochSecond(),
                        !g.refreshExpiresAt().isAfter(now), g.scopes()))
                .toList();
        List<CustomerService.ConversationDump> conversations = cs.dump();
        List<WebhookDispatcher.Delivery> deliveries = webhooks.deliveries();
        List<ApiCallLog.Call> all = calls.list(0);
        Counts counts = new Counts(conversations.size(),
                conversations.stream().mapToInt(c -> c.messages().size()).sum(),
                products.all().size(), deliveries.size(),
                (int) deliveries.stream().filter(d -> !d.delivered()).count(),
                all.size(), (int) all.stream().filter(ApiCallLog.Call::error).count());
        return new Overview(props.shop(), props.appKey(), props.defaultWebhookAddress(),
                registry.list(props.shop().id()), grants, counts, now.getEpochSecond());
    }

    @GetMapping("/sim/ui/conversations")
    public List<CustomerService.ConversationDump> conversations() {
        return cs.dump();
    }

    @GetMapping("/sim/ui/api-calls")
    public List<ApiCallLog.Call> apiCalls(@RequestParam(name = "after", defaultValue = "0") long after) {
        return calls.list(after);
    }

    @PostMapping("/sim/ui/api-calls/clear")
    public Map<String, Object> clearApiCalls() {
        calls.clear();
        return Map.of("cleared", true);
    }

    /** Ảnh hub đã upload qua Upload Product Image, để xem trên tab sản phẩm. */
    @GetMapping("/sim/ui/images")
    public ResponseEntity<byte[]> image(@RequestParam String uri) {
        return products.imageData(uri)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(sniff(bytes))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Đoán kiểu ảnh từ vài byte đầu (Upload Product Image không giữ content type). */
    static MediaType sniff(byte[] b) {
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return MediaType.IMAGE_PNG;
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String mask(String token) {
        if (token == null) return null;
        return token.length() <= 8 ? "****" : token.substring(0, 8) + "…";
    }
}

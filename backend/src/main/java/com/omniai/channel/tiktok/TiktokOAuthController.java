package com.omniai.channel.tiktok;

import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.credential.ChannelCredentialRepository;
import com.omniai.channel.tiktok.TiktokModels.AuthorizedShop;
import com.omniai.channel.tiktok.TiktokModels.AuthorizedShops;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.common.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ủy quyền OAuth2 của seller TikTok Shop (docs/channel-api/tiktok.md mục 4). Owner: TuTM.
 * <ol>
 *   <li>Admin gọi GET /api/admin/channels/tiktok/authorize (Basic) để lấy URL, mở bằng trình duyệt.</li>
 *   <li>Seller đồng ý → sàn chuyển về GET /api/channels/tiktok/oauth/callback?code&state.</li>
 *   <li>Hub đổi code lấy token → Get Authorized Shops (lấy cipher) → lưu channel_credential → đăng ký webhook.</li>
 * </ol>
 */
@RestController
public class TiktokOAuthController {

    private static final Logger log = LoggerFactory.getLogger(TiktokOAuthController.class);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final TiktokProperties props;
    private final TiktokApi api;
    private final TiktokTokenManager tokens;
    private final TiktokShopClient client;
    private final ChannelCredentialRepository repository;
    private final SecureRandom random = new SecureRandom();
    /** state → hạn dùng. Chống CSRF: callback chỉ nhận state do chính hub sinh, dùng 1 lần. */
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    public TiktokOAuthController(TiktokProperties props, TiktokApi api, TiktokTokenManager tokens,
                                 TiktokShopClient client, ChannelCredentialRepository repository) {
        this.props = props;
        this.api = api;
        this.tokens = tokens;
        this.client = client;
        this.repository = repository;
    }

    public record AuthorizeUrl(String authorizeUrl, Instant stateExpiresAt) {
    }

    /** Thông tin credential trả ra ngoài, không kèm token. */
    public record CredentialView(String shopId, String shopName, ChannelCredential.Status status,
                                 Instant accessExpiresAt, Instant refreshExpiresAt, List<String> scopes) {
        static CredentialView of(ChannelCredential c) {
            return new CredentialView(c.getShopId(), c.getShopName(), c.getStatus(),
                    c.getAccessExpiresAt().truncatedTo(ChronoUnit.SECONDS),
                    c.getRefreshExpiresAt().truncatedTo(ChronoUnit.SECONDS), c.scopeList());
        }
    }

    @GetMapping("/api/admin/channels/tiktok/authorize")
    public AuthorizeUrl authorizeUrl() {
        Instant now = Instant.now();
        states.entrySet().removeIf(e -> e.getValue().isBefore(now));
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        String state = HexFormat.of().formatHex(buf);
        Instant expires = now.plus(STATE_TTL);
        states.put(state, expires);
        String url = UriComponentsBuilder.fromUriString(props.authorizeUrl())
                .queryParam("app_key", props.appKey())
                .queryParam("state", state)
                .encode().build().toUriString();
        return new AuthorizeUrl(url, expires);
    }

    @GetMapping("/api/admin/channels/tiktok/credentials")
    public List<CredentialView> credentials() {
        return repository.findByChannelOrderByShopId(ChannelType.TIKTOK).stream().map(CredentialView::of).toList();
    }

    @GetMapping("/api/channels/tiktok/oauth/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestParam(required = false) String code,
                                                        @RequestParam(required = false) String state) {
        Instant expires = state == null ? null : states.remove(state);
        if (expires == null || expires.isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_state"));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "missing_code"));
        }
        TokenData token;
        AuthorizedShops shops;
        try {
            token = api.getToken(code);
            shops = api.call(HttpMethod.GET, "/authorization/202309/shops", Map.of(), null,
                    token.accessToken(), null, AuthorizedShops.class);
        } catch (TiktokApiException e) {
            log.warn("Ủy quyền TikTok thất bại: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "tiktok_error", "code", e.code(), "message", e.getMessage()));
        }

        List<CredentialView> saved = new ArrayList<>();
        for (AuthorizedShop shop : shops.shops()) {
            ChannelCredential c = tokens.saveAuthorization(token, shop);
            client.registerWebhooks(shop.id(), props.webhookUrl(), TiktokWebhookType.SUBSCRIBED_EVENTS);
            log.info("Đã ủy quyền shop TikTok {} ({}), đăng ký webhook {}", shop.id(), shop.name(), props.webhookUrl());
            saved.add(CredentialView.of(c));
        }
        return ResponseEntity.ok(Map.of("shops", saved));
    }
}

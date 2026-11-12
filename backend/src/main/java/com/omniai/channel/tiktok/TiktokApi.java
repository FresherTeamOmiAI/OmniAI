package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.TokenData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Gọi HTTP tới TikTok Shop (S2: simulator): thêm tham số chung, ký request, đọc envelope {code, message, request_id, data}.
 * Tự giới hạn số request đồng thời ({@code max-concurrent} < 50 của sàn), gặp 429 thì chờ rồi thử lại.
 * Không biết gì về token lưu ở đâu: việc đó của {@link TiktokTokenManager}.
 */
@Component
public class TiktokApi {

    private static final Logger log = LoggerFactory.getLogger(TiktokApi.class);
    static final String ACCESS_TOKEN_HEADER = "x-tts-access-token";

    private final TiktokProperties props;
    private final RestClient rest;
    private final Clock clock;
    private final Semaphore concurrency;

    @Autowired
    public TiktokApi(TiktokProperties props) {
        this(props, RestClient.builder().requestFactory(requestFactory(props)), Clock.systemUTC());
    }

    /** Test truyền builder đã gắn MockRestServiceServer và clock cố định. */
    TiktokApi(TiktokProperties props, RestClient.Builder builder, Clock clock) {
        this.props = props;
        this.rest = builder.build();
        this.clock = clock;
        this.concurrency = new Semaphore(props.maxConcurrent());
    }

    private static SimpleClientHttpRequestFactory requestFactory(TiktokProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeout());
        factory.setReadTimeout(props.timeout());
        return factory;
    }

    // ---------- token (host auth, không ký) ----------

    public TokenData getToken(String authCode) {
        return tokenCall("/api/v2/token/get", "auth_code", authCode, "authorized_code");
    }

    public TokenData refreshToken(String refreshToken) {
        return tokenCall("/api/v2/token/refresh", "refresh_token", refreshToken, "refresh_token");
    }

    private TokenData tokenCall(String path, String credentialParam, String credential, String grantType) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("app_key", props.appKey());
        query.put("app_secret", props.appSecret());
        query.put(credentialParam, credential);
        query.put("grant_type", grantType);
        Raw raw = send(HttpMethod.GET, uri(path, query), null, null);
        return TiktokModels.JSON.treeToValue(unwrap(raw), TokenData.class);
    }

    // ---------- API mở (ký) ----------

    /**
     * Gọi 1 API có ký. shopCipher null với API không theo shop (Get Authorized Shops).
     * Lỗi nghiệp vụ ném {@link TiktokApiException}; 429 thì thử lại tối đa {@code max-retries} lần.
     */
    public <T> T call(HttpMethod method, String path, Map<String, String> params, Object body,
                      String accessToken, String shopCipher, Class<T> dataType) {
        String bodyJson = body == null ? null : TiktokModels.JSON.writeValueAsString(body);
        for (int attempt = 1; ; attempt++) {
            Map<String, String> query = new LinkedHashMap<>(params);
            query.put("app_key", props.appKey());
            query.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
            if (shopCipher != null) {
                query.put("shop_cipher", shopCipher);
            }
            query.put("sign", TiktokSigner.signRequest(path, query, bodyJson, props.appSecret()));
            try {
                JsonNode data = unwrap(send(method, uri(path, query), bodyJson, accessToken));
                return dataType == Void.class ? null : TiktokModels.JSON.treeToValue(data, dataType);
            } catch (TiktokApiException e) {
                if (!e.isRateLimited() || attempt >= props.maxRetries()) {
                    throw e;
                }
                log.info("TikTok rate limit ở {} (lần {}), chờ rồi thử lại", path, attempt);
                sleep(props.retryBackoff().toMillis() * attempt);
            }
        }
    }

    private record Raw(int status, String body) {
    }

    private Raw send(HttpMethod method, URI uri, String body, String accessToken) {
        boolean acquired;
        try {
            acquired = concurrency.tryAcquire(props.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ lượt gọi TikTok", e);
        }
        if (!acquired) {
            throw new TiktokApiException(TiktokErrorCode.TOO_MANY_REQUESTS.code(),
                    "Hub đang có quá nhiều request tới TikTok", null, 429);
        }
        try {
            RestClient.RequestBodySpec spec = rest.method(method).uri(uri).contentType(MediaType.APPLICATION_JSON);
            if (accessToken != null) {
                spec.header(ACCESS_TOKEN_HEADER, accessToken);
            }
            if (body != null) {
                spec.body(body);
            }
            return spec.exchange((request, response) -> new Raw(response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } finally {
            concurrency.release();
        }
    }

    private static JsonNode unwrap(Raw raw) {
        JsonNode root;
        try {
            root = TiktokModels.JSON.readTree(raw.body());
        } catch (RuntimeException e) {
            throw new TiktokApiException(-1, "Response không phải JSON (HTTP " + raw.status() + ")", null, raw.status());
        }
        int code = root.path("code").asInt(raw.status() == 429 ? TiktokErrorCode.TOO_MANY_REQUESTS.code() : -1);
        String requestId = root.path("request_id").asString(null);
        if (code != 0 || raw.status() == 429) {
            throw new TiktokApiException(code, root.path("message").asString(""), requestId, raw.status());
        }
        return root.path("data");
    }

    private URI uri(String path, Map<String, String> query) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(props.baseUrl()).path(path);
        query.forEach(b::queryParam);
        return b.encode().build().toUri();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

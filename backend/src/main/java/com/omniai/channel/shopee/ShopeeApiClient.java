package com.omniai.channel.shopee;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;

/**
 * Gọi Shop API của Shopee Open Platform v2 (S2: simulator {@code simulators/shopee}, {@code SHOPEE_SIM_URL}).
 * Ký {@code partner_id + path + timestamp + access_token + shop_id} bằng partner key, đọc envelope
 * {@code {error, message, response}}. Dùng chung cho connector giá/tồn và chat. Owner: ChienPX, TuTM.
 */
@Component
public class ShopeeApiClient {

    /** Lỗi do dữ liệu, khóa, chữ ký: thử lại cũng vậy. Lỗi khác (error_server, error_busy...) thì thử lại. */
    static final Set<String> PERMANENT = Set.of("error_auth", "error_sign", "error_param", "error_permission",
            "error_param_shop_id_not_found", "error_item_not_found");

    /** Shopee trả lỗi (envelope có {@code error}), HTTP 5xx hoặc không gọi được. */
    public static final class ShopeeApiException extends RuntimeException {
        private final String error;
        private final boolean retryable;

        ShopeeApiException(String error, String message, boolean retryable) {
            super(message);
            this.error = error;
            this.retryable = retryable;
        }

        public String error() {
            return error;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final ShopeeChannelProperties props;
    private final RestClient http;
    private final JsonMapper json;
    private final Clock clock;

    @Autowired
    public ShopeeApiClient(ShopeeChannelProperties props, JsonMapper json) {
        this(props, RestClient.create(), json, Clock.systemUTC());
    }

    ShopeeApiClient(ShopeeChannelProperties props, RestClient http, JsonMapper json, Clock clock) {
        this.props = props;
        this.http = http;
        this.json = json;
        this.clock = clock;
    }

    /** POST có ký. Trả {@code response} của envelope; lỗi thì ném {@link ShopeeApiException}. */
    public JsonNode post(String path, Object body) {
        long timestamp = clock.instant().getEpochSecond();
        String partnerId = Long.toString(props.getPartnerId());
        String shopId = Long.toString(props.getShopId());
        String sign = ShopeeSign.api(partnerId, path, timestamp, props.getAccessToken(), shopId, props.getPartnerKey());
        var uri = UriComponentsBuilder.fromUriString(props.getApiBaseUrl()).path(path)
                .queryParam("partner_id", partnerId).queryParam("timestamp", timestamp)
                .queryParam("access_token", props.getAccessToken()).queryParam("shop_id", shopId)
                .queryParam("sign", sign).encode().build().toUri();
        String raw;
        try {
            raw = http.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (status >= 500) {
                            throw new ShopeeApiException("http_" + status, "Shopee " + path + " HTTP " + status, true);
                        }
                        return text;
                    });
        } catch (RestClientException e) {
            throw new ShopeeApiException("io", "Không gọi được Shopee " + path + ": " + e.getMessage(), true);
        }
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (RuntimeException e) {
            throw new ShopeeApiException("invalid_response", "Shopee " + path + " trả body không phải JSON", true);
        }
        String error = root.path("error").asString("");
        if (!error.isEmpty()) {
            throw new ShopeeApiException(error,
                    "Shopee " + path + " lỗi " + error + ": " + root.path("message").asString(""),
                    !PERMANENT.contains(error));
        }
        return root.path("response");
    }
}

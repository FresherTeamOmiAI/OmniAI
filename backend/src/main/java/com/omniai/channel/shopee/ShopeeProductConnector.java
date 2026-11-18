package com.omniai.channel.shopee;

import com.omniai.sync.ProductChannelConnector;
import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncAspect;
import com.omniai.sync.SyncException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đẩy giá và tồn kho từ hub ra Shopee (US-50, T-50.2) qua {@code v2.product.update_price} và
 * {@code v2.product.update_stock} có ký (docs/channel-api/shopee.md). Owner: TuTM.
 * <p>
 * Chưa tạo được sản phẩm trên Shopee (add_item chờ T-49.2), nên {@code item_id} lấy từ liên kết admin
 * (POST /api/admin/sync/products/{id}/SHOPEE/link). Sản phẩm 1 model: model_id = 0, kho mặc định (không gửi location_id).
 */
@Component
@ConditionalOnProperty(prefix = "omniai.channels.shopee", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
public class ShopeeProductConnector implements ProductChannelConnector {

    static final String PRICE_PATH = "/api/v2/product/update_price";
    static final String STOCK_PATH = "/api/v2/product/update_stock";
    /** Lỗi do dữ liệu, khóa, chữ ký: thử lại cũng vậy. Lỗi khác (error_server, error_busy...) thì thử lại. */
    private static final Set<String> PERMANENT = Set.of("error_auth", "error_sign", "error_param", "error_permission",
            "error_param_shop_id_not_found", "error_item_not_found");

    private final ShopeeChannelProperties props;
    private final RestClient http;
    private final JsonMapper json;
    private final Clock clock;

    @Autowired
    public ShopeeProductConnector(ShopeeChannelProperties props, JsonMapper json) {
        this(props, RestClient.create(), json, Clock.systemUTC());
    }

    ShopeeProductConnector(ShopeeChannelProperties props, RestClient http, JsonMapper json, Clock clock) {
        this.props = props;
        this.http = http;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public String channel() {
        return "SHOPEE";
    }

    @Override
    public Set<SyncAspect> aspects() {
        return Set.of(SyncAspect.PRICE, SyncAspect.STOCK);
    }

    @Override
    public void pushPrice(ProductSnapshot p, String externalId) {
        if (p.price() == null || p.price() <= 0) {
            throw new SyncException("Sản phẩm " + p.sku() + " chưa có giá kênh SHOPEE", false);
        }
        call(PRICE_PATH, Map.of("item_id", itemId(externalId),
                "price_list", List.of(Map.of("model_id", 0, "original_price", p.price()))));
    }

    @Override
    public void pushStock(ProductSnapshot p, String externalId) {
        call(STOCK_PATH, Map.of("item_id", itemId(externalId),
                "stock_list", List.of(Map.of("model_id", 0, "seller_stock", List.of(Map.of("stock", p.stock()))))));
    }

    private static long itemId(String externalId) {
        try {
            return Long.parseLong(externalId.trim());
        } catch (RuntimeException e) {
            throw new SyncException("item_id Shopee phải là số, đang là " + externalId, false);
        }
    }

    private void call(String path, Map<String, Object> body) {
        long timestamp = clock.instant().getEpochSecond();
        String partnerId = Long.toString(props.getPartnerId());
        String shopId = Long.toString(props.getShopId());
        String sign = ShopeeSign.api(partnerId, path, timestamp, props.getAccessToken(), shopId, props.getPartnerKey());
        var uri = UriComponentsBuilder.fromUriString(props.getApiBaseUrl()).path(path)
                .queryParam("partner_id", partnerId).queryParam("timestamp", timestamp)
                .queryParam("access_token", props.getAccessToken()).queryParam("shop_id", shopId)
                .queryParam("sign", sign).encode().build().toUri();
        String raw = http.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String text = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (status >= 500) {
                        throw new SyncException("Shopee HTTP " + status, true);
                    }
                    return text;
                });
        JsonNode root = json.readTree(raw);
        String error = root.path("error").asString("");
        if (!error.isEmpty()) {
            String message = "Shopee " + path + " lỗi " + error + ": " + root.path("message").asString("");
            throw new SyncException(message, !PERMANENT.contains(error));
        }
        JsonNode failures = root.path("response").path("failure_list");
        if (failures.isArray() && !failures.isEmpty()) {
            throw new SyncException("Shopee " + path + " từ chối: " + failures, false);
        }
    }
}

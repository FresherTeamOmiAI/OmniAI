package com.omniai.channel.shopee;

import com.omniai.sync.ProductChannelConnector;
import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncAspect;
import com.omniai.sync.SyncException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

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

    private final ShopeeApiClient api;

    public ShopeeProductConnector(ShopeeApiClient api) {
        this.api = api;
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
        JsonNode response;
        try {
            response = api.post(path, body);
        } catch (ShopeeApiClient.ShopeeApiException e) {
            throw new SyncException(e.getMessage(), e.retryable());
        }
        JsonNode failures = response.path("failure_list");
        if (failures.isArray() && !failures.isEmpty()) {
            throw new SyncException("Shopee " + path + " từ chối: " + failures, false);
        }
    }
}

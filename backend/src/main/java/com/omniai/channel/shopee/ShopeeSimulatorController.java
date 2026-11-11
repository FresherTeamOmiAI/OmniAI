package com.omniai.channel.shopee;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simulator Shopee theo schema Open Platform v2. Owner: ChienPX. T-50.3, T-51.1.
 * Không gọi API sàn thật. Hub gửi giá/tồn vào đúng path của Shopee; simulator phát đơn bằng push code 3
 * rồi trả chi tiết đơn khi hub gọi get_order_detail.
 */
@RestController
public class ShopeeSimulatorController {

    static final String PRICE_PATH = "/api/v2/product/update_price";
    static final String STOCK_PATH = "/api/v2/product/update_stock";
    static final String ORDER_DETAIL_PATH = "/api/v2/order/get_order_detail";
    private static final long SIGN_WINDOW_SECONDS = 300;

    private final ShopeeChannelProperties properties;
    private final ShopeeSimulatorStore store;
    private final JsonMapper json;

    public ShopeeSimulatorController(ShopeeChannelProperties properties, ShopeeSimulatorStore store, JsonMapper json) {
        this.properties = properties;
        this.store = store;
        this.json = json;
    }

    @PostMapping(value = PRICE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updatePrice(@RequestParam("partner_id") long partnerId,
                                            @RequestParam long timestamp,
                                            @RequestParam("access_token") String accessToken,
                                            @RequestParam("shop_id") long shopId,
                                            @RequestParam String sign,
                                            @RequestBody JsonNode body) {
        Map<String, Object> rejected = reject(PRICE_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        long itemId = requiredLong(body, "item_id");
        JsonNode price = first(body, "price_list");
        long originalPrice = requiredLong(price, "original_price");
        store.savePrice(itemId, originalPrice);
        return ok(Map.of(
                "failure_list", List.of(),
                "success_list", List.of(Map.of("model_id", number(price, "model_id"), "original_price", originalPrice))));
    }

    @PostMapping(value = STOCK_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateStock(@RequestParam("partner_id") long partnerId,
                                            @RequestParam long timestamp,
                                            @RequestParam("access_token") String accessToken,
                                            @RequestParam("shop_id") long shopId,
                                            @RequestParam String sign,
                                            @RequestBody JsonNode body) {
        Map<String, Object> rejected = reject(STOCK_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        long itemId = requiredLong(body, "item_id");
        JsonNode model = first(body, "stock_list");
        JsonNode location = first(model, "seller_stock");
        int stock = (int) requiredLong(location, "stock");
        store.saveStock(itemId, stock);
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("model_id", number(model, "model_id"));
        if (location.hasNonNull("location_id")) {
            success.put("location_id", location.get("location_id").asString());
        }
        success.put("stock", stock);
        return ok(Map.of("failure_list", List.of(), "success_list", List.of(success)));
    }

    @GetMapping(ORDER_DETAIL_PATH)
    public Map<String, Object> getOrderDetail(@RequestParam("partner_id") long partnerId,
                                               @RequestParam long timestamp,
                                               @RequestParam("access_token") String accessToken,
                                               @RequestParam("shop_id") long shopId,
                                               @RequestParam String sign,
                                               @RequestParam("order_sn_list") String orderSnList) {
        Map<String, Object> rejected = reject(ORDER_DETAIL_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        List<Map<String, Object>> orders = new ArrayList<>();
        for (String orderSn : orderSnList.split(",")) {
            String trimmed = orderSn.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            store.order(trimmed).ifPresent(order -> orders.add(orderBody(order)));
        }
        if (orders.isEmpty()) {
            return error("error_not_found", "Order not found.");
        }
        return ok(Map.of("order_list", orders));
    }

    /** Bàn điều khiển tối thiểu: phát một đơn, ký push code 3, rồi hub lấy chi tiết bằng get_order_detail. */
    @PostMapping(value = "/api/simulator/shopee/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emitOrder(@RequestBody JsonNode body) {
        long now = Instant.now().getEpochSecond();
        String orderSn = "26" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        ShopeeSimulatorStore.OrderLine line = new ShopeeSimulatorStore.OrderLine(
                requiredLong(body, "item_id"),
                requiredText(body, "item_sku"),
                requiredText(body, "item_name"),
                (int) requiredLong(body, "quantity"),
                requiredLong(body, "price"));
        String status = body.path("status").asString("READY_TO_SHIP");
        ShopeeSimulatorStore.Order order = new ShopeeSimulatorStore.Order(orderSn, status, now, line);

        ObjectNode data = json.createObjectNode();
        data.put("ordersn", orderSn);
        data.put("status", status);
        data.put("update_time", now);
        ObjectNode push = json.createObjectNode();
        push.set("data", data);
        push.put("shop_id", properties.getShopId());
        push.put("code", 3);
        push.put("timestamp", now);
        String raw = json.writeValueAsString(push);
        String authorization = ShopeeSign.push(properties.getPushCallbackUrl(), raw, properties.getPartnerKey());
        store.saveOrder(order, new ShopeeSimulatorStore.Push(orderSn, properties.getPushCallbackUrl(), raw, authorization));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("order_sn", orderSn);
        response.put("callback_url", properties.getPushCallbackUrl());
        response.put("authorization", authorization);
        response.put("raw_body", raw);
        response.put("body", json.readValue(raw, Map.class));
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> badParam(IllegalArgumentException error) {
        return error("error_param", error.getMessage());
    }

    @GetMapping("/api/simulator/shopee/items/{itemId}")
    public ShopeeSimulatorStore.Item item(@PathVariable long itemId) {
        return store.item(itemId).orElseThrow(() -> new IllegalArgumentException("Shopee chưa nhận item " + itemId));
    }

    private Map<String, Object> orderBody(ShopeeSimulatorStore.Order order) {
        ShopeeSimulatorStore.OrderLine line = order.line();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item_id", line.itemId());
        item.put("item_name", line.itemName());
        item.put("item_sku", line.itemSku());
        item.put("model_id", 0);
        item.put("model_quantity_purchased", line.quantity());
        item.put("model_original_price", line.price());
        item.put("model_discounted_price", line.price());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order_sn", order.orderSn());
        detail.put("order_status", order.status());
        detail.put("region", "VN");
        detail.put("currency", "VND");
        detail.put("total_amount", line.price() * line.quantity());
        detail.put("create_time", order.updateTime());
        detail.put("update_time", order.updateTime());
        detail.put("item_list", List.of(item));
        return detail;
    }

    private Map<String, Object> reject(String path, long partnerId, long timestamp, String accessToken, long shopId,
                                        String sign) {
        long now = Instant.now().getEpochSecond();
        if (partnerId != properties.getPartnerId() || shopId != properties.getShopId()
                || !properties.getAccessToken().equals(accessToken)) {
            return error("error_auth", "Invalid partner or shop.");
        }
        if (Math.abs(now - timestamp) > SIGN_WINDOW_SECONDS) {
            return error("error_param", "Timestamp expired.");
        }
        String expected = ShopeeSign.api(Long.toString(partnerId), path, timestamp, accessToken, Long.toString(shopId),
                properties.getPartnerKey());
        if (!ShopeeSign.equals(expected, sign)) {
            return error("error_sign", "Wrong sign.");
        }
        return null;
    }

    private Map<String, Object> ok(Map<String, Object> response) {
        Map<String, Object> body = envelope("");
        body.put("response", response);
        return body;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = envelope(code);
        body.put("message", message);
        return body;
    }

    private Map<String, Object> envelope(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", "");
        body.put("request_id", UUID.randomUUID().toString().replace("-", ""));
        return body;
    }

    private static JsonNode first(JsonNode body, String field) {
        JsonNode list = body.get(field);
        if (list == null || !list.isArray() || list.isEmpty()) {
            throw new IllegalArgumentException("Thiếu " + field);
        }
        return list.get(0);
    }

    private static long requiredLong(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Thiếu " + field);
        }
        return value.asLong();
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.asString().isBlank()) {
            throw new IllegalArgumentException("Thiếu " + field);
        }
        return value.asString();
    }

    private static long number(JsonNode body, String field) {
        JsonNode value = body.get(field);
        return value == null || !value.isNumber() ? 0 : value.asLong();
    }
}

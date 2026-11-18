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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simulator Shopee theo schema Open Platform v2. Owner: ChienPX. T-50.3, T-51.1.
 * Không gọi API sàn thật. Hub gửi giá/tồn vào đúng path của Shopee; simulator phát đơn bằng push code 3
 * rồi trả chi tiết đơn khi hub gọi get_order_detail. Chat shop là bản giả lập: trang seller chat bị khóa quyền.
 */
@RestController
public class ShopeeSimulatorController {

    static final String PRICE_PATH = "/api/v2/product/update_price";
    static final String STOCK_PATH = "/api/v2/product/update_stock";
    static final String ORDER_DETAIL_PATH = "/api/v2/order/get_order_detail";
    static final String SEND_MESSAGE_PATH = "/api/v2/sellerchat/send_message";
    static final String GET_MESSAGE_PATH = "/api/v2/sellerchat/get_message";
    static final String CONVERSATION_LIST_PATH = "/api/v2/sellerchat/get_conversation_list";
    private static final long SIGN_WINDOW_SECONDS = 300;
    private static final int CHAT_PAGE_DEFAULT = 25;
    private static final int CHAT_PAGE_MAX = 60;

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
        JsonNode prices = array(body, "price_list", 50);
        List<Map<String, Object>> success = new ArrayList<>();
        for (JsonNode price : prices) {
            long modelId = number(price, "model_id");
            long originalPrice = wholeVnd(price, "original_price");
            store.savePrice(shopId, itemId, modelId, originalPrice);
            success.add(Map.of("model_id", modelId, "original_price", originalPrice));
        }
        Map<String, Object> response = productOk(Map.of("failure_list", List.of(), "success_list", success));
        return response;
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
        JsonNode models = array(body, "stock_list", 50);
        List<Map<String, Object>> success = new ArrayList<>();
        for (JsonNode model : models) {
            long modelId = number(model, "model_id");
            for (JsonNode location : array(model, "seller_stock", 50)) {
                long stock = requiredLong(location, "stock");
                if (stock < 0) {
                    throw new ShopeeCallException("error_param", "Wrong parameters, detail: stock.");
                }
                String locationId = location.hasNonNull("location_id") ? location.get("location_id").asString() : "-";
                store.saveStock(shopId, itemId, modelId, locationId, stock);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("model_id", modelId);
                row.put("location_id", locationId);
                row.put("stock", stock);
                success.add(row);
            }
        }
        return productOk(Map.of("failure_list", List.of(), "success_list", success));
    }

    @GetMapping(ORDER_DETAIL_PATH)
    public Map<String, Object> getOrderDetail(@RequestParam("partner_id") long partnerId,
                                               @RequestParam long timestamp,
                                               @RequestParam("access_token") String accessToken,
                                               @RequestParam("shop_id") long shopId,
                                               @RequestParam String sign,
                                               @RequestParam("order_sn_list") String orderSnList,
                                               @RequestParam(value = "response_optional_fields", required = false)
                                               String responseOptionalFields) {
        Map<String, Object> rejected = reject(ORDER_DETAIL_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        Set<String> optional = new HashSet<>();
        if (responseOptionalFields != null) {
            for (String field : responseOptionalFields.split(",")) {
                if (!field.isBlank()) {
                    optional.add(field.trim());
                }
            }
        }
        String[] requested = orderSnList.split(",");
        if (requested.length > 50) {
            return error("error_param", "Wrong parameters, detail: order_sn_list limit is 50.");
        }
        List<Map<String, Object>> orders = new ArrayList<>();
        for (String orderSn : requested) {
            String trimmed = orderSn.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            store.order(trimmed).ifPresent(order -> orders.add(orderBody(order, optional)));
        }
        if (orders.isEmpty()) {
            return error("error_param", "The information you queried is not found.");
        }
        return ok(Map.of("order_list", orders));
    }

    /** Bàn điều khiển tối thiểu: phát một đơn, ký push code 3, rồi hub lấy chi tiết bằng get_order_detail. */
    @PostMapping(value = "/api/simulator/shopee/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emitOrder(@RequestBody JsonNode body) {
        long now = Instant.now().getEpochSecond();
        String orderSn = "26" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        int daysToShip = body.has("days_to_ship") ? (int) requiredLong(body, "days_to_ship") : 0;
        ShopeeSimulatorStore.OrderLine line = new ShopeeSimulatorStore.OrderLine(
                requiredLong(body, "item_id"),
                requiredText(body, "item_sku"),
                requiredText(body, "item_name"),
                (int) requiredLong(body, "quantity"),
                requiredLong(body, "price"),
                body.path("model_name").asString(""),
                body.path("model_sku").asString(""));
        String status = body.path("status").asString("READY_TO_SHIP");
        ShopeeSimulatorStore.Order order = new ShopeeSimulatorStore.Order(
                orderSn, status, now, body.path("cod").asBoolean(false), body.path("message_to_seller").asString(""),
                daysToShip, now + daysToShip * 86400L, line);

        ObjectNode data = json.createObjectNode();
        data.putArray("items");
        data.put("ordersn", orderSn);
        data.put("status", status);
        data.put("completed_scenario", "COMPLETED".equals(status) ? "NORMAL" : "");
        data.put("update_time", now);
        ObjectNode push = json.createObjectNode();
        push.set("data", data);
        push.put("shop_id", properties.getShopId());
        push.put("code", 3);
        push.put("timestamp", now);
        String raw = json.writeValueAsString(push);
        String authorization = ShopeeSign.push(properties.getPushCallbackUrl(), raw, properties.getPartnerKey());
        store.saveOrder(properties.getShopId(), order,
                new ShopeeSimulatorStore.Push(orderSn, properties.getPushCallbackUrl(), raw, authorization));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("order_sn", orderSn);
        response.put("callback_url", properties.getPushCallbackUrl());
        response.put("authorization", authorization);
        response.put("raw_body", raw);
        response.put("body", json.readValue(raw, Map.class));
        return response;
    }

    /**
     * Khách nhắn vào shop. Không phải API Shopee: tạo tin và trả raw body webchat_push code 10.
     * Path không dùng /messages vì path đó thuộc simulator bench.
     */
    @PostMapping(value = "/api/simulator/shopee/webchat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> emitWebchat(@RequestBody JsonNode body) {
        long buyerId = requiredLong(body, "from_id");
        String buyerName = requiredText(body, "from_user_name");
        String messageType = body.path("message_type").asString("text");
        if (messageType.isBlank()) {
            messageType = "text";
        }
        String contentJson = chatContentJson(messageType, body);
        long now = Instant.now().getEpochSecond();
        ShopeeSimulatorStore.StoredMessage message = store.appendMessage(
                properties.getShopId(), buyerId, buyerName, true,
                buyerId, buyerName, properties.getShopId(), "",
                messageType, contentJson, "user_chat", now);
        return signedWebchat(message, now);
    }

    /** Shop trả lời khách. Bản giả lập của v2.sellerchat.send_message. */
    @PostMapping(value = SEND_MESSAGE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> sendMessage(@RequestParam("partner_id") long partnerId,
                                            @RequestParam long timestamp,
                                            @RequestParam("access_token") String accessToken,
                                            @RequestParam("shop_id") long shopId,
                                            @RequestParam String sign,
                                            @RequestBody JsonNode body) {
        Map<String, Object> rejected = reject(SEND_MESSAGE_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        long buyerId = requiredLong(body, "to_id");
        String messageType = requiredText(body, "message_type");
        JsonNode content = body.get("content");
        if (content == null || !content.isObject()) {
            throw new IllegalArgumentException("Wrong parameters, detail: missing content.");
        }
        String contentJson = chatContentJson(messageType, content);
        long now = Instant.now().getEpochSecond();
        String buyerName = store.buyerName(shopId, buyerId);
        ShopeeSimulatorStore.StoredMessage message = store.appendMessage(
                shopId, buyerId, "", false,
                shopId, "", buyerId, buyerName,
                messageType, contentJson, "normal", now);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message_id", message.messageId());
        response.put("to_id", buyerId);
        response.put("message_type", messageType);
        response.put("content", json.readValue(contentJson, Map.class));
        response.put("conversation_id", message.conversationId());
        response.put("created_timestamp", now);
        return ok(response);
    }

    @GetMapping(GET_MESSAGE_PATH)
    public Map<String, Object> getMessage(@RequestParam("partner_id") long partnerId,
                                           @RequestParam long timestamp,
                                           @RequestParam("access_token") String accessToken,
                                           @RequestParam("shop_id") long shopId,
                                           @RequestParam String sign,
                                           @RequestParam("conversation_id") String conversationId,
                                           @RequestParam(value = "page_size", required = false) String pageSize,
                                           @RequestParam(value = "offset", required = false) String offset) {
        Map<String, Object> rejected = reject(GET_MESSAGE_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        if (!store.conversationExists(conversationId)) {
            return error("error_param", "The information you queried is not found.");
        }
        int size = pageSize(pageSize);
        int skip = offset(offset);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ShopeeSimulatorStore.StoredMessage message : store.messages(conversationId, size, skip)) {
            messages.add(messageBody(message));
        }
        int next = skip + messages.size();
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("next_offset", next < store.messageCount(conversationId) ? Integer.toString(next) : "");
        return ok(Map.of("messages", messages, "page_result", page));
    }

    @GetMapping(CONVERSATION_LIST_PATH)
    public Map<String, Object> getConversationList(@RequestParam("partner_id") long partnerId,
                                                    @RequestParam long timestamp,
                                                    @RequestParam("access_token") String accessToken,
                                                    @RequestParam("shop_id") long shopId,
                                                    @RequestParam String sign,
                                                    @RequestParam(value = "page_size", required = false) String pageSize,
                                                    @RequestParam(value = "direction", required = false) String direction) {
        Map<String, Object> rejected = reject(CONVERSATION_LIST_PATH, partnerId, timestamp, accessToken, shopId, sign);
        if (rejected != null) {
            return rejected;
        }
        if (direction != null && !direction.isBlank() && !"latest".equals(direction) && !"older".equals(direction)) {
            return error("error_param", "Wrong parameters, detail: direction.");
        }
        List<Map<String, Object>> conversations = new ArrayList<>();
        for (ShopeeSimulatorStore.StoredConversation conversation : store.conversations(shopId, pageSize(pageSize))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("conversation_id", conversation.conversationId());
            row.put("to_id", conversation.toId());
            row.put("to_name", conversation.toName());
            row.put("to_avatar", "");
            row.put("shop_id", shopId);
            row.put("unread_count", conversation.unreadCount());
            row.put("pinned", false);
            row.put("last_read_message_id", "");
            row.put("latest_message_id", conversation.latestMessageId());
            row.put("latest_message_type", conversation.latestMessageType());
            row.put("latest_message_content", json.readValue(conversation.latestMessageContent(), Map.class));
            row.put("latest_message_from_id", conversation.latestMessageFromId());
            row.put("last_message_timestamp", conversation.lastMessageTimestamp());
            conversations.add(row);
        }
        Map<String, Object> cursor = new LinkedHashMap<>();
        cursor.put("next_message_time_nano", "");
        cursor.put("conversation_id", "");
        return ok(Map.of("conversations", conversations, "page_result", Map.of("next_cursor", cursor)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> badParam(IllegalArgumentException error) {
        return error("error_param", error.getMessage());
    }

    @ExceptionHandler(ShopeeCallException.class)
    public Map<String, Object> shopeeError(ShopeeCallException error) {
        return error(error.code, error.getMessage());
    }

    @GetMapping("/api/simulator/shopee/items/{itemId}")
    public ShopeeSimulatorStore.Item item(@PathVariable long itemId) {
        return store.item(itemId).orElseThrow(() -> new IllegalArgumentException("Shopee chưa nhận item " + itemId));
    }

    private Map<String, Object> orderBody(ShopeeSimulatorStore.Order order, Set<String> optional) {
        ShopeeSimulatorStore.OrderLine line = order.line();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order_sn", order.orderSn());
        detail.put("region", "VN");
        detail.put("currency", "VND");
        detail.put("cod", order.cod());
        detail.put("order_status", order.status());
        detail.put("message_to_seller", order.messageToSeller());
        detail.put("create_time", order.updateTime());
        detail.put("update_time", order.updateTime());
        detail.put("days_to_ship", order.daysToShip());
        detail.put("ship_by_date", order.shipByDate());
        if (optional.contains("total_amount")) {
            detail.put("total_amount", line.price() * line.quantity());
        }
        if (optional.contains("item_list")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("item_id", line.itemId());
            item.put("item_name", line.itemName());
            item.put("item_sku", line.itemSku());
            item.put("model_id", 0);
            item.put("model_name", line.modelName());
            item.put("model_sku", line.modelSku());
            item.put("model_quantity_purchased", line.quantity());
            item.put("model_original_price", line.price());
            item.put("model_discounted_price", line.price());
            detail.put("item_list", List.of(item));
        }
        return detail;
    }

    private Map<String, Object> signedWebchat(ShopeeSimulatorStore.StoredMessage message, long now) {
        ObjectNode content = json.createObjectNode();
        content.put("message_id", message.messageId());
        content.put("shop_id", properties.getShopId());
        content.put("request_id", UUID.randomUUID().toString());
        content.put("from_id", message.fromId());
        content.put("from_user_name", message.fromUserName());
        content.put("to_id", message.toId());
        content.put("to_user_name", message.toUserName());
        content.put("message_type", message.messageType());
        content.set("content", json.readTree(message.contentJson()));
        content.put("conversation_id", message.conversationId());
        content.put("created_timestamp", message.createdTimestamp());
        content.put("region", "VN");
        content.put("is_in_chatbot_session", false);
        ObjectNode source = json.createObjectNode();
        if ("item".equals(message.messageType())) {
            source.put("item_id", json.readTree(message.contentJson()).path("item_id").asLong());
        }
        content.set("source_content", source);
        ObjectNode quoted = json.createObjectNode();
        quoted.put("message_id", "");
        content.set("quoted_msg", quoted);
        content.put("sub_account_id", 0);
        content.put("sub_account_name", 0);
        content.put("business_type", 0);
        content.put("to_shop_id", properties.getShopId());
        content.put("from_shop_id", 0);
        content.put("status", message.status());

        ObjectNode data = json.createObjectNode();
        data.put("type", "message");
        data.put("region", "VN");
        data.set("content", content);
        ObjectNode push = json.createObjectNode();
        push.put("msg_id", "");
        push.set("data", data);
        push.put("shop_id", properties.getShopId());
        push.put("code", 10);
        push.put("timestamp", now);
        String raw = json.writeValueAsString(push);
        String authorization = ShopeeSign.push(properties.getPushCallbackUrl(), raw, properties.getPartnerKey());
        store.saveWebchatPush(message.messageId(), properties.getShopId(), properties.getPushCallbackUrl(), raw,
                authorization, now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message_id", message.messageId());
        response.put("conversation_id", message.conversationId());
        response.put("callback_url", properties.getPushCallbackUrl());
        response.put("authorization", authorization);
        response.put("raw_body", raw);
        response.put("body", json.readValue(raw, Map.class));
        return response;
    }

    private Map<String, Object> messageBody(ShopeeSimulatorStore.StoredMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("message_id", message.messageId());
        row.put("from_id", message.fromId());
        row.put("from_user_name", message.fromUserName());
        row.put("to_id", message.toId());
        row.put("to_user_name", message.toUserName());
        row.put("message_type", message.messageType());
        row.put("content", json.readValue(message.contentJson(), Map.class));
        row.put("conversation_id", message.conversationId());
        row.put("created_timestamp", message.createdTimestamp());
        row.put("region", "VN");
        row.put("status", message.status());
        row.put("business_type", 0);
        return row;
    }

    /** text → content.text, item → content.item_id, order → content.order_sn phải có trong order_shopee. */
    private String chatContentJson(String messageType, JsonNode fields) {
        ObjectNode content = json.createObjectNode();
        switch (messageType) {
            case "text" -> content.put("text", requiredText(fields, "text"));
            case "item" -> content.put("item_id", requiredLong(fields, "item_id"));
            case "order" -> {
                String orderSn = requiredText(fields, "order_sn");
                if (store.order(orderSn).isEmpty()) {
                    throw new IllegalArgumentException("Wrong parameters, detail: order_sn.");
                }
                content.put("order_sn", orderSn);
            }
            default -> throw new IllegalArgumentException("Wrong parameters, detail: message_type.");
        }
        return json.writeValueAsString(content);
    }

    private static int pageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHAT_PAGE_DEFAULT;
        }
        long value = positive(raw, "page_size");
        if (value > CHAT_PAGE_MAX) {
            throw new IllegalArgumentException("Wrong parameters, detail: page_size.");
        }
        return (int) value;
    }

    private static int offset(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        long value = positive(raw, "offset");
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Wrong parameters, detail: offset.");
        }
        return (int) value;
    }

    private static long positive(String raw, String field) {
        try {
            long value = Long.parseLong(raw);
            if (value < 0 || (field.equals("page_size") && value < 1)) {
                throw new IllegalArgumentException("Wrong parameters, detail: " + field + ".");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Wrong parameters, detail: " + field + ".");
        }
    }

    private Map<String, Object> reject(String path, long partnerId, long timestamp, String accessToken, long shopId,
                                        String sign) {
        long now = Instant.now().getEpochSecond();
        if (!properties.getAccessToken().equals(accessToken)) {
            return error("error_auth", "Invalid access_token.");
        }
        if (shopId != properties.getShopId()) {
            return error("error_param_shop_id_not_found", "Shop_id is not found.");
        }
        if (partnerId != properties.getPartnerId()) {
            return error("error_auth", "Invalid partner_id.");
        }
        if (Math.abs(now - timestamp) > SIGN_WINDOW_SECONDS) {
            return error("error_param", "Wrong parameters, detail: timestamp expired.");
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

    /** update_price và update_stock có thêm warning. get_order_detail không có field này. */
    private Map<String, Object> productOk(Map<String, Object> response) {
        Map<String, Object> body = ok(response);
        body.put("warning", "");
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

    private static JsonNode array(JsonNode body, String field, int max) {
        JsonNode list = body.get(field);
        if (list == null || !list.isArray() || list.isEmpty() || list.size() > max) {
            throw new IllegalArgumentException("Wrong parameters, detail: " + field + " length must be between 1 and " + max + ".");
        }
        return list;
    }

    private static long requiredLong(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Wrong parameters, detail: missing " + field + ".");
        }
        return value.asLong();
    }

    /** VN chỉ được đặt giá nguyên. SG/MY/BR/MX/PL/ES/AR mới được hai chữ số thập phân. */
    private static long wholeVnd(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Wrong parameters, detail: missing " + field + ".");
        }
        if (value.isFloatingPointNumber() && value.asDouble() != value.asLong()) {
            throw new ShopeeCallException("error_invalid_price", "Invalid price, please use the correct format");
        }
        return value.asLong();
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.asString().isBlank()) {
            throw new IllegalArgumentException("Wrong parameters, detail: missing " + field + ".");
        }
        return value.asString();
    }

    private static long number(JsonNode body, String field) {
        JsonNode value = body.get(field);
        return value == null || !value.isNumber() ? 0 : value.asLong();
    }

    static final class ShopeeCallException extends RuntimeException {
        final String code;

        ShopeeCallException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}

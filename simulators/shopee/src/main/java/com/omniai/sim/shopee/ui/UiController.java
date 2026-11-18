package com.omniai.sim.shopee.ui;

import com.omniai.sim.shopee.SimProperties;
import com.omniai.sim.shopee.push.PushDispatcher;
import com.omniai.sim.shopee.store.ShopeeStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu cho trang kiểm tra trực quan /ui (chỉ có ở simulator): hub đã gửi gì lên sàn (giá, tồn, tin trả lời, lời gọi
 * API) và sàn đã đẩy gì về hub (push code 3, code 10). Thao tác (buyer nhắn, phát đơn) dùng lại
 * {@code /api/simulator/shopee/*} sẵn có với {@code "deliver": true}.
 */
@RestController
public class UiController {

    private static final int CONVERSATION_LIMIT = 100;
    private static final int MESSAGE_LIMIT = 500;
    private static final int ORDER_LIMIT = 200;

    private final SimProperties props;
    private final ShopeeStore store;
    private final PushDispatcher pushes;
    private final ApiCallLog calls;
    private final JsonMapper json;
    private final Clock clock;

    public UiController(SimProperties props, ShopeeStore store, PushDispatcher pushes, ApiCallLog calls,
                        JsonMapper json, Clock clock) {
        this.props = props;
        this.store = store;
        this.pushes = pushes;
        this.calls = calls;
        this.json = json;
        this.clock = clock;
    }

    @GetMapping({"/", "/ui", "/ui/"})
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/ui/index.html").build();
    }

    public record Shop(long shopId, String shopName, long partnerId, String region, String currency) {
    }

    public record Counts(int items, int orders, int conversations, int messages, int pushes, int failedPushes,
                         int apiCalls, int apiErrors) {
    }

    /** {@code accessToken} chỉ hiện 8 ký tự đầu. */
    public record Overview(Shop shop, String accessToken, String pushCallbackUrl, Counts counts, long serverTime) {
    }

    @GetMapping("/sim/ui/overview")
    public Overview overview() {
        ShopeeStore.Stats stats = store.stats();
        List<PushDispatcher.Delivery> deliveries = pushes.deliveries();
        List<ApiCallLog.Call> all = calls.list(0);
        Counts counts = new Counts(stats.items(), stats.orders(), stats.conversations(), stats.messages(),
                deliveries.size(), (int) deliveries.stream().filter(d -> !d.delivered()).count(),
                all.size(), (int) all.stream().filter(ApiCallLog.Call::failed).count());
        return new Overview(new Shop(props.shopId(), props.shopName(), props.partnerId(), "VN", "VND"),
                mask(props.accessToken()), props.pushCallbackUrl(), counts, clock.instant().getEpochSecond());
    }

    @GetMapping("/sim/ui/items")
    public List<ShopeeStore.ItemView> items() {
        return store.items();
    }

    @GetMapping("/sim/ui/orders")
    public List<ShopeeStore.OrderView> orders() {
        return store.orders(ORDER_LIMIT);
    }

    /** Tin trong hội thoại. {@code fromShop} = tin shop gửi ra (send_message). {@code content} là JSON đã mở. */
    public record MessageView(String messageId, long fromId, String fromUserName, long toId, String toUserName,
                              boolean fromShop, String messageType, JsonNode content, long createdTimestamp,
                              String status) {
    }

    public record ConversationView(String conversationId, long buyerId, String buyerName, int unreadCount,
                                   long lastMessageTimestamp, List<MessageView> messages) {
    }

    @GetMapping("/sim/ui/conversations")
    public List<ConversationView> conversations() {
        return store.conversations(props.shopId(), CONVERSATION_LIMIT).stream()
                .map(c -> new ConversationView(c.conversationId(), c.toId(), c.toName(), c.unreadCount(),
                        c.lastMessageTimestamp(),
                        store.messages(c.conversationId(), MESSAGE_LIMIT, 0).stream()
                                .map(m -> new MessageView(m.messageId(), m.fromId(), m.fromUserName(), m.toId(),
                                        m.toUserName(), m.fromId() == props.shopId(), m.messageType(),
                                        json.readTree(m.contentJson()), m.createdTimestamp(), m.status()))
                                .toList()))
                .toList();
    }

    @GetMapping("/sim/ui/pushes")
    public List<PushDispatcher.Delivery> pushes() {
        return pushes.deliveries();
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

    /** Xóa mọi bảng tbl_shopee_*, log push và log API. Khóa test giữ nguyên. */
    @PostMapping("/sim/reset")
    public Map<String, Object> reset() {
        store.reset();
        pushes.clear();
        calls.clear();
        return Map.of("reset", true);
    }

    private static String mask(String token) {
        if (token == null) return null;
        return token.length() <= 8 ? "****" : token.substring(0, 8) + "…";
    }
}

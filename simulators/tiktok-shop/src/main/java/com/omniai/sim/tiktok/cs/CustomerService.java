package com.omniai.sim.tiktok.cs;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.Ids;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.control.Faults;
import com.omniai.sim.tiktok.cs.CsModels.ConversationView;
import com.omniai.sim.tiktok.cs.CsModels.Message;
import com.omniai.sim.tiktok.cs.CsModels.Participant;
import com.omniai.sim.tiktok.cs.CsModels.Sender;
import com.omniai.sim.tiktok.event.WebhookDispatcher;
import com.omniai.sim.tiktok.event.WebhookType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hội thoại buyer ↔ shop trong bộ nhớ. Mỗi tin mới (của buyer hay của shop) đều bắn webhook NEW_MESSAGE như sàn thật,
 * nên hub phải tự bỏ qua tin của chính mình.
 */
@Service
public class CustomerService {

    private static final String AVATAR = "https://p16-oec-ttp.tiktokcdn-us.com/sim-avatar.image";

    /** Hội thoại giữa 1 buyer và 1 shop. Mọi thay đổi đều đồng bộ trên chính object. */
    static final class Conversation {
        final String id;
        final String shopId;
        final Participant buyer;
        final Participant shop;
        final long createTime;
        final List<Message> messages = new ArrayList<>();
        int unreadCount;
        boolean canSendMessage;

        Conversation(String id, String shopId, Participant buyer, Participant shop, long createTime, boolean canSend) {
            this.id = id;
            this.shopId = shopId;
            this.buyer = buyer;
            this.shop = shop;
            this.createTime = createTime;
            this.canSendMessage = canSend;
        }

        synchronized ConversationView view() {
            Message latest = messages.isEmpty() ? null : messages.getLast();
            return new ConversationView(id, 2, canSendMessage, unreadCount, createTime, List.of(buyer, shop), latest);
        }

        synchronized long latestTime() {
            return messages.isEmpty() ? createTime : messages.getLast().createTime();
        }
    }

    /** Kết quả buyer gửi tin, dùng cho POST /sim/buyer/messages. */
    public record BuyerMessageResult(String conversationId, Message message, boolean newConversation) {
    }

    private final SimProperties props;
    private final Faults faults;
    private final WebhookDispatcher webhooks;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final AtomicLong nextIndex = new AtomicLong(7_494_560_109_732_334_000L);
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    /** buyer user_id → conversation id (mỗi buyer 1 hội thoại với shop). */
    private final Map<String, String> byBuyer = new ConcurrentHashMap<>();
    private final Map<String, List<CompletableFuture<Message>>> replyWaiters = new ConcurrentHashMap<>();

    public CustomerService(SimProperties props, Faults faults, WebhookDispatcher webhooks, JsonMapper jsonMapper,
                           Clock clock) {
        this.props = props;
        this.faults = faults;
        this.webhooks = webhooks;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    // ---------- API mở ----------

    public List<ConversationView> conversations(String shopId) {
        return conversations.values().stream()
                .filter(c -> c.shopId.equals(shopId))
                .sorted(Comparator.comparingLong(Conversation::latestTime).reversed().thenComparing(c -> c.id))
                .map(Conversation::view)
                .toList();
    }

    public List<Message> messages(String shopId, String conversationId, String sortField, String sortOrder) {
        Comparator<Message> order = switch (sortField == null ? "create_time" : sortField) {
            case "create_time" -> Comparator.comparingLong(Message::createTime).thenComparing(m -> Long.parseLong(m.index()));
            case "index" -> Comparator.comparing(m -> Long.parseLong(m.index()));
            default -> throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "sort_field must be create_time or index.");
        };
        switch (sortOrder == null ? "DESC" : sortOrder) {
            case "DESC" -> order = order.reversed();
            case "ASC" -> { }
            default -> throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "sort_order must be ASC or DESC.");
        }
        Conversation c = owned(shopId, conversationId);
        synchronized (c) {
            return c.messages.stream().sorted(order).toList();
        }
    }

    public String createConversation(String shopId, String buyerUserId) {
        if (buyerUserId == null || buyerUserId.isBlank()) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "buyer_user_id is required.");
        }
        String id = byBuyer.get(shopId + ":" + buyerUserId);
        if (id == null) {
            // Sàn chỉ cho shop mở hội thoại với buyer đã chat hoặc mua trong 30/60 ngày; simulator chưa có đơn hàng.
            throw new ApiException(TiktokErrorCode.CS_CANNOT_CREATE_CONVERSATION);
        }
        return id;
    }

    public void markRead(String shopId, String conversationId) {
        Conversation c = owned(shopId, conversationId);
        synchronized (c) {
            c.unreadCount = 0;
        }
    }

    /** Send Message: kiểm theo docv2 rồi lưu tin, bắn NEW_MESSAGE, báo cho ai đang chờ tin trả lời. */
    public Message send(String shopId, String conversationId, CsModels.SendMessageRequest body) {
        if (body == null || body.type() == null || !CsModels.SEND_TYPES.contains(body.type())) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "type must be one of " + CsModels.SEND_TYPES + ".");
        }
        if (body.content() == null || body.content().isBlank()) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "content is required.");
        }
        String role = body.senderRole() == null ? "CUSTOMER_SERVICE" : body.senderRole();
        if (!CsModels.SENDER_ROLES.contains(role)) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "sender_role must be one of " + CsModels.SENDER_ROLES + ".");
        }
        JsonNode content = parseContent(body.content());
        if ("TEXT".equals(body.type())) {
            JsonNode text = content.get("content");
            if (text == null || !text.isString() || text.asString().isBlank()) {
                throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "TEXT content must be {\"content\": \"...\"}.");
            }
            if (text.asString().codePointCount(0, text.asString().length()) > CsModels.TEXT_MAX_LENGTH) {
                throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "TEXT content exceeds 2000 characters.");
            }
            if (faults.containsSensitive(text.asString())) {
                throw new ApiException(TiktokErrorCode.CS_SENSITIVE_CONTENT);
            }
        }
        Conversation c = owned(shopId, conversationId);
        Message message;
        synchronized (c) {
            if (!c.canSendMessage || faults.blockSending()) {
                throw new ApiException(TiktokErrorCode.CS_CONVERSATION_RULES);
            }
            message = new Message(Ids.next(), body.type(), body.content(), now(), true,
                    new Sender(c.shop.imUserId(), role, c.shop.nickname(), AVATAR), nextIndex());
            c.messages.add(message);
        }
        notifyMessage(c, message);
        List<CompletableFuture<Message>> waiters = replyWaiters.remove(c.id);
        if (waiters != null) {
            waiters.forEach(w -> w.complete(message));
        }
        return message;
    }

    // ---------- điều khiển của simulator ----------

    /** Buyer nhắn tin cho shop (POST /sim/buyer/messages). Chưa bắn NEW_MESSAGE: gọi expectReply trước rồi notifyBuyerMessage. */
    public BuyerMessageResult buyerSays(String buyerUserId, String nickname, String text) {
        String shopId = props.shop().id();
        boolean[] created = {false};
        String key = shopId + ":" + buyerUserId;
        String conversationId = byBuyer.computeIfAbsent(key, k -> {
            created[0] = true;
            String id = Ids.next();
            Participant buyer = new Participant(Ids.next(), AVATAR, buyerUserId, "BUYER",
                    nickname == null || nickname.isBlank() ? "buyer-" + buyerUserId : nickname);
            Participant shop = new Participant("im_" + shopId, AVATAR, shopId, "SHOP", props.shop().name());
            conversations.put(id, new Conversation(id, shopId, buyer, shop, now(), true));
            return id;
        });
        Conversation c = conversations.get(conversationId);
        if (created[0]) {
            webhooks.dispatch(shopId, WebhookType.NEW_CONVERSATION,
                    new CsModels.NewConversationData(conversationId, c.createTime));
        }
        Message message;
        synchronized (c) {
            String content = jsonMapper.writeValueAsString(Map.of("content", text));
            message = new Message(Ids.next(), "TEXT", content, now(), true,
                    new Sender(c.buyer.imUserId(), "BUYER", c.buyer.nickname(), AVATAR), nextIndex());
            c.messages.add(message);
            c.unreadCount++;
        }
        return new BuyerMessageResult(conversationId, message, created[0]);
    }

    /** Đăng ký chờ tin của shop TRƯỚC khi bắn webhook, để không lỡ câu trả lời đến quá nhanh. */
    public CompletableFuture<Message> expectReply(String conversationId) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        replyWaiters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(future);
        return future;
    }

    public String notifyBuyerMessage(String conversationId, Message message) {
        return notifyMessage(conversations.get(conversationId), message);
    }

    public static Optional<Message> await(CompletableFuture<Message> future, Duration timeout) {
        try {
            return Optional.of(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }

    public void clear() {
        conversations.clear();
        byBuyer.clear();
        replyWaiters.values().forEach(list -> list.forEach(f -> f.cancel(true)));
        replyWaiters.clear();
    }

    // ---------- nội bộ ----------

    private String notifyMessage(Conversation c, Message m) {
        return webhooks.dispatch(c.shopId, WebhookType.NEW_MESSAGE, new CsModels.NewMessageData(
                m.id(), c.id, m.type(), m.content(), m.createTime(), m.isVisible(), m.index(),
                new CsModels.SenderRef(m.sender().imUserId(), m.sender().role())));
    }

    private Conversation owned(String shopId, String conversationId) {
        Conversation c = conversationId == null ? null : conversations.get(conversationId);
        if (c == null) {
            throw new ApiException(TiktokErrorCode.CS_RECORD_NOT_FOUND);
        }
        if (!c.shopId.equals(shopId)) {
            throw new ApiException(TiktokErrorCode.CS_NO_PERMISSION);
        }
        return c;
    }

    private JsonNode parseContent(String raw) {
        try {
            JsonNode node = jsonMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("not an object");
            }
            return node;
        } catch (RuntimeException e) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "content must be a JSON serialized object string.");
        }
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private String nextIndex() {
        return Long.toString(nextIndex.incrementAndGet());
    }
}

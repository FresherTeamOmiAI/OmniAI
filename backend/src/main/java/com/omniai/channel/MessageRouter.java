package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainProduct;
import com.omniai.brain.BrainResponse;
import com.omniai.brain.BrainTurn;
import com.omniai.catalog.ProductCard;
import com.omniai.catalog.ProductSuggestions;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.conversation.TurnHistory;
import com.omniai.policy.PolicyEngine;
import com.omniai.policy.PolicyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Luồng chung cho mọi kênh: tin chuẩn hóa -> lưu tin vào -> AI Brain -> Policy của kênh -> lưu câu ra
 * -> câu trả lời. Owner: TuTM.
 */
@Service
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);
    private static final int IDEMPOTENCY_CACHE_SIZE = 10_000;

    private final BrainClient brainClient;
    private final PolicyEngine policyEngine;
    private final ConversationRecorder recorder;
    private final TurnHistory history;
    private final ProductSuggestions suggestions;

    // Idempotency tạm trong bộ nhớ: kênh gửi lại cùng messageId (webhook retry) thì trả lại kết quả cũ.
    // Giữ future thay vì kết quả để chống trùng nguyên tử: request trùng đến khi lượt đầu chưa xong thì chờ
    // và nhận cùng câu trả lời, không gọi Brain lần 2 (T-11.4). Sprint 7 chuyển sang Redis để chạy nhiều instance.
    private final Map<String, CompletableFuture<BotReply>> processed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<BotReply>> eldest) {
                    return size() > IDEMPOTENCY_CACHE_SIZE;
                }
            });

    @Autowired
    public MessageRouter(BrainClient brainClient, PolicyEngine policyEngine, ConversationRecorder recorder, TurnHistory history,
                         ProductSuggestions suggestions) {
        this.brainClient = brainClient;
        this.policyEngine = policyEngine;
        this.recorder = recorder;
        this.history = history;
        this.suggestions = suggestions;
    }

    /** Test không cần thẻ sản phẩm (câu trả lời WEB có {@code products} rỗng). */
    public MessageRouter(BrainClient brainClient, PolicyEngine policyEngine, ConversationRecorder recorder, TurnHistory history) {
        this(brainClient, policyEngine, recorder, history, null);
    }

    /** Test và chỗ chưa cần các lượt trước. */
    public MessageRouter(BrainClient brainClient, PolicyEngine policyEngine, ConversationRecorder recorder) {
        this(brainClient, policyEngine, recorder, TurnHistory.NONE);
    }

    public BotReply handle(CanonicalMessage message) {
        String key = message.channel() + ":" + message.messageId();
        CompletableFuture<BotReply> mine = new CompletableFuture<>();
        CompletableFuture<BotReply> earlier = processed.putIfAbsent(key, mine);
        if (earlier != null) {
            return await(earlier);
        }
        try {
            BotReply reply = process(message, key);
            mine.complete(reply);
            return reply;
        } catch (RuntimeException e) {
            // Lượt lỗi không được nhớ: kênh gửi lại thì xử lý lại. Request đang chờ nhận cùng lỗi.
            processed.remove(key, mine);
            mine.completeExceptionally(e);
            throw e;
        }
    }

    private static BotReply await(CompletableFuture<BotReply> earlier) {
        try {
            return earlier.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private BotReply process(CanonicalMessage message, String key) {
        // Lưu tin khách trước khi hỏi Brain: Brain treo hay lỗi thì tin vẫn còn (NFR-06).
        record("tin vào", key, () -> recorder.inbound(message));

        BrainResponse brain = ask(message, earlierTurns(message));
        PolicyResult policy = policyEngine.apply(message.channel(), brain.reply());
        BotReply reply = new BotReply(
                message.messageId(),
                message.channel(),
                brain.reply(),
                policy.finalText(),
                policy.violations(),
                policy.blocked(),
                brain.handoff(),
                productCards(message, brain, policy));

        record("câu trả lời", key, () -> recorder.reply(message, reply));
        return reply;
    }

    /**
     * Thẻ sản phẩm cho widget WEB (T-43.3): tra các SKU AI Brain gợi ý. Kênh sàn chỉ nhận chữ nên không tra; câu bị
     * policy chặn thì không có thẻ. Tra lỗi chỉ ghi log, câu trả lời vẫn đi.
     */
    private List<ProductCard> productCards(CanonicalMessage message, BrainResponse brain, PolicyResult policy) {
        if (suggestions == null || message.channel() != ChannelType.WEB || policy.blocked() || brain.products().isEmpty()) {
            return List.of();
        }
        try {
            return suggestions.cards(brain.products().stream().map(BrainProduct::sku).toList());
        } catch (RuntimeException e) {
            log.warn("Không tra được thẻ sản phẩm cho {}: {}", message.messageId(), e.toString());
            return List.of();
        }
    }

    private List<BrainTurn> earlierTurns(CanonicalMessage message) {
        try {
            List<BrainTurn> turns = history.before(message);
            return turns == null ? List.of() : turns;
        } catch (RuntimeException e) {
            log.error("Không đọc được lịch sử của {}: {}", message.messageId(), e.toString());
            return List.of();
        }
    }

    private BrainResponse ask(CanonicalMessage message, List<BrainTurn> turns) {
        try {
            BrainResponse response = brainClient.ask(message, turns);
            return response != null ? response : BrainResponse.unavailable();
        } catch (RuntimeException e) {
            // HttpBrainClient đã tự bắt lỗi HTTP; đây là lưới cuối để khách không bị bỏ rơi.
            log.warn("AI Brain lỗi với tin {}: {}", message.messageId(), e.toString());
            return BrainResponse.unavailable();
        }
    }

    /** Lưu lỗi (DB sập...) chỉ ghi log, không chặn câu trả lời gửi khách. */
    private static void record(String what, String key, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Không lưu được {} của {}: {}", what, key, e.toString());
        }
    }
}

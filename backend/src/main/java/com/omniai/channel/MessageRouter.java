package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.policy.PolicyEngine;
import com.omniai.policy.PolicyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    // Idempotency tạm trong bộ nhớ: kênh gửi lại cùng messageId (webhook retry) thì trả lại kết quả cũ.
    // Sprint 7 chuyển sang Redis để chạy được nhiều instance.
    private final Map<String, BotReply> processed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BotReply> eldest) {
                    return size() > IDEMPOTENCY_CACHE_SIZE;
                }
            });

    public MessageRouter(BrainClient brainClient, PolicyEngine policyEngine, ConversationRecorder recorder) {
        this.brainClient = brainClient;
        this.policyEngine = policyEngine;
        this.recorder = recorder;
    }

    public BotReply handle(CanonicalMessage message) {
        String key = message.channel() + ":" + message.messageId();
        BotReply cached = processed.get(key);
        if (cached != null) {
            return cached;
        }
        // Lưu tin khách trước khi hỏi Brain: Brain treo hay lỗi thì tin vẫn còn (NFR-06).
        record("tin vào", key, () -> recorder.inbound(message));

        BrainResponse brain = ask(message);
        PolicyResult policy = policyEngine.apply(message.channel(), brain.reply());
        BotReply reply = new BotReply(
                message.messageId(),
                message.channel(),
                brain.reply(),
                policy.finalText(),
                policy.violations(),
                policy.blocked(),
                brain.handoff());

        record("câu trả lời", key, () -> recorder.reply(message, reply));
        processed.put(key, reply);
        return reply;
    }

    private BrainResponse ask(CanonicalMessage message) {
        try {
            BrainResponse response = brainClient.ask(message);
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

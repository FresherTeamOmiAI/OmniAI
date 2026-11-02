package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.policy.PolicyEngine;
import com.omniai.policy.PolicyResult;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Luồng chung cho mọi kênh: tin chuẩn hóa -> AI Brain -> Policy của kênh -> câu trả lời. Owner: TuTM.
 */
@Service
public class MessageRouter {

    private static final int IDEMPOTENCY_CACHE_SIZE = 10_000;

    private final BrainClient brainClient;
    private final PolicyEngine policyEngine;

    // Idempotency tạm trong bộ nhớ: kênh gửi lại cùng messageId (webhook retry) thì trả lại kết quả cũ.
    // Sprint 7 chuyển sang Redis để chạy được nhiều instance.
    private final Map<String, BotReply> processed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BotReply> eldest) {
                    return size() > IDEMPOTENCY_CACHE_SIZE;
                }
            });

    public MessageRouter(BrainClient brainClient, PolicyEngine policyEngine) {
        this.brainClient = brainClient;
        this.policyEngine = policyEngine;
    }

    public BotReply handle(CanonicalMessage message) {
        String key = message.channel() + ":" + message.messageId();
        BotReply cached = processed.get(key);
        if (cached != null) {
            return cached;
        }
        BrainResponse brain = brainClient.ask(message);
        PolicyResult policy = policyEngine.apply(message.channel(), brain.reply());
        BotReply reply = new BotReply(
                message.messageId(),
                message.channel(),
                brain.reply(),
                policy.finalText(),
                policy.violations(),
                policy.blocked(),
                brain.handoff());
        processed.put(key, reply);
        return reply;
    }
}

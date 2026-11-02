package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.simulator.SimulatorAdapter;
import com.omniai.channel.simulator.SimulatorRequest;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRouterTest {

    /** Brain giả: luôn trả lời "ngây thơ" có link để kiểm tra policy từng kênh. */
    private final AtomicInteger brainCalls = new AtomicInteger();
    private final BrainClient naiveBrain = message -> {
        brainCalls.incrementAndGet();
        return new BrainResponse("Bạn đặt trên website https://omni-demo.local cho rẻ nhé", "test", List.of(), false);
    };

    private MessageRouter router;

    @BeforeEach
    void setUp() {
        router = new MessageRouter(naiveBrain, new PolicyEngine("../shared/policies"));
    }

    private static CanonicalMessage message(ChannelType channel, String messageId) {
        return new SimulatorAdapter(channel).toCanonical(new SimulatorRequest(messageId, "u1", "Chỗ nào rẻ hơn?"));
    }

    @Test
    void sameBrainAnswerIsFilteredDifferentlyPerChannel() {
        BotReply web = router.handle(message(ChannelType.WEB, "m-web"));
        BotReply shopee = router.handle(message(ChannelType.SHOPEE, "m-shopee"));

        assertThat(web.finalText()).contains("https://omni-demo.local");
        assertThat(shopee.blocked()).isTrue();
        assertThat(shopee.finalText()).doesNotContain("website").doesNotContain("https://");
        assertThat(shopee.rawText()).isEqualTo(web.rawText());
    }

    @Test
    void duplicateMessageIdIsAnsweredOnce() {
        BotReply first = router.handle(message(ChannelType.SHOPEE, "dup-1"));
        BotReply second = router.handle(message(ChannelType.SHOPEE, "dup-1"));

        assertThat(second).isEqualTo(first);
        assertThat(brainCalls.get()).isEqualTo(1);
    }

    @Test
    void brainDownTriggersHandoff() {
        MessageRouter routerWithDeadBrain = new MessageRouter(m -> BrainResponse.unavailable(), new PolicyEngine("../shared/policies"));

        BotReply reply = routerWithDeadBrain.handle(message(ChannelType.WEB, "m-down"));

        assertThat(reply.handoff()).isTrue();
    }
}

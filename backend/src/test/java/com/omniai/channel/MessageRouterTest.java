package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.simulator.SimulatorAdapter;
import com.omniai.channel.simulator.SimulatorRequest;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        router = new MessageRouter(naiveBrain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);
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
    void idempotencyKeySeparatesDifferentChannelsWithSameMessageId() {
        BotReply web = router.handle(message(ChannelType.WEB, "shared-id"));
        BotReply shopee = router.handle(message(ChannelType.SHOPEE, "shared-id"));

        assertThat(web.channel()).isEqualTo(ChannelType.WEB);
        assertThat(shopee.channel()).isEqualTo(ChannelType.SHOPEE);
        assertThat(shopee.blocked()).isTrue();
        assertThat(web.blocked()).isFalse();
        assertThat(brainCalls.get()).isEqualTo(2);
    }

    @Test
    void routerAppliesMedicalClaimSofteningOnWebReply() {
        BrainClient medicalBrain = m -> new BrainResponse("Serum này đặc trị mụn viêm rất nhanh", "test", List.of(), false);
        MessageRouter medRouter = new MessageRouter(medicalBrain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);

        BotReply reply = medRouter.handle(message(ChannelType.WEB, "m-med"));

        assertThat(reply.finalText()).doesNotContain("đặc trị").contains("hỗ trợ cải thiện");
        assertThat(reply.violations()).isNotEmpty();
    }

    @Test
    void brainDownTriggersHandoff() {
        MessageRouter routerWithDeadBrain = new MessageRouter(m -> BrainResponse.unavailable(), new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);

        BotReply reply = routerWithDeadBrain.handle(message(ChannelType.WEB, "m-down"));

        assertThat(reply.handoff()).isTrue();
    }

    /** Recorder giả: ghi lại thứ tự lời gọi, có thể bắt ném lỗi như DB sập. */
    private static final class RecordingRecorder implements ConversationRecorder {
        final List<String> calls = new ArrayList<>();
        boolean fail;

        @Override
        public void inbound(CanonicalMessage message) {
            calls.add("in:" + message.messageId());
            if (fail) throw new IllegalStateException("DB sập");
        }

        @Override
        public void reply(CanonicalMessage message, BotReply reply) {
            calls.add("out:" + reply.messageId() + ":handoff=" + reply.handoff());
            if (fail) throw new IllegalStateException("DB sập");
        }
    }

    @Test
    void recordsInboundBeforeAskingBrainAndReplyAfter() {
        RecordingRecorder recorder = new RecordingRecorder();
        BrainClient brain = m -> {
            recorder.calls.add("brain");
            return new BrainResponse("Chào bạn", "test", List.of(), false);
        };
        MessageRouter r = new MessageRouter(brain, new PolicyEngine("../shared/policies"), recorder);

        r.handle(message(ChannelType.WEB, "m-rec"));
        r.handle(message(ChannelType.WEB, "m-rec"));

        // Gửi trùng messageId thì trả cache, không lưu lần nữa.
        assertThat(recorder.calls).containsExactly("in:m-rec", "brain", "out:m-rec:handoff=false");
    }

    @Test
    void storageFailureDoesNotBlockReply() {
        RecordingRecorder recorder = new RecordingRecorder();
        recorder.fail = true;
        MessageRouter r = new MessageRouter(naiveBrain, new PolicyEngine("../shared/policies"), recorder);

        BotReply reply = r.handle(message(ChannelType.WEB, "m-dbdown"));

        assertThat(reply.finalText()).isNotBlank();
        assertThat(recorder.calls).hasSize(2);
    }

    @Test
    void brainExceptionBecomesHandoffAndIsRecorded() {
        RecordingRecorder recorder = new RecordingRecorder();
        BrainClient crashing = m -> {
            throw new IllegalStateException("brain nổ");
        };
        MessageRouter r = new MessageRouter(crashing, new PolicyEngine("../shared/policies"), recorder);

        BotReply reply = r.handle(message(ChannelType.SHOPEE, "m-crash"));

        assertThat(reply.handoff()).isTrue();
        assertThat(reply.finalText()).isEqualTo(BrainResponse.unavailable().reply());
        assertThat(recorder.calls).containsExactly("in:m-crash", "out:m-crash:handoff=true");
    }
}

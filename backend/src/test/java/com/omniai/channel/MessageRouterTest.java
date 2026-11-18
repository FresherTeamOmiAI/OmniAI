package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.brain.BrainTurn;
import com.omniai.channel.simulator.SimulatorAdapter;
import com.omniai.channel.simulator.SimulatorRequest;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.conversation.TurnHistory;
import com.omniai.policy.PolicyEngine;
import com.omniai.policy.PolicyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRouterTest {

    /** Brain giả: luôn trả lời "ngây thơ" có link để kiểm tra policy từng kênh. */
    private final AtomicInteger brainCalls = new AtomicInteger();
    private final BrainClient naiveBrain = (message, history) -> {
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
    void concurrentDuplicatesAskBrainOnceAndShareTheReply() throws Exception {
        // Brain chậm: lượt đầu còn đang chờ Brain thì request trùng thứ hai đã tới (T-11.4).
        CountDownLatch brainEntered = new CountDownLatch(1);
        CountDownLatch releaseBrain = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        BrainClient slowBrain = (m, history) -> {
            calls.incrementAndGet();
            brainEntered.countDown();
            try {
                releaseBrain.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new BrainResponse("Chào bạn", "test", List.of(), false);
        };
        RecordingRecorder recorder = new RecordingRecorder();
        MessageRouter r = new MessageRouter(slowBrain, new PolicyEngine("../shared/policies"), recorder);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<BotReply> first = pool.submit(() -> r.handle(message(ChannelType.SHOPEE, "race-1")));
            assertThat(brainEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<BotReply> second = pool.submit(() -> r.handle(message(ChannelType.SHOPEE, "race-1")));
            // Cho request thứ hai kịp vào handle() trước khi Brain trả lời.
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();
            releaseBrain.countDown();

            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(first.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(recorder.calls).containsExactly("in:race-1", "out:race-1:handoff=false");
    }

    @Test
    void failedTurnIsNotRememberedSoRetryIsProcessed() {
        AtomicInteger applies = new AtomicInteger();
        PolicyEngine flaky = new PolicyEngine("../shared/policies") {
            @Override
            public PolicyResult apply(ChannelType channel, String text) {
                if (applies.incrementAndGet() == 1) {
                    throw new IllegalStateException("policy nổ");
                }
                return super.apply(channel, text);
            }
        };
        MessageRouter r = new MessageRouter(naiveBrain, flaky, ConversationRecorder.NOOP);

        assertThatThrownBy(() -> r.handle(message(ChannelType.WEB, "m-retry"))).hasMessage("policy nổ");
        BotReply retried = r.handle(message(ChannelType.WEB, "m-retry"));

        assertThat(retried.finalText()).isNotBlank();
        assertThat(brainCalls.get()).isEqualTo(2);
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
        BrainClient medicalBrain = (m, history) -> new BrainResponse("Serum này đặc trị mụn viêm rất nhanh", "test", List.of(), false);
        MessageRouter medRouter = new MessageRouter(medicalBrain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);

        BotReply reply = medRouter.handle(message(ChannelType.WEB, "m-med"));

        assertThat(reply.finalText()).doesNotContain("đặc trị").contains("hỗ trợ cải thiện");
        assertThat(reply.violations()).isNotEmpty();
    }

    @Test
    void brainDownTriggersHandoff() {
        MessageRouter routerWithDeadBrain = new MessageRouter((m, history) -> BrainResponse.unavailable(), new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);

        BotReply reply = routerWithDeadBrain.handle(message(ChannelType.WEB, "m-down"));

        assertThat(reply.handoff()).isTrue();
    }

    @Test
    void passesEarlierTurnsToTheBrain() {
        List<List<BrainTurn>> seen = new ArrayList<>();
        BrainClient brain = (message, history) -> {
            seen.add(history);
            return new BrainResponse("Chào bạn", "test", List.of(), false);
        };
        TurnHistory history = message -> List.of(new BrainTurn("assistant", "Gợi ý trong shop:\n1. CeraVe — Foaming Cleanser — 289.000đ"));
        MessageRouter r = new MessageRouter(brain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP, history);

        r.handle(message(ChannelType.WEB, "m-hist"));

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)).containsExactly(new BrainTurn("assistant", "Gợi ý trong shop:\n1. CeraVe — Foaming Cleanser — 289.000đ"));
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
        BrainClient brain = (m, history) -> {
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
        BrainClient crashing = (m, history) -> {
            throw new IllegalStateException("brain nổ");
        };
        MessageRouter r = new MessageRouter(crashing, new PolicyEngine("../shared/policies"), recorder);

        BotReply reply = r.handle(message(ChannelType.SHOPEE, "m-crash"));

        assertThat(reply.handoff()).isTrue();
        assertThat(reply.finalText()).isEqualTo(BrainResponse.unavailable().reply());
        assertThat(recorder.calls).containsExactly("in:m-crash", "out:m-crash:handoff=true");
    }
}

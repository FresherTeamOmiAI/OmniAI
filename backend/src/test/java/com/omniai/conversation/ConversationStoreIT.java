package com.omniai.conversation;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.MessageRouter;
import com.omniai.channel.simulator.SimulatorAdapter;
import com.omniai.channel.simulator.SimulatorRequest;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Lưu hội thoại qua MessageRouter thật trên Postgres (Testcontainers, V9). Chỉ giả AI Brain.
 * DB dùng chung với các IT khác nên mỗi test dùng userId ngẫu nhiên và chỉ đếm dòng của hội thoại mình.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ConversationStoreIT {

    private static final String LINK_REPLY = "Bạn đặt trên website https://omni-demo.local cho rẻ nhé";

    @Autowired
    MessageRouter router;

    @Autowired
    ConversationService service;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    BrainClient brainClient;

    private static CanonicalMessage message(ChannelType channel, String userId, String messageId, String text) {
        return new SimulatorAdapter(channel).toCanonical(new SimulatorRequest(messageId, userId, text));
    }

    private Conversation conversationOf(CanonicalMessage m) {
        return conversations.findAll().stream()
                .filter(c -> c.getChannel() == m.channel() && c.getExternalId().equals(m.conversationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Chưa lưu hội thoại " + m.conversationId()));
    }

    private List<Map<String, Object>> rows(Long conversationId) {
        return jdbc.queryForList("""
                SELECT id, direction, message_id, text, raw_text, blocked, handoff
                  FROM conversation_message WHERE conversation_id = ? ORDER BY id""", conversationId);
    }

    @Test
    void storesInboundAndReplyWithRawFinalTextAndViolations() {
        when(brainClient.ask(any())).thenReturn(new BrainResponse(LINK_REPLY, "test", List.of(), false));
        CanonicalMessage m = message(ChannelType.SHOPEE, "u-" + UUID.randomUUID(), "m-" + UUID.randomUUID(),
                "Chỗ nào rẻ hơn?");

        BotReply reply = router.handle(m);

        Conversation c = conversationOf(m);
        assertThat(c.getChannelUserId()).isEqualTo(m.channelUserId());
        assertThat(c.getMessageCount()).isEqualTo(2);
        assertThat(c.getCustomerId()).isNull();

        List<Map<String, Object>> rows = rows(c.getId());
        assertThat(rows).extracting(r -> r.get("direction")).containsExactly("IN", "OUT");
        assertThat(rows.get(0).get("text")).isEqualTo("Chỗ nào rẻ hơn?");
        assertThat(rows.get(0).get("raw_text")).isNull();

        Map<String, Object> out = rows.get(1);
        assertThat(out.get("message_id")).isEqualTo(m.messageId());
        assertThat(out.get("raw_text")).isEqualTo(LINK_REPLY);
        assertThat(out.get("text")).isEqualTo(reply.finalText()).isNotEqualTo(LINK_REPLY);
        assertThat(out.get("blocked")).isEqualTo(true);

        List<String> rules = jdbc.queryForList(
                "SELECT rule_id FROM message_violation WHERE message_id = ? ORDER BY position", String.class,
                out.get("id"));
        assertThat(rules).containsExactlyElementsOf(reply.violations().stream().map(v -> v.ruleId()).toList());
        assertThat(rules).isNotEmpty();
    }

    @Test
    void messagesOfSameConversationShareOneRowAndDuplicatesAreNotStoredTwice() {
        when(brainClient.ask(any())).thenReturn(new BrainResponse("Chào bạn", "test", List.of(), false));
        String user = "u-" + UUID.randomUUID();
        CanonicalMessage first = message(ChannelType.WEB, user, "m1-" + UUID.randomUUID(), "Xin chào");
        CanonicalMessage second = message(ChannelType.WEB, user, "m2-" + UUID.randomUUID(), "Có kem chống nắng không?");

        router.handle(first);
        router.handle(second);
        router.handle(second); // kênh gửi lại cùng messageId: router trả cache
        service.inbound(second); // như sau khi restart mất cache idempotency: DB tự bỏ qua dòng trùng

        Conversation c = conversationOf(first);
        assertThat(c.getMessageCount()).isEqualTo(4);
        assertThat(rows(c.getId())).extracting(r -> r.get("direction")).containsExactly("IN", "OUT", "IN", "OUT");
        assertThat(c.getLastMessageAt()).isAfterOrEqualTo(c.getStartedAt());
    }

    @Test
    void sameUserOnDifferentChannelsGivesSeparateConversations() {
        when(brainClient.ask(any())).thenReturn(new BrainResponse("Chào bạn", "test", List.of(), false));
        String user = "u-" + UUID.randomUUID();
        CanonicalMessage web = message(ChannelType.WEB, user, "m-" + UUID.randomUUID(), "Hi");
        CanonicalMessage tiktok = message(ChannelType.TIKTOK, user, "m-" + UUID.randomUUID(), "Hi");

        router.handle(web);
        router.handle(tiktok);

        assertThat(conversationOf(web).getId()).isNotEqualTo(conversationOf(tiktok).getId());
    }
}

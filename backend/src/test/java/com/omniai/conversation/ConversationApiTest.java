package com.omniai.conversation;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationMessage.Direction;
import com.omniai.policy.PolicyAction;
import com.omniai.policy.PolicyViolation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API xem hội thoại (T-11.2). Dữ liệu seed qua repository và rollback sau mỗi test (@Transactional).
 * DB dùng chung với các IT khác (có thể còn hội thoại của chúng), nên test chỉ kiểm hội thoại tự seed:
 * mốc thời gian năm 2100 để luôn đứng đầu danh sách sắp theo tin mới nhất.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class ConversationApiTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin");
    private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationMessageRepository messages;

    @MockitoBean
    BrainClient brainClient;

    private Conversation seed(ChannelType channel, Instant lastMessageAt) {
        Conversation c = new Conversation();
        c.setChannel(channel);
        String user = "u-" + UUID.randomUUID();
        c.setChannelUserId(user);
        c.setExternalId(channel.name().toLowerCase() + ":" + user);
        c.setStartedAt(lastMessageAt.minusSeconds(60));
        c.setLastMessageAt(lastMessageAt);
        c.setMessageCount(2);
        return conversations.saveAndFlush(c);
    }

    private ConversationMessage seedMessage(Conversation c, Direction direction, String text, Instant at) {
        ConversationMessage m = new ConversationMessage();
        m.setConversationId(c.getId());
        m.setDirection(direction);
        m.setMessageId("m-1");
        m.setText(text);
        m.setCreatedAt(at);
        return m;
    }

    // --- Xác thực ---

    @Test
    void requiresAdminBasicAuth() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations").with(httpBasic("admin", "sai"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations/1/messages")).andExpect(status().isUnauthorized());
    }

    // --- Danh sách ---

    @Test
    void filtersByChannelCaseInsensitive() throws Exception {
        Conversation web = seed(ChannelType.WEB, FUTURE);
        Conversation tiktok = seed(ChannelType.TIKTOK, FUTURE.plusSeconds(1));

        mvc.perform(get("/api/conversations").param("channel", "web").param("size", "100").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].channel", everyItem(is("WEB"))))
                .andExpect(jsonPath("$.items[0].id").value(web.getId()))
                .andExpect(jsonPath("$.items[0].conversationId").value(web.getExternalId()))
                .andExpect(jsonPath("$.items[0].channelUserId").value(web.getChannelUserId()))
                .andExpect(jsonPath("$.items[0].messageCount").value(2))
                .andExpect(jsonPath("$.items[0].customerId", nullValue()));

        mvc.perform(get("/api/conversations").param("channel", "TIKTOK").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].channel", everyItem(is("TIKTOK"))))
                .andExpect(jsonPath("$.items[0].id").value(tiktok.getId()));

        // Bỏ trống channel = mọi kênh
        mvc.perform(get("/api/conversations").param("size", "100").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(web.getId().intValue())))
                .andExpect(jsonPath("$.items[*].id", hasItem(tiktok.getId().intValue())));
    }

    @Test
    void pagesNewestFirst() throws Exception {
        Conversation older = seed(ChannelType.SHOPEE, FUTURE);
        Conversation newer = seed(ChannelType.SHOPEE, FUTURE.plusSeconds(3600));

        mvc.perform(get("/api/conversations").param("channel", "shopee").param("size", "1").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId()));

        mvc.perform(get("/api/conversations").param("channel", "shopee").param("size", "1").param("page", "1")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(older.getId()));
    }

    @Test
    void rejectsUnknownChannelAndBadPaging() throws Exception {
        mvc.perform(get("/api/conversations").param("channel", "telegram").with(ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("size", "0").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("size", "101").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("page", "-1").with(ADMIN)).andExpect(status().isBadRequest());
    }

    // --- Tin của 1 hội thoại ---

    @Test
    void messagesShowRawFinalTextAndViolationsInOrder() throws Exception {
        Conversation c = seed(ChannelType.SHOPEE, FUTURE);
        ConversationMessage in = seedMessage(c, Direction.IN, "Chỗ nào rẻ hơn?", FUTURE.minusSeconds(5));
        ConversationMessage out = seedMessage(c, Direction.OUT, "Dạ bạn đặt ngay tại shop nhé", FUTURE);
        out.setRawText("Bạn đặt trên website https://omni-demo.local cho rẻ nhé");
        out.setBlocked(true);
        out.getViolations().add(StoredViolation.of(new PolicyViolation("no_external_link", PolicyAction.BLOCK,
                "https://omni-demo.local")));
        messages.save(out); // lưu OUT trước để chắc thứ tự trả về theo thời gian, không theo id
        messages.saveAndFlush(in);

        mvc.perform(get("/api/conversations/" + c.getId() + "/messages").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].text").value("Chỗ nào rẻ hơn?"))
                .andExpect(jsonPath("$.items[0].rawText", nullValue()))
                .andExpect(jsonPath("$.items[0].violations.length()").value(0))
                .andExpect(jsonPath("$.items[1].direction").value("OUT"))
                .andExpect(jsonPath("$.items[1].messageId").value("m-1"))
                .andExpect(jsonPath("$.items[1].text").value("Dạ bạn đặt ngay tại shop nhé"))
                .andExpect(jsonPath("$.items[1].rawText").value("Bạn đặt trên website https://omni-demo.local cho rẻ nhé"))
                .andExpect(jsonPath("$.items[1].blocked").value(true))
                .andExpect(jsonPath("$.items[1].handoff").value(false))
                .andExpect(jsonPath("$.items[1].violations[0].ruleId").value("no_external_link"))
                .andExpect(jsonPath("$.items[1].violations[0].action").value("BLOCK"))
                .andExpect(jsonPath("$.items[1].violations[0].matched").value("https://omni-demo.local"));
    }

    @Test
    void unknownConversationIs404() throws Exception {
        mvc.perform(get("/api/conversations/999999999/messages").with(ADMIN)).andExpect(status().isNotFound());
    }
}

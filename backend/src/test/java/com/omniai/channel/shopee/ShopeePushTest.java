package com.omniai.channel.shopee;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.MessageRouter;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hub nhận push Shopee: kiểm chữ ký, qua luật kênh, trả lời bằng send_message (T-21.6). */
class ShopeePushTest {

    private static final String CALLBACK = "http://127.0.0.1:8080/api/channels/shopee/push";
    private static final String KEY = "test-partner-key";

    private final JsonMapper json = JsonMapper.builder().build();
    private final ShopeeChatClient chat = mock(ShopeeChatClient.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BrainClient brain = (message, history) -> new BrainResponse(
                "Xem tại https://omni-demo.local nhé", "test", List.of(), false);
        MessageRouter router = new MessageRouter(brain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);
        ShopeeChannelProperties properties = new ShopeeChannelProperties();
        properties.setPartnerKey(KEY);
        properties.setPushCallbackUrl(CALLBACK);
        ShopeePushController controller = new ShopeePushController(
                properties, new ShopeePushProcessor(router, chat), clock);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void wrongSignatureIsRejectedAndNothingIsStored() throws Exception {
        String body = buyerPush("m-bad", "Da dầu dùng kem nào?");

        mvc.perform(post("/api/channels/shopee/push")
                        .header("Authorization", "not-the-sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("error_sign"));

        verify(chat, never()).sendText(anyLong(), anyString());
    }

    @Test
    void buyerTextIsAnsweredWithinShopeeRules() throws Exception {
        String body = buyerPush("m-1", "Da dầu dùng kem chống nắng nào?");

        mvc.perform(post("/api/channels/shopee/push")
                        .header("Authorization", ShopeeSign.push(CALLBACK, body, KEY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final_text").value("Xem tại [đã ẩn] nhé"));

        verify(chat).sendText(eq(165105353L), eq("Xem tại [đã ẩn] nhé"));
    }

    @Test
    void failedSendMessageReturns500SoSimulatorRetries() throws Exception {
        String body = buyerPush("m-retry", "Còn hàng không?");
        String sign = ShopeeSign.push(CALLBACK, body, KEY);
        when(chat.sendText(anyLong(), anyString()))
                .thenThrow(new ShopeeApiClient.ShopeeApiException("io", "simulator tắt", true))
                .thenReturn("42");

        mvc.perform(post("/api/channels/shopee/push").header("Authorization", sign)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("process_failed"));
        mvc.perform(post("/api/channels/shopee/push").header("Authorization", sign)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final_text").exists());

        verify(chat, times(2)).sendText(eq(165105353L), anyString());
    }

    @Test
    void replyThatNamesAnotherChannelIsReplacedByTheShopeeFallback() throws Exception {
        String body = buyerPush("m-block", "Cho mình xin Zalo");
        MockMvc blocked = mvcWithBrain("Bạn nhắn Zalo 0901234567 nhé");

        String raw = blocked.perform(post("/api/channels/shopee/push")
                        .header("Authorization", ShopeeSign.push(CALLBACK, body, KEY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String finalText = json.readTree(raw).path("final_text").asString();

        assertThat(finalText).startsWith("Dạ, shop xin phép tư vấn trong phạm vi Shopee");
        assertThat(finalText).doesNotContain("Zalo").doesNotContain("090");
    }

    @Test
    void replyDoesNotExceedShopeeMaxLength() throws Exception {
        String body = buyerPush("m-long", "Kể chi tiết giúp mình");
        MockMvc longReply = mvcWithBrain("a".repeat(1_500));

        String raw = longReply.perform(post("/api/channels/shopee/push")
                        .header("Authorization", ShopeeSign.push(CALLBACK, body, KEY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String finalText = json.readTree(raw).path("final_text").asString();

        assertThat(finalText.codePointCount(0, finalText.length())).isEqualTo(1_000);
        assertThat(finalText).endsWith("…");
    }

    @Test
    void duplicatePushDoesNotReplyTwice() throws Exception {
        String body = buyerPush("m-dup", "Còn hàng không?");
        String sign = ShopeeSign.push(CALLBACK, body, KEY);

        mvc.perform(post("/api/channels/shopee/push").header("Authorization", sign)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/channels/shopee/push").header("Authorization", sign)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final_text").doesNotExist());

        verify(chat, times(1)).sendText(anyLong(), anyString());
    }

    private MockMvc mvcWithBrain(String reply) {
        BrainClient brain = (message, history) -> new BrainResponse(reply, "test", List.of(), false);
        MessageRouter router = new MessageRouter(brain, new PolicyEngine("../shared/policies"), ConversationRecorder.NOOP);
        ShopeeChannelProperties properties = new ShopeeChannelProperties();
        properties.setPartnerKey(KEY);
        properties.setPushCallbackUrl(CALLBACK);
        return MockMvcBuilders.standaloneSetup(new ShopeePushController(
                properties, new ShopeePushProcessor(router, chat), clock)).build();
    }

    private String buyerPush(String messageId, String text) {
        JsonNode push = json.readTree("""
                {
                  "msg_id": "",
                  "data": {
                    "type": "message",
                    "region": "VN",
                    "content": {
                      "message_id": "%s",
                      "from_id": 165105353,
                      "from_user_name": "nguyen.a",
                      "to_id": 10001,
                      "message_type": "text",
                      "content": {"text": "%s"},
                      "conversation_id": "709122092476686867",
                      "created_timestamp": 1700000000,
                      "from_shop_id": 0,
                      "status": "user_chat"
                    }
                  },
                  "shop_id": 10001,
                  "code": 10,
                  "timestamp": 1700000000
                }
                """.formatted(messageId, text));
        return json.writeValueAsString(push);
    }
}

package com.omniai.channel.tiktok;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.credential.ChannelCredentialRepository;
import com.omniai.channel.tiktok.TiktokModels.AuthorizedShop;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationMessage;
import com.omniai.conversation.ConversationMessage.DeliveryStatus;
import com.omniai.conversation.ConversationMessage.Direction;
import com.omniai.conversation.ConversationMessageRepository;
import com.omniai.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook TikTok qua Spring thật + Postgres (Testcontainers). Chỉ giả AI Brain và client gửi tin ra sàn.
 * Kiểm: chữ ký, chống replay, trả 200 trước khi gọi Brain, chống trùng, bỏ qua tin của shop, luật TIKTOK, OAuth,
 * gửi lỗi sau khi đã trả 200 thì thử lại / handoff, hàng đợi đầy trả 503 (T-25.4).
 */
@SpringBootTest(properties = {"omniai.tiktok.app-key=test-app", "omniai.tiktok.app-secret=test-secret",
        "omniai.tiktok.send-attempts=4", "omniai.tiktok.send-retry-backoff=20ms"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class TiktokWebhookIT {

    private static final String WEBHOOK = "/api/channels/tiktok/webhook";
    private static final String SHOP = "7494049642642441621";
    private static final String REPLY = "Dạ, bạn da dầu dùng gel chống nắng kiềm dầu sẽ thoáng hơn ạ.";

    @Autowired
    MockMvc mvc;
    @Autowired
    PolicyEngine policyEngine;
    @Autowired
    TiktokTokenManager tokenManager;
    @Autowired
    ChannelCredentialRepository credentials;
    @Autowired
    ConversationMessageRepository messages;
    @MockitoSpyBean
    TiktokWebhookQueue queue;

    @MockitoBean
    BrainClient brainClient;
    @MockitoBean
    TiktokShopClient shopClient;

    @BeforeEach
    void setUp() {
        reset(brainClient, shopClient, queue);
        when(brainClient.ask(any(), any())).thenReturn(new BrainResponse(REPLY, "test", List.of(), false));
    }

    // ---------- helper ----------

    private static String id() {
        return Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    private static String envelope(int type, String notificationId, long timestamp, Map<String, Object> data) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("type", type);
        env.put("tts_notification_id", notificationId);
        env.put("shop_id", SHOP);
        env.put("timestamp", timestamp);
        env.put("data", data);
        return TiktokModels.JSON.writeValueAsString(env);
    }

    private static Map<String, Object> message(String messageId, String conversationId, String role, String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", messageId);
        data.put("conversation_id", conversationId);
        data.put("type", "TEXT");
        data.put("content", TiktokModels.JSON.writeValueAsString(Map.of("content", text)));
        data.put("create_time", Instant.now().getEpochSecond());
        data.put("is_visible", true);
        data.put("index", "1");
        data.put("sender", Map.of("im_user_id", "im-" + role, "role", role));
        return data;
    }

    private String newMessage(String messageId, String conversationId, String role, String text) {
        return envelope(TiktokWebhookType.NEW_MESSAGE, id(), Instant.now().getEpochSecond(),
                message(messageId, conversationId, role, text));
    }

    /** Câu bot đã lưu cho tin buyer {@code messageId}. */
    private Optional<ConversationMessage> reply(String messageId) {
        return messages.findAll().stream()
                .filter(m -> m.getDirection() == Direction.OUT && m.getMessageId().equals(messageId))
                .findFirst();
    }

    private ConversationMessage awaitDelivery(String messageId, DeliveryStatus status) {
        await().atMost(Duration.ofSeconds(5)).until(() -> reply(messageId)
                .map(m -> m.getDeliveryStatus() == status).orElse(false));
        return reply(messageId).orElseThrow();
    }

    private ResultActions postSigned(String body) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", TiktokSigner.signWebhook("test-app", body, "test-secret"))
                .content(body));
    }

    // ---------- bảo mật ----------

    @Test
    void rejectsMissingOrWrongSignature() throws Exception {
        String body = newMessage(id(), "c1", "BUYER", "hi");
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", TiktokSigner.signWebhook("test-app", body, "wrong-secret"))
                        .content(body))
                .andExpect(status().isUnauthorized());
        // Đổi body sau khi ký
        String signature = TiktokSigner.signWebhook("test-app", body, "test-secret");
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).header("Authorization", signature)
                        .content(body.replace("hi", "hello")))
                .andExpect(status().isUnauthorized());
        verify(brainClient, after(300).never()).ask(any(), any());
    }

    @Test
    void rejectsReplayedOldWebhook() throws Exception {
        String body = envelope(TiktokWebhookType.NEW_MESSAGE, id(), Instant.now().minusSeconds(3600).getEpochSecond(),
                message(id(), "c1", "BUYER", "hi"));
        postSigned(body).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("stale_timestamp"));
    }

    @Test
    void rejectsSignedButMalformedPayload() throws Exception {
        postSigned("{\"type\":\"x\"").andExpect(status().isBadRequest());
        postSigned("{\"type\":14,\"shop_id\":\"s\",\"timestamp\":1}").andExpect(status().isBadRequest());
    }

    // ---------- trả 200 nhanh, xử lý async ----------

    @Test
    void acknowledgesWithinThreeSecondsEvenWhenBrainIsSlow() throws Exception {
        when(brainClient.ask(any(), any())).thenAnswer(inv -> {
            Thread.sleep(5_000);
            return new BrainResponse(REPLY, "test", List.of(), false);
        });
        String conversation = id();
        long started = System.nanoTime();
        postSigned(newMessage(id(), conversation, "BUYER", "Da dầu dùng kem chống nắng nào?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));

        verify(shopClient, timeout(10_000)).sendText(SHOP, conversation, REPLY);
    }

    // ---------- chống trùng ----------

    @Test
    void sameNotificationTwiceAnswersOnce() throws Exception {
        String conversation = id();
        String body = newMessage(id(), conversation, "BUYER", "hi");
        postSigned(body).andExpect(status().isOk());
        postSigned(body).andExpect(status().isOk());
        verify(shopClient, timeout(3_000)).sendText(SHOP, conversation, REPLY);
        verify(shopClient, after(500).times(1)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void sameMessageInTwoNotificationsAnswersOnce() throws Exception {
        String conversation = id();
        String messageId = id();
        postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());
        postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());
        verify(shopClient, timeout(3_000)).sendText(SHOP, conversation, REPLY);
        verify(shopClient, after(500).times(1)).sendText(anyString(), anyString(), anyString());
    }

    // ---------- lọc tin ----------

    @Test
    void ignoresMessagesFromShopOrBot() throws Exception {
        for (String role : List.of("ROBOT", "SHOP", "CUSTOMER_SERVICE", "SYSTEM")) {
            postSigned(newMessage(id(), id(), role, "Dạ shop chào bạn")).andExpect(status().isOk());
        }
        verify(brainClient, after(500).never()).ask(any(), any());
        verify(shopClient, never()).sendText(anyString(), anyString(), anyString());
    }

    // ---------- luật kênh + lỗi sàn ----------

    @Test
    void replyGoesThroughTiktokPolicy() throws Exception {
        when(brainClient.ask(any(), any())).thenReturn(new BrainResponse(
                "Bạn nhắn Zalo shop để được giá tốt hơn nhé", "test", List.of(), false));
        String conversation = id();
        postSigned(newMessage(id(), conversation, "BUYER", "rẻ hơn ở đâu?")).andExpect(status().isOk());
        String fallback = policyEngine.policyOf(ChannelType.TIKTOK).fallbackMessage();
        verify(shopClient, timeout(3_000)).sendText(SHOP, conversation, fallback);
    }

    @Test
    void sensitiveContentRejectedByPlatformFallsBackToChannelMessage() throws Exception {
        String conversation = id();
        when(shopClient.sendText(SHOP, conversation, REPLY)).thenThrow(new TiktokApiException(
                TiktokErrorCode.CS_SENSITIVE_CONTENT.code(), "sensitive", "REQ", 200));
        postSigned(newMessage(id(), conversation, "BUYER", "hi")).andExpect(status().isOk());
        String fallback = policyEngine.policyOf(ChannelType.TIKTOK).fallbackMessage();
        verify(shopClient, timeout(3_000)).sendText(SHOP, conversation, fallback);
    }

    @Test
    void storedReplyShowsFallbackWhenPlatformRejectsSensitiveContent() throws Exception {
        String conversation = id();
        String messageId = id();
        when(shopClient.sendText(SHOP, conversation, REPLY)).thenThrow(new TiktokApiException(
                TiktokErrorCode.CS_SENSITIVE_CONTENT.code(), "sensitive", "REQ", 200));
        postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());

        ConversationMessage row = awaitDelivery(messageId, DeliveryStatus.SENT);
        // DB ghi đúng câu khách nhận (fallback), câu gốc AI vẫn ở rawText.
        assertThat(row.getText()).isEqualTo(policyEngine.policyOf(ChannelType.TIKTOK).fallbackMessage());
        assertThat(row.getRawText()).isEqualTo(REPLY);
        assertThat(row.isHandoff()).isFalse();
    }

    // ---------- gửi lỗi sau khi đã trả 200 (T-25.4) ----------

    @Test
    void transientSendFailureIsRetriedUntilDelivered() throws Exception {
        String conversation = id();
        String messageId = id();
        when(shopClient.sendText(SHOP, conversation, REPLY))
                .thenThrow(new TiktokApiException(TiktokErrorCode.INTERNAL_ERROR.code(), "internal", "REQ", 500))
                .thenThrow(new ResourceAccessException("connection reset"))
                .thenReturn("tts-msg-1");
        postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());

        verify(shopClient, timeout(5_000).times(3)).sendText(SHOP, conversation, REPLY);
        ConversationMessage row = awaitDelivery(messageId, DeliveryStatus.SENT);
        assertThat(row.getDeliveryAttempts()).isEqualTo(3);
        assertThat(row.getDeliveryError()).isNull();
        assertThat(row.isHandoff()).isFalse();
        verify(shopClient, after(300).times(3)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void sendFailingEveryAttemptIsHandedOffAfterLastRetry() throws Exception {
        String conversation = id();
        String messageId = id();
        when(shopClient.sendText(SHOP, conversation, REPLY)).thenThrow(new ResourceAccessException("connection refused"));
        postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());

        ConversationMessage row = awaitDelivery(messageId, DeliveryStatus.FAILED);
        assertThat(row.isHandoff()).isTrue();
        assertThat(row.getDeliveryAttempts()).isEqualTo(4);
        assertThat(row.getDeliveryError()).contains("connection refused");
        // Lần đầu + 3 lần thử lại, rồi dừng.
        verify(shopClient, after(300).times(4)).sendText(SHOP, conversation, REPLY);
    }

    @Test
    void conversationRuleOrPermissionErrorsHandOffWithoutRetry() throws Exception {
        for (TiktokErrorCode code : List.of(TiktokErrorCode.CS_CONVERSATION_RULES, TiktokErrorCode.CS_NO_PERMISSION,
                TiktokErrorCode.SCOPE_NOT_AUTHORIZED)) {
            String conversation = id();
            String messageId = id();
            when(shopClient.sendText(SHOP, conversation, REPLY))
                    .thenThrow(new TiktokApiException(code.code(), code.name(), "REQ", 200));
            postSigned(newMessage(messageId, conversation, "BUYER", "hi")).andExpect(status().isOk());

            ConversationMessage row = awaitDelivery(messageId, DeliveryStatus.FAILED);
            assertThat(row.isHandoff()).as(code.name()).isTrue();
            assertThat(row.getDeliveryAttempts()).as(code.name()).isEqualTo(1);
            verify(shopClient, after(200).times(1)).sendText(SHOP, conversation, REPLY);
        }
    }

    @Test
    void fullQueueReturns503AndSameNotificationIsAcceptedLater() throws Exception {
        String conversation = id();
        String body = newMessage(id(), conversation, "BUYER", "hi");
        doReturn(false).when(queue).offer(any());

        postSigned(body).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("busy"));
        verify(brainClient, after(300).never()).ask(any(), any());

        // Sàn gửi lại cùng tts_notification_id khi hàng đợi đã rảnh: không bị coi là trùng.
        reset(queue);
        postSigned(body).andExpect(status().isOk());
        verify(shopClient, timeout(3_000)).sendText(SHOP, conversation, REPLY);
    }

    // ---------- ủy quyền ----------

    @Test
    void credentialIsPersistedAndRevokedBySellerDeauthorization() throws Exception {
        long now = Instant.now().getEpochSecond();
        tokenManager.saveAuthorization(
                new TokenData("A1", now + 3600, "R1", now + 86400, "open", "Shop", "VN", 0,
                        List.of("seller.authorization.info", "seller.customer_service")),
                new AuthorizedShop(SHOP, "OmniAI Beauty", "VN", "LOCAL", "CIPHER", "VN01"));
        ChannelCredential saved = credentials.findByChannelAndShopId(ChannelType.TIKTOK, SHOP).orElseThrow();
        assertThat(saved.getShopCipher()).isEqualTo("CIPHER");
        assertThat(saved.scopeList()).containsExactly("seller.authorization.info", "seller.customer_service");
        assertThat(saved.getStatus()).isEqualTo(ChannelCredential.Status.ACTIVE);

        postSigned(envelope(TiktokWebhookType.SELLER_DEAUTHORIZATION, id(), now, Map.of("message", "deauthorized")))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(3)).until(() -> credentials.findByChannelAndShopId(ChannelType.TIKTOK, SHOP)
                .orElseThrow().getStatus() == ChannelCredential.Status.REVOKED);

        mvc.perform(get("/api/admin/channels/tiktok/credentials").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist());
        credentials.deleteAll();
    }

    @Test
    void authorizeUrlNeedsAdminAndCallbackChecksState() throws Exception {
        mvc.perform(get("/api/admin/channels/tiktok/authorize")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/channels/tiktok/authorize").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "http://localhost:8090/oauth/authorize\\?app_key=test-app&state=[0-9a-f]{32}")));
        mvc.perform(get("/api/channels/tiktok/oauth/callback").param("code", "x").param("state", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_state"));
        verify(shopClient, never()).registerWebhooks(anyString(), anyString(), any());
    }
}

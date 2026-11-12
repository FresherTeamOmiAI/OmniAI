package com.omniai.channel.tiktok;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.credential.ChannelCredentialRepository;
import com.omniai.channel.tiktok.TiktokModels.AuthorizedShop;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.common.ChannelType;
import com.omniai.policy.PolicyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
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
 * Kiểm: chữ ký, chống replay, trả 200 trước khi gọi Brain, chống trùng, bỏ qua tin của shop, luật TIKTOK, OAuth.
 */
@SpringBootTest(properties = {"omniai.tiktok.app-key=test-app", "omniai.tiktok.app-secret=test-secret"})
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

    @MockitoBean
    BrainClient brainClient;
    @MockitoBean
    TiktokShopClient shopClient;

    @BeforeEach
    void setUp() {
        reset(brainClient, shopClient);
        when(brainClient.ask(any())).thenReturn(new BrainResponse(REPLY, "test", List.of(), false));
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
        verify(brainClient, after(300).never()).ask(any());
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
        when(brainClient.ask(any())).thenAnswer(inv -> {
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
        verify(brainClient, after(500).never()).ask(any());
        verify(shopClient, never()).sendText(anyString(), anyString(), anyString());
    }

    // ---------- luật kênh + lỗi sàn ----------

    @Test
    void replyGoesThroughTiktokPolicy() throws Exception {
        when(brainClient.ask(any())).thenReturn(new BrainResponse(
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

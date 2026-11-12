package com.omniai.channel.tiktok;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * DTO của TikTok Shop dùng ở hub (JSON snake_case, khác phần còn lại của backend nên có JsonMapper riêng, không phải bean).
 * Trường lấy theo docv2: docs/channel-api/tiktok-dto.md. Fixture: shared/channel-fixtures/tiktok/.
 */
public final class TiktokModels {

    /**
     * JsonMapper snake_case chỉ dùng cho hợp đồng TikTok. Trường lạ thì bỏ qua, trường số thiếu thì về 0:
     * sàn thêm hoặc bớt trường không được làm hub lỗi.
     */
    public static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private TiktokModels() {
    }

    // ---------- Authorization ----------

    /** Get/Refresh Access Token (chưa đối chiếu). *_expire_in là thời điểm hết hạn, Unix giây. */
    public record TokenData(String accessToken, long accessTokenExpireIn, String refreshToken, long refreshTokenExpireIn,
                            String openId, String sellerName, String sellerBaseRegion, int userType,
                            List<String> grantedScopes) {
    }

    public record AuthorizedShop(String id, String name, String region, String sellerType, String cipher, String code) {
    }

    public record AuthorizedShops(List<AuthorizedShop> shops) {
    }

    // ---------- Event ----------

    public record UpdateWebhookRequest(String address, String eventType) {
    }

    // ---------- Customer Service ----------

    public record Sender(String imUserId, String role, String nickname, String avatar) {
    }

    public record Message(String id, String type, String content, long createTime, boolean isVisible,
                          Sender sender, String index) {
    }

    public record MessagesPage(String nextPageToken, List<Message> messages) {
    }

    public record Participant(String imUserId, String userId, String role, String nickname, String avatar) {
    }

    public record Conversation(String id, int participantCount, boolean canSendMessage, int unreadCount,
                               long createTime, List<Participant> participants, Message latestMessage) {
    }

    public record ConversationsPage(String nextPageToken, List<Conversation> conversations) {
    }

    /** sender_role: CUSTOMER_SERVICE | SYSTEM | ROBOT. Bot của hub gửi ROBOT. */
    public record SendMessageRequest(String type, String content, String senderRole) {
    }

    public record SendMessageResponse(String messageId) {
    }

    // ---------- Webhook (chưa đối chiếu) ----------

    public record WebhookEnvelope(int type, String ttsNotificationId, String shopId, long timestamp, JsonNode data) {
    }

    /** data của NEW_MESSAGE. content là chuỗi JSON, với TEXT có dạng {"content": "..."}. */
    public record NewMessageEvent(String messageId, String conversationId, String type, String content,
                                  long createTime, boolean isVisible, String index, Sender sender) {
    }
}

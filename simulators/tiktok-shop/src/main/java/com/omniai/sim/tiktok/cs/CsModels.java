package com.omniai.sim.tiktok.cs;

import java.util.List;

/** DTO của Customer Service theo docv2 (JSON snake_case qua cấu hình Jackson). */
public final class CsModels {

    private CsModels() {
    }

    public static final List<String> SEND_TYPES = List.of("TEXT", "IMAGE", "VIDEO", "PRODUCT_CARD", "ORDER_CARD",
            "RETURN_REFUND_CARD", "COUPON_CARD", "LOGISTICS_CARD");
    public static final List<String> SENDER_ROLES = List.of("CUSTOMER_SERVICE", "SYSTEM", "ROBOT");
    public static final int TEXT_MAX_LENGTH = 2000;

    /** sender của message. Với SYSTEM/ROBOT, im_user_id/nickname/avatar giống SHOP (docv2). */
    public record Sender(String imUserId, String role, String nickname, String avatar) {
    }

    /** create_time tính bằng giây; index tăng theo thời gian, dùng để sắp xếp. */
    public record Message(String id, String type, String content, long createTime, boolean isVisible,
                          Sender sender, String index) {
    }

    public record Participant(String imUserId, String avatar, String userId, String role, String nickname) {
    }

    public record ConversationView(String id, int participantCount, boolean canSendMessage, int unreadCount,
                                   long createTime, List<Participant> participants, Message latestMessage) {
    }

    public record ConversationsPage(String nextPageToken, List<ConversationView> conversations) {
    }

    public record MessagesPage(String nextPageToken, String unsupportedMsgTips, List<Message> messages) {
    }

    public record CreateConversationRequest(String buyerUserId) {
    }

    public record CreateConversationResponse(String conversationId) {
    }

    public record SendMessageRequest(String type, String content, String senderRole) {
    }

    public record SendMessageResponse(String messageId) {
    }

    /** data của webhook NEW_MESSAGE (chưa đối chiếu, xem shared/channel-fixtures/tiktok/webhook-new-message.json). */
    public record NewMessageData(String messageId, String conversationId, String type, String content,
                                 long createTime, boolean isVisible, String index, SenderRef sender) {
    }

    public record SenderRef(String imUserId, String role) {
    }

    /** data của webhook NEW_CONVERSATION (chưa đối chiếu). */
    public record NewConversationData(String conversationId, long createTime) {
    }
}

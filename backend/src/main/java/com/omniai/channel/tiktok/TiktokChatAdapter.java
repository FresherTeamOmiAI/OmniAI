package com.omniai.channel.tiktok;

import com.omniai.channel.ChannelAdapter;
import com.omniai.channel.tiktok.TiktokModels.NewMessageEvent;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook NEW_MESSAGE của TikTok Shop → CanonicalMessage. Owner: TuTM.
 * Chỉ tin TEXT, hiện cho CS, do BUYER gửi mới là câu hỏi cần bot trả lời ({@link #isBuyerText}).
 */
@Component
public class TiktokChatAdapter implements ChannelAdapter<TiktokChatAdapter.Inbound> {

    /** Payload kèm thông tin của envelope. */
    public record Inbound(String shopId, String notificationId, NewMessageEvent event) {
    }

    @Override
    public ChannelType channel() {
        return ChannelType.TIKTOK;
    }

    /**
     * Webhook NEW_MESSAGE bắn cả cho tin shop/bot vừa gửi; trả lời những tin đó sẽ thành vòng lặp.
     * Tin hệ thống (is_visible=false) và tin không phải TEXT cũng bỏ qua.
     */
    public static boolean isBuyerText(NewMessageEvent event) {
        return event != null && event.sender() != null && "BUYER".equals(event.sender().role())
                && "TEXT".equals(event.type()) && event.isVisible();
    }

    @Override
    public CanonicalMessage toCanonical(Inbound payload) {
        NewMessageEvent e = payload.event();
        return new CanonicalMessage(
                e.messageId(),
                ChannelType.TIKTOK,
                e.sender().imUserId(),
                null,
                "tiktok:" + e.conversationId(),
                textOf(e.content()),
                e.createTime() > 0 ? Instant.ofEpochSecond(e.createTime()) : Instant.now(),
                Map.of("source", "tiktok-webhook",
                        "shopId", payload.shopId(),
                        "tiktokConversationId", e.conversationId(),
                        "notificationId", payload.notificationId()));
    }

    /** content của TEXT là chuỗi JSON {"content": "..."} (docv2). Hỏng thì trả chuỗi rỗng. */
    static String textOf(String content) {
        if (content == null) {
            return "";
        }
        try {
            JsonNode node = TiktokModels.JSON.readTree(content);
            return node.path("content").asString("");
        } catch (RuntimeException ex) {
            return "";
        }
    }
}

package com.omniai.channel.web;

import com.omniai.channel.ChannelAdapter;
import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kênh chat trên website demo qua WebSocket /ws/chat. Owner: TuTM.
 * channelUserId tạm = WebSocket session id; sprint 3 thay bằng tài khoản web đã đăng nhập.
 */
@Component
public class WebChatAdapter extends TextWebSocketHandler implements ChannelAdapter<WebChatAdapter.Inbound> {

    /** Payload đã kèm session để chuyển sang CanonicalMessage. */
    public record Inbound(String sessionId, WebChatInbound body) {
    }

    private final MessageRouter router;
    private final JsonMapper jsonMapper;

    public WebChatAdapter(MessageRouter router, JsonMapper jsonMapper) {
        this.router = router;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ChannelType channel() {
        return ChannelType.WEB;
    }

    @Override
    public CanonicalMessage toCanonical(Inbound payload) {
        String messageId = payload.body().messageId() != null ? payload.body().messageId() : UUID.randomUUID().toString();
        return new CanonicalMessage(
                messageId,
                ChannelType.WEB,
                payload.sessionId(),
                null,
                "web:" + payload.sessionId(),
                payload.body().text(),
                Instant.now(),
                Map.of());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebChatInbound body = jsonMapper.readValue(message.getPayload(), WebChatInbound.class);
        if (body.text() == null || body.text().isBlank()) {
            return;
        }
        BotReply reply = router.handle(toCanonical(new Inbound(session.getId(), body)));
        session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(reply)));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }
}

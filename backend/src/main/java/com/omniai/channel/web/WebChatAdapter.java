package com.omniai.channel.web;

import com.omniai.channel.ChannelAdapter;
import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WebChatAdapter.class);

    /** Payload đã kèm session để chuyển sang CanonicalMessage. */
    public record Inbound(String sessionId, WebChatInbound body) {
    }

    /** Frame lỗi gửi về widget: {"error":"invalid_message","messageId":...}. messageId null nếu JSON hỏng. */
    public record ErrorReply(String error, String messageId) {
        static final String INVALID_MESSAGE = "invalid_message";

        static ErrorReply invalidMessage(String messageId) {
            return new ErrorReply(INVALID_MESSAGE, messageId);
        }
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
        String rawMessageId = payload.body() != null ? payload.body().messageId() : null;
        String messageId = (rawMessageId != null && !rawMessageId.isBlank())
                ? rawMessageId
                : UUID.randomUUID().toString();
        String text = payload.body() != null ? payload.body().text() : "";
        return new CanonicalMessage(
                messageId,
                ChannelType.WEB,
                payload.sessionId(),
                null,
                "web:" + payload.sessionId(),
                text,
                Instant.now(),
                Map.of());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket chat mở kết nối: session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebChatInbound body;
        try {
            body = jsonMapper.readValue(message.getPayload(), WebChatInbound.class);
        } catch (Exception e) {
            log.warn("Tin nhắn không đúng định dạng JSON từ session {}: {}", session.getId(), e.getMessage());
            send(session, ErrorReply.invalidMessage(null));
            return;
        }

        if (body == null || body.text() == null || body.text().isBlank()) {
            send(session, ErrorReply.invalidMessage(body != null ? body.messageId() : null));
            return;
        }

        BotReply reply = router.handle(toCanonical(new Inbound(session.getId(), body)));
        send(session, reply);
    }

    private void send(WebSocketSession session, Object payload) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(payload)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket chat đóng kết nối: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Lỗi transport WebSocket session {}: {}", session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}

package com.omniai.channel.web;

import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebChatAdapterTest {

    private MessageRouter router;
    private JsonMapper jsonMapper;
    private WebChatAdapter adapter;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        router = mock(MessageRouter.class);
        jsonMapper = JsonMapper.builder().build();
        adapter = new WebChatAdapter(router, jsonMapper);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-123");
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void channelIsWeb() {
        assertThat(adapter.channel()).isEqualTo(ChannelType.WEB);
    }

    @Test
    void toCanonicalPreservesProvidedMessageId() {
        WebChatInbound body = new WebChatInbound("custom-msg-id", "Xin chào shop");
        CanonicalMessage msg = adapter.toCanonical(new WebChatAdapter.Inbound("sess-1", body));

        assertThat(msg.messageId()).isEqualTo("custom-msg-id");
        assertThat(msg.channel()).isEqualTo(ChannelType.WEB);
        assertThat(msg.channelUserId()).isEqualTo("sess-1");
        assertThat(msg.conversationId()).isEqualTo("web:sess-1");
        assertThat(msg.customerId()).isNull();
        assertThat(msg.text()).isEqualTo("Xin chào shop");
        assertThat(msg.receivedAt()).isNotNull();
    }

    @Test
    void toCanonicalGeneratesUuidWhenMessageIdNullOrBlank() {
        WebChatInbound nullId = new WebChatInbound(null, "Hỏi giá kem");
        CanonicalMessage msg1 = adapter.toCanonical(new WebChatAdapter.Inbound("sess-2", nullId));
        assertThat(msg1.messageId()).isNotBlank();

        WebChatInbound blankId = new WebChatInbound("   ", "Hỏi giá kem");
        CanonicalMessage msg2 = adapter.toCanonical(new WebChatAdapter.Inbound("sess-2", blankId));
        assertThat(msg2.messageId()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void handleTextMessageRoutesAndSendsBotReply() throws Exception {
        BotReply mockReply = new BotReply("msg-1", ChannelType.WEB, "Raw", "Dạ chào bạn", List.of(), false, false);
        when(router.handle(any(CanonicalMessage.class))).thenReturn(mockReply);

        String jsonPayload = """
                {"messageId": "msg-1", "text": "Chào shop mỹ phẩm"}
                """;
        adapter.handleTextMessage(session, new TextMessage(jsonPayload));

        ArgumentCaptor<CanonicalMessage> captor = ArgumentCaptor.forClass(CanonicalMessage.class);
        verify(router).handle(captor.capture());
        CanonicalMessage captured = captor.getValue();
        assertThat(captured.messageId()).isEqualTo("msg-1");
        assertThat(captured.channel()).isEqualTo(ChannelType.WEB);
        assertThat(captured.channelUserId()).isEqualTo("sess-123");
        assertThat(captured.text()).isEqualTo("Chào shop mỹ phẩm");

        ArgumentCaptor<TextMessage> textCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(textCaptor.capture());
        assertThat(textCaptor.getValue().getPayload()).contains("Dạ chào bạn");
    }

    @Test
    void handleTextMessageIgnoresBlankOrNullText() throws Exception {
        adapter.handleTextMessage(session, new TextMessage("""
                {"messageId": "m1", "text": "   "}
                """));
        adapter.handleTextMessage(session, new TextMessage("""
                {"messageId": "m2", "text": null}
                """));

        verify(router, never()).handle(any());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleTextMessageHandlesMalformedJsonGracefullyWithoutCrashing() throws Exception {
        adapter.handleTextMessage(session, new TextMessage("not-a-valid-json"));

        verify(router, never()).handle(any());
        verify(session, never()).sendMessage(any());
        verify(session, never()).close(any());
    }

    @Test
    void handleTransportErrorClosesOpenSession() throws Exception {
        adapter.handleTransportError(session, new RuntimeException("connection reset"));

        verify(session).close(CloseStatus.SERVER_ERROR);
    }
}

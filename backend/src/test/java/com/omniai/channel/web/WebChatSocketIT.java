package com.omniai.channel.web;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** WebSocket /ws/chat thật: server Tomcat cổng ngẫu nhiên + Postgres Testcontainers, chỉ giả AI Brain. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class WebChatSocketIT {

    private static final String FIXED_REPLY = "Dạ shop chào bạn, bạn cần tư vấn sản phẩm nào ạ?";

    @LocalServerPort
    int port;

    @MockitoBean
    BrainClient brainClient;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private WebSocketSession session;

    @BeforeEach
    void connect() throws Exception {
        when(brainClient.ask(any(), any())).thenReturn(new BrainResponse(FIXED_REPLY, "test", List.of(), false));
        session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        received.offer(message.getPayload());
                    }
                }, "ws://localhost:" + port + "/ws/chat")
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    private JsonNode sendAndReceive(String payload) throws Exception {
        session.sendMessage(new TextMessage(payload));
        String frame = received.poll(5, TimeUnit.SECONDS);
        assertThat(frame).as("server phải trả lời trong 5 s").isNotNull();
        return jsonMapper.readTree(frame);
    }

    @Test
    void validMessageGetsBotReplyWithSameMessageId() throws Exception {
        JsonNode reply = sendAndReceive("""
                {"messageId": "it-valid-1", "text": "Chào shop"}
                """);

        assertThat(reply.get("messageId").asString()).isEqualTo("it-valid-1");
        assertThat(reply.get("channel").asString()).isEqualTo("WEB");
        assertThat(reply.get("finalText").asString()).isEqualTo(FIXED_REPLY);
        assertThat(reply.has("error")).isFalse();
    }

    @Test
    void malformedJsonGetsInvalidMessage() throws Exception {
        JsonNode reply = sendAndReceive("not-a-json");

        assertThat(reply.get("error").asString()).isEqualTo("invalid_message");
        assertThat(reply.get("messageId").isNull()).isTrue();
    }

    @Test
    void blankTextGetsInvalidMessageWithMessageId() throws Exception {
        JsonNode reply = sendAndReceive("""
                {"messageId": "it-blank-1", "text": "   "}
                """);

        assertThat(reply.get("error").asString()).isEqualTo("invalid_message");
        assertThat(reply.get("messageId").asString()).isEqualTo("it-blank-1");
    }
}

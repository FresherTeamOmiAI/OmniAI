package com.omniai.channel.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Gắn khách đã đăng nhập vào kết nối /ws/chat (US-13). Trình duyệt không gửi được header khi mở WebSocket nên
 * widget truyền JWT qua {@code ?token=}. Token hợp lệ thì lưu {@code customerId} vào attribute của session;
 * thiếu, sai hoặc hết hạn thì vẫn cho nối như khách vãng lai (không chặn chat). Không log token.
 */
@Component
public class WebChatAuthInterceptor implements HandshakeInterceptor {

    static final String CUSTOMER_ID = "omniai.customerId";

    private static final Logger log = LoggerFactory.getLogger(WebChatAuthInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public WebChatAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            return true;
        }
        try {
            attributes.put(CUSTOMER_ID, Long.valueOf(jwtDecoder.decode(token).getSubject()));
        } catch (JwtException | NumberFormatException e) {
            log.info("WebSocket chat: token không hợp lệ, nối như khách vãng lai");
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}

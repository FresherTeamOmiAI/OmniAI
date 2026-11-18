package com.omniai.channel.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.socket.WebSocketHandler;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Handshake /ws/chat: token hợp lệ thì gắn customerId; thiếu/sai/hết hạn thì vẫn cho nối như khách vãng lai. */
class WebChatAuthInterceptorTest {

    private final SecretKey key = new SecretKeySpec(
            "test-secret-test-secret-test-secret-123".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final WebChatAuthInterceptor interceptor = new WebChatAuthInterceptor(
            NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build());

    @Test
    void validTokenAddsCustomerId() {
        Map<String, Object> attributes = handshake("token=" + token("42", Instant.now().plus(Duration.ofHours(1))));
        assertThat(attributes).containsEntry(WebChatAuthInterceptor.CUSTOMER_ID, 42L);
    }

    @Test
    void missingInvalidOrExpiredTokenStaysAnonymous() {
        assertThat(handshake(null)).isEmpty();
        assertThat(handshake("token=")).isEmpty();
        assertThat(handshake("token=khong.phai.jwt")).isEmpty();
        assertThat(handshake("token=" + token("42", Instant.now().minus(Duration.ofHours(1))))).isEmpty();
        assertThat(handshake("token=" + token("abc", Instant.now().plus(Duration.ofHours(1))))).isEmpty();
    }

    private Map<String, Object> handshake(String query) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/ws/chat");
        servlet.setQueryString(query);
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(servlet),
                new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WebSocketHandler.class), attributes);
        assertThat(accepted).isTrue();
        return attributes;
    }

    private String token(String subject, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("omniai")
                .subject(subject)
                .issuedAt(expiresAt.minus(Duration.ofDays(1)))
                .expiresAt(expiresAt)
                .build();
        return NimbusJwtEncoder.withSecretKey(key).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

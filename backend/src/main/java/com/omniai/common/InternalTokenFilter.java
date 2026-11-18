package com.omniai.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Xác thực service-to-service cho /api/internal/** (ai-brain gọi backend, decisions #48): header
 * {@value #HEADER} phải bằng {@code omniai.internal.token} (env INTERNAL_API_TOKEN). Đúng thì request mang
 * ROLE_INTERNAL; sai, thiếu, hoặc server chưa đặt token thì không xác thực, chain trả 401 (fail-closed).
 * Không là @Component: Spring Boot sẽ tự gắn filter vào mọi request. SecurityConfig tạo và chỉ gắn vào chain nội bộ.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "INTERNAL";

    private final byte[] expected;

    public InternalTokenFilter(String token) {
        this.expected = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    boolean configured() {
        return expected != null;
    }

    /** So sánh thời gian không đổi, để không đoán dần token qua thời gian phản hồi. */
    boolean matches(String presented) {
        if (expected == null || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            var auth = new UsernamePasswordAuthenticationToken("ai-brain", null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}

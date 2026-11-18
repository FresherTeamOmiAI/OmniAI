package com.omniai.sim.shopee.ui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghi lại mọi request vào Open API {@code /api/v2/*} (những gì hub gọi). Không đụng {@code /api/simulator/*},
 * {@code /sim/*}, {@code /ui/*}. Body cắt ở {@link #MAX_BODY} ký tự.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiCallLogFilter extends OncePerRequestFilter {

    static final int MAX_BODY = 16 * 1024;

    private final ApiCallLog log;
    private final JsonMapper jsonMapper;

    public ApiCallLogFilter(ApiCallLog log, JsonMapper jsonMapper) {
        this.log = log;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        long time = System.currentTimeMillis();
        ContentCachingRequestWrapper cached = new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cached, res);
        } finally {
            long latency = (System.nanoTime() - started) / 1_000_000;
            String requestBody = text(cached.getContentAsByteArray());
            String responseBody = text(res.getContentAsByteArray());
            String error = null;
            String message = null;
            String requestId = null;
            try {
                JsonNode node = responseBody == null ? null : jsonMapper.readTree(responseBody);
                if (node != null && node.isObject() && node.has("error")) {
                    error = node.get("error").asString("");
                    message = node.path("message").asString(null);
                    requestId = node.path("request_id").asString(null);
                }
            } catch (RuntimeException ignored) {
                // body không phải JSON (hoặc đã bị cắt): chỉ hiện nguyên văn
            }
            log.add(new ApiCallLog.Call(log.nextId(), time, request.getMethod(), request.getRequestURI(),
                    maskToken(request.getQueryString()), request.getContentType(), res.getStatus(), error, message,
                    requestId, latency, requestBody, responseBody));
            res.copyBodyToResponse();
        }
    }

    /** access_token chỉ hiện 8 ký tự đầu trên /ui. */
    static String maskToken(String query) {
        if (query == null) {
            return null;
        }
        return query.replaceAll("(^|&)access_token=([^&]{0,8})[^&]*", "$1access_token=$2***");
    }

    private static String text(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "…[cắt]" : s;
    }
}

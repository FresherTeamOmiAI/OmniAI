package com.omniai.sim.tiktok.ui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghi lại mọi request vào API mở và API token (những gì hub gọi). Chạy TRƯỚC {@code GatewayFilter} nên request bị
 * từ chối (sai chữ ký, token hết hạn, rate limit) cũng hiện trên /ui. Không đụng /sim/*, /ui/* và trang OAuth.
 * Body cắt ở {@link #MAX_BODY} ký tự; multipart chỉ ghi kích thước.
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
        String path = request.getRequestURI();
        return !(path.startsWith("/authorization/") || path.startsWith("/event/")
                || path.startsWith("/customer_service/") || path.startsWith("/product/")
                || path.startsWith("/api/v2/token/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        long time = System.currentTimeMillis();
        boolean multipart = String.valueOf(request.getContentType()).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        // multipart để Spring tự đọc part, không bọc request
        ContentCachingRequestWrapper cached = multipart ? null : new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cached == null ? request : cached, res);
        } finally {
            long latency = (System.nanoTime() - started) / 1_000_000;
            String requestBody = cached == null
                    ? "[multipart/form-data, " + request.getContentLengthLong() + " byte]"
                    : text(cached.getContentAsByteArray());
            String responseBody = text(res.getContentAsByteArray());
            Integer code = null;
            String message = null;
            String requestId = null;
            try {
                JsonNode node = responseBody == null ? null : jsonMapper.readTree(responseBody);
                if (node != null && node.isObject() && node.has("code")) {
                    code = node.get("code").asInt();
                    message = node.path("message").asString(null);
                    requestId = node.path("request_id").asString(null);
                }
            } catch (RuntimeException ignored) {
                // body không phải JSON (hoặc đã bị cắt): chỉ hiện nguyên văn
            }
            log.add(new ApiCallLog.Call(log.nextId(), time, request.getMethod(), request.getRequestURI(),
                    maskSecret(request.getQueryString()), request.getContentType(), res.getStatus(), code, message, requestId,
                    latency, requestBody, responseBody));
            res.copyBodyToResponse();
        }
    }

    /** API token gửi app_secret trên URL: không hiện lên /ui. */
    static String maskSecret(String query) {
        return query == null ? null : query.replaceAll("(^|&)app_secret=[^&]*", "$1app_secret=***");
    }

    private static String text(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "…[cắt]" : s;
    }
}

package com.omniai.sim.tiktok.gateway;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.auth.TokenStore;
import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.ApiResponse;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.common.TiktokSigner;
import com.omniai.sim.tiktok.control.Faults;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Cổng chung cho các API mở (/authorization, /event, /customer_service, /product), làm đúng thứ tự sàn kiểm:
 * rate limit → app_key, timestamp, sign → access token → scope → shop_cipher.
 * Token, OAuth và /sim/* không đi qua cổng này.
 */
@Component
public class GatewayFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_HEADER = "x-tts-access-token";
    private static final String GRANT_ATTRIBUTE = GatewayFilter.class.getName() + ".grant";

    private final SimProperties props;
    private final TokenStore tokens;
    private final Faults faults;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final Semaphore concurrency;

    public GatewayFilter(SimProperties props, TokenStore tokens, Faults faults, Clock clock, JsonMapper jsonMapper) {
        this.props = props;
        this.tokens = tokens;
        this.faults = faults;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
        this.concurrency = new Semaphore(props.rateLimit().maxConcurrent());
    }

    /** Lấy grant mà cổng đã kiểm cho request này (controller dùng để biết shop). */
    public static TokenStore.Grant grant(HttpServletRequest request) {
        Object grant = request.getAttribute(GRANT_ATTRIBUTE);
        if (grant == null) {
            throw new IllegalStateException("Request chưa đi qua GatewayFilter");
        }
        return (TokenStore.Grant) grant;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return requiredScope(request.getMethod(), request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!concurrency.tryAcquire()) {
            writeError(response, TiktokErrorCode.TOO_MANY_REQUESTS,
                    "Max " + props.rateLimit().maxConcurrent() + " concurrent requests.");
            return;
        }
        try {
            // multipart/form-data (Upload Product Image) không ký body và phải để Spring tự đọc các part
            boolean multipart = String.valueOf(request.getContentType()).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
            HttpServletRequest wrapped = multipart ? request : new CachedBodyRequest(request);
            String body = multipart ? null : ((CachedBodyRequest) wrapped).bodyText();
            try {
                request.setAttribute(GRANT_ATTRIBUTE, check(wrapped, body));
            } catch (ApiException e) {
                writeError(response, e.error(), e.detail());
                return;
            }
            sleep(faults.apiLatencyMs());
            chain.doFilter(wrapped, response);
        } finally {
            concurrency.release();
        }
    }

    private TokenStore.Grant check(HttpServletRequest request, String body) {
        String path = request.getRequestURI();
        // Chỉ lấy tham số trên URL: với multipart, getParameterMap() gộp cả trường form vào (không được ký).
        Map<String, String> query = queryParams(request.getQueryString());

        if (!props.appKey().equals(query.get("app_key"))) {
            throw new ApiException(TiktokErrorCode.INVALID_SIGN, "Unknown app_key.");
        }
        checkTimestamp(query.get("timestamp"));
        String expected = TiktokSigner.signRequest(path, query, body, props.appSecret());
        if (!TiktokSigner.matches(expected, query.get("sign"))) {
            throw new ApiException(TiktokErrorCode.INVALID_SIGN);
        }

        TokenStore.Grant grant = tokens.authenticate(request.getHeader(ACCESS_TOKEN_HEADER));
        String scope = requiredScope(request.getMethod(), path);
        if (!grant.scopes().contains(scope) || faults.isRevoked(scope)) {
            throw new ApiException(TiktokErrorCode.SCOPE_NOT_AUTHORIZED, "Required scope: " + scope + ".");
        }
        if (!path.startsWith("/authorization/") && !path.equals(IMAGE_UPLOAD_PATH)
                && !props.shop().cipher().equals(query.get("shop_cipher"))) {
            throw new ApiException(TiktokErrorCode.INVALID_SHOP_CIPHER);
        }
        return grant;
    }

    static Map<String, String> queryParams(String queryString) {
        Map<String, String> query = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return query;
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            query.putIfAbsent(key, value);
        }
        return query;
    }

    private void checkTimestamp(String raw) {
        long timestamp;
        try {
            timestamp = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(TiktokErrorCode.INVALID_TIMESTAMP);
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (skew > props.timestampSkew().toSeconds()) {
            throw new ApiException(TiktokErrorCode.INVALID_TIMESTAMP, "Skew " + skew + "s.");
        }
    }

    /** Upload Product Image không theo shop nên không có shop_cipher (docv2). */
    static final String IMAGE_UPLOAD_PATH = "/product/202309/images/upload";

    /**
     * Scope theo trang docv2 của từng API. null = không đi qua cổng. Products: Create/Edit/Update Price/Inventory cần
     * seller.product.write; Activate/Deactivate, Get Product, Upload Image cần seller.product.basic.
     */
    static String requiredScope(String method, String path) {
        if (path.startsWith("/product/")) {
            boolean basic = "GET".equals(method) || path.endsWith("/activate") || path.endsWith("/deactivate")
                    || path.equals(IMAGE_UPLOAD_PATH);
            return basic ? "seller.product.basic" : "seller.product.write";
        }
        if (path.startsWith("/customer_service/")) return "seller.customer_service";
        if (path.startsWith("/event/") || path.startsWith("/authorization/")) return "seller.authorization.info";
        return null;
    }

    private void writeError(HttpServletResponse response, TiktokErrorCode error, String detail) throws IOException {
        response.setStatus(error.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(error, detail)));
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Đọc body 1 lần để ký, controller vẫn đọc lại được. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        String bodyText() {
            return body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}

package com.omniai.sim.tiktok;

import com.omniai.sim.tiktok.common.TiktokSigner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Simulator chạy thật ở cổng ngẫu nhiên + 1 HTTP server nhỏ đóng vai hub nhận webhook.
 * Timeout webhook rút còn 500 ms (thay cho 3 s) để test nhanh; logic như nhau.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sim.app-key=test-app",
        "sim.app-secret=test-secret",
        "sim.redirect-uri=http://localhost/callback",
        "sim.webhook.timeout=500ms",
        "sim.webhook.backoff=20ms",
})
public abstract class SimTestSupport {

    protected static final String APP_KEY = "test-app";
    protected static final String APP_SECRET = "test-secret";
    protected static final String CIPHER = "ROW_OmniAiSimShopCipher01";
    protected static final String SHOP_ID = "7494049642642441621";

    /** Webhook nhận được: header Authorization và body thô. */
    public record Received(String authorization, String body) {
    }

    /** Phản hồi kế tiếp của bên nhận webhook; hết hàng đợi thì trả 200 ngay. */
    public record Reply(int status, long delayMs) {
    }

    protected static final BlockingQueue<Received> RECEIVED = new LinkedBlockingQueue<>();
    protected static final Deque<Reply> REPLIES = new ConcurrentLinkedDeque<>();
    private static final HttpServer RECEIVER = startReceiver();

    protected final HttpClient http = HttpClient.newHttpClient();
    protected final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void webhookAddress(DynamicPropertyRegistry registry) {
        registry.add("sim.default-webhook-address", SimTestSupport::receiverUrl);
    }

    protected static String receiverUrl() {
        return "http://localhost:" + RECEIVER.getAddress().getPort() + "/webhook";
    }

    @BeforeEach
    void resetSimulator() throws Exception {
        post("/sim/reset", "{}");
        RECEIVED.clear();
        REPLIES.clear();
    }

    private static HttpServer startReceiver() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"), body));
                Reply reply = REPLIES.pollFirst();
                if (reply != null && reply.delayMs() > 0) {
                    try {
                        Thread.sleep(reply.delayMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.sendResponseHeaders(reply == null ? 200 : reply.status(), -1);
                exchange.close();
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- OAuth ----------

    /** Đi hết luồng ủy quyền: approve → lấy code từ redirect → đổi token. Trả data của token. */
    protected JsonNode authorize() throws Exception {
        HttpResponse<String> redirect = http.send(HttpRequest.newBuilder(
                uri("/oauth/authorize/approve?app_key=" + APP_KEY + "&state=s1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String location = redirect.headers().firstValue("Location").orElseThrow();
        String code = query(location).get("code");
        JsonNode token = getJson("/api/v2/token/get?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET
                + "&auth_code=" + code + "&grant_type=authorized_code");
        return token.get("data");
    }

    protected String accessToken() throws Exception {
        return authorize().get("access_token").asString();
    }

    // ---------- gọi API mở có ký ----------

    protected JsonNode signed(String method, String path, Map<String, String> params, String body, String token)
            throws Exception {
        return json.readTree(signedRaw(method, path, params, body, token, Instant.now().getEpochSecond()).body());
    }

    protected HttpResponse<String> signedRaw(String method, String path, Map<String, String> params, String body,
                                             String token, long timestamp) throws Exception {
        Map<String, String> query = new LinkedHashMap<>(params);
        query.put("app_key", APP_KEY);
        query.put("timestamp", Long.toString(timestamp));
        query.putIfAbsent("shop_cipher", CIPHER);
        query.put("sign", TiktokSigner.signRequest(path, query, body, APP_SECRET));
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path + "?" + encode(query)))
                .header("content-type", "application/json");
        if (token != null) {
            b.header("x-tts-access-token", token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        b.method(method, publisher);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    protected JsonNode get(String path, Map<String, String> params, String token) throws Exception {
        return signed("GET", path, params, null, token);
    }

    protected JsonNode sendText(String conversationId, String text, String token) throws Exception {
        String content = json.writeValueAsString(Map.of("content", text));
        String body = json.writeValueAsString(Map.of("type", "TEXT", "content", content, "sender_role", "ROBOT"));
        return signed("POST", "/customer_service/202606/conversations/" + conversationId + "/messages",
                Map.of(), body, token);
    }

    // ---------- /sim ----------

    protected JsonNode buyer(String buyerUserId, String text) throws Exception {
        return post("/sim/buyer/messages", json.writeValueAsString(Map.of("buyer_user_id", buyerUserId, "text", text)));
    }

    protected JsonNode post(String path, String body) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri(path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return json.readTree(r.body());
    }

    protected JsonNode getJson(String pathAndQuery) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri(pathAndQuery)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return json.readTree(r.body());
    }

    protected URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }

    protected static String encode(Map<String, String> query) {
        return query.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    protected static Map<String, String> query(String url) {
        String q = URI.create(url).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(kv[0], java.net.URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return map;
    }
}

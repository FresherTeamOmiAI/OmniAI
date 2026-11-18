package com.omniai.sim.shopee.push;

import com.omniai.sim.shopee.SimProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gửi push như Shopee: POST raw body tới {@code sim.push-callback-url}, chữ ký ở header {@code Authorization}
 * (HMAC của {@code url + "|" + body}). Hub không trả 2xx trong {@code sim.push.timeout} thì gửi lại, tối đa
 * {@code max-attempts} lần, backoff tăng dần. Mỗi lần giao ghi vào {@link #deliveries()} cho trang /ui (chỉ trong bộ nhớ).
 * Hub xử lý push đồng bộ: body trả về có {@code final_text} là câu shop đã trả lời.
 */
@Component
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    static final int MAX_DELIVERY_LOG = 500;
    static final int MAX_BODY = 4096;

    public record Attempt(int attempt, Integer httpStatus, long latencyMs, String error, String responseBody) {
    }

    /**
     * Kết quả giao 1 push. {@code ref} là order_sn (code 3) hoặc message_id (code 10). {@code time} là mili giây epoch.
     */
    public record Delivery(long id, long time, int code, String ref, String address, boolean delivered,
                           String finalText, List<Attempt> attempts) {
    }

    private final SimProperties props;
    private final JsonMapper json;
    private final ExecutorService executor;
    private final Clock clock;
    private final HttpClient http;
    private final AtomicLong nextId = new AtomicLong();
    private final Deque<Delivery> deliveries = new ConcurrentLinkedDeque<>();

    public PushDispatcher(SimProperties props, JsonMapper json, ExecutorService pushExecutor, Clock clock) {
        this.props = props;
        this.json = json;
        this.executor = pushExecutor;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(props.push().timeout()).build();
    }

    /** Gửi bất đồng bộ, trả ngay. */
    public void dispatch(int code, String ref, String rawBody, String authorization) {
        long time = clock.millis();
        executor.submit(() -> deliver(time, code, ref, props.pushCallbackUrl(), rawBody, authorization));
    }

    /** Mới nhất trước. */
    public List<Delivery> deliveries() {
        List<Delivery> list = new ArrayList<>(deliveries);
        Collections.reverse(list);
        return list;
    }

    public void clear() {
        deliveries.clear();
    }

    private void deliver(long time, int code, String ref, String address, String body, String authorization) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(address))
                    .timeout(props.push().timeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            record(time, code, ref, address, false, null,
                    List.of(new Attempt(1, null, 0, "URL không hợp lệ: " + e.getMessage(), null)));
            return;
        }
        List<Attempt> attempts = new ArrayList<>();
        boolean delivered = false;
        String finalText = null;
        int max = Math.max(1, props.push().maxAttempts());
        for (int i = 1; i <= max && !delivered; i++) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long latency = (System.nanoTime() - started) / 1_000_000;
                delivered = response.statusCode() / 100 == 2;
                attempts.add(new Attempt(i, response.statusCode(), latency, null, cut(response.body())));
                if (delivered) {
                    finalText = finalText(response.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                attempts.add(new Attempt(i, null, (System.nanoTime() - started) / 1_000_000, "interrupted", null));
                break;
            } catch (Exception e) {
                attempts.add(new Attempt(i, null, (System.nanoTime() - started) / 1_000_000,
                        e.getClass().getSimpleName(), null));
            }
            if (!delivered && i < max) {
                sleep(props.push().backoff().toMillis() * i);
            }
        }
        if (!delivered) {
            log.warn("Push code {} ({}) tới {} thất bại sau {} lần", code, ref, address, attempts.size());
        }
        record(time, code, ref, address, delivered, finalText, attempts);
    }

    private void record(long time, int code, String ref, String address, boolean delivered, String finalText,
                        List<Attempt> attempts) {
        deliveries.addLast(new Delivery(nextId.incrementAndGet(), time, code, ref, address, delivered, finalText,
                List.copyOf(attempts)));
        while (deliveries.size() > MAX_DELIVERY_LOG) {
            deliveries.pollFirst();
        }
    }

    private String finalText(String body) {
        try {
            JsonNode node = json.readTree(body);
            return node.hasNonNull("final_text") ? node.get("final_text").asString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cut(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "…[cắt]" : s;
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

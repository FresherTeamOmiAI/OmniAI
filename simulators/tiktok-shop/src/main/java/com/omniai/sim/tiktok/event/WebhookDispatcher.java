package com.omniai.sim.tiktok.event;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.common.Ids;
import com.omniai.sim.tiktok.common.TiktokSigner;
import com.omniai.sim.tiktok.control.Faults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

/**
 * Gửi webhook như sàn: envelope {type, tts_notification_id, shop_id, timestamp, data}, ký ở header Authorization.
 * Bên nhận không trả 2xx trong {@code sim.webhook.timeout} (3 s) thì gửi lại, tối đa {@code max-attempts} lần,
 * backoff tăng dần. Mỗi lần giao ghi vào {@link #deliveries()} (GET /sim/deliveries).
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int MAX_DELIVERY_LOG = 500;

    public record Attempt(int attempt, Integer httpStatus, long latencyMs, String error) {
    }

    /** Kết quả giao 1 notification. duplicate = bản gửi lần 2 do fault webhook_duplicate. */
    public record Delivery(String ttsNotificationId, String eventType, String shopId, String address,
                           boolean duplicate, boolean delivered, List<Attempt> attempts) {
    }

    private final SimProperties props;
    private final WebhookRegistry registry;
    private final Faults faults;
    private final JsonMapper jsonMapper;
    private final ExecutorService executor;
    private final Clock clock;
    private final HttpClient http;
    private final Deque<Delivery> deliveries = new ConcurrentLinkedDeque<>();

    public WebhookDispatcher(SimProperties props, WebhookRegistry registry, Faults faults, JsonMapper jsonMapper,
                             ExecutorService webhookExecutor, Clock clock) {
        this.props = props;
        this.registry = registry;
        this.faults = faults;
        this.jsonMapper = jsonMapper;
        this.executor = webhookExecutor;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(props.webhook().timeout()).build();
    }

    /** Gửi bất đồng bộ, trả về tts_notification_id ngay. */
    public String dispatch(String shopId, WebhookType type, Object data) {
        String notificationId = Ids.next();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type.type());
        envelope.put("tts_notification_id", notificationId);
        envelope.put("shop_id", shopId);
        envelope.put("timestamp", clock.instant().getEpochSecond());
        envelope.put("data", data);
        String body = jsonMapper.writeValueAsString(envelope);

        registry.addressFor(shopId, type).ifPresentOrElse(
                address -> executor.submit(() -> {
                    sleep(faults.webhookDelayMs());
                    deliver(notificationId, type, shopId, address, body, false);
                    if (faults.webhookDuplicate()) {
                        deliver(notificationId, type, shopId, address, body, true);
                    }
                }),
                () -> log.info("Không có địa chỉ webhook cho {} của shop {}, bỏ qua", type, shopId));
        return notificationId;
    }

    public List<Delivery> deliveries() {
        List<Delivery> list = new ArrayList<>(deliveries);
        Collections.reverse(list);
        return list;
    }

    public void clear() {
        deliveries.clear();
    }

    private void deliver(String notificationId, WebhookType type, String shopId, String address, String body,
                         boolean duplicate) {
        String signature = TiktokSigner.signWebhook(props.appKey(), body, props.appSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(address))
                .timeout(props.webhook().timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        List<Attempt> attempts = new ArrayList<>();
        boolean delivered = false;
        for (int i = 1; i <= props.webhook().maxAttempts() && !delivered; i++) {
            long started = System.nanoTime();
            try {
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                long latency = (System.nanoTime() - started) / 1_000_000;
                delivered = response.statusCode() / 100 == 2;
                attempts.add(new Attempt(i, response.statusCode(), latency, null));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                attempts.add(new Attempt(i, null, (System.nanoTime() - started) / 1_000_000, "interrupted"));
                break;
            } catch (Exception e) {
                attempts.add(new Attempt(i, null, (System.nanoTime() - started) / 1_000_000,
                        e.getClass().getSimpleName()));
            }
            if (!delivered && i < props.webhook().maxAttempts()) {
                sleep(props.webhook().backoff().toMillis() * i);
            }
        }
        if (!delivered) {
            log.warn("Webhook {} {} tới {} thất bại sau {} lần", type, notificationId, address, attempts.size());
        }
        deliveries.addLast(new Delivery(notificationId, type.name(), shopId, address, duplicate, delivered,
                List.copyOf(attempts)));
        while (deliveries.size() > MAX_DELIVERY_LOG) {
            deliveries.pollFirst();
        }
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

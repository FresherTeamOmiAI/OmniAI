package com.omniai.channel.tiktok;

import com.omniai.channel.RecentIds;
import com.omniai.channel.tiktok.TiktokModels.WebhookEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * POST /api/channels/tiktok/webhook: nhận webhook TikTok Shop (S2: từ simulator). Owner: TuTM.
 * <p>
 * Sàn chỉ chờ khoảng 3 s, nên ở đây chỉ làm việc nhanh: kiểm chữ ký trên body thô → kiểm timestamp → chống trùng
 * theo tts_notification_id → đưa vào hàng đợi → trả 200. Gọi AI Brain và gửi tin chạy ở {@link TiktokWebhookProcessor}.
 */
@RestController
@RequestMapping("/api/channels/tiktok")
public class TiktokWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TiktokWebhookController.class);
    private static final Map<String, Object> ACK = Map.of("code", 0, "message", "success");

    private final TiktokProperties props;
    private final TiktokWebhookProcessor processor;
    private final TiktokWebhookQueue queue;
    private final Clock clock;
    private final RecentIds seen = new RecentIds(10_000);

    public TiktokWebhookController(TiktokProperties props, TiktokWebhookProcessor processor, TiktokWebhookQueue queue) {
        this.props = props;
        this.processor = processor;
        this.queue = queue;
        this.clock = Clock.systemUTC();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "empty_body");
        }
        String expected = TiktokSigner.signWebhook(props.appKey(), rawBody, props.appSecret());
        if (!TiktokSigner.matches(expected, signature)) {
            log.warn("TikTok webhook sai chữ ký, từ chối");
            return error(HttpStatus.UNAUTHORIZED, "invalid_signature");
        }

        WebhookEnvelope envelope;
        try {
            envelope = TiktokModels.JSON.readValue(rawBody, WebhookEnvelope.class);
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_REQUEST, "invalid_payload");
        }
        if (envelope.ttsNotificationId() == null || envelope.ttsNotificationId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_payload");
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - envelope.timestamp());
        if (skew > props.webhookMaxSkew().toSeconds()) {
            log.warn("TikTok webhook {} có timestamp lệch {} s, từ chối (chống replay)", envelope.ttsNotificationId(), skew);
            return error(HttpStatus.UNAUTHORIZED, "stale_timestamp");
        }

        if (!seen.markIfNew(envelope.ttsNotificationId())) {
            log.info("TikTok webhook {} trùng, bỏ qua", envelope.ttsNotificationId());
            return ResponseEntity.ok(ACK);
        }
        if (!queue.offer(() -> processor.process(envelope))) {
            seen.forget(envelope.ttsNotificationId());
            log.warn("Hàng đợi webhook TikTok đầy, trả 503 để sàn gửi lại {}", envelope.ttsNotificationId());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "busy");
        }
        return ResponseEntity.ok(ACK);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}

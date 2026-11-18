package com.omniai.channel.shopee;

import com.omniai.channel.RecentIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/channels/shopee/push: hub nhận push Shopee (S2: từ simulator). Owner: ChienPX.
 * Chữ ký là HMAC của {@code callbackUrl + "|" + body}. Sai chữ ký thì từ chối, không trả lời khách.
 */
@RestController
@RequestMapping("/api/channels/shopee")
public class ShopeePushController {

    private static final Logger log = LoggerFactory.getLogger(ShopeePushController.class);
    private static final long MAX_SKEW_SECONDS = 300;

    private final ShopeeChannelProperties properties;
    private final ShopeePushProcessor processor;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RecentIds seen = new RecentIds(10_000);

    @Autowired
    public ShopeePushController(ShopeeChannelProperties properties, ShopeePushProcessor processor) {
        this(properties, processor, Clock.systemUTC());
    }

    ShopeePushController(ShopeeChannelProperties properties, ShopeePushProcessor processor, Clock clock) {
        this.properties = properties;
        this.processor = processor;
        this.clock = clock;
    }

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "empty_body");
        }
        String expected = ShopeeSign.push(properties.getPushCallbackUrl(), rawBody, properties.getPartnerKey());
        if (!ShopeeSign.equals(expected, signature)) {
            log.warn("Shopee push sai chữ ký, từ chối");
            return error(HttpStatus.UNAUTHORIZED, "error_sign");
        }
        JsonNode push;
        try {
            push = json.readTree(rawBody);
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_REQUEST, "invalid_payload");
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - push.path("timestamp").asLong(0));
        if (skew > MAX_SKEW_SECONDS) {
            return error(HttpStatus.UNAUTHORIZED, "stale_timestamp");
        }
        String messageId = push.path("data").path("content").path("message_id").asString("");
        if (!messageId.isBlank() && !seen.markIfNew(messageId)) {
            log.info("Shopee push {} trùng, bỏ qua", messageId);
            return ResponseEntity.ok(ack(null));
        }
        try {
            return ResponseEntity.ok(ack(processor.onPush(push)));
        } catch (RuntimeException e) {
            if (!messageId.isBlank()) {
                seen.forget(messageId);
            }
            log.error("Shopee: xử lý push lỗi: {}", e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "process_failed");
        }
    }

    private static Map<String, Object> ack(String finalText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "");
        if (finalText != null) {
            body.put("final_text", finalText);
        }
        return body;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}

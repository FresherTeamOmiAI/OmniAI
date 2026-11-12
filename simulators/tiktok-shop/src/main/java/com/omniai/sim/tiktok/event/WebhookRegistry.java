package com.omniai.sim.tiktok.event;

import com.omniai.sim.tiktok.SimProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Địa chỉ webhook theo shop và event_type (Get/Update/Delete Shop Webhooks).
 * Chưa đăng ký thì dùng địa chỉ mặc định của app (giống URL webhook cấu hình trong Partner Center).
 */
@Component
public class WebhookRegistry {

    /** create_time/update_time tính bằng mili giây (docv2). */
    public record Registration(String eventType, String address, long createTime, long updateTime) {
    }

    private final SimProperties props;
    private final Clock clock;
    private final Map<String, Map<WebhookType, Registration>> byShop = new ConcurrentHashMap<>();

    public WebhookRegistry(SimProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public List<Registration> list(String shopId) {
        return byShop.getOrDefault(shopId, Map.of()).values().stream()
                .sorted(Comparator.comparing(Registration::eventType))
                .toList();
    }

    public void put(String shopId, WebhookType type, String address) {
        long now = clock.millis();
        byShop.computeIfAbsent(shopId, k -> new ConcurrentHashMap<>())
                .merge(type, new Registration(type.name(), address, now, now),
                        (old, neu) -> new Registration(old.eventType(), address, old.createTime(), now));
    }

    public boolean remove(String shopId, WebhookType type) {
        Map<WebhookType, Registration> shop = byShop.get(shopId);
        return shop != null && shop.remove(type) != null;
    }

    /** Địa chỉ nhận sự kiện: đã đăng ký cho shop, nếu không thì địa chỉ mặc định (có thể rỗng). */
    public Optional<String> addressFor(String shopId, WebhookType type) {
        Registration r = byShop.getOrDefault(shopId, Map.of()).get(type);
        if (r != null) {
            return Optional.of(r.address());
        }
        String fallback = props.defaultWebhookAddress();
        return fallback == null || fallback.isBlank() ? Optional.empty() : Optional.of(fallback);
    }

    public void clear() {
        byShop.clear();
    }
}

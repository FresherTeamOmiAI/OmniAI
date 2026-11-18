package com.omniai.sim.shopee;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Cấu hình `sim.*` trong application.yml. Khóa test, không phải khóa sàn thật. */
@ConfigurationProperties(prefix = "sim")
public record SimProperties(
        @DefaultValue("2000001") long partnerId,
        @DefaultValue("test-partner-key") String partnerKey,
        @DefaultValue("10001") long shopId,
        @DefaultValue("OmniAI Beauty (simulator)") String shopName,
        @DefaultValue("test-access-token") String accessToken,
        @DefaultValue("http://127.0.0.1:8080/api/channels/shopee/push") String pushCallbackUrl,
        @DefaultValue Push push,
        @DefaultValue Datasource datasource) {

    /** Gửi push tới hub: hết {@code timeout} hoặc không 2xx thì gửi lại, tối đa {@code maxAttempts} lần. */
    public record Push(@DefaultValue("30s") Duration timeout,
                       @DefaultValue("3") int maxAttempts,
                       @DefaultValue("1s") Duration backoff) {
    }

    /** Postgres của simulator, không dùng database của hub. */
    public record Datasource(@DefaultValue("jdbc:postgresql://localhost:5432/shopee") String url,
                             @DefaultValue("omniai") String username,
                             @DefaultValue("omniai") String password) {
    }
}

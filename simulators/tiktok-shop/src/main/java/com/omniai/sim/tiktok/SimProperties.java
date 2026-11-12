package com.omniai.sim.tiktok;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** Cấu hình `sim.*` trong application.yml. */
@ConfigurationProperties(prefix = "sim")
public record SimProperties(
        String appKey,
        String appSecret,
        String redirectUri,
        @DefaultValue("false") boolean autoApprove,
        Shop shop,
        List<String> grantedScopes,
        String defaultWebhookAddress,
        @DefaultValue("7d") Duration accessTokenTtl,
        @DefaultValue("365d") Duration refreshTokenTtl,
        @DefaultValue("5m") Duration timestampSkew,
        RateLimit rateLimit,
        Webhook webhook) {

    public record Shop(String id, String name, String region, String sellerType, String cipher, String code) {
    }

    public record RateLimit(@DefaultValue("50") int maxConcurrent) {
    }

    public record Webhook(@DefaultValue("3s") Duration timeout,
                          @DefaultValue("3") int maxAttempts,
                          @DefaultValue("1s") Duration backoff) {
    }
}

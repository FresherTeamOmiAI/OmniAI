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
        Webhook webhook,
        @DefaultValue Products products) {

    public record Shop(String id, String name, String region, String sellerType, String cipher, String code) {
    }

    /**
     * Nhóm Products (US-50). Kho và danh mục là của shop giả lập; danh mục thật lấy từ Get Categories (chưa giả lập).
     *
     * @param warehouseId kho duy nhất của shop (Get Warehouse List chưa giả lập)
     * @param categories  category_id lá được phép đăng
     */
    public record Products(@DefaultValue("7494049642642500001") String warehouseId,
                           @DefaultValue({"601450", "601451", "601452", "601453", "601454", "601455", "601456",
                                   "601457", "601458", "601459"}) List<String> categories,
                           @DefaultValue("99999") int maxQuantity) {
    }

    public record RateLimit(@DefaultValue("50") int maxConcurrent) {
    }

    public record Webhook(@DefaultValue("3s") Duration timeout,
                          @DefaultValue("3") int maxAttempts,
                          @DefaultValue("1s") Duration backoff) {
    }
}

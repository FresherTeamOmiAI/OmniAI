package com.omniai.channel.tiktok;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình `omniai.tiktok.*`. base-url trỏ tới simulator (decisions #22); trên sàn thật API ở open-api.tiktokglobalshop.com,
 * token ở auth.tiktok-shops.com, trang ủy quyền ở services.tiktokshop.com (docs/channel-api/tiktok.md mục 4).
 *
 * @param webhookUrl      địa chỉ public của hub để đăng ký với Update Shop Webhook
 * @param webhookMaxSkew  webhook có timestamp lệch quá mức này thì từ chối (chống replay)
 * @param maxConcurrent   số request đồng thời tối đa hub gửi ra sàn (sàn chặn ở 50)
 * @param sendAttempts    số lần gửi câu trả lời tối đa (lần đầu + thử lại) khi Send Message lỗi tạm thời (T-25.4)
 * @param sendRetryBackoff chờ trước lần thử lại đầu tiên, các lần sau gấp đôi
 */
@ConfigurationProperties(prefix = "omniai.tiktok")
public record TiktokProperties(
        String appKey,
        String appSecret,
        String baseUrl,
        String redirectUri,
        String webhookUrl,
        @DefaultValue("5m") Duration webhookMaxSkew,
        @DefaultValue("40") int maxConcurrent,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("3") int maxRetries,
        @DefaultValue("200ms") Duration retryBackoff,
        @DefaultValue("5m") Duration refreshBefore,
        @DefaultValue("200") int webhookQueueCapacity,
        @DefaultValue("4") int sendAttempts,
        @DefaultValue("2s") Duration sendRetryBackoff) {

    public String authorizeUrl() {
        return baseUrl + "/oauth/authorize";
    }
}

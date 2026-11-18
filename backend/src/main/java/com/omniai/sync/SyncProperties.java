package com.omniai.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình {@code omniai.sync.*} (T-49.1).
 *
 * @param enabled      tắt thì worker không tự chạy (test gọi {@link ProductSyncWorker#runOnce()} bằng tay)
 * @param pollIntervalMs khoảng nghỉ giữa 2 lượt quét hàng đợi; AC US-49 cần sàn nhận trong &lt;= 10 s
 * @param maxAttempts  số lần gửi tối đa (lần đầu + thử lại); quá thì FAILED
 * @param retryBackoff chờ trước lần thử lại đầu, các lần sau gấp đôi
 * @param lease        thời gian giữ dòng đang gửi để worker khác không nhận trùng
 * @param batchSize    số dòng nhận mỗi lượt quét
 */
@ConfigurationProperties(prefix = "omniai.sync")
public record SyncProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1000") long pollIntervalMs,
        @DefaultValue("4") int maxAttempts,
        @DefaultValue("2s") Duration retryBackoff,
        @DefaultValue("60s") Duration lease,
        @DefaultValue("20") int batchSize) {
}

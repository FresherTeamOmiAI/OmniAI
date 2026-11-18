package com.omniai.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** 1 dòng product_channel_sync (V18): hàng đợi + trạng thái đồng bộ của 1 sản phẩm trên 1 kênh. */
@Entity
@Table(name = "product_channel_sync")
@Getter
@Setter
@NoArgsConstructor
public class ProductSyncState {

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "product_id")
        private Long productId;

        @Column(name = "channel_code", length = 20)
        private String channel;

        public Key(Long productId, String channel) {
            this.productId = productId;
            this.channel = channel;
        }
    }

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SyncStatus status;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "synced_hash", length = 64)
    private String syncedHash;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    ProductSyncState(Long productId, String channel) {
        this.id = new Key(productId, channel);
    }
}

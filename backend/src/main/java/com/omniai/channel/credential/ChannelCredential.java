package com.omniai.channel.credential;

import com.omniai.common.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Token OAuth2 của 1 shop trên 1 kênh sàn (bảng channel_credential, V8). Owner: TuTM.
 * ACTIVE = gọi API được; NEEDS_REAUTH = refresh token hết hạn, seller phải ủy quyền lại; REVOKED = seller gỡ app.
 */
@Entity
@Table(name = "channel_credential",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "shop_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChannelCredential {

    public enum Status { ACTIVE, NEEDS_REAUTH, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_cipher")
    private String shopCipher;

    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name = "refresh_expires_at", nullable = false)
    private Instant refreshExpiresAt;

    /** Scope đã cấp, ngăn cách bằng dấu phẩy. */
    @Column(nullable = false, length = 2000)
    private String scopes = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public List<String> scopeList() {
        return scopes.isBlank() ? List.of() : Arrays.asList(scopes.split(","));
    }
}

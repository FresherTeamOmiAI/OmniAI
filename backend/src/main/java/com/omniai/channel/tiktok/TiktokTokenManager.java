package com.omniai.channel.tiktok;

import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.credential.ChannelCredentialRepository;
import com.omniai.channel.tiktok.TiktokModels.AuthorizedShop;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.common.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Vòng đời token OAuth2 của shop TikTok (docs/channel-api/tiktok.md mục 4). Owner: TuTM.
 * <ul>
 *   <li>Token còn dưới {@code refresh-before} (5 phút) thì refresh trước khi gọi.</li>
 *   <li>Sàn báo token sai/hết hạn thì refresh 1 lần rồi gọi lại.</li>
 *   <li>Mỗi shop chỉ 1 luồng refresh; luồng khác chờ rồi dùng token mới (refresh làm token cũ mất hiệu lực).</li>
 *   <li>Refresh token hết hạn → NEEDS_REAUTH; seller gỡ app → REVOKED. Hai trạng thái này không gọi API nữa.</li>
 * </ul>
 */
@Component
public class TiktokTokenManager {

    private static final Logger log = LoggerFactory.getLogger(TiktokTokenManager.class);

    /** Shop chưa ủy quyền, hoặc credential không còn ACTIVE. */
    public static class NotAuthorizedException extends RuntimeException {
        private final ChannelCredential.Status status;

        public NotAuthorizedException(String shopId, ChannelCredential.Status status) {
            super("Shop TikTok " + shopId + " chưa ủy quyền hoặc cần ủy quyền lại (" + status + ")");
            this.status = status;
        }

        public ChannelCredential.Status status() {
            return status;
        }
    }

    private final ChannelCredentialRepository repository;
    private final TiktokApi api;
    private final TiktokProperties props;
    private final Clock clock;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public TiktokTokenManager(ChannelCredentialRepository repository, TiktokApi api, TiktokProperties props) {
        this(repository, api, props, Clock.systemUTC());
    }

    TiktokTokenManager(ChannelCredentialRepository repository, TiktokApi api, TiktokProperties props, Clock clock) {
        this.repository = repository;
        this.api = api;
        this.props = props;
        this.clock = clock;
    }

    /** Lưu (hoặc ghi đè) credential sau khi seller ủy quyền. */
    public ChannelCredential saveAuthorization(TokenData token, AuthorizedShop shop) {
        ChannelCredential c = repository.findByChannelAndShopId(ChannelType.TIKTOK, shop.id())
                .orElseGet(ChannelCredential::new);
        c.setChannel(ChannelType.TIKTOK);
        c.setShopId(shop.id());
        c.setShopName(shop.name());
        c.setShopCipher(shop.cipher());
        apply(c, token);
        return repository.save(c);
    }

    /**
     * Gọi API bằng credential còn hạn. Sàn báo token sai/hết hạn thì refresh rồi gọi lại đúng 1 lần.
     * Lỗi khác (sai scope, invalid_params...) ném thẳng cho bên gọi, không retry.
     */
    public <T> T withCredential(String shopId, Function<ChannelCredential, T> call) {
        ChannelCredential credential = validCredential(shopId);
        // Chụp token trước khi gọi: luồng khác có thể đã refresh trong lúc call chạy.
        String usedToken = credential.getAccessToken();
        try {
            return call.apply(credential);
        } catch (TiktokApiException e) {
            if (!e.isTokenError()) {
                throw e;
            }
            log.info("Token TikTok của shop {} bị từ chối ({}), refresh rồi thử lại", shopId, e.code());
            return call.apply(refresh(shopId, usedToken));
        }
    }

    ChannelCredential validCredential(String shopId) {
        ChannelCredential c = load(shopId);
        if (c.getAccessExpiresAt().isBefore(clock.instant().plus(props.refreshBefore()))) {
            return refresh(shopId, c.getAccessToken());
        }
        return c;
    }

    /**
     * Refresh nếu token hiện tại vẫn là staleAccessToken. Luồng đến sau (token đã được luồng trước đổi) dùng luôn token mới.
     */
    ChannelCredential refresh(String shopId, String staleAccessToken) {
        ReentrantLock lock = locks.computeIfAbsent(shopId, k -> new ReentrantLock());
        lock.lock();
        try {
            ChannelCredential c = load(shopId);
            if (!c.getAccessToken().equals(staleAccessToken)
                    && c.getAccessExpiresAt().isAfter(clock.instant().plus(props.refreshBefore()))) {
                return c;
            }
            if (c.getRefreshExpiresAt().isBefore(clock.instant())) {
                return markNeedsReauth(c);
            }
            try {
                apply(c, api.refreshToken(c.getRefreshToken()));
            } catch (TiktokApiException e) {
                if (e.is(TiktokErrorCode.REFRESH_TOKEN_EXPIRED)) {
                    return markNeedsReauth(c);
                }
                throw e;
            }
            log.info("Đã refresh token TikTok cho shop {}", shopId);
            return repository.save(c);
        } finally {
            lock.unlock();
        }
    }

    public void markStatus(String shopId, ChannelCredential.Status status) {
        repository.findByChannelAndShopId(ChannelType.TIKTOK, shopId).ifPresent(c -> {
            c.setStatus(status);
            c.setUpdatedAt(clock.instant());
            repository.save(c);
        });
    }

    private ChannelCredential markNeedsReauth(ChannelCredential c) {
        c.setStatus(ChannelCredential.Status.NEEDS_REAUTH);
        c.setUpdatedAt(clock.instant());
        repository.save(c);
        log.warn("Refresh token TikTok của shop {} đã hết hạn, seller phải ủy quyền lại", c.getShopId());
        throw new NotAuthorizedException(c.getShopId(), ChannelCredential.Status.NEEDS_REAUTH);
    }

    private ChannelCredential load(String shopId) {
        ChannelCredential c = repository.findByChannelAndShopId(ChannelType.TIKTOK, shopId)
                .orElseThrow(() -> new NotAuthorizedException(shopId, null));
        if (c.getStatus() != ChannelCredential.Status.ACTIVE) {
            throw new NotAuthorizedException(shopId, c.getStatus());
        }
        return c;
    }

    private void apply(ChannelCredential c, TokenData token) {
        c.setAccessToken(token.accessToken());
        c.setAccessExpiresAt(Instant.ofEpochSecond(token.accessTokenExpireIn()));
        c.setRefreshToken(token.refreshToken());
        c.setRefreshExpiresAt(Instant.ofEpochSecond(token.refreshTokenExpireIn()));
        if (token.grantedScopes() != null) {
            c.setScopes(String.join(",", token.grantedScopes()));
        }
        c.setStatus(ChannelCredential.Status.ACTIVE);
        c.setUpdatedAt(clock.instant());
    }
}

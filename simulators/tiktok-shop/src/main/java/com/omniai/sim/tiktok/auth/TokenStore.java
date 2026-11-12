package com.omniai.sim.tiktok.auth;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth code, access token, refresh token của seller, lưu trong bộ nhớ.
 * Refresh xong thì cặp token cũ hết hiệu lực (giống sàn thật), nên 2 luồng cùng refresh sẽ có 1 luồng lỗi.
 */
@Component
public class TokenStore {

    private static final Duration AUTH_CODE_TTL = Duration.ofMinutes(10);

    public record Grant(String shopId, List<String> scopes, String accessToken, Instant accessExpiresAt,
                        String refreshToken, Instant refreshExpiresAt) {
    }

    private record AuthCode(String shopId, Instant expiresAt) {
    }

    private final SimProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, AuthCode> authCodes = new ConcurrentHashMap<>();
    private final Map<String, Grant> byAccess = new ConcurrentHashMap<>();
    private final Map<String, Grant> byRefresh = new ConcurrentHashMap<>();

    public TokenStore(SimProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public String issueAuthCode(String shopId) {
        String code = "ROW_" + randomToken(16);
        authCodes.put(code, new AuthCode(shopId, clock.instant().plus(AUTH_CODE_TTL)));
        return code;
    }

    /** Đổi auth code lấy token. Code chỉ dùng được 1 lần. */
    public Grant exchange(String authCode) {
        AuthCode code = authCode == null ? null : authCodes.remove(authCode);
        if (code == null || code.expiresAt().isBefore(clock.instant())) {
            throw new ApiException(TiktokErrorCode.AUTH_CODE_INVALID);
        }
        return issue(code.shopId());
    }

    public synchronized Grant refresh(String refreshToken) {
        Grant old = refreshToken == null ? null : byRefresh.get(refreshToken);
        if (old == null || old.refreshExpiresAt().isBefore(clock.instant())) {
            throw new ApiException(TiktokErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        byRefresh.remove(old.refreshToken());
        byAccess.remove(old.accessToken());
        return issue(old.shopId());
    }

    /** Kiểm access token của request API. */
    public Grant authenticate(String accessToken) {
        Grant grant = accessToken == null ? null : byAccess.get(accessToken);
        if (grant == null) {
            throw new ApiException(TiktokErrorCode.ACCESS_TOKEN_INVALID);
        }
        if (!grant.accessExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(TiktokErrorCode.ACCESS_TOKEN_EXPIRED);
        }
        return grant;
    }

    /** Fault: cho mọi access token (và refresh token nếu includeRefresh) hết hạn ngay. */
    public synchronized int expireAll(boolean includeRefresh) {
        Instant past = clock.instant().minusSeconds(1);
        int count = 0;
        for (Grant g : List.copyOf(byAccess.values())) {
            Grant expired = new Grant(g.shopId(), g.scopes(), g.accessToken(), past, g.refreshToken(),
                    includeRefresh ? past : g.refreshExpiresAt());
            byAccess.put(g.accessToken(), expired);
            byRefresh.put(g.refreshToken(), expired);
            count++;
        }
        return count;
    }

    public void clear() {
        authCodes.clear();
        byAccess.clear();
        byRefresh.clear();
    }

    private Grant issue(String shopId) {
        Instant now = clock.instant();
        Grant grant = new Grant(shopId, List.copyOf(props.grantedScopes()),
                "TTP_" + randomToken(32), now.plus(props.accessTokenTtl()),
                "TTP_" + randomToken(32), now.plus(props.refreshTokenTtl()));
        byAccess.put(grant.accessToken(), grant);
        byRefresh.put(grant.refreshToken(), grant);
        return grant;
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}

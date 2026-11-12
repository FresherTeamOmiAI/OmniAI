package com.omniai.channel.tiktok;

import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.credential.ChannelCredentialRepository;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.common.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Vòng đời token: refresh trước hạn, refresh khi sàn báo lỗi token, NEEDS_REAUTH, sai scope, race. */
class TiktokTokenManagerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final String SHOP = "shop-1";
    private static final TiktokProperties PROPS = new TiktokProperties("app", "secret", "http://sim", "http://cb",
            "http://hook", Duration.ofMinutes(5), 40, Duration.ofSeconds(5), 3, Duration.ofMillis(1),
            Duration.ofMinutes(5), 10);

    private ChannelCredentialRepository repository;
    private TiktokApi api;
    private TiktokTokenManager manager;
    private ChannelCredential credential;

    @BeforeEach
    void setUp() {
        repository = mock(ChannelCredentialRepository.class);
        api = mock(TiktokApi.class);
        manager = new TiktokTokenManager(repository, api, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        credential = new ChannelCredential();
        credential.setChannel(ChannelType.TIKTOK);
        credential.setShopId(SHOP);
        credential.setShopCipher("CIPHER");
        credential.setAccessToken("old");
        credential.setAccessExpiresAt(NOW.plus(Duration.ofDays(1)));
        credential.setRefreshToken("refresh-old");
        credential.setRefreshExpiresAt(NOW.plus(Duration.ofDays(30)));
        when(repository.findByChannelAndShopId(ChannelType.TIKTOK, SHOP)).thenReturn(Optional.of(credential));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TokenData token(String access) {
        return new TokenData(access, NOW.plus(Duration.ofDays(7)).getEpochSecond(), "refresh-" + access,
                NOW.plus(Duration.ofDays(365)).getEpochSecond(), "open", "Shop", "VN", 0, List.of("seller.customer_service"));
    }

    private static TiktokApiException error(TiktokErrorCode code) {
        return new TiktokApiException(code.code(), code.name(), "REQ", 200);
    }

    @Test
    void usesStoredTokenWhileValid() {
        assertThat(manager.withCredential(SHOP, ChannelCredential::getAccessToken)).isEqualTo("old");
        verify(api, never()).refreshToken(anyString());
    }

    @Test
    void refreshesProactivelyWhenAboutToExpire() {
        credential.setAccessExpiresAt(NOW.plus(Duration.ofMinutes(2)));
        when(api.refreshToken("refresh-old")).thenReturn(token("new"));
        assertThat(manager.withCredential(SHOP, ChannelCredential::getAccessToken)).isEqualTo("new");
        assertThat(credential.getRefreshToken()).isEqualTo("refresh-new");
    }

    @Test
    void expiredTokenFromPlatformTriggersOneRefreshAndRetry() {
        when(api.refreshToken("refresh-old")).thenReturn(token("new"));
        AtomicInteger calls = new AtomicInteger();
        String result = manager.withCredential(SHOP, c -> {
            calls.incrementAndGet();
            if (c.getAccessToken().equals("old")) {
                throw error(TiktokErrorCode.ACCESS_TOKEN_EXPIRED);
            }
            return "ok:" + c.getAccessToken();
        });
        assertThat(result).isEqualTo("ok:new");
        assertThat(calls).hasValue(2);
    }

    @Test
    void stillFailingAfterRefreshIsNotRetriedForever() {
        when(api.refreshToken(anyString())).thenReturn(token("new"));
        assertThatThrownBy(() -> manager.withCredential(SHOP, c -> {
            throw error(TiktokErrorCode.ACCESS_TOKEN_INVALID);
        })).isInstanceOf(TiktokApiException.class);
        verify(api, times(1)).refreshToken(anyString());
    }

    @Test
    void expiredRefreshTokenMarksNeedsReauth() {
        when(api.refreshToken("refresh-old")).thenThrow(error(TiktokErrorCode.REFRESH_TOKEN_EXPIRED));
        assertThatThrownBy(() -> manager.withCredential(SHOP, c -> {
            throw error(TiktokErrorCode.ACCESS_TOKEN_EXPIRED);
        })).isInstanceOf(TiktokTokenManager.NotAuthorizedException.class);
        assertThat(credential.getStatus()).isEqualTo(ChannelCredential.Status.NEEDS_REAUTH);

        // Đã NEEDS_REAUTH thì không gọi sàn nữa
        assertThatThrownBy(() -> manager.withCredential(SHOP, ChannelCredential::getAccessToken))
                .isInstanceOf(TiktokTokenManager.NotAuthorizedException.class);
    }

    @Test
    void refreshTokenPastExpiryNeedsReauthWithoutCallingPlatform() {
        credential.setAccessExpiresAt(NOW.minusSeconds(1));
        credential.setRefreshExpiresAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> manager.withCredential(SHOP, ChannelCredential::getAccessToken))
                .isInstanceOf(TiktokTokenManager.NotAuthorizedException.class);
        verify(api, never()).refreshToken(anyString());
    }

    @Test
    void scopeErrorIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> manager.withCredential(SHOP, c -> {
            calls.incrementAndGet();
            throw error(TiktokErrorCode.SCOPE_NOT_AUTHORIZED);
        })).isInstanceOfSatisfying(TiktokApiException.class,
                e -> assertThat(e.is(TiktokErrorCode.SCOPE_NOT_AUTHORIZED)).isTrue());
        assertThat(calls).hasValue(1);
        verify(api, never()).refreshToken(anyString());
    }

    @Test
    void revokedShopIsNotCalled() {
        credential.setStatus(ChannelCredential.Status.REVOKED);
        assertThatThrownBy(() -> manager.withCredential(SHOP, ChannelCredential::getAccessToken))
                .isInstanceOfSatisfying(TiktokTokenManager.NotAuthorizedException.class,
                        e -> assertThat(e.status()).isEqualTo(ChannelCredential.Status.REVOKED));
    }

    @Test
    void concurrentTokenErrorsRefreshOnlyOnce() throws Exception {
        when(api.refreshToken("refresh-old")).thenAnswer(inv -> {
            Thread.sleep(200);
            return token("new");
        });
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return manager.withCredential(SHOP, c -> {
                        if (c.getAccessToken().equals("old")) {
                            throw error(TiktokErrorCode.ACCESS_TOKEN_EXPIRED);
                        }
                        return c.getAccessToken();
                    });
                }));
            }
            start.countDown();
            for (Future<String> f : results) {
                assertThat(f.get()).isEqualTo("new");
            }
        }
        verify(api, times(1)).refreshToken("refresh-old");
    }
}

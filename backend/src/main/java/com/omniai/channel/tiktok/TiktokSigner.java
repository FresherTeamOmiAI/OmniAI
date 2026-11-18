package com.omniai.channel.tiktok;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ký request gửi TikTok Shop và kiểm chữ ký webhook. Bản sao ở simulator ({@code com.omniai.sim.tiktok.common.TiktokSigner}),
 * hai bên test cùng 1 test vector. Thuật toán CHƯA ĐỐI CHIẾU với docv2 (docs/channel-api/tiktok.md mục 3, 5).
 */
public final class TiktokSigner {

    private static final Set<String> EXCLUDED_PARAMS = Set.of("sign", "access_token");

    private TiktokSigner() {
    }

    /** secret + path + (key+value đã sắp xếp, trừ sign/access_token) + body + secret, HMAC-SHA256(app_secret), hex. */
    public static String signRequest(String path, Map<String, String> query, String body, String appSecret) {
        StringBuilder base = new StringBuilder(path);
        new TreeMap<>(query).forEach((key, value) -> {
            if (!EXCLUDED_PARAMS.contains(key)) {
                base.append(key).append(value == null ? "" : value);
            }
        });
        if (body != null) {
            base.append(body);
        }
        return hmacHex(appSecret, appSecret + base + appSecret);
    }

    /** Header Authorization của webhook = hex(HMAC-SHA256(app_secret, app_key + raw body)). */
    public static String signWebhook(String appKey, String rawBody, String appSecret) {
        return hmacHex(appSecret, appKey + rawBody);
    }

    /** So sánh thời gian hằng để không lộ chữ ký qua thời gian phản hồi. */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không tính được HMAC-SHA256", e);
        }
    }
}

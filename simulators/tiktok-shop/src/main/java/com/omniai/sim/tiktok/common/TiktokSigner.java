package com.omniai.sim.tiktok.common;

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
 * Chữ ký request và webhook. Bản sao ở backend: {@code com.omniai.channel.tiktok.TiktokSigner}; cả hai test cùng 1 test vector.
 * <p>
 * Chưa đối chiếu (docs/channel-api/tiktok.md mục 3, 5):
 * <ul>
 *   <li>Request: secret + path + (key+value của query đã sắp xếp, trừ sign, access_token) + body + secret,
 *       HMAC-SHA256 với khóa app_secret, hex chữ thường.</li>
 *   <li>Webhook: header Authorization = hex(HMAC-SHA256(app_secret, app_key + raw body)).</li>
 * </ul>
 */
public final class TiktokSigner {

    private static final Set<String> EXCLUDED_PARAMS = Set.of("sign", "access_token");

    private TiktokSigner() {
    }

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

    public static String signWebhook(String appKey, String rawBody, String appSecret) {
        return hmacHex(appSecret, appKey + rawBody);
    }

    /** So sánh thời gian hằng để không lộ chữ ký qua thời gian phản hồi. */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.toLowerCase().getBytes(StandardCharsets.UTF_8));
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

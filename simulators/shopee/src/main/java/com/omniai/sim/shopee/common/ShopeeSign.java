package com.omniai.sim.shopee.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Chữ ký Shopee Open Platform v2. Owner: ChienPX.
 * API shop: HMAC-SHA256(partner_id + path + timestamp + access_token + shop_id, partner_key).
 * Push: HMAC-SHA256(callbackUrl + "|" + rawBody, partner_key), hex thường, header Authorization.
 */
public final class ShopeeSign {

    private ShopeeSign() {
    }

    public static String api(String partnerId, String path, long timestamp, String accessToken, String shopId,
                             String partnerKey) {
        return hmac(partnerId + path + timestamp + accessToken + shopId, partnerKey);
    }

    public static String push(String callbackUrl, String rawBody, String partnerKey) {
        return hmac(callbackUrl + "|" + rawBody, partnerKey);
    }

    public static boolean equals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String base, String partnerKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(partnerKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte value : raw) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Không tạo được chữ ký Shopee", error);
        }
    }
}

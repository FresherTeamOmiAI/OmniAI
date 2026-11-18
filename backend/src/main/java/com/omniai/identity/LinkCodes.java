package com.omniai.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dạng mã liên kết {@code OMNI-XXXXXX} (SRS UC-08, G-01): 6 ký tự A–Z và 2–9, bỏ O, I (và 0, 1) cho khỏi nhầm.
 * 32 ký tự nên có 32^6 ≈ 1,07 tỷ mã.
 */
public final class LinkCodes {

    public static final String PREFIX = "OMNI-";
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    /** Tìm mã trong câu chat (T-22.3). Không phân biệt hoa thường. */
    public static final Pattern PATTERN = Pattern.compile("(?i)\\bOMNI-[A-HJ-NP-Z2-9]{6}\\b");

    private static final Pattern BODY = Pattern.compile("[A-HJ-NP-Z2-9]{6}");

    private LinkCodes() {
    }

    static String generate(SecureRandom random) {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Đưa mã khách gõ về dạng {@code OMNI-XXXXXX}: bỏ khoảng trắng, viết hoa, có hay không có tiền tố đều được.
     *
     * @return null nếu không đúng dạng
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (s.startsWith(PREFIX)) {
            s = s.substring(PREFIX.length());
        }
        return BODY.matcher(s).matches() ? PREFIX + s : null;
    }

    /** SHA-256 hex của mã đã chuẩn hoá: lưu và tra trong DB thay cho mã gốc. */
    static String hash(String normalized) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không có SHA-256", e);
        }
    }

    /** Hiện danh tính sàn trên web mà không lộ cả id: {@code ***abc}. */
    static String mask(String externalId) {
        if (externalId == null || externalId.length() <= 3) {
            return "***";
        }
        return "***" + externalId.substring(externalId.length() - 3);
    }
}

package com.omniai.common;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 so sánh bằng {@link MessageDigest#isEqual} (thời gian không phụ thuộc vị trí khác nhau). Chỉ dùng cho tài
 * khoản admin trong bộ nhớ (HTTP Basic, SRS Q-01) với id {@value #ID} trong DelegatingPasswordEncoder (US-43).
 *
 * <p>Vì sao không bcrypt: mật khẩu admin vốn ở dạng rõ trong env và bộ nhớ của tiến trình, hash chỉ nằm cạnh nó trong
 * RAM, nên bcrypt không bảo vệ thêm gì (bcrypt có ích khi kho hash bị lộ). Đổi lại bcrypt tốn ~90 ms CPU cho MỖI
 * request Basic: đo 18/11 {@code /api/conversations} p95 530 ms dù DB chỉ vài ms. Không dùng cho mật khẩu lưu DB.
 */
public final class InMemorySecretEncoder implements PasswordEncoder {

    public static final String ID = "sha256-ct";

    @Override
    public String encode(CharSequence rawPassword) {
        return HexFormat.of().formatHex(digest(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(digest(rawPassword), expected);
    }

    private static byte[] digest(CharSequence value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }
}

package com.omniai.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Encoder SHA-256 so sánh thời gian không đổi cho tài khoản admin trong bộ nhớ (US-43, T-43.1). */
class InMemorySecretEncoderTest {

    private final InMemorySecretEncoder encoder = new InMemorySecretEncoder();

    @Test
    void matchesOnlyTheSamePassword() {
        String encoded = encoder.encode("mat-khau-admin");
        assertThat(encoded).hasSize(64).doesNotContain("mat-khau-admin");
        assertThat(encoder.matches("mat-khau-admin", encoded)).isTrue();
        assertThat(encoder.matches("mat-khau-admiN", encoded)).isFalse();
        assertThat(encoder.matches("", encoded)).isFalse();
    }

    @Test
    void rejectsMalformedOrMissingHash() {
        assertThat(encoder.matches("x", "khong-phai-hex")).isFalse();
        assertThat(encoder.matches("x", null)).isFalse();
        assertThat(encoder.matches(null, encoder.encode("x"))).isFalse();
    }

    /** Bean của SecurityConfig: mặc định vẫn bcrypt (mật khẩu/mã lưu DB), {sha256-ct} chỉ là id bổ sung. */
    @Test
    void delegatingEncoderStillDefaultsToBcrypt() {
        PasswordEncoder delegating = new SecurityConfig().passwordEncoder();
        assertThat(delegating.encode("abc")).startsWith("{bcrypt}");
        assertThat(delegating.matches("abc", "{" + InMemorySecretEncoder.ID + "}" + encoder.encode("abc"))).isTrue();
        assertThat(delegating.matches("abd", "{" + InMemorySecretEncoder.ID + "}" + encoder.encode("abc"))).isFalse();
    }

    /**
     * Tài khoản admin lưu {sha256-ct} và KHÔNG được là UserDetailsPasswordService: nếu là, Spring tự nâng cấp mật khẩu
     * sang bcrypt sau lần đăng nhập đầu và lỗi chậm quay lại (đo được 18/11 khi viết T-43.1).
     */
    @Test
    void adminUserKeepsFastHashAndCannotBeUpgradedToBcrypt() {
        UserDetailsService admin = new SecurityConfig().adminUser("admin", "mat-khau-admin");
        assertThat(admin).isNotInstanceOf(UserDetailsPasswordService.class);
        assertThat(admin.loadUserByUsername("admin").getPassword()).startsWith("{" + InMemorySecretEncoder.ID + "}");
    }
}

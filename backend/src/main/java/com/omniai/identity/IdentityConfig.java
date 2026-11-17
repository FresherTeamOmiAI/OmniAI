package com.omniai.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/** Bean của đăng nhập người mua (US-13): khoá JWT HS256, cách gửi OTP, đồng hồ. */
@Slf4j
@Configuration
@EnableConfigurationProperties({OtpProperties.class, AuthProperties.class})
public class IdentityConfig {

    /** Khoá dev khi JWT_SECRET trống (application.yml, .env.example). Chạy ngoài máy dev phải đặt JWT_SECRET. */
    static final String DEV_DEFAULT_SECRET = "omniai-dev-jwt-secret-change-me-32bytes!";

    @Bean
    SecretKey jwtKey(AuthProperties props) {
        String secret = props.jwtSecret() == null || props.jwtSecret().isBlank() ? DEV_DEFAULT_SECRET : props.jwtSecret();
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET phải dài tối thiểu 32 byte");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("Đang dùng JWT_SECRET mặc định của dev. Đặt JWT_SECRET trong .env nếu chạy ngoài máy dev.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return NimbusJwtEncoder.withSecretKey(jwtKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER));
        return decoder;
    }

    /** Test thay bằng đồng hồ chỉnh được (@Primary) để kiểm hết hạn/khoá mà không phải chờ. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Dev: không gửi mail, in mã ra log (SRS Q-02). */
    @Bean
    @ConditionalOnProperty(prefix = "omniai.otp", name = "sender", havingValue = "log", matchIfMissing = true)
    OtpSender logOtpSender() {
        return (email, code, ttl) -> log.info("[DEV] OTP cho {}: {} (hết hạn sau {} phút)", email, code, ttl.toMinutes());
    }

    @Bean
    @ConditionalOnProperty(prefix = "omniai.otp", name = "sender", havingValue = "smtp")
    OtpSender smtpOtpSender(JavaMailSender mailSender, OtpProperties props) {
        return (email, code, ttl) -> {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(email);
            msg.setSubject("Mã đăng nhập Lumi Demo Store: " + code);
            msg.setText("Mã đăng nhập của bạn là " + code + ".\nMã hết hạn sau " + ttl.toMinutes()
                    + " phút. Đừng đưa mã này cho ai, kể cả nhân viên shop.");
            mailSender.send(msg);
        };
    }
}

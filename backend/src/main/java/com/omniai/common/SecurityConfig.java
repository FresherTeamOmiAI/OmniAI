package com.omniai.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Khóa /api/admin/** và /api/conversations/** bằng HTTP Basic với 1 tài khoản admin từ biến môi trường (SRS Q-01).
 * Mọi đường khác (store, simulator, /ws/chat, actuator, webhook kênh) vẫn mở như trước. US-13 (S3) thay bằng tài khoản thật.
 */
@Slf4j
@Configuration
public class SecurityConfig {

    static final String DEV_DEFAULT_PASSWORD = "admin";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API REST stateless xác thực bằng header Authorization, không dùng cookie nên không cần CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Hội thoại là dữ liệu cá nhân của khách (US-11, decisions #30).
                        .requestMatchers("/api/conversations", "/api/conversations/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUser(@Value("${omniai.admin.username}") String username,
                                 @Value("${omniai.admin.password}") String password,
                                 PasswordEncoder encoder) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD không được để trống");
        }
        if (DEV_DEFAULT_PASSWORD.equals(password)) {
            log.warn("Đang dùng mật khẩu admin mặc định của dev. Đặt ADMIN_PASSWORD trong .env nếu chạy ngoài máy dev.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }
}

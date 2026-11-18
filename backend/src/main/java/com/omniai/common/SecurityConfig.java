package com.omniai.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.util.List;
import java.util.Map;

/**
 * 3 chuỗi filter:
 * <ul>
 *   <li>{@code /api/internal/**}: service-to-service (ai-brain đọc/ghi hồ sơ da, US-19), header {@code X-Internal-Token}
 *   = INTERNAL_API_TOKEN (InternalTokenFilter, decisions #48). Basic admin hay JWT khách đều không vào được.</li>
 *   <li>{@code /api/me/**}, {@code /api/orders/**}: người mua, {@code Authorization: Bearer <JWT>} cấp sau khi đăng nhập
 *   OTP (US-13, decisions #39). Đặt thử cần đăng nhập, đơn gắn khách (SRS G-04, decisions #40).</li>
 *   <li>Còn lại: khóa /api/admin/** và /api/conversations/** bằng HTTP Basic với 1 tài khoản admin từ biến môi trường
 *   (SRS Q-01, admin giữ Basic, không thuộc US-13). Mọi đường khác (store /api/products, /api/auth, simulator, /ws/chat, actuator,
 *   webhook kênh) vẫn mở.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class SecurityConfig {

    static final String DEV_DEFAULT_PASSWORD = "admin";

    public static final String ROLE_CUSTOMER = "CUSTOMER";

    @Bean
    @Order(0)
    SecurityFilterChain internalFilterChain(HttpSecurity http, @Value("${omniai.internal.token:}") String token) throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter(token);
        if (!filter.configured()) {
            log.warn("INTERNAL_API_TOKEN trống: mọi request /api/internal/** (ai-brain đọc hồ sơ da) sẽ bị 401.");
        }
        http
                .securityMatcher("/api/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalTokenFilter.ROLE))
                .addFilterBefore(filter, BasicAuthenticationFilter.class)
                // Không có httpBasic/formLogin thì mặc định trả 403; thiếu/sai token phải là 401.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_CUSTOMER)));
        http
                .securityMatcher("/api/me", "/api/me/**", "/api/orders", "/api/orders/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(ROLE_CUSTOMER))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API REST stateless xác thực bằng header Authorization, không dùng cookie nên không cần CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Hội thoại là dữ liệu cá nhân của khách (US-11, decisions #31).
                        .requestMatchers("/api/conversations", "/api/conversations/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Mặc định mã hoá bằng bcrypt (mật khẩu/mã lưu DB). Thêm {@code {sha256-ct}} chỉ cho tài khoản admin trong bộ nhớ:
     * kiểm bcrypt ~90 ms mỗi request Basic là phần lớn độ trễ của API admin (US-43, xem InMemorySecretEncoder).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                InMemorySecretEncoder.ID, new InMemorySecretEncoder()));
    }

    @Bean
    UserDetailsService adminUser(@Value("${omniai.admin.username}") String username,
                                 @Value("${omniai.admin.password}") String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD không được để trống");
        }
        if (DEV_DEFAULT_PASSWORD.equals(password)) {
            log.warn("Đang dùng mật khẩu admin mặc định của dev. Đặt ADMIN_PASSWORD trong .env nếu chạy ngoài máy dev.");
        }
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{" + InMemorySecretEncoder.ID + "}" + new InMemorySecretEncoder().encode(password))
                .roles("ADMIN")
                .build());
        // Chỉ đưa ra UserDetailsService đọc: InMemoryUserDetailsManager còn là UserDetailsPasswordService nên sau lần
        // đăng nhập đúng đầu tiên Spring "nâng cấp" {sha256-ct} thành bcrypt (id mặc định) và mọi request lại tốn ~90 ms.
        return manager::loadUserByUsername;
    }
}

package com.omniai.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình `omniai.auth.*`: JWT của người mua (US-13, decisions #39).
 *
 * @param jwtSecret khoá HMAC-SHA256, tối thiểu 32 byte (env {@code JWT_SECRET})
 * @param tokenTtl  thời hạn token
 */
@ConfigurationProperties(prefix = "omniai.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("7d") Duration tokenTtl) {
}

package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Kiểm tra toàn bộ cấu hình Spring + Flyway + JPA chạy được (dùng H2, không cần Docker). */
@SpringBootTest
@ActiveProfiles("h2")
class OmniAiApplicationTests {

    @Test
    void contextLoads() {
    }
}

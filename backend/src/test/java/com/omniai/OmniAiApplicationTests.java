package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy toàn bộ app với PostgreSQL + pgvector thật trong container (cần Docker đang chạy). */
@SpringBootTest
class OmniAiApplicationTests {

    /** Cùng image với docker-compose.yml. Ryuk của Testcontainers tự xóa container khi JVM thoát. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayMigratesAndSeedsProducts() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(8);
    }

    @Test
    void pgvectorExtensionIsEnabled() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }
}

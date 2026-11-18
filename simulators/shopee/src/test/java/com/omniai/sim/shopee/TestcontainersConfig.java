package com.omniai.sim.shopee;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres thật trong container (cần Docker đang chạy). Simulator dùng database {@code shopee} trên container đó;
 * {@code ShopeeStore} tự tạo database nếu chưa có. Cùng image với docker-compose.yml để khỏi tải thêm.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    DynamicPropertyRegistrar shopeeDatabase(PostgreSQLContainer postgres) {
        return registry -> {
            postgres.start();
            String jdbc = postgres.getJdbcUrl();
            int slash = jdbc.lastIndexOf('/');
            int query = jdbc.indexOf('?', slash);
            String suffix = query < 0 ? "" : jdbc.substring(query);
            registry.add("sim.datasource.url", () -> jdbc.substring(0, slash + 1) + "shopee" + suffix);
            registry.add("sim.datasource.username", postgres::getUsername);
            registry.add("sim.datasource.password", postgres::getPassword);
        };
    }
}

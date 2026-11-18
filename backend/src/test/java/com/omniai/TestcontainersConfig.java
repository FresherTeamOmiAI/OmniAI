package com.omniai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL + pgvector thật trong container (cần Docker đang chạy), dùng chung cho mọi @SpringBootTest
 * qua {@code @Import(TestcontainersConfig.class)} để Spring cache 1 context và 1 container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** Cùng image với docker-compose.yml. Ryuk của Testcontainers tự xóa container khi JVM thoát. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    /** Simulator Shopee dùng database `shopee` trên cùng container, không dùng database hub. */
    @Bean
    DynamicPropertyRegistrar shopeeDatabase(PostgreSQLContainer postgres) {
        return registry -> {
            postgres.start();
            String jdbc = postgres.getJdbcUrl();
            int slash = jdbc.lastIndexOf('/');
            int query = jdbc.indexOf('?', slash);
            String suffix = query < 0 ? "" : jdbc.substring(query);
            String shopeeJdbc = jdbc.substring(0, slash + 1) + "shopee" + suffix;
            registry.add("omniai.channels.shopee.datasource.url", () -> shopeeJdbc);
            registry.add("omniai.channels.shopee.datasource.username", postgres::getUsername);
            registry.add("omniai.channels.shopee.datasource.password", postgres::getPassword);
        };
    }
}

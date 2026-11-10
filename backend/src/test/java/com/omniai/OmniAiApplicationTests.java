package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void chatbotReaderCanReadCatalogAndNotCustomers() {
        Integer names = jdbc.queryForObject("SELECT COUNT(*) FROM chatbot_catalog", Integer.class);
        assertThat(names).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'chatbot_catalog' AND column_name = 'price_web'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'chatbot_catalog' AND column_name = 'stock'",
                Integer.class)).isZero();

        jdbc.execute((Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE chatbot_reader");
                try {
                    try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM chatbot_catalog")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(8);
                    }
                    assertThatThrownBy(() -> query(statement, "SELECT email FROM customer"))
                            .isInstanceOf(DataAccessException.class)
                            .hasMessageContaining("permission denied");
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
            return null;
        });
    }

    private static void query(Statement statement, String sql) {
        try (ResultSet ignored = statement.executeQuery(sql)) {
            ignored.next();
        } catch (SQLException error) {
            throw new org.springframework.jdbc.UncategorizedSQLException("query", sql, error);
        }
    }

    @Test
    void productRejectsNegativePriceAndUnknownUsageTime() {
        String insert = "INSERT INTO product (sku, name, brand, category, skin_types, ingredients_inci, size_ml, pao_months,"
                + " price_web, price_shopee, price_tiktok, usage_time) VALUES (?, 'x', 'x', 'serum', 'oily', 'Aqua', 30, 6, ?, 1, 1, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, "T-NEG", -1, "am")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "T-USE", 1, "noon")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productRequiresPriceOnEveryChannel() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product (sku, name, brand, category, skin_types, ingredients_inci,"
                + " size_ml, pao_months, price_web, price_shopee) VALUES ('T-NULL', 'x', 'x', 'serum', 'oily', 'Aqua', 30, 6, 1, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pgvectorExtensionIsEnabled() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }
}

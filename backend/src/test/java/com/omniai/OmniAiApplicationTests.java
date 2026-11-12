package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy toàn bộ app với PostgreSQL + pgvector thật trong container (cần Docker đang chạy). */
@SpringBootTest
@Import(TestcontainersConfig.class)
class OmniAiApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    /**
     * V8 xoá 80 SKU hư cấu, V9 nạp catalog thật từ shared/catalog/products.csv (T-47.1). Số mong đợi tính từ CSV.
     */
    @Test
    void flywayImportsRealCatalogFromCsv() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(TestCatalog.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE active", Long.class))
                .isEqualTo(TestCatalog.activeCount());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE stock = 0", Long.class))
                .isEqualTo(TestCatalog.outOfStockCount());
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT brand) FROM product", Long.class))
                .isEqualTo(TestCatalog.brandCount());
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT category) FROM product", Integer.class)).isEqualTo(10);
        assertThat(TestCatalog.size()).isGreaterThanOrEqualTo(80);
    }

    /** V8 (decisions #28): không còn SKU nào của 5 thương hiệu hư cấu từ V2/V5. */
    @Test
    void fictionalCatalogIsDeleted() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE split_part(sku, '-', 1)"
                + " IN ('LUMI', 'SEN', 'HYD', 'MOC', 'VAN') OR brand IN ('Lumi Lab', 'Hydra Mint')", Integer.class)).isZero();
    }

    /** Mỗi SKU của catalog thật có barcode, nguồn, khoá ảnh <SKU>.webp và ghi chú giấy phép (US-47 AC). */
    @Test
    void realCatalogHasSourceAndImageForEverySku() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE barcode IS NULL OR source_url IS NULL"
                + " OR image_key <> sku || '.webp' OR image_license_note IS NULL", Integer.class)).isZero();
        var row = jdbc.queryForMap("SELECT barcode, image_key, source_url FROM product WHERE sku = 'CERA-CLE-01'");
        assertThat(row.get("barcode")).isEqualTo("3337875597197");
        assertThat(row.get("image_key")).isEqualTo("CERA-CLE-01.webp");
        assertThat((String) row.get("source_url")).endsWith("/product/3337875597197");
    }

    @Test
    void chatbotReaderCanReadCatalogAndNotCustomers() {
        Long names = jdbc.queryForObject("SELECT COUNT(*) FROM chatbot_catalog", Long.class);
        assertThat(names).isEqualTo(TestCatalog.activeCount());
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
                        assertThat((long) rows.getInt(1)).isEqualTo(TestCatalog.activeCount());
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

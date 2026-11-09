package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy toàn bộ app với PostgreSQL + pgvector thật trong container (cần Docker đang chạy). */
@SpringBootTest
@Import(TestcontainersConfig.class)
class OmniAiApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    /** V5 (T-08.2) nạp 80 SKU từ shared/catalog/products.csv, gồm 2 SKU ẩn và 4 SKU hết hàng. */
    @Test
    void flywayImportsCatalogOf80Skus() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE active", Integer.class)).isEqualTo(78);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE stock = 0", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT brand) FROM product", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT category) FROM product", Integer.class)).isEqualTo(10);
    }

    /** SKU có sẵn từ V2 được upsert: giữ giá cũ, có thêm các trường của V4. */
    @Test
    void catalogImportUpdatesSkusSeededByV2() {
        var row = jdbc.queryForMap("SELECT price_web, usage_time, skin_concerns, description FROM product"
                + " WHERE sku = 'LUMI-SUN-01'");
        assertThat(row.get("price_web")).isEqualTo(289000L);
        assertThat(row.get("usage_time")).isEqualTo("am");
        assertThat(row.get("skin_concerns")).isEqualTo("oil-control,sun-protection");
        assertThat((String) row.get("description")).isNotBlank();
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

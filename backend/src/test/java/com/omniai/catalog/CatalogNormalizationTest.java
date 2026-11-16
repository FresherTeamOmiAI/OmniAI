package com.omniai.catalog;

import com.omniai.TestCatalog;
import com.omniai.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V15 chuẩn hoá bảng product (T-04.1 sửa theo review, decisions #35): dữ liệu sau khi tách bảng phải khớp
 * shared/catalog/products.csv từng SKU, ràng buộc mới có hiệu lực, 2 view chatbot giữ nguyên cột và giá trị.
 * Cấu hình giống OmniAiApplicationTests để dùng chung Spring context.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class CatalogNormalizationTest {

    private static final List<String> OLD_COLUMNS = List.of("brand", "skin_types", "skin_concerns", "usage_time",
            "ingredients_inci", "image_key", "image_source_url", "image_license_note");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void oldMultiValueColumnsAreGone() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'product'",
                String.class)).doesNotContainAnyElementsOf(OLD_COLUMNS).contains("brand_id", "use_am", "use_pm", "created_at");
    }

    @Test
    void lookupTablesHoldTheVocabulary() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT code FROM skin_type ORDER BY sort_order", String.class))
                .containsExactly("oily", "dry", "combination", "sensitive", "normal");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skin_concern", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brand", Long.class)).isEqualTo(TestCatalog.brandCount());
        // Catalog thật: tiền tố SKU và hãng khớp 1-1, nên hãng nào cũng có code.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brand WHERE code IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT code FROM brand WHERE name = 'CeraVe'", String.class)).isEqualTo("CERA");
    }

    /** Từng SKU: hãng, tập loại da, tập vấn đề da, buổi dùng, INCI đúng thứ tự (bỏ trùng), ảnh chính khớp CSV. */
    @Test
    void everySkuMatchesCsvAfterSplit() {
        Map<String, String> brand = new HashMap<>();
        Map<String, String> usage = new HashMap<>();
        jdbc.query("SELECT p.sku, b.name, p.use_am, p.use_pm FROM product p JOIN brand b ON b.id = p.brand_id", (ResultSet r) -> {
            brand.put(r.getString(1), r.getString(2));
            boolean am = r.getBoolean(3);
            boolean pm = r.getBoolean(4);
            usage.put(r.getString(1), am && pm ? "am,pm" : am ? "am" : pm ? "pm" : "");
        });
        Map<String, Set<String>> skins = collectSet("SELECT p.sku, x.skin_type_code FROM product_skin_type x JOIN product p ON p.id = x.product_id");
        Map<String, Set<String>> concerns = collectSet("SELECT p.sku, x.skin_concern_code FROM product_skin_concern x JOIN product p ON p.id = x.product_id");
        Map<String, List<String>> inci = new HashMap<>();
        jdbc.query("SELECT p.sku, i.inci_name FROM product_ingredient x JOIN product p ON p.id = x.product_id"
                + " JOIN ingredient i ON i.id = x.ingredient_id ORDER BY p.sku, x.position", (ResultSet r) -> {
            inci.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getString(2).toLowerCase(Locale.ROOT));
        });
        Map<String, String> image = new HashMap<>();
        jdbc.query("SELECT p.sku, i.storage_key FROM product_image i JOIN product p ON p.id = i.product_id WHERE i.is_primary",
                (ResultSet r) -> {
                    image.put(r.getString(1), r.getString(2));
                });

        for (TestCatalog.Row row : TestCatalog.rows()) {
            String sku = row.sku();
            assertThat(brand.get(sku)).as(sku).isEqualTo(row.brand());
            assertThat(skins.get(sku)).as(sku).isEqualTo(row.skinTypes());
            assertThat(concerns.getOrDefault(sku, Set.of())).as(sku).isEqualTo(row.skinConcerns());
            assertThat(usage.get(sku)).as(sku).isEqualTo(row.usageTime());
            // Cách viết hoa của 1 thành phần lấy theo lần gặp đầu trong catalog, nên so không phân biệt hoa thường.
            assertThat(inci.get(sku)).as(sku).isEqualTo(row.inci().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
            assertThat(image.get(sku)).as(sku).isEqualTo(row.imageKey());
        }
    }

    /** Nhóm hoạt chất cho luật thành phần (khớp ai-brain/app/rules/ingredients.py) gắn đúng số SKU. */
    @Test
    void activeGroupsMatchCsvCounts() {
        Map<String, Set<String>> groups = Map.of(
                "retinoid", Set.of("retinol", "retinal", "retinaldehyde", "tretinoin", "adapalene", "retinyl palmitate"),
                "aha", Set.of("glycolic acid", "lactic acid", "mandelic acid"),
                "bha", Set.of("salicylic acid"),
                "vitamin_c", Set.of("ascorbic acid"),
                "azelaic", Set.of("azelaic acid"));
        groups.forEach((group, names) -> {
            long expected = TestCatalog.rows().stream()
                    .filter(r -> r.inci().stream().anyMatch(n -> names.contains(n.toLowerCase(Locale.ROOT)))).count();
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT x.product_id) FROM product_ingredient x"
                    + " JOIN ingredient i ON i.id = x.ingredient_id WHERE i.active_group = ?", Long.class, group))
                    .as(group).isEqualTo(expected);
        });
    }

    /** ai-brain đọc view như cũ: cùng tên cột, chuỗi CSV theo sort_order. */
    @Test
    void chatbotViewsKeepColumnsAndValues() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'chatbot_catalog'"
                + " ORDER BY ordinal_position", String.class)).containsExactly("brand", "name", "category", "skin_types",
                "skin_concerns", "usage_time", "price_web", "price_shopee", "price_tiktok");
        TestCatalog.Row cerave = TestCatalog.rows().stream().filter(r -> r.sku().equals("CERA-CLE-01")).findFirst().orElseThrow();
        var view = jdbc.queryForMap("SELECT brand, skin_types, usage_time FROM chatbot_catalog_search WHERE sku = 'CERA-CLE-01'");
        assertThat(view.get("brand")).isEqualTo(cerave.brand());
        assertThat(Set.of(((String) view.get("skin_types")).split(","))).isEqualTo(cerave.skinTypes());
        assertThat(view.get("usage_time")).isEqualTo(cerave.usageTime());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chatbot_catalog", Long.class)).isEqualTo(TestCatalog.activeCount());
    }

    // --- Ràng buộc mới của V15 (mỗi test 1 câu bị chặn; rollback) ---

    @Test
    @Transactional
    void unknownSkinTypeIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_skin_type (product_id, skin_type_code)"
                + " VALUES ((SELECT min(id) FROM product), 'alien')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void sameIngredientTwiceInOneProductIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_ingredient (product_id, position, ingredient_id)"
                + " SELECT product_id, 999, ingredient_id FROM product_ingredient WHERE position = 1 LIMIT 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void secondPrimaryImageIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_image (product_id, sort_order, storage_key, is_primary)"
                + " SELECT product_id, 1, 'other-' || storage_key, true FROM product_image LIMIT 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void unknownActiveGroupIsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ingredient SET active_group = 'magic' WHERE id = (SELECT min(id) FROM ingredient)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Map<String, Set<String>> collectSet(String sql) {
        Map<String, Set<String>> result = new HashMap<>();
        jdbc.query(sql, (ResultSet r) -> {
            result.computeIfAbsent(r.getString(1), k -> new HashSet<>()).add(r.getString(2));
        });
        return result;
    }
}

package com.omniai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.conversation.TurnHistory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy toàn bộ app với PostgreSQL + pgvector thật trong container (cần Docker đang chạy). */
@SpringBootTest
@Import(TestcontainersConfig.class)
class OmniAiApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConversationRecorder recorder;

    @Autowired
    TurnHistory history;

    /**
     * V9 xoá 80 SKU hư cấu, V10 nạp catalog thật từ shared/catalog/products.csv (T-47.1). Số mong đợi tính từ CSV.
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

    /** V9 (decisions #30): không còn SKU nào của 5 thương hiệu hư cấu từ V2/V5. */
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
                    try (ResultSet rows = statement.executeQuery(
                            "SELECT description FROM chatbot_catalog_search WHERE sku = 'CERA-CLE-01'")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isNotBlank();
                    }
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

    @Test
    void chatHistoryDoesNotCrossOwners() {
        CanonicalMessage owner = turn("owner-a", "web:shared", "m-owner", "kem chống nắng cho da dầu");
        recorder.inbound(owner);

        assertThat(history.before(turn("owner-b", "web:shared", "m-other", "cách sử dụng của bọn này"))).isEmpty();
        assertThat(history.before(turn("owner-a", "web:shared", "m-next", "cách sử dụng của bọn này")))
                .extracting(com.omniai.brain.BrainTurn::content)
                .contains("kem chống nắng cho da dầu");
    }

    private static CanonicalMessage turn(String owner, String conversationId, String messageId, String text) {
        return new CanonicalMessage(messageId, ChannelType.WEB, owner, null, conversationId, text, Instant.now(), Map.of());
    }

    private static void query(Statement statement, String sql) {
        try (ResultSet ignored = statement.executeQuery(sql)) {
            ignored.next();
        } catch (SQLException error) {
            throw new org.springframework.jdbc.UncategorizedSQLException("query", sql, error);
        }
    }

    /** Không @Transactional: mỗi câu bị DB từ chối sẽ huỷ cả transaction. Tự xoá sản phẩm thử ở cuối (CASCADE giá). */
    @Test
    void productRejectsNegativePriceUnknownChannelAndUnknownUsageTime() {
        String insert = "INSERT INTO product (sku, name, brand, category, skin_types, ingredients_inci, size_ml, pao_months,"
                + " usage_time) VALUES (?, 'x', 'x', 'serum', 'oily', 'Aqua', 30, 6, ?)";
        assertThatThrownBy(() -> jdbc.update(insert, "T-USE", "noon")).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(insert, "T-PRICE", "am");
        try {
            Long id = jdbc.queryForObject("SELECT id FROM product WHERE sku = 'T-PRICE'", Long.class);
            String price = "INSERT INTO product_channel (product_id, channel_code, price) VALUES (?, ?, ?)";
            assertThatThrownBy(() -> jdbc.update(price, id, "WEB", -1)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(price, id, "LAZADA", 1)).isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(price, id, "WEB", 1);
            assertThatThrownBy(() -> jdbc.update(price, id, "WEB", 2)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM product WHERE sku = 'T-PRICE'");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_channel", Long.class)).isEqualTo(TestCatalog.size() * 3L);
    }

    /** V13 (decisions #32): giá không còn là cột của product; mỗi SKU có đúng giá của CSV trên cả 3 kênh. */
    @Test
    void channelPricesLiveInProductChannelAndMatchCsv() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_name = 'product' AND column_name LIKE 'price%'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT code FROM sales_channel WHERE owned", String.class)).containsExactly("WEB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_channel WHERE active", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_channel", Long.class)).isEqualTo(TestCatalog.size() * 3L);

        Map<String, Map<String, Long>> db = new HashMap<>();
        jdbc.query("SELECT p.sku, pc.channel_code, pc.price FROM product_channel pc JOIN product p ON p.id = pc.product_id",
                (ResultSet row) -> {
                    db.computeIfAbsent(row.getString(1), k -> new HashMap<>()).put(row.getString(2), row.getLong(3));
                });
        for (TestCatalog.Row row : TestCatalog.rows()) {
            assertThat(db.get(row.sku())).as(row.sku()).isEqualTo(row.prices());
        }

        // 2 view chatbot giữ nguyên tên cột cũ để ai-brain không phải đổi.
        TestCatalog.Row cerave = TestCatalog.rows().stream().filter(r -> r.sku().equals("CERA-CLE-01")).findFirst().orElseThrow();
        var view = jdbc.queryForMap("SELECT price_web, price_shopee, price_tiktok FROM chatbot_catalog_search WHERE sku = 'CERA-CLE-01'");
        assertThat(view).containsEntry("price_web", cerave.prices().get("WEB"))
                .containsEntry("price_shopee", cerave.prices().get("SHOPEE"))
                .containsEntry("price_tiktok", cerave.prices().get("TIKTOK"));
    }

    /** Lý do của V13: thêm 1 sàn chỉ là INSERT dữ liệu, không ALTER bảng nào. Rollback sau test. */
    @Test
    @Transactional
    void addingChannelNeedsNoSchemaChange() {
        jdbc.update("INSERT INTO sales_channel (code, name, owned, active) VALUES ('LAZADA', 'Lazada', false, true)");
        jdbc.update("INSERT INTO product_channel (product_id, channel_code, price)"
                + " SELECT product_id, 'LAZADA', price FROM product_channel WHERE channel_code = 'SHOPEE'");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_channel WHERE channel_code = 'LAZADA'", Long.class))
                .isEqualTo((long) TestCatalog.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chatbot_catalog", Long.class)).isEqualTo(TestCatalog.activeCount());
    }

    @Test
    void pgvectorExtensionIsEnabled() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    /** T-10.1: cột embedding, view chỉ sản phẩm đang bán, cosine trả đúng sản phẩm gần hơn. */
    @Test
    void nearestEmbeddingIsTheQueriedProduct() {
        int active = (int) TestCatalog.activeCount();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chatbot_catalog_search", Integer.class)).isEqualTo(active);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chatbot_catalog_search WHERE sku IN ('NEUT-SUN-02', 'NIVE-MSK-01')", Integer.class)).isZero();

        var skus = jdbc.queryForList("SELECT sku FROM product WHERE active = true ORDER BY sku LIMIT 2", String.class);
        jdbc.update("UPDATE product SET embedding = CAST(? AS vector) WHERE sku = ?", vector(1), skus.get(0));
        jdbc.update("UPDATE product SET embedding = CAST(? AS vector) WHERE sku = ?", vector(0), skus.get(1));
        assertThat(jdbc.queryForObject(
                "SELECT sku FROM product WHERE embedding IS NOT NULL ORDER BY embedding <=> CAST(? AS vector) LIMIT 1",
                String.class, vector(1))).isEqualTo(skus.get(0));

        jdbc.execute((Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE chatbot_reader");
                try {
                    try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM chatbot_catalog_search")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo((int) TestCatalog.activeCount());
                    }
                    assertThatThrownBy(() -> query(statement, "SELECT embedding FROM product"))
                            .isInstanceOf(DataAccessException.class)
                            .hasMessageContaining("permission denied");
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
            return null;
        });
    }

    private static String vector(int first) {
        StringBuilder literal = new StringBuilder("[").append(first);
        for (int i = 1; i < 768; i++) {
            literal.append(",0");
        }
        return literal.append("]").toString();
    }
}

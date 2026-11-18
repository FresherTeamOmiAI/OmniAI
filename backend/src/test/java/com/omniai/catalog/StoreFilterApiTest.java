package com.omniai.catalog;

import com.omniai.TestCatalog;
import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bộ lọc web store GET /api/products (T-09.2, US-09): 6 tổ hợp lọc + tham số sai.
 * Số kết quả mong đợi tính từ shared/catalog/products.csv ({@link TestCatalog}), không ghi cứng.
 * Cấu hình giống AdminProductApiTest để dùng chung Spring context (và container Postgres).
 * Từ US-43 (T-43.1): tối đa 100 SP/trang nên test đọc lần lượt các trang; list trả thẻ {@link ProductCard};
 * 1 trang store ≤ 8 câu SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
// Tắt worker đồng bộ: nó poll DB mỗi giây, chen câu SQL vào phép đếm của StoreFilterApiTest. Giữ giống nhau ở 3 test
// (AdminProductApiTest, StoreFilterApiTest, DemoOrderApiTest) để dùng chung Spring context.
@TestPropertySource(properties = {"omniai.images.base-url=https://test.supabase.co/", "omniai.sync.enabled=false"})
@Transactional
class StoreFilterApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    EntityManagerFactory emf;

    @PersistenceContext
    EntityManager em;

    @MockitoBean
    BrainClient brainClient;

    // --- 6 tổ hợp lọc (tiêu chí của T-09.2) ---

    @Test
    void skinTypeOnly() throws Exception {
        expect("skinType=oily", r -> r.skinTypes().contains("oily"));
    }

    @Test
    void categoryOnly() throws Exception {
        expect("category=serum", r -> r.category().equals("serum"));
    }

    @Test
    void priceRangeOnly() throws Exception {
        expect("minPrice=200000&maxPrice=400000", r -> between(r, 200_000, 400_000));
    }

    @Test
    void skinTypeAndCategory() throws Exception {
        expect("skinType=dry&category=moisturizer", r -> r.skinTypes().contains("dry") && r.category().equals("moisturizer"));
    }

    @Test
    void skinTypeCategoryAndPrice() throws Exception {
        expect("skinType=sensitive&category=cleanser&minPrice=100000&maxPrice=500000",
                r -> r.skinTypes().contains("sensitive") && r.category().equals("cleanser") && between(r, 100_000, 500_000));
    }

    @Test
    void noMatchGivesEmptyPage() throws Exception {
        expect("category=lip-care&minPrice=100000000", r -> false);
    }

    // --- Các trường hợp khác ---

    /** Tiêu chí T-09.1 "lọc 5 loại da đúng": mỗi loại da trả đúng số SKU active có loại da đó. */
    @Test
    void everyOneOfFiveSkinTypesFiltersExactly() throws Exception {
        for (String skin : List.of("oily", "dry", "combination", "sensitive", "normal")) {
            expect("skinType=" + skin, r -> r.skinTypes().contains(skin));
        }
    }

    @Test
    void filterIsCaseInsensitiveAndBlankMeansNoFilter() throws Exception {
        expect("skinType=OILY&category= Serum ", r -> r.skinTypes().contains("oily") && r.category().equals("serum"));
        expect("skinType=&category=", r -> true);
    }

    /** Sản phẩm ẩn không bao giờ hiện ở store, kể cả khi khớp bộ lọc (SRS G-06). */
    @Test
    void hiddenProductsNeverAppear() throws Exception {
        List<String> skus = allSkus("category=sunscreen");
        assertThat(skus).isNotEmpty().doesNotContainAnyElementsOf(TestCatalog.hiddenSkus());
    }

    @Test
    void invalidFilterIsBadRequestWithFields() throws Exception {
        mvc.perform(get("/api/products?skinType=alien&category=shampoo&minPrice=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("skinType", "category", "minPrice")));
        mvc.perform(get("/api/products?minPrice=500000&maxPrice=100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("minPrice")));
        mvc.perform(get("/api/products?minPrice=re")).andExpect(status().isBadRequest());
    }

    /** Lọc giá + phân trang: tổng không bị nhân bản bởi join product_channel, các trang nối lại không trùng SKU. */
    @Test
    void priceFilterPagesWithoutDuplicates() throws Exception {
        String query = "minPrice=100000&maxPrice=600000&sort=id&size=20";
        long total = page(query + "&page=0").get("total").asLong();
        List<String> all = new ArrayList<>();
        for (int p = 0; p * 20 < total; p++) {
            all.addAll(skus(page(query + "&page=" + p)));
        }
        assertThat(all).hasSize((int) total).doesNotHaveDuplicates();
        assertThat(total).isEqualTo(TestCatalog.rows().stream().filter(r -> r.active() && between(r, 100_000, 600_000)).count());
    }

    // --- US-43, T-43.1: giới hạn trang, sort, thẻ gọn, số câu SQL ---

    @Test
    void pageSizeIsCappedAt100AndDefaultIs20SortedById() throws Exception {
        JsonNode big = page("size=500");
        assertThat(big.get("size").asInt()).isEqualTo(100);
        assertThat(big.get("items").size()).isEqualTo(100);

        JsonNode first = page("");
        assertThat(first.get("items").size()).isEqualTo(20);
        List<Long> ids = new ArrayList<>();
        first.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        assertThat(ids).isSorted();
    }

    @Test
    void listReturnsCardFieldsOnly() throws Exception {
        JsonNode item = page("size=1").get("items").get(0);
        List<String> fields = item.properties().stream().map(Map.Entry::getKey).toList();
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "sku", "brand", "name", "category", "skinTypes", "imageUrl", "prices", "stock");
    }

    @Test
    void sortOnlyByIdNameOrCreatedAt() throws Exception {
        page("sort=name,desc&size=5");
        page("sort=createdAt&size=5");
        mvc.perform(get("/api/products?sort=prices"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("sort")));
        mvc.perform(get("/api/products?sort=khongco")).andExpect(status().isBadRequest());
    }

    /** Đặc tả US-43 mục 2: 1 trang store 20 SP tối đa 8 câu SQL (trước T-43.1 đo 16–28 câu). */
    @Test
    void storePageRunsAtMostEightSqlStatements() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        page("page=0&size=20"); // nạp cache bảng tra cứu như lúc app đã chạy
        try {
            stats.setStatisticsEnabled(true);
            for (String query : List.of("page=0&size=20", "page=5&size=20", "page=0&size=20&skinType=oily&category=serum")) {
                em.clear(); // test @Transactional dùng chung persistence context với request: xoá để đếm như request mới
                stats.clear();
                page(query);
                assertThat(stats.getPrepareStatementCount()).as(query).isBetween(2L, 8L); // ít nhất: count + trang
            }
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    // --- helpers ---

    /** Gọi API với size đủ lớn, so tổng và tập SKU với CSV (chỉ SKU active). */
    private void expect(String query, Predicate<TestCatalog.Row> match) throws Exception {
        List<String> expected = TestCatalog.rows().stream().filter(TestCatalog.Row::active).filter(match)
                .map(TestCatalog.Row::sku).toList();
        assertThat(page(query + "&size=100").get("total").asLong()).as(query).isEqualTo(expected.size());
        assertThat(allSkus(query)).as(query).containsExactlyInAnyOrderElementsOf(expected);
    }

    /** Mọi SKU khớp bộ lọc, đọc lần lượt các trang 100 SP (trần max-page-size). */
    private List<String> allSkus(String query) throws Exception {
        List<String> all = new ArrayList<>();
        long total = Long.MAX_VALUE;
        for (int p = 0; (long) p * 100 < total; p++) {
            JsonNode body = page(query + "&size=100&sort=id&page=" + p);
            total = body.get("total").asLong();
            all.addAll(skus(body));
        }
        return all;
    }

    private JsonNode page(String query) throws Exception {
        String content = mvc.perform(get("/api/products?" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    private static List<String> skus(JsonNode page) {
        List<String> skus = new ArrayList<>();
        page.get("items").forEach(item -> skus.add(item.get("sku").asString()));
        return skus;
    }

    private static boolean between(TestCatalog.Row row, long min, long max) {
        long web = row.prices().get("WEB");
        return web >= min && web <= max;
    }
}

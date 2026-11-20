package com.omniai.promotion;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lịch sử giá (T-33.1, US-33 AC "lịch sử giá lưu mỗi lần đổi"): sửa giá theo kênh qua API admin sản phẩm thì có 1 dòng
 * cũ → mới; không đổi giá thì không ghi. V23 ghi sẵn mốc đầu cho giá hiện có. Mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {"omniai.images.base-url=https://test.supabase.co/", "omniai.sync.enabled=false"})
@Transactional
class PriceHistoryTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin");
    private static final String SKU = "CERA-CLE-01";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    BrainClient brainClient;

    private long id;

    @BeforeEach
    void setUp() {
        id = jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, SKU);
    }

    @Test
    void migrationRecordsCurrentPricesAsBaseline() {
        Integer missing = jdbc.queryForObject("""
                SELECT count(*) FROM product_channel pc
                WHERE NOT EXISTS (SELECT 1 FROM price_history h WHERE h.product_id = pc.product_id
                                  AND h.channel_code = pc.channel_code AND h.old_price IS NULL AND h.new_price = pc.price)
                """, Integer.class);
        assertThat(missing).isZero();
    }

    @Test
    void changingOneChannelPriceAddsOneRowOldToNew() throws Exception {
        Map<String, Object> product = current();
        long oldShopee = prices(product).get("SHOPEE").asLong();
        Map<String, Object> body = request(product, Map.of("SHOPEE", oldShopee + 5000));
        int before = rows();

        mvc.perform(admin(put("/api/admin/products/" + id), body)).andExpect(status().isOk());

        assertThat(rows()).isEqualTo(before + 1);
        mvc.perform(get("/api/admin/products/" + id + "/price-history").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].channel").value("SHOPEE"))
                .andExpect(jsonPath("$.items[0].oldPrice").value(oldShopee))
                .andExpect(jsonPath("$.items[0].newPrice").value(oldShopee + 5000))
                .andExpect(jsonPath("$.items[0].changedAt").value(endsWith("+07:00")));
        mvc.perform(get("/api/admin/products/" + id + "/price-history?channel=web").with(ADMIN))
                .andExpect(jsonPath("$.items[*].channel").value(everyItem(is("WEB"))))
                .andExpect(jsonPath("$.items[0].oldPrice").isEmpty());
    }

    @Test
    void savingWithoutPriceChangeOrTogglingActiveAddsNothing() throws Exception {
        Map<String, Object> product = current();
        int before = rows();
        mvc.perform(admin(put("/api/admin/products/" + id), request(product, Map.of()))).andExpect(status().isOk());
        mvc.perform(admin(patch("/api/admin/products/" + id + "/active"), Map.of("active", false)))
                .andExpect(status().isOk());
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void newProductStartsHistoryForEveryChannel() throws Exception {
        Map<String, Object> product = current();
        Map<String, Object> body = request(product, Map.of());
        body.put("sku", "TEST-PRC-01");
        String created = mvc.perform(admin(post("/api/admin/products"), body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long newId = json.readTree(created).get("id").asLong();

        mvc.perform(get("/api/admin/products/" + newId + "/price-history").with(ADMIN))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].channel").value(containsInAnyOrder("WEB", "SHOPEE", "TIKTOK")))
                .andExpect(jsonPath("$.items[*].oldPrice").value(everyItem(nullValue())));
    }

    @Test
    void historyOfUnknownProductOrChannel() throws Exception {
        mvc.perform(get("/api/admin/products/999999999/price-history").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/products/" + id + "/price-history?channel=LAZADA").with(ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/products/" + id + "/price-history")).andExpect(status().isUnauthorized());
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM price_history", Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> current() throws Exception {
        String body = mvc.perform(get("/api/admin/products/" + id).with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, Map.class);
    }

    private JsonNode prices(Map<String, Object> product) {
        return json.valueToTree(product.get("prices"));
    }

    /** Body PUT từ sản phẩm hiện có, ghi đè giá các kênh trong {@code priceOverrides}. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> request(Map<String, Object> product, Map<String, Long> priceOverrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String field : List.of("sku", "name", "brand", "category", "skinTypes", "skinConcerns", "usageTime",
                "description", "ingredientsInci", "sizeMl", "paoMonths", "stock", "active")) {
            body.put(field, product.get(field));
        }
        Map<String, Object> prices = new LinkedHashMap<>((Map<String, Object>) product.get("prices"));
        prices.putAll(priceOverrides);
        body.put("prices", prices);
        return body;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
}

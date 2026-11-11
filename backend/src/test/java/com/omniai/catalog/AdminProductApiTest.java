package com.omniai.catalog;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API admin sản phẩm (T-08.1, US-08 AC, UC-20) trên catalog 80 SKU của V5.
 * Mỗi test rollback (@Transactional) để không làm lệch số SKU mà test khác đếm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminProductApiTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin");
    private static final long LUMI_SUN_01 = 1L;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    /** Simulator gọi AI Brain qua HTTP; mock để test không cần ai-brain chạy. */
    @MockitoBean
    BrainClient brainClient;

    // --- Xác thực (SRS Q-01) ---

    @Test
    void adminApiRequiresBasicAuth() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/products").with(httpBasic("admin", "sai"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(body(validProduct())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storeSimulatorAndActuatorStayOpenWithoutAuth() throws Exception {
        when(brainClient.ask(any())).thenReturn(new BrainResponse("Chào bạn", "stub", List.of(), false));

        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(post("/api/simulator/web/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\",\"text\":\"xin chào\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalText").value("Chào bạn"));
    }

    // --- Danh sách admin ---

    @Test
    void adminListIncludesHiddenProductsAndSearches() throws Exception {
        mvc.perform(get("/api/admin/products").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(80));
        mvc.perform(get("/api/admin/products?active=false").with(ADMIN))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].sku", containsInAnyOrder("MOC-SER-40", "LUMI-MSK-72")));
        mvc.perform(get("/api/admin/products?q=lumi-sun").with(ADMIN))
                .andExpect(jsonPath("$.items[*].sku", hasItem("LUMI-SUN-01")));
    }

    @Test
    void unknownSortFieldIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/products?sort=khongco").with(ADMIN)).andExpect(status().isBadRequest());
    }

    // --- Tạo ---

    @Test
    void createNormalizesAndShowsInStoreImmediately() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("sku", " test-ser-01 ");
        p.put("skinTypes", "Oily, oily ,DRY");
        p.put("ingredientsInci", "Aqua,Glycerin , Niacinamide");

        String created = mvc.perform(admin(post("/api/admin/products"), p))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/admin/products/\\d+$")))
                .andExpect(jsonPath("$.sku").value("TEST-SER-01"))
                .andExpect(jsonPath("$.skinTypes").value("oily,dry"))
                .andExpect(jsonPath("$.ingredientsInci").value("Aqua, Glycerin, Niacinamide"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/products?size=1")).andExpect(jsonPath("$.total").value(79));
    }

    @Test
    void duplicateSkuIsConflict() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("sku", "lumi-sun-01");
        mvc.perform(admin(post("/api/admin/products"), p))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Trùng SKU"));
    }

    @Test
    void missingAndOutOfRangeFieldsAreBadRequest() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("priceWeb", -1);
        p.put("sizeMl", 0);
        p.remove("ingredientsInci");
        mvc.perform(admin(post("/api/admin/products"), p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("priceWeb", "sizeMl", "ingredientsInci")));
    }

    @Test
    void vocabularyAndMedicalClaimAreBadRequest() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("sku", "SERUM1");
        p.put("category", "shampoo");
        p.put("skinTypes", "oily,alien");
        p.put("usageTime", "noon");
        p.put("ingredientsInci", "Aqua");
        p.put("name", "Serum đặc trị mụn");
        mvc.perform(admin(post("/api/admin/products"), p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "sku", "category", "skinTypes", "usageTime", "ingredientsInci", "name")));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/admin/products").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"X\", \"priceWeb\": \"rẻ\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Sửa ---

    @Test
    void updateShowsInStoreImmediately() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("sku", "LUMI-SUN-01");
        p.put("priceWeb", 300000);
        mvc.perform(admin(put("/api/admin/products/" + LUMI_SUN_01), p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceWeb").value(300000));
        mvc.perform(get("/api/products/" + LUMI_SUN_01)).andExpect(jsonPath("$.priceWeb").value(300000));
    }

    @Test
    void updateCannotChangeSku() throws Exception {
        Map<String, Object> p = validProduct();
        p.put("sku", "LUMI-SUN-99");
        mvc.perform(admin(put("/api/admin/products/" + LUMI_SUN_01), p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mvc.perform(get("/api/admin/products/999999").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        Map<String, Object> p = validProduct();
        mvc.perform(admin(put("/api/admin/products/999999"), p)).andExpect(status().isNotFound());
    }

    // --- Ẩn / hiện (G-06, G-07) ---

    @Test
    void hiddenProductDisappearsFromStoreButAdminStillSeesIt() throws Exception {
        mvc.perform(admin(patch("/api/admin/products/" + LUMI_SUN_01 + "/active"), Map.of("active", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/products/" + LUMI_SUN_01)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products?size=1")).andExpect(jsonPath("$.total").value(77));
        mvc.perform(get("/api/admin/products/" + LUMI_SUN_01).with(ADMIN)).andExpect(jsonPath("$.active").value(false));

        mvc.perform(admin(patch("/api/admin/products/" + LUMI_SUN_01 + "/active"), Map.of("active", true)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + LUMI_SUN_01)).andExpect(status().isOk());
    }

    @Test
    void storeDetailOfInactiveSkuFromCatalogIsNotFound() throws Exception {
        Long id = jdbc.queryForObject("SELECT id FROM product WHERE sku = 'MOC-SER-40'", Long.class);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void setActiveRequiresValue() throws Exception {
        mvc.perform(admin(patch("/api/admin/products/" + LUMI_SUN_01 + "/active"), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("active"));
    }

    // --- Helpers ---

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private static Map<String, Object> validProduct() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("sku", "TEST-SER-01");
        p.put("name", "Serum thử nghiệm Niacinamide 5%");
        p.put("brand", "Lumi Lab");
        p.put("category", "serum");
        p.put("skinTypes", "oily,combination");
        p.put("skinConcerns", "pores,oil-control");
        p.put("usageTime", "am,pm");
        p.put("description", "Serum mỏng nhẹ giúp da trông mịn hơn.");
        p.put("ingredientsInci", "Aqua, Niacinamide, Glycerin");
        p.put("sizeMl", 30);
        p.put("paoMonths", 12);
        p.put("priceWeb", 250000);
        p.put("priceShopee", 260000);
        p.put("priceTiktok", 243000);
        p.put("stock", 10);
        return p;
    }
}

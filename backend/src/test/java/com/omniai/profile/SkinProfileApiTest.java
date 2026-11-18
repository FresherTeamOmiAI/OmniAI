package com.omniai.profile;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import com.omniai.identity.TokenService;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API hồ sơ da (US-19, T-19.3): web qua /api/me/profile (JWT), ai-brain qua /api/internal/... (X-Internal-Token).
 * Mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "omniai.images.base-url=https://test.supabase.co/",
        "omniai.internal.token=test-internal-token"})
@Transactional
class SkinProfileApiTest {

    private static final String INTERNAL = "test-internal-token";
    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());
    private static final String VALID = """
            {"skinType": "oily", "skinConcerns": ["acne", "pores"], "allergies": ["hương liệu", "Cồn"],
             "pregnant": false, "consent": true}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerRepository customers;

    @Autowired
    TokenService tokens;

    @Autowired
    EntityManager em;

    @MockitoBean
    BrainClient brainClient;

    private Customer buyer;
    private String bearer;

    @BeforeEach
    void buyer() {
        Customer c = new Customer();
        c.setEmail("profile-test@example.com");
        buyer = customers.saveAndFlush(c);
        bearer = "Bearer " + tokens.issue(buyer).token();
    }

    private ResultActions me(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions internal(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-Internal-Token", INTERNAL).contentType(MediaType.APPLICATION_JSON));
    }

    /** Đẩy thay đổi của JPA xuống DB (cùng transaction test) trước khi kiểm bằng SQL. */
    private int count(String sql, Object... args) {
        em.flush();
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String consentAt() {
        em.flush();
        return jdbc.queryForObject("SELECT health_consent_at::text FROM skin_profile WHERE customer_id = ?", String.class, buyer.getId());
    }

    /** Xoá khách bằng SQL (như admin/UC-12 xoá dữ liệu), sau khi đẩy thay đổi JPA xuống và bỏ cache. */
    private void deleteBuyer() {
        em.flush();
        jdbc.update("DELETE FROM channel_identity WHERE customer_id = ?", buyer.getId());
        jdbc.update("DELETE FROM customer WHERE id = ?", buyer.getId());
        em.clear();
    }

    private String internalPath(long customerId) {
        return "/api/internal/customers/" + customerId + "/profile";
    }

    @Test
    void customerDeclaresReadsAndUpdatesOwnProfile() throws Exception {
        me(get("/api/me/profile")).andExpect(status().isNotFound());

        me(put("/api/me/profile").content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(buyer.getId()))
                .andExpect(jsonPath("$.skinType").value("oily"))
                .andExpect(jsonPath("$.skinConcerns", containsInAnyOrder("acne", "pores")))
                .andExpect(jsonPath("$.allergies", contains("hương liệu", "Cồn")))
                .andExpect(jsonPath("$.pregnant").value(false))
                .andExpect(jsonPath("$.source").value("FORM"))
                .andExpect(jsonPath("$.healthConsentAt", notNullValue()));
        String consentAt = consentAt();

        me(put("/api/me/profile").content("""
                {"skinType": "DRY", "skinConcerns": [], "allergies": ["cồn", "  paraben  "], "pregnant": true, "consent": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value("dry"))
                .andExpect(jsonPath("$.skinConcerns").isEmpty())
                .andExpect(jsonPath("$.allergies", contains("cồn", "paraben")))
                .andExpect(jsonPath("$.pregnant").value(true));
        assertThat(count("SELECT COUNT(*) FROM skin_profile_concern WHERE customer_id = ?", buyer.getId())).as("PUT với skinConcerns rỗng phải xoá vấn đề da cũ trong DB").isZero();
        assertThat(jdbc.queryForList("SELECT name FROM skin_profile_allergy WHERE customer_id = ? ORDER BY position",
                String.class, buyer.getId())).containsExactly("cồn", "paraben");
        me(get("/api/me/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.skinType").value("dry"));
        // Lần đồng ý đầu được giữ, không bị ghi đè mỗi lần sửa.
        assertThat(consentAt()).isEqualTo(consentAt);
        assertThat(count("SELECT COUNT(*) FROM skin_profile_concern WHERE customer_id = ?", buyer.getId())).isZero();
    }

    @Test
    void writingNeedsConsentAndKnownCodes() throws Exception {
        me(put("/api/me/profile").content("{\"skinType\": \"oily\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("consent")));
        me(put("/api/me/profile").content("""
                {"skinType": "da-xanh", "skinConcerns": ["acne", "toc-gay"], "allergies": ["", "%s"], "consent": false}"""
                .formatted("x".repeat(61))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("consent", "skinType", "skinConcerns[1]", "allergies[0]", "allergies[1]")));
        String eleven = String.join(",", java.util.stream.IntStream.rangeClosed(1, 11).mapToObj(i -> "\"chất " + i + "\"").toList());
        me(put("/api/me/profile").content("{\"allergies\": [" + eleven + "], \"consent\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("allergies"));
        me(put("/api/me/profile").content("không phải json")).andExpect(status().isBadRequest());
        assertThat(count("SELECT COUNT(*) FROM skin_profile")).isZero();
    }

    @Test
    void customerCanDeleteProfile() throws Exception {
        me(put("/api/me/profile").content(VALID)).andExpect(status().isOk());
        me(delete("/api/me/profile")).andExpect(status().isNoContent());
        me(get("/api/me/profile")).andExpect(status().isNotFound());
        me(delete("/api/me/profile")).andExpect(status().isNoContent());
        assertThat(count("SELECT COUNT(*) FROM skin_profile_allergy WHERE customer_id = ?", buyer.getId())).isZero();
    }

    @Test
    void meProfileNeedsCustomerLogin() throws Exception {
        for (String header : new String[] {null, "Bearer khong.phai.token", BASIC}) {
            MockHttpServletRequestBuilder req = put("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(VALID);
            if (header != null) {
                req.header("Authorization", header);
            }
            mvc.perform(req).andExpect(status().isUnauthorized());
        }
        // Token còn hạn nhưng khách đã bị xoá.
        deleteBuyer();
        me(get("/api/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void brainReadsAndWritesTheSameProfileWithInternalToken() throws Exception {
        me(put("/api/me/profile").content(VALID)).andExpect(status().isOk());
        internal(get(internalPath(buyer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value("oily"))
                .andExpect(jsonPath("$.allergies", contains("hương liệu", "Cồn")));

        internal(put(internalPath(buyer.getId())).content("""
                {"skinType": "sensitive", "allergies": ["hương liệu"], "consent": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CHAT"));
        me(get("/api/me/profile"))
                .andExpect(jsonPath("$.skinType").value("sensitive"))
                .andExpect(jsonPath("$.source").value("CHAT"));

        internal(get(internalPath(999_999))).andExpect(status().isNotFound());
        internal(put(internalPath(999_999)).content(VALID)).andExpect(status().isNotFound());
    }

    @Test
    void internalApiRejectsEveryOtherCredential() throws Exception {
        me(put("/api/me/profile").content(VALID)).andExpect(status().isOk());
        String path = internalPath(buyer.getId());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Internal-Token", "sai-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Internal-Token", "")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", BASIC)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingCustomerDeletesProfile() throws Exception {
        me(put("/api/me/profile").content(VALID)).andExpect(status().isOk());
        deleteBuyer();
        assertThat(count("SELECT COUNT(*) FROM skin_profile_concern WHERE customer_id = ?", buyer.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM skin_profile WHERE customer_id = ?", buyer.getId())).isZero();
    }
}

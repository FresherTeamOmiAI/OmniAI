package com.omniai.order;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đặt hàng ảo (T-09.3, SRS UC-06, G-04): đơn DEMO, giá web chụp lại lúc đặt, KHÔNG trừ tồn kho, hết hàng thì không cho đặt.
 * Cấu hình giống StoreFilterApiTest để dùng chung Spring context. Mỗi test rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "omniai.images.base-url=https://test.supabase.co/")
@Transactional
class DemoOrderApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    BrainClient brainClient;

    /** Sản phẩm thật, active, còn hàng (dòng manual trong selection.csv). */
    private long ceraId;
    private long ceraPrice;
    private int ceraStock;

    @BeforeEach
    void load() {
        ceraId = jdbc.queryForObject("SELECT id FROM product WHERE sku = 'CERA-CLE-01'", Long.class);
        ceraPrice = jdbc.queryForObject("SELECT price FROM product_channel WHERE product_id = ? AND channel_code = 'WEB'", Long.class, ceraId);
        ceraStock = jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, ceraId);
    }

    @Test
    void createsDemoOrderWithWebPriceAndKeepsStock() throws Exception {
        order("[{\"productId\": %d, \"quantity\": 2}]".formatted(ceraId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/orders/")))
                .andExpect(jsonPath("$.status").value("DEMO"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.items[0].sku").value("CERA-CLE-01"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(ceraPrice))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(ceraPrice * 2))
                .andExpect(jsonPath("$.total").value(ceraPrice * 2))
                .andExpect(jsonPath("$.notice", containsString("không thanh toán, không giao hàng")));

        // G-04: đơn DEMO không trừ tồn kho.
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, ceraId)).isEqualTo(ceraStock);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order WHERE status = 'DEMO' AND customer_id IS NULL"
                + " AND external_order_id IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void getReturnsSavedOrderAndUnknownIsNotFound() throws Exception {
        String location = order("[{\"productId\": %d, \"quantity\": 1}]".formatted(ceraId))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("CERA-CLE-01"))
                .andExpect(jsonPath("$.status").value("DEMO"));
        mvc.perform(get("/api/orders/999999")).andExpect(status().isNotFound());
    }

    /** Đơn chụp lại giá lúc đặt: shop đổi giá sau đó thì đơn cũ không đổi. */
    @Test
    void orderKeepsPriceAtTimeOfOrder() throws Exception {
        String location = order("[{\"productId\": %d, \"quantity\": 1}]".formatted(ceraId))
                .andReturn().getResponse().getHeader("Location");
        jdbc.update("UPDATE product_channel SET price = price + 50000 WHERE product_id = ? AND channel_code = 'WEB'", ceraId);
        jdbc.update("UPDATE product SET name = 'Tên mới' WHERE id = ?", ceraId);
        mvc.perform(get(location))
                .andExpect(jsonPath("$.items[0].unitPrice").value(ceraPrice))
                .andExpect(jsonPath("$.items[0].name").value(org.hamcrest.Matchers.not("Tên mới")));
    }

    @Test
    void duplicateLinesAreMerged() throws Exception {
        order("[{\"productId\": %d, \"quantity\": 1}, {\"productId\": %d, \"quantity\": 2}]".formatted(ceraId, ceraId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(ceraPrice * 3));
    }

    /** UC-06 ngoại lệ + G-06: hết hàng, không đủ hàng, sản phẩm ẩn, id không có đều không cho đặt. */
    @Test
    void outOfStockHiddenAndUnknownProductsAreRejected() throws Exception {
        long soldOut = jdbc.queryForObject("SELECT id FROM product WHERE active AND stock = 0 ORDER BY id LIMIT 1", Long.class);
        long hidden = jdbc.queryForObject("SELECT id FROM product WHERE sku = 'NEUT-SUN-02'", Long.class);
        jdbc.update("UPDATE product SET stock = 3 WHERE id = ?", ceraId);
        order("""
                [{"productId": %d, "quantity": 1}, {"productId": %d, "quantity": 1},
                 {"productId": 999999, "quantity": 1}, {"productId": %d, "quantity": 4}]""".formatted(soldOut, hidden, ceraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "items[0].quantity", "items[1].productId", "items[2].productId", "items[3].quantity")))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[3].quantity')].message").value(org.hamcrest.Matchers.hasItem("chỉ còn 3 sản phẩm")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order", Integer.class)).isZero();
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        order("[]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("items")));
        order("[{\"productId\": %d, \"quantity\": 0}, {\"productId\": null, \"quantity\": 11}]".formatted(ceraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("items[0].quantity", "items[1].productId", "items[1].quantity")));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\": \"x\"}"))
                .andExpect(status().isBadRequest());
        // Tổng sau khi gộp vượt 10 cũng bị chặn.
        order("[{\"productId\": %d, \"quantity\": 6}, {\"productId\": %d, \"quantity\": 6}]".formatted(ceraId, ceraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(endsWith("quantity")));
    }

    // Ràng buộc V14 ở mức DB: mỗi test 1 câu bị chặn (câu lỗi làm hỏng cả transaction nên không gộp được).

    @Test
    void unknownStatusIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customer_order (channel_code, status, total_amount) VALUES ('WEB', 'PAID', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownChannelIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customer_order (channel_code, status, total_amount) VALUES ('LAZADA', 'DEMO', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void itemConstraintsHold() {
        long orderId = jdbc.queryForObject(
                "INSERT INTO customer_order (channel_code, status, total_amount) VALUES ('WEB', 'DEMO', 0) RETURNING id", Long.class);
        String item = "INSERT INTO customer_order_item (order_id, line_no, product_id, sku, product_name, unit_price, quantity, line_total)"
                + " VALUES (?, 1, ?, 'X', 'X', 100, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(item, orderId, ceraId, 11, 1100)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lineTotalMustMatch() {
        long orderId = jdbc.queryForObject(
                "INSERT INTO customer_order (channel_code, status, total_amount) VALUES ('WEB', 'DEMO', 0) RETURNING id", Long.class);
        String item = "INSERT INTO customer_order_item (order_id, line_no, product_id, sku, product_name, unit_price, quantity, line_total)"
                + " VALUES (?, 1, ?, 'X', 'X', 100, 2, 999)";
        assertThatThrownBy(() -> jdbc.update(item, orderId, ceraId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions order(String itemsJson) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\": " + itemsJson + "}"));
    }
}

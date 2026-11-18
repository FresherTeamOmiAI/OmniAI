package com.omniai.channel.shopee;

import com.omniai.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simulator Shopee (T-50.3, T-51.1). Giá 299000 và tồn 120 là price_shopee, stock của LUMI-SUN-01 trên hub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ShopeeSimulatorTest {

    private static final long ITEM_ID = 1L;
    private static final long HUB_PRICE_SHOPEE = 299_000L;
    private static final int HUB_STOCK = 120;

    @Autowired
    MockMvc mvc;
    @Autowired
    ShopeeChannelProperties shopee;
    @Autowired
    ShopeeSimulatorStore store;
    @Autowired
    JsonMapper json;

    @BeforeEach
    void emptyShopeeTables() {
        JdbcTemplate jdbc = store.jdbc();
        jdbc.execute("""
                TRUNCATE TABLE tbl_shopee_webchat_push, tbl_shopee_message, tbl_shopee_conversation,
                               tbl_shopee_push, tbl_shopee_order_item, tbl_shopee_order,
                               tbl_shopee_seller_stock, tbl_shopee_price_list, tbl_shopee_item
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void priceAndStockMatchHubAndWrongSignIsRejected() throws Exception {
        mvc.perform(signed(post(ShopeeSimulatorController.PRICE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item_id":%d,"price_list":[{"model_id":0,"original_price":%d}]}
                                """.formatted(ITEM_ID, HUB_PRICE_SHOPEE)),
                ShopeeSimulatorController.PRICE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(""))
                .andExpect(jsonPath("$.warning").value(""))
                .andExpect(jsonPath("$.response.success_list[0].original_price").value(HUB_PRICE_SHOPEE));

        mvc.perform(signed(post(ShopeeSimulatorController.STOCK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item_id":%d,"stock_list":[{"model_id":0,"seller_stock":[{"stock":%d}]}]}
                                """.formatted(ITEM_ID, HUB_STOCK)),
                ShopeeSimulatorController.STOCK_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.success_list[0].stock").value(HUB_STOCK));

        mvc.perform(get("/api/simulator/shopee/items/" + ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalPrice").value(HUB_PRICE_SHOPEE))
                .andExpect(jsonPath("$.stock").value(HUB_STOCK));

        mvc.perform(signed(post(ShopeeSimulatorController.PRICE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item_id":%d,"price_list":[{"model_id":0,"original_price":1}]}
                                """.formatted(ITEM_ID)),
                ShopeeSimulatorController.PRICE_PATH, "sai"))
                .andExpect(jsonPath("$.error").value("error_sign"));

        mvc.perform(get("/api/simulator/shopee/items/" + ITEM_ID))
                .andExpect(jsonPath("$.originalPrice").value(HUB_PRICE_SHOPEE));

        assertThat(store.jdbc().queryForObject(
                "SELECT original_price FROM tbl_shopee_price_list WHERE item_id = ? AND model_id = 0",
                Long.class, ITEM_ID)).isEqualTo(HUB_PRICE_SHOPEE);
        assertThat(store.jdbc().queryForObject(
                "SELECT stock FROM tbl_shopee_seller_stock WHERE item_id = ? AND model_id = 0 AND location_id = '-'",
                Long.class, ITEM_ID)).isEqualTo((long) HUB_STOCK);
    }

    @Test
    void emittedOrderIsSignedAndDetailMatches() throws Exception {
        MvcResult emitted = mvc.perform(post("/api/simulator/shopee/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item_id":1,"item_sku":"LUMI-SUN-01","item_name":"Kem chống nắng kiềm dầu SPF50+ PA++++","quantity":2,"price":299000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.code").value(3))
                .andExpect(jsonPath("$.body.data.status").value("READY_TO_SHIP"))
                .andExpect(jsonPath("$.body.data.completed_scenario").value(""))
                .andExpect(jsonPath("$.body.data.items").isArray())
                .andReturn();
        JsonNode body = json.readTree(emitted.getResponse().getContentAsString());
        String orderSn = body.get("order_sn").asString();
        String raw = body.get("raw_body").asString();
        String authorization = body.get("authorization").asString();
        assertThat(body.get("body").get("data").get("ordersn").asString()).isEqualTo(orderSn);
        assertThat(authorization).isEqualTo(ShopeeSign.push(shopee.getPushCallbackUrl(), raw, shopee.getPartnerKey()));
        assertThat(body.get("body").has("data")).isTrue();
        assertThat(body.get("body").has("shop_id")).isTrue();
        assertThat(body.get("body").has("code")).isTrue();
        assertThat(body.get("body").has("timestamp")).isTrue();
        assertThat(body.get("body").get("data").has("ordersn")).isTrue();
        assertThat(body.get("body").get("data").has("status")).isTrue();
        assertThat(body.get("body").get("data").has("update_time")).isTrue();

        mvc.perform(signed(get(ShopeeSimulatorController.ORDER_DETAIL_PATH)
                        .param("order_sn_list", orderSn)
                        .param("response_optional_fields", "item_list,total_amount"),
                ShopeeSimulatorController.ORDER_DETAIL_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.order_list[0].order_sn").value(orderSn))
                .andExpect(jsonPath("$.response.order_list[0].currency").value("VND"))
                .andExpect(jsonPath("$.response.order_list[0].total_amount").value(598000))
                .andExpect(jsonPath("$.response.order_list[0].item_list[0].item_sku").value("LUMI-SUN-01"))
                .andExpect(jsonPath("$.response.order_list[0].item_list[0].model_quantity_purchased").value(2));

        assertThat(store.jdbc().queryForObject(
                "SELECT currency FROM tbl_shopee_order WHERE order_sn = ?", String.class, orderSn)).isEqualTo("VND");
        assertThat(store.jdbc().queryForObject(
                "SELECT item_sku FROM tbl_shopee_order_item WHERE order_sn = ?", String.class, orderSn)).isEqualTo("LUMI-SUN-01");
        assertThat(store.jdbc().queryForObject(
                "SELECT ordersn FROM tbl_shopee_push WHERE order_sn = ?", String.class, orderSn)).isEqualTo(orderSn);
        assertThat(store.jdbc().queryForObject(
                "SELECT code FROM tbl_shopee_push WHERE order_sn = ?", Integer.class, orderSn)).isEqualTo(3);

        mvc.perform(signed(get(ShopeeSimulatorController.ORDER_DETAIL_PATH)
                        .param("order_sn_list", orderSn),
                ShopeeSimulatorController.ORDER_DETAIL_PATH, "sai"))
                .andExpect(jsonPath("$.error").value("error_sign"));
    }

    @Test
    void buyerWebchatCanBeReadAndAnswered() throws Exception {
        MvcResult emitted = mvc.perform(post("/api/simulator/shopee/webchat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from_id":165105353,"from_user_name":"nguyen.a","text":"Đơn của tôi đang ở đâu?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.code").value(10))
                .andExpect(jsonPath("$.body.msg_id").value(""))
                .andExpect(jsonPath("$.body.data.type").value("message"))
                .andExpect(jsonPath("$.body.data.region").value("VN"))
                .andExpect(jsonPath("$.body.data.content.from_id").value(165105353))
                .andExpect(jsonPath("$.body.data.content.to_id").value((int) shopee.getShopId()))
                .andExpect(jsonPath("$.body.data.content.message_type").value("text"))
                .andExpect(jsonPath("$.body.data.content.content.text").value("Đơn của tôi đang ở đâu?"))
                .andExpect(jsonPath("$.body.data.content.region").value("VN"))
                .andExpect(jsonPath("$.body.data.content.is_in_chatbot_session").value(false))
                .andExpect(jsonPath("$.body.data.content.quoted_msg.message_id").value(""))
                .andExpect(jsonPath("$.body.data.content.sub_account_id").value(0))
                .andExpect(jsonPath("$.body.data.content.sub_account_name").value(0))
                .andExpect(jsonPath("$.body.data.content.business_type").value(0))
                .andExpect(jsonPath("$.body.data.content.status").value("user_chat"))
                .andExpect(jsonPath("$.body.data.content.to_shop_id").value((int) shopee.getShopId()))
                .andExpect(jsonPath("$.body.data.content.from_shop_id").value(0))
                .andExpect(jsonPath("$.body.shop_id").value((int) shopee.getShopId()))
                .andReturn();
        JsonNode body = json.readTree(emitted.getResponse().getContentAsString());
        String raw = body.get("raw_body").asString();
        String conversationId = body.get("conversation_id").asString();
        assertThat(body.get("authorization").asString())
                .isEqualTo(ShopeeSign.push(shopee.getPushCallbackUrl(), raw, shopee.getPartnerKey()));
        assertThat(store.jdbc().queryForObject(
                "SELECT code FROM tbl_shopee_webchat_push WHERE message_id = ?",
                Integer.class, Long.parseLong(body.get("message_id").asString()))).isEqualTo(10);

        mvc.perform(signed(get(ShopeeSimulatorController.CONVERSATION_LIST_PATH),
                ShopeeSimulatorController.CONVERSATION_LIST_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(""))
                .andExpect(jsonPath("$.response.conversations[0].conversation_id").value(conversationId))
                .andExpect(jsonPath("$.response.conversations[0].to_id").value(165105353))
                .andExpect(jsonPath("$.response.conversations[0].to_name").value("nguyen.a"))
                .andExpect(jsonPath("$.response.conversations[0].unread_count").value(1))
                .andExpect(jsonPath("$.response.conversations[0].latest_message_content.text").value("Đơn của tôi đang ở đâu?"));

        mvc.perform(signed(get(ShopeeSimulatorController.GET_MESSAGE_PATH)
                        .param("conversation_id", conversationId),
                ShopeeSimulatorController.GET_MESSAGE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.messages[0].content.text").value("Đơn của tôi đang ở đâu?"))
                .andExpect(jsonPath("$.response.messages[0].status").value("user_chat"))
                .andExpect(jsonPath("$.response.page_result.next_offset").value(""));

        mvc.perform(signed(post(ShopeeSimulatorController.SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_id":165105353,"message_type":"text","content":{"text":"Đơn đang được chuẩn bị"}}
                                """),
                ShopeeSimulatorController.SEND_MESSAGE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(""))
                .andExpect(jsonPath("$.response.conversation_id").value(conversationId))
                .andExpect(jsonPath("$.response.message_type").value("text"))
                .andExpect(jsonPath("$.response.content.text").value("Đơn đang được chuẩn bị"));

        mvc.perform(signed(get(ShopeeSimulatorController.GET_MESSAGE_PATH)
                        .param("conversation_id", conversationId),
                ShopeeSimulatorController.GET_MESSAGE_PATH))
                .andExpect(jsonPath("$.response.messages.length()").value(2))
                .andExpect(jsonPath("$.response.messages[1].from_id").value((int) shopee.getShopId()))
                .andExpect(jsonPath("$.response.messages[1].status").value("normal"));

        mvc.perform(signed(get(ShopeeSimulatorController.CONVERSATION_LIST_PATH),
                ShopeeSimulatorController.CONVERSATION_LIST_PATH))
                .andExpect(jsonPath("$.response.conversations[0].unread_count").value(0));

        mvc.perform(signed(post(ShopeeSimulatorController.SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_id":165105353,"message_type":"text","content":{"text":"không được lưu"}}
                                """),
                ShopeeSimulatorController.SEND_MESSAGE_PATH, "sai"))
                .andExpect(jsonPath("$.error").value("error_sign"));
        assertThat(store.jdbc().queryForObject(
                "SELECT COUNT(*) FROM tbl_shopee_message WHERE conversation_id = ?",
                Integer.class, Long.parseLong(conversationId))).isEqualTo(2);

        mvc.perform(signed(post(ShopeeSimulatorController.SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_id":165105353,"message_type":"order","content":{"order_sn":"KHONG-CO"}}
                                """),
                ShopeeSimulatorController.SEND_MESSAGE_PATH))
                .andExpect(jsonPath("$.error").value("error_param"));
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String path) {
        return signed(request, path, null);
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String path, String signOverride) {
        long timestamp = Instant.now().getEpochSecond();
        String sign = signOverride != null ? signOverride : ShopeeSign.api(
                Long.toString(shopee.getPartnerId()), path, timestamp, shopee.getAccessToken(),
                Long.toString(shopee.getShopId()), shopee.getPartnerKey());
        return request
                .param("partner_id", Long.toString(shopee.getPartnerId()))
                .param("timestamp", Long.toString(timestamp))
                .param("access_token", shopee.getAccessToken())
                .param("shop_id", Long.toString(shopee.getShopId()))
                .param("sign", sign);
    }
}

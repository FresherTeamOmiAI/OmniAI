package com.omniai.channel.shopee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
@WebMvcTest(ShopeeSimulatorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShopeeSimulatorStore.class)
@EnableConfigurationProperties(ShopeeChannelProperties.class)
class ShopeeSimulatorTest {

    private static final long ITEM_ID = 1L;
    private static final long HUB_PRICE_SHOPEE = 299_000L;
    private static final int HUB_STOCK = 120;

    @Autowired
    MockMvc mvc;
    @Autowired
    ShopeeChannelProperties shopee;
    @Autowired
    JsonMapper json;

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
                        .param("order_sn_list", orderSn),
                ShopeeSimulatorController.ORDER_DETAIL_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.order_list[0].order_sn").value(orderSn))
                .andExpect(jsonPath("$.response.order_list[0].currency").value("VND"))
                .andExpect(jsonPath("$.response.order_list[0].total_amount").value(598000))
                .andExpect(jsonPath("$.response.order_list[0].item_list[0].item_sku").value("LUMI-SUN-01"))
                .andExpect(jsonPath("$.response.order_list[0].item_list[0].model_quantity_purchased").value(2));

        mvc.perform(signed(get(ShopeeSimulatorController.ORDER_DETAIL_PATH)
                        .param("order_sn_list", orderSn),
                ShopeeSimulatorController.ORDER_DETAIL_PATH, "sai"))
                .andExpect(jsonPath("$.error").value("error_sign"));
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

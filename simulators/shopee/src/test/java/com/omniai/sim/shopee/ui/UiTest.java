package com.omniai.sim.shopee.ui;

import com.omniai.sim.shopee.SimProperties;
import com.omniai.sim.shopee.TestcontainersConfig;
import com.omniai.sim.shopee.common.ShopeeSign;
import com.omniai.sim.shopee.push.PushDispatcher;
import com.omniai.sim.shopee.store.ShopeeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang /ui đọc đúng dữ liệu hub đã gửi và push simulator đã đẩy. Hub ở cổng 1 (đóng) để push luôn thất bại. */
@SpringBootTest(properties = {
        "sim.push-callback-url=http://127.0.0.1:1/api/channels/shopee/push",
        "sim.push.timeout=1s", "sim.push.backoff=10ms"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class UiTest {

    private static final String PRICE_PATH = "/api/v2/product/update_price";
    private static final String SEND_PATH = "/api/v2/sellerchat/send_message";

    @Autowired
    MockMvc mvc;
    @Autowired
    SimProperties shopee;
    @Autowired
    ShopeeStore store;
    @Autowired
    PushDispatcher pushes;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/sim/reset")).andExpect(status().isOk());
    }

    @Test
    void homeRedirectsToPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(header().string("Location", "/ui/index.html"));
        mvc.perform(get("/ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void chatThreadShowsBuyerAndShopMessagesAndApiCallIsLogged() throws Exception {
        mvc.perform(post("/api/simulator/shopee/webchat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from_id\":777,\"from_user_name\":\"lan.nguyen\",\"text\":\"Serum giá bao nhiêu?\"}"))
                .andExpect(jsonPath("$.delivered_by_simulator").value(false));
        mvc.perform(signedPost(SEND_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to_id\":777,\"message_type\":\"text\",\"content\":{\"text\":\"Dạ 299.000đ ạ\"}}"))
                .andExpect(jsonPath("$.error").value(""));

        mvc.perform(get("/sim/ui/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyer_id").value(777))
                .andExpect(jsonPath("$[0].buyer_name").value("lan.nguyen"))
                .andExpect(jsonPath("$[0].messages.length()").value(2))
                .andExpect(jsonPath("$[0].messages[0].from_shop").value(false))
                .andExpect(jsonPath("$[0].messages[0].content.text").value("Serum giá bao nhiêu?"))
                .andExpect(jsonPath("$[0].messages[1].from_shop").value(true))
                .andExpect(jsonPath("$[0].messages[1].content.text").value("Dạ 299.000đ ạ"));

        mvc.perform(get("/sim/ui/api-calls"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].path").value(SEND_PATH))
                .andExpect(jsonPath("$[0].http_status").value(200))
                .andExpect(jsonPath("$[0].error").value(""))
                .andExpect(jsonPath("$[0].query").value(containsString("access_token=test-acc***")))
                .andExpect(jsonPath("$[0].query").value(not(containsString("test-access-token"))));

        mvc.perform(get("/sim/ui/overview"))
                .andExpect(jsonPath("$.shop.shop_id").value((int) shopee.shopId()))
                .andExpect(jsonPath("$.counts.conversations").value(1))
                .andExpect(jsonPath("$.counts.messages").value(2))
                .andExpect(jsonPath("$.counts.api_calls").value(1))
                .andExpect(jsonPath("$.counts.api_errors").value(0));
    }

    @Test
    void itemsShowPriceFromHubAndWrongSignIsLoggedAsError() throws Exception {
        mvc.perform(signedPost(PRICE_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"item_id\":880011,\"price_list\":[{\"model_id\":0,\"original_price\":289000}]}"));
        mvc.perform(post(PRICE_PATH).param("partner_id", "2000001").param("timestamp", "1")
                .param("access_token", "x").param("shop_id", "10001").param("sign", "sai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"item_id\":880011,\"price_list\":[{\"model_id\":0,\"original_price\":1}]}"));

        mvc.perform(get("/sim/ui/items"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].item_id").value(880011))
                .andExpect(jsonPath("$[0].original_price").value(289000))
                .andExpect(jsonPath("$[0].models").value(1));
        mvc.perform(get("/sim/ui/overview"))
                .andExpect(jsonPath("$.counts.items").value(1))
                .andExpect(jsonPath("$.counts.api_calls").value(2))
                .andExpect(jsonPath("$.counts.api_errors").value(1));
    }

    @Test
    void emittedOrderIsListedAndDeliveryFailureIsVisible() throws Exception {
        mvc.perform(post("/api/simulator/shopee/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item_id":1,"item_sku":"LUMI-SUN-01","item_name":"Kem chống nắng","quantity":2,
                                 "price":299000,"deliver":true}
                                """))
                .andExpect(jsonPath("$.delivered_by_simulator").value(true));

        mvc.perform(get("/sim/ui/orders"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total_amount").value(598000))
                .andExpect(jsonPath("$[0].line.item_sku").value("LUMI-SUN-01"));

        await().atMost(Duration.ofSeconds(10)).until(() -> pushes.deliveries().size() == 1);
        PushDispatcher.Delivery delivery = pushes.deliveries().getFirst();
        assertThat(delivery.code()).isEqualTo(3);
        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.attempts()).hasSize(3);
        mvc.perform(get("/sim/ui/overview")).andExpect(jsonPath("$.counts.failed_pushes").value(1));

        mvc.perform(post("/sim/reset")).andExpect(status().isOk());
        mvc.perform(get("/sim/ui/orders")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/sim/ui/pushes")).andExpect(jsonPath("$.length()").value(0));
    }

    /** Tham số ký nằm trên URL như hub gửi thật (MockMvc .param() không tạo query string). */
    private MockHttpServletRequestBuilder signedPost(String path) {
        long timestamp = Instant.now().getEpochSecond();
        String sign = ShopeeSign.api(Long.toString(shopee.partnerId()), path, timestamp, shopee.accessToken(),
                Long.toString(shopee.shopId()), shopee.partnerKey());
        return post(path + "?partner_id=" + shopee.partnerId() + "&timestamp=" + timestamp
                + "&access_token=" + shopee.accessToken() + "&shop_id=" + shopee.shopId() + "&sign=" + sign);
    }
}

package com.omniai.channel.shopee;

import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Connector giá/tồn (T-50.2) và chat (T-21.6) gọi Shop API Shopee có ký. Simulator giờ là app riêng
 * (simulators/shopee, có test riêng cho phía nhận), nên ở đây giả HTTP bằng {@link MockRestServiceServer}.
 */
class ShopeeProductConnectorTest {

    private static final long NOW = 1_700_000_000L;
    private static final String BASE = "http://shopee-sim.test";

    private final JsonMapper json = JsonMapper.builder().build();
    private ShopeeChannelProperties props;
    private MockRestServiceServer server;
    private ShopeeProductConnector connector;
    private ShopeeChatClient chat;

    @BeforeEach
    void setUp() {
        props = new ShopeeChannelProperties();
        props.setApiBaseUrl(BASE);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ShopeeApiClient api = new ShopeeApiClient(props, builder.build(), json,
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        connector = new ShopeeProductConnector(api);
        chat = new ShopeeChatClient(api);
    }

    private static ProductSnapshot snapshot(long price, int stock) {
        return new ProductSnapshot(42L, "SHOPEE", "CERA-SER-01", "Serum", "CeraVe", "serum", "Mô tả", "Aqua", 30,
                List.of(), List.of(), price, stock, true);
    }

    private String sign(String path) {
        return ShopeeSign.api(Long.toString(props.getPartnerId()), path, NOW, props.getAccessToken(),
                Long.toString(props.getShopId()), props.getPartnerKey());
    }

    private static String ok(String response) {
        return "{\"error\":\"\",\"message\":\"\",\"warning\":\"\",\"request_id\":\"r1\",\"response\":" + response + "}";
    }

    @Test
    void priceAndStockAreSignedAndUseModelZero() {
        server.expect(requestTo(startsWith(BASE + ShopeeProductConnector.PRICE_PATH + "?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("partner_id", "2000001"))
                .andExpect(queryParam("shop_id", "10001"))
                .andExpect(queryParam("timestamp", Long.toString(NOW)))
                .andExpect(queryParam("access_token", "test-access-token"))
                .andExpect(queryParam("sign", sign(ShopeeProductConnector.PRICE_PATH)))
                .andExpect(content().json("{\"item_id\":880011,\"price_list\":[{\"model_id\":0,\"original_price\":289000}]}"))
                .andRespond(withSuccess(ok("{\"failure_list\":[],\"success_list\":[]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + ShopeeProductConnector.STOCK_PATH + "?")))
                .andExpect(queryParam("sign", sign(ShopeeProductConnector.STOCK_PATH)))
                .andExpect(content().json("{\"item_id\":880011,\"stock_list\":[{\"model_id\":0,\"seller_stock\":[{\"stock\":15}]}]}"))
                .andRespond(withSuccess(ok("{\"failure_list\":[],\"success_list\":[]}"), MediaType.APPLICATION_JSON));

        connector.pushPrice(snapshot(289_000, 15), "880011");
        connector.pushStock(snapshot(289_000, 15), " 880011 ");

        server.verify();
    }

    @Test
    void wrongSignIsPermanentAndServerErrorIsRetryable() {
        server.expect(requestTo(startsWith(BASE + ShopeeProductConnector.PRICE_PATH)))
                .andRespond(withSuccess("{\"error\":\"error_sign\",\"message\":\"Wrong sign.\",\"request_id\":\"r\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + ShopeeProductConnector.STOCK_PATH)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> connector.pushPrice(snapshot(289_000, 1), "880011"))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("error_sign")
                .satisfies(e -> assertThat(((SyncException) e).retryable()).isFalse());
        assertThatThrownBy(() -> connector.pushStock(snapshot(289_000, 1), "880011"))
                .isInstanceOf(SyncException.class)
                .satisfies(e -> assertThat(((SyncException) e).retryable()).isTrue());
        server.verify();
    }

    @Test
    void failureListAndBadItemIdAreRejectedWithoutRetry() {
        server.expect(requestTo(startsWith(BASE + ShopeeProductConnector.PRICE_PATH)))
                .andRespond(withSuccess(ok("{\"failure_list\":[{\"model_id\":0,\"failed_reason\":\"x\"}],\"success_list\":[]}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.pushPrice(snapshot(289_000, 1), "880011"))
                .isInstanceOf(SyncException.class)
                .satisfies(e -> assertThat(((SyncException) e).retryable()).isFalse());
        assertThatThrownBy(() -> connector.pushStock(snapshot(1, 1), "abc"))
                .isInstanceOf(SyncException.class)
                .satisfies(e -> assertThat(((SyncException) e).retryable()).isFalse());
        server.verify();
    }

    @Test
    void chatReplyGoesThroughSignedSendMessage() {
        server.expect(requestTo(startsWith(BASE + ShopeeChatClient.SEND_MESSAGE_PATH + "?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("sign", sign(ShopeeChatClient.SEND_MESSAGE_PATH)))
                .andExpect(content().json("{\"to_id\":165105353,\"message_type\":\"text\",\"content\":{\"text\":\"Dạ còn hàng ạ\"}}"))
                .andRespond(withSuccess(ok("{\"message_id\":\"77\",\"to_id\":165105353}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + ShopeeChatClient.SEND_MESSAGE_PATH)))
                .andRespond(withSuccess("{\"error\":\"error_sign\",\"message\":\"Wrong sign.\"}", MediaType.APPLICATION_JSON));

        assertThat(chat.sendText(165105353L, "Dạ còn hàng ạ")).isEqualTo("77");
        assertThatThrownBy(() -> chat.sendText(165105353L, "lần 2"))
                .isInstanceOf(ShopeeApiClient.ShopeeApiException.class)
                .satisfies(e -> {
                    ShopeeApiClient.ShopeeApiException error = (ShopeeApiClient.ShopeeApiException) e;
                    assertThat(error.error()).isEqualTo("error_sign");
                    assertThat(error.retryable()).isFalse();
                });
        server.verify();
    }
}

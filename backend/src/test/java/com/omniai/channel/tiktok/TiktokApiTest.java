package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.SendMessageRequest;
import com.omniai.channel.tiktok.TiktokModels.SendMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** TiktokApi: tham số chung + chữ ký, đọc envelope lỗi, retry khi 429. */
class TiktokApiTest {

    private static final TiktokProperties PROPS = new TiktokProperties("app", "secret", "http://sim", "http://cb",
            "http://hook", Duration.ofMinutes(5), 40, Duration.ofSeconds(5), 3, Duration.ofMillis(1),
            Duration.ofMinutes(5), 10);
    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final String SEND_PATH = "/customer_service/202606/conversations/c1/messages";

    private MockRestServiceServer server;
    private TiktokApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new TiktokApi(PROPS, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String ok(String data) {
        return "{\"code\":0,\"message\":\"Success\",\"request_id\":\"R1\",\"data\":" + data + "}";
    }

    @Test
    void signsRequestWithCommonParamsAndSendsAccessToken() {
        SendMessageRequest body = new SendMessageRequest("TEXT", "{\"content\":\"hi\"}", "ROBOT");
        String bodyJson = TiktokModels.JSON.writeValueAsString(body);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("app_key", "app");
        query.put("timestamp", Long.toString(NOW.getEpochSecond()));
        query.put("shop_cipher", "CIPHER");
        String sign = TiktokSigner.signRequest(SEND_PATH, query, bodyJson, "secret");
        URI expected = UriComponentsBuilder.fromUriString("http://sim" + SEND_PATH)
                .queryParam("app_key", "app").queryParam("timestamp", NOW.getEpochSecond())
                .queryParam("shop_cipher", "CIPHER").queryParam("sign", sign).build().toUri();

        server.expect(requestTo(expected))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-tts-access-token", "TOKEN"))
                .andRespond(withSuccess(ok("{\"message_id\":\"m9\"}"), MediaType.APPLICATION_JSON));

        SendMessageResponse r = api.call(HttpMethod.POST, SEND_PATH, Map.of(), body, "TOKEN", "CIPHER",
                SendMessageResponse.class);
        assertThat(r.messageId()).isEqualTo("m9");
        server.verify();
    }

    @Test
    void businessErrorKeepsCodeAndRequestId() {
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess(
                "{\"code\":45101002,\"message\":\"Invalid params.\",\"request_id\":\"REQ42\",\"data\":{}}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> api.call(HttpMethod.POST, SEND_PATH, Map.of(), Map.of(), "T", "C", Void.class))
                .isInstanceOfSatisfying(TiktokApiException.class, e -> {
                    assertThat(e.is(TiktokErrorCode.CS_INVALID_PARAMS)).isTrue();
                    assertThat(e.requestId()).isEqualTo("REQ42");
                    assertThat(e.isTokenError()).isFalse();
                });
    }

    @Test
    void retriesOn429ThenSucceeds() {
        String limited = "{\"code\":36009006,\"message\":\"Too many requests.\",\"request_id\":\"R\",\"data\":{}}";
        server.expect(times(2), method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(limited).contentType(MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(ok("{}"), MediaType.APPLICATION_JSON));
        api.call(HttpMethod.GET, "/event/202309/webhooks", Map.of(), null, "T", "C", Void.class);
        server.verify();
    }

    @Test
    void givesUpAfterMaxRetriesOn429() {
        String limited = "{\"code\":36009006,\"message\":\"Too many requests.\",\"request_id\":\"R\",\"data\":{}}";
        server.expect(times(3), method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(limited).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> api.call(HttpMethod.GET, "/event/202309/webhooks", Map.of(), null, "T", "C", Void.class))
                .isInstanceOfSatisfying(TiktokApiException.class, e -> assertThat(e.isRateLimited()).isTrue());
        server.verify();
    }

    @Test
    void tokenEndpointSendsAppSecretAndGrantType() {
        server.expect(requestTo("http://sim/api/v2/token/refresh?app_key=app&app_secret=secret&refresh_token=R1&grant_type=refresh_token"))
                .andRespond(withSuccess(ok("{\"access_token\":\"A2\",\"access_token_expire_in\":1,\"refresh_token\":\"R2\","
                        + "\"refresh_token_expire_in\":2,\"granted_scopes\":[\"s\"]}"), MediaType.APPLICATION_JSON));
        assertThat(api.refreshToken("R1").accessToken()).isEqualTo("A2");
    }
}

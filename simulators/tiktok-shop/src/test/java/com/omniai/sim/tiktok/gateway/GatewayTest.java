package com.omniai.sim.tiktok.gateway;

import com.omniai.sim.tiktok.SimTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Thứ tự kiểm của cổng: sign/timestamp → token → scope → shop_cipher. */
class GatewayTest extends SimTestSupport {

    private static final String CONVERSATIONS = "/customer_service/202309/conversations";

    @Test
    void validRequestPasses() throws Exception {
        assertThat(get(CONVERSATIONS, Map.of("page_size", "20"), accessToken()).get("code").asInt()).isZero();
    }

    @Test
    void missingOrTamperedSignIsRejected() throws Exception {
        String token = accessToken();
        HttpResponse<String> noSign = http.send(HttpRequest.newBuilder(uri(CONVERSATIONS + "?app_key=" + APP_KEY
                        + "&timestamp=" + Instant.now().getEpochSecond() + "&shop_cipher=" + CIPHER + "&page_size=5"))
                .header("x-tts-access-token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json.readTree(noSign.body()).get("code").asInt()).isEqualTo(106001);

        // Ký với page_size=5 nhưng gửi page_size=6
        HttpResponse<String> signed = signedRaw("GET", CONVERSATIONS, Map.of("page_size", "5"), null, token,
                Instant.now().getEpochSecond());
        assertThat(signed.statusCode()).isEqualTo(200);
        String tampered = signed.request().uri().toString().replace("page_size=5", "page_size=6");
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(java.net.URI.create(tampered))
                .header("x-tts-access-token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json.readTree(r.body()).get("code").asInt()).isEqualTo(106001);
    }

    @Test
    void unknownAppKeyIsRejected() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri(CONVERSATIONS + "?app_key=other&timestamp="
                + Instant.now().getEpochSecond() + "&sign=x")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json.readTree(r.body()).get("code").asInt()).isEqualTo(106001);
    }

    @Test
    void timestampOutsideFiveMinutesIsRejected() throws Exception {
        HttpResponse<String> r = signedRaw("GET", CONVERSATIONS, Map.of("page_size", "5"), null, accessToken(),
                Instant.now().minusSeconds(600).getEpochSecond());
        assertThat(json.readTree(r.body()).get("code").asInt()).isEqualTo(106002);
    }

    @Test
    void missingOrUnknownAccessTokenIsInvalid() throws Exception {
        assertThat(get(CONVERSATIONS, Map.of("page_size", "5"), null).get("code").asInt()).isEqualTo(105001);
        assertThat(get(CONVERSATIONS, Map.of("page_size", "5"), "TTP_nope").get("code").asInt()).isEqualTo(105001);
    }

    @Test
    void expiredAccessTokenIsReportedAsExpired() throws Exception {
        String token = accessToken();
        post("/sim/tokens/expire?kind=access", "{}");
        JsonNode r = get(CONVERSATIONS, Map.of("page_size", "5"), token);
        assertThat(r.get("code").asInt()).isEqualTo(105002);
        assertThat(r.get("request_id").asString()).isNotBlank();
    }

    @Test
    void revokedScopeIsNotAuthorizedButOtherScopesStillWork() throws Exception {
        String token = accessToken();
        post("/sim/faults", "{\"revoked_scopes\":[\"seller.customer_service\"]}");
        assertThat(get(CONVERSATIONS, Map.of("page_size", "5"), token).get("code").asInt()).isEqualTo(105005);
        assertThat(get("/event/202309/webhooks", Map.of(), token).get("code").asInt()).isZero();
    }

    @Test
    void wrongShopCipherIsRejected() throws Exception {
        JsonNode r = get(CONVERSATIONS, Map.of("page_size", "5", "shop_cipher", "ROW_other"), accessToken());
        assertThat(r.get("code").asInt()).isEqualTo(106011);
    }
}

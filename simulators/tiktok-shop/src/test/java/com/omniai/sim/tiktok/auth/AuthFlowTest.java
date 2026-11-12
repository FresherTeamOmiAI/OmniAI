package com.omniai.sim.tiktok.auth;

import com.omniai.sim.tiktok.SimTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** OAuth2: ủy quyền → đổi code → refresh, và các lỗi token. */
class AuthFlowTest extends SimTestSupport {

    @Test
    void consentPageLinksToApproveAndApproveRedirectsWithCodeAndState() throws Exception {
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(uri("/oauth/authorize?app_key=" + APP_KEY + "&state=xyz"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.body()).contains("/oauth/authorize/approve").contains("seller.customer_service");

        HttpResponse<String> redirect = http.send(HttpRequest.newBuilder(
                uri("/oauth/authorize/approve?app_key=" + APP_KEY + "&state=xyz")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(redirect.statusCode()).isEqualTo(302);
        Map<String, String> q = query(redirect.headers().firstValue("Location").orElseThrow());
        assertThat(q.get("state")).isEqualTo("xyz");
        assertThat(q.get("code")).startsWith("ROW_");
    }

    @Test
    void tokenHasSameFieldsAsFixture() throws Exception {
        JsonNode data = authorize();
        JsonNode fixture = json.readTree(new java.io.File("../../shared/channel-fixtures/tiktok/token-get.response.json"));
        assertThat(fieldNames(data)).containsExactlyInAnyOrderElementsOf(fieldNames(fixture.get("data")));
        assertThat(data.get("access_token_expire_in").asLong()).isGreaterThan(java.time.Instant.now().getEpochSecond());
    }

    @Test
    void authCodeIsSingleUse() throws Exception {
        HttpResponse<String> redirect = http.send(HttpRequest.newBuilder(
                uri("/oauth/authorize/approve?app_key=" + APP_KEY + "&state=s")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String code = query(redirect.headers().firstValue("Location").orElseThrow()).get("code");
        String url = "/api/v2/token/get?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET + "&auth_code=" + code
                + "&grant_type=authorized_code";
        assertThat(getJson(url).get("code").asInt()).isZero();
        assertThat(getJson(url).get("code").asInt()).isEqualTo(105004);
    }

    @Test
    void wrongSecretOrGrantTypeIsRejected() throws Exception {
        assertThat(getJson("/api/v2/token/get?app_key=" + APP_KEY + "&app_secret=nope&auth_code=x&grant_type=authorized_code")
                .get("code").asInt()).isEqualTo(106001);
        assertThat(getJson("/api/v2/token/get?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET + "&auth_code=x&grant_type=password")
                .get("code").asInt()).isEqualTo(36009004);
        assertThat(getJson("/api/v2/token/get?app_key=" + APP_KEY).get("code").asInt()).isEqualTo(36009004);
    }

    @Test
    void refreshRotatesTokensAndOldAccessTokenStopsWorking() throws Exception {
        JsonNode first = authorize();
        JsonNode refreshed = getJson("/api/v2/token/refresh?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET
                + "&refresh_token=" + first.get("refresh_token").asString() + "&grant_type=refresh_token");
        assertThat(refreshed.get("code").asInt()).isZero();
        String newToken = refreshed.get("data").get("access_token").asString();
        assertThat(newToken).isNotEqualTo(first.get("access_token").asString());

        assertThat(get("/authorization/202309/shops", Map.of(), first.get("access_token").asString())
                .get("code").asInt()).isEqualTo(105001);
        assertThat(get("/authorization/202309/shops", Map.of(), newToken).get("code").asInt()).isZero();

        // refresh token cũ cũng đã bị thu hồi
        assertThat(getJson("/api/v2/token/refresh?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET
                + "&refresh_token=" + first.get("refresh_token").asString() + "&grant_type=refresh_token")
                .get("code").asInt()).isEqualTo(105003);
    }

    @Test
    void expiredRefreshTokenRequiresReauthorization() throws Exception {
        JsonNode grant = authorize();
        post("/sim/tokens/expire?kind=refresh", "{}");
        assertThat(getJson("/api/v2/token/refresh?app_key=" + APP_KEY + "&app_secret=" + APP_SECRET
                + "&refresh_token=" + grant.get("refresh_token").asString() + "&grant_type=refresh_token")
                .get("code").asInt()).isEqualTo(105003);
    }

    @Test
    void authorizedShopsMatchesFixtureShape() throws Exception {
        JsonNode response = get("/authorization/202309/shops", Map.of(), accessToken());
        JsonNode fixture = json.readTree(new java.io.File("../../shared/channel-fixtures/tiktok/get-authorized-shops.response.json"));
        assertThat(fieldNames(response)).containsExactlyInAnyOrderElementsOf(fieldNames(fixture));
        JsonNode shop = response.get("data").get("shops").get(0);
        assertThat(fieldNames(shop)).containsExactlyInAnyOrderElementsOf(fieldNames(fixture.get("data").get("shops").get(0)));
        assertThat(shop.get("cipher").asString()).isEqualTo(CIPHER);
        assertThat(shop.get("id").asString()).isEqualTo(SHOP_ID);
    }

    static java.util.List<String> fieldNames(JsonNode node) {
        return node.propertyNames().stream().toList();
    }
}

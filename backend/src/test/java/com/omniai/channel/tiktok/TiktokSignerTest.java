package com.omniai.channel.tiktok;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Cùng test vector với simulator (simulators/tiktok-shop/.../TiktokSignerTest): hai bên phải ký giống nhau. */
class TiktokSignerTest {

    static final String SECRET = "testsecret";
    static final Map<String, String> QUERY = Map.of(
            "app_key", "38abcd",
            "timestamp", "1623812664",
            "shop_cipher", "GCP_XF90igAAAABh00qsWgtvOiGFNqyubMt3");

    @Test
    void getRequestIgnoresSignAndAccessToken() {
        Map<String, String> query = new HashMap<>(QUERY);
        query.put("page_size", "10");
        query.put("sign", "ignored");
        query.put("access_token", "ignored");
        assertThat(TiktokSigner.signRequest("/customer_service/202309/conversations", query, null, SECRET))
                .isEqualTo("20151e052ca171e0e6d8be696f67843e7e02e2e5354cc42d0476621cd4bed5f2");
    }

    @Test
    void postRequestIncludesRawBody() {
        String body = "{\"type\":\"TEXT\",\"content\":\"{\\\"content\\\":\\\"hello\\\"}\"}";
        assertThat(TiktokSigner.signRequest(
                "/customer_service/202606/conversations/7494560109732334262/messages", QUERY, body, SECRET))
                .isEqualTo("cb7a592f0abf25ce6b4f8fbbb78766e3fc1d642e4ff81b359a7ed305849b02f7");
    }

    @Test
    void webhookSignatureIsHmacOfAppKeyPlusBody() {
        assertThat(TiktokSigner.signWebhook("38abcd", "{\"type\":14,\"tts_notification_id\":\"1\"}", SECRET))
                .isEqualTo("093afc4842d1a2679314722bfe0942f02a2612e39ff72f53a28bdad7a17839c3");
    }

    @Test
    void matchesToleratesCaseAndWhitespaceButRejectsMissing() {
        assertThat(TiktokSigner.matches("abc", " ABC ")).isTrue();
        assertThat(TiktokSigner.matches("abc", "abd")).isFalse();
        assertThat(TiktokSigner.matches("abc", null)).isFalse();
    }
}

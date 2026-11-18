package com.omniai.sim.tiktok.ui;

import com.omniai.sim.tiktok.SimTestSupport;
import com.omniai.sim.tiktok.common.TiktokSigner;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Trang kiểm tra trực quan /ui và API /sim/ui/* của nó. */
class UiTest extends SimTestSupport {

    @Test
    void homeRedirectsToStaticPage() throws Exception {
        HttpResponse<String> root = http.send(HttpRequest.newBuilder(uri("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(root.statusCode()).isEqualTo(302);
        assertThat(root.headers().firstValue("Location")).hasValue("/ui/index.html");

        HttpResponse<String> page = http.send(HttpRequest.newBuilder(uri("/ui/index.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("TikTok Shop", "ui.js");
        HttpResponse<String> js = http.send(HttpRequest.newBuilder(uri("/ui/ui.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(js.statusCode()).isEqualTo(200);
    }

    @Test
    void logsHubCallsIncludingRejectedOnes() throws Exception {
        String token = accessToken();
        String conversationId = buyer("u-ui-1", "Chào shop").get("conversation_id").asString();
        sendText(conversationId, "Dạ shop chào bạn", token);
        // sai chữ ký: cổng chặn nhưng vẫn phải có trong log
        http.send(HttpRequest.newBuilder(uri("/customer_service/202309/conversations?app_key=" + APP_KEY
                        + "&timestamp=" + Instant.now().getEpochSecond() + "&shop_cipher=" + CIPHER + "&sign=bad"))
                .header("x-tts-access-token", token).GET().build(), HttpResponse.BodyHandlers.ofString());

        JsonNode calls = getJson("/sim/ui/api-calls");
        JsonNode send = find(calls, "/customer_service/202606/conversations/" + conversationId + "/messages");
        assertThat(send.get("method").asString()).isEqualTo("POST");
        assertThat(send.get("http_status").asInt()).isEqualTo(200);
        assertThat(send.get("code").asInt()).isZero();
        assertThat(send.get("request_body").asString()).contains("ROBOT", "Dạ shop chào bạn");
        assertThat(send.get("response_body").asString()).contains("message_id");

        JsonNode rejected = find(calls, "/customer_service/202309/conversations");
        assertThat(rejected.get("code").asInt()).isNotZero();
        JsonNode tokenCall = find(calls, "/api/v2/token/get");
        assertThat(tokenCall.get("code").asInt()).isZero();
        assertThat(tokenCall.get("query").asString()).contains("app_secret=***").doesNotContain(APP_SECRET);

        long newest = calls.get(0).get("id").asLong();
        assertThat(getJson("/sim/ui/api-calls?after=" + newest)).isEmpty();
        assertThat(getJson("/sim/ui/overview").at("/counts/api_errors").asInt()).isGreaterThanOrEqualTo(1);

        post("/sim/reset", "{}");
        assertThat(getJson("/sim/ui/api-calls")).isEmpty();
    }

    @Test
    void conversationsDumpKeepsMessageOrder() throws Exception {
        String token = accessToken();
        String conversationId = buyer("u-ui-2", "Serum giá bao nhiêu?").get("conversation_id").asString();
        sendText(conversationId, "Dạ 350.000đ ạ", token);
        buyer("u-ui-2", "Cảm ơn shop");

        JsonNode dump = getJson("/sim/ui/conversations");
        assertThat(dump).hasSize(1);
        JsonNode messages = dump.get(0).get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).at("/sender/role").asString()).isEqualTo("BUYER");
        assertThat(messages.get(1).at("/sender/role").asString()).isEqualTo("ROBOT");
        assertThat(messages.get(2).get("content").asString()).contains("Cảm ơn shop");
        assertThat(dump.get(0).get("shop_id").asString()).isEqualTo(SHOP_ID);

        JsonNode overview = getJson("/sim/ui/overview");
        assertThat(overview.at("/counts/conversations").asInt()).isEqualTo(1);
        assertThat(overview.at("/counts/messages").asInt()).isEqualTo(3);
        assertThat(overview.at("/tokens/0/access_token").asString()).endsWith("…");
        assertThat(overview.at("/shop/cipher").asString()).isEqualTo(CIPHER);
    }

    @Test
    void servesUploadedImageBytes() throws Exception {
        String token = accessToken();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        String uri = json.readTree(upload(png, token).body()).at("/data/uri").asString();

        HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(
                        uri("/sim/ui/images?uri=" + java.net.URLEncoder.encode(uri, StandardCharsets.UTF_8))).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(image.body()).isEqualTo(png);

        JsonNode call = find(getJson("/sim/ui/api-calls"), "/product/202309/images/upload");
        assertThat(call.get("request_body").asString()).startsWith("[multipart/form-data");

        HttpResponse<String> missing = http.send(HttpRequest.newBuilder(uri("/sim/ui/images?uri=none")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> upload(byte[] data, String token) throws Exception {
        String path = "/product/202309/images/upload";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("app_key", APP_KEY);
        query.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        query.put("sign", TiktokSigner.signRequest(path, query, null, APP_SECRET));
        String boundary = "----omniai" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\"a.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(uri(path + "?" + encode(query)))
                .header("content-type", "multipart/form-data; boundary=" + boundary)
                .header("x-tts-access-token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode find(JsonNode calls, String path) {
        for (JsonNode c : calls) {
            if (c.get("path").asString().equals(path)) {
                return c;
            }
        }
        throw new AssertionError("Không thấy lời gọi " + path + " trong " + calls);
    }
}

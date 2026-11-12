package com.omniai.sim.tiktok.event;

import com.omniai.sim.tiktok.SimTestSupport;
import com.omniai.sim.tiktok.common.TiktokSigner;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Webhook: envelope + chữ ký, retry khi bên nhận lỗi/chậm, gửi trùng, đăng ký địa chỉ qua Event API. */
class WebhookTest extends SimTestSupport {

    private Received next() throws InterruptedException {
        Received r = RECEIVED.poll(5, TimeUnit.SECONDS);
        assertThat(r).as("không nhận được webhook").isNotNull();
        return r;
    }

    private List<Received> nextOfType(int type, int count) throws Exception {
        List<Received> out = new ArrayList<>();
        while (out.size() < count) {
            Received r = next();
            if (json.readTree(r.body()).get("type").asInt() == type) {
                out.add(r);
            }
        }
        return out;
    }

    @Test
    void buyerMessageSendsSignedNewConversationAndNewMessage() throws Exception {
        buyer("b1", "Shop ơi");
        List<Received> received = List.of(next(), next());
        List<Integer> types = new ArrayList<>();
        for (Received r : received) {
            assertThat(r.authorization()).isEqualTo(TiktokSigner.signWebhook(APP_KEY, r.body(), APP_SECRET));
            types.add(json.readTree(r.body()).get("type").asInt());
        }
        assertThat(types).containsExactlyInAnyOrder(13, 14);

        JsonNode newMessage = json.readTree(received.stream()
                .filter(r -> r.body().contains("\"type\":14")).findFirst().orElseThrow().body());
        JsonNode fixture = json.readTree(new File("../../shared/channel-fixtures/tiktok/webhook-new-message.json"));
        assertThat(newMessage.propertyNames()).containsExactlyInAnyOrderElementsOf(fixture.propertyNames());
        assertThat(newMessage.get("data").propertyNames())
                .containsExactlyInAnyOrderElementsOf(fixture.get("data").propertyNames());
        assertThat(newMessage.get("shop_id").asString()).isEqualTo(SHOP_ID);
        assertThat(newMessage.get("data").get("sender").get("role").asString()).isEqualTo("BUYER");
        assertThat(json.readTree(newMessage.get("data").get("content").asString()).get("content").asString())
                .isEqualTo("Shop ơi");
    }

    @Test
    void shopReplyAlsoFiresNewMessageWithShopRole() throws Exception {
        String id = buyer("b1", "hi").get("conversation_id").asString();
        nextOfType(14, 1);
        sendText(id, "Dạ shop chào bạn", accessToken());
        JsonNode echo = json.readTree(nextOfType(14, 1).getFirst().body());
        assertThat(echo.get("data").get("sender").get("role").asString()).isEqualTo("ROBOT");
    }

    @Test
    void retriesWhenReceiverFailsOrIsTooSlow() throws Exception {
        // Tạo hội thoại trước để tin sau chỉ còn 1 webhook NEW_MESSAGE
        buyer("b", "hi");
        await().atMost(Duration.ofSeconds(5)).until(() -> deliveries().size() == 2);
        RECEIVED.clear();

        REPLIES.add(new Reply(500, 0));     // lần 1: lỗi
        REPLIES.add(new Reply(200, 800));   // lần 2: quá timeout 500 ms
        // lần 3: mặc định 200 ngay
        String notificationId = buyer("b", "còn hàng không").get("tts_notification_id").asString();
        await().atMost(Duration.ofSeconds(10)).until(() -> deliveries().size() == 3);

        JsonNode delivery = deliveries().getFirst();
        assertThat(delivery.get("tts_notification_id").asString()).isEqualTo(notificationId);
        assertThat(delivery.get("delivered").asBoolean()).isTrue();
        JsonNode attempts = delivery.get("attempts");
        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).get("http_status").asInt()).isEqualTo(500);
        assertThat(attempts.get(1).get("error").asString()).isEqualTo("HttpTimeoutException");
        assertThat(attempts.get(2).get("http_status").asInt()).isEqualTo(200);
    }

    @Test
    void duplicateFaultSendsSameNotificationTwice() throws Exception {
        post("/sim/faults", "{\"webhook_duplicate\":true}");
        buyer("b1", "hi");
        List<Received> messages = nextOfType(14, 2);
        String id1 = json.readTree(messages.get(0).body()).get("tts_notification_id").asString();
        String id2 = json.readTree(messages.get(1).body()).get("tts_notification_id").asString();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void eventApiRegistersAndDeletesWebhookAddress() throws Exception {
        String token = accessToken();
        String alt = receiverUrl() + "/alt";
        JsonNode put = signed("PUT", "/event/202309/webhooks", Map.of(),
                json.writeValueAsString(Map.of("address", alt, "event_type", "NEW_MESSAGE")), token);
        assertThat(put.get("code").asInt()).isZero();

        JsonNode list = get("/event/202309/webhooks", Map.of(), token);
        JsonNode fixture = json.readTree(new File("../../shared/channel-fixtures/tiktok/get-shop-webhooks.response.json"));
        assertThat(list.get("data").get("total_count").asInt()).isEqualTo(1);
        JsonNode hook = list.get("data").get("webhooks").get(0);
        assertThat(hook.propertyNames()).containsExactlyInAnyOrderElementsOf(
                fixture.get("data").get("webhooks").get(0).propertyNames());
        assertThat(hook.get("address").asString()).isEqualTo(alt);
        assertThat(hook.get("create_time").asLong()).isGreaterThan(1_000_000_000_000L); // mili giây

        buyer("b1", "hi");
        await().atMost(Duration.ofSeconds(5)).until(() ->
                deliveries().stream().anyMatch(d -> d.get("event_type").asString().equals("NEW_MESSAGE")));
        assertThat(deliveries()).filteredOn(d -> d.get("event_type").asString().equals("NEW_MESSAGE"))
                .allSatisfy(d -> assertThat(d.get("address").asString()).isEqualTo(alt));

        assertThat(signed("DELETE", "/event/202309/webhooks", Map.of(),
                "{\"event_type\":\"NEW_MESSAGE\"}", token).get("code").asInt()).isZero();
        assertThat(get("/event/202309/webhooks", Map.of(), token).get("data").get("total_count").asInt()).isZero();
    }

    @Test
    void eventApiValidatesInput() throws Exception {
        String token = accessToken();
        assertThat(signed("PUT", "/event/202309/webhooks", Map.of(),
                "{\"address\":\"http://x\",\"event_type\":\"WHATEVER\"}", token).get("code").asInt()).isEqualTo(36009004);
        assertThat(signed("PUT", "/event/202309/webhooks", Map.of(),
                "{\"address\":\"ftp://x\",\"event_type\":\"NEW_MESSAGE\"}", token).get("code").asInt()).isEqualTo(36009004);
        assertThat(signed("PUT", "/event/202309/webhooks", Map.of(),
                json.writeValueAsString(Map.of("address", "http://" + "a".repeat(260), "event_type", "NEW_MESSAGE")), token)
                .get("code").asInt()).isEqualTo(36009004);
    }

    @Test
    void buyerCanWaitForShopReply() throws Exception {
        String token = accessToken();
        // Bên nhận webhook (vai hub) trả lời ngay khi nhận NEW_MESSAGE của buyer
        Thread responder = Thread.ofVirtual().start(() -> {
            try {
                Received r = nextOfType(14, 1).getFirst();
                String conversationId = json.readTree(r.body()).get("data").get("conversation_id").asString();
                sendText(conversationId, "Dạ shop đây ạ", token);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonNode result = post("/sim/buyer/messages", "{\"buyer_user_id\":\"b\",\"text\":\"hi\",\"wait_ms\":5000}");
        responder.join();
        assertThat(result.get("reply").get("sender").get("role").asString()).isEqualTo("ROBOT");
        assertThat(json.readTree(result.get("reply").get("content").asString()).get("content").asString())
                .isEqualTo("Dạ shop đây ạ");
        assertThat(result.get("reply_latency_ms").asLong()).isLessThan(5000);
    }

    private List<JsonNode> deliveries() throws Exception {
        List<JsonNode> out = new ArrayList<>();
        getJson("/sim/deliveries").forEach(out::add);
        return out;
    }
}

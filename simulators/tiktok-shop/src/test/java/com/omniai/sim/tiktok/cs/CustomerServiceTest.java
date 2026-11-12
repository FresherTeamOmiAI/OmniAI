package com.omniai.sim.tiktok.cs;

import com.omniai.sim.tiktok.SimTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Customer Service: phân trang, invalid_params, các lỗi nghiệp vụ, khớp fixture docv2. */
class CustomerServiceTest extends SimTestSupport {

    private static final String CONVERSATIONS = "/customer_service/202309/conversations";
    private static final File FIXTURES = new File("../../shared/channel-fixtures/tiktok");

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = accessToken();
    }

    private String messagesPath(String conversationId) {
        return CONVERSATIONS + "/" + conversationId + "/messages";
    }

    @Test
    void buyerMessageCreatesConversationWithUnreadCount() throws Exception {
        JsonNode sent = buyer("buyer-1", "Shop ơi tư vấn kem chống nắng");
        assertThat(sent.get("new_conversation").asBoolean()).isTrue();
        String conversationId = sent.get("conversation_id").asString();
        assertThat(buyer("buyer-1", "còn đó không shop").get("conversation_id").asString()).isEqualTo(conversationId);

        JsonNode page = get(CONVERSATIONS, Map.of("page_size", "20"), token).get("data");
        JsonNode c = page.get("conversations").get(0);
        assertThat(c.get("id").asString()).isEqualTo(conversationId);
        assertThat(c.get("unread_count").asInt()).isEqualTo(2);
        assertThat(c.get("latest_message").get("sender").get("role").asString()).isEqualTo("BUYER");
        assertThat(page.get("next_page_token").asString()).isEmpty();
    }

    @Test
    void conversationsPaginateWithoutLossOrDuplicates() throws Exception {
        for (int i = 0; i < 25; i++) {
            buyer("buyer-" + i, "xin chào " + i);
        }
        Set<String> seen = new HashSet<>();
        List<Integer> pageSizes = new ArrayList<>();
        String pageToken = "";
        do {
            Map<String, String> params = pageToken.isEmpty()
                    ? Map.of("page_size", "20")
                    : Map.of("page_size", "20", "page_token", pageToken);
            JsonNode data = get(CONVERSATIONS, params, token).get("data");
            pageSizes.add(data.get("conversations").size());
            data.get("conversations").forEach(c -> seen.add(c.get("id").asString()));
            pageToken = data.get("next_page_token").asString();
        } while (!pageToken.isEmpty());
        assertThat(pageSizes).containsExactly(20, 5);
        assertThat(seen).hasSize(25);
    }

    @Test
    void pageSizeAndPageTokenAreValidated() throws Exception {
        assertThat(get(CONVERSATIONS, Map.of(), token).get("code").asInt()).isEqualTo(45101002);
        assertThat(get(CONVERSATIONS, Map.of("page_size", "0"), token).get("code").asInt()).isEqualTo(45101002);
        assertThat(get(CONVERSATIONS, Map.of("page_size", "21"), token).get("code").asInt()).isEqualTo(45101002);
        assertThat(get(CONVERSATIONS, Map.of("page_size", "5", "page_token", "rác"), token).get("code").asInt())
                .isEqualTo(45101002);
        String id = buyer("b", "hi").get("conversation_id").asString();
        assertThat(get(messagesPath(id), Map.of("page_size", "11"), token).get("code").asInt()).isEqualTo(45101002);
        assertThat(get(messagesPath(id), Map.of("page_size", "5", "sort_field", "nickname"), token).get("code").asInt())
                .isEqualTo(45101002);
    }

    @Test
    void messagesSortAndPaginate() throws Exception {
        String id = null;
        for (int i = 0; i < 12; i++) {
            id = buyer("b", "tin " + i).get("conversation_id").asString();
        }
        JsonNode first = get(messagesPath(id), Map.of("page_size", "10", "sort_order", "ASC", "sort_field", "index"), token)
                .get("data");
        assertThat(first.get("messages")).hasSize(10);
        assertThat(first.get("messages").get(0).get("content").asString()).contains("tin 0");
        JsonNode second = get(messagesPath(id), Map.of("page_size", "10", "sort_order", "ASC", "sort_field", "index",
                "page_token", first.get("next_page_token").asString()), token).get("data");
        assertThat(second.get("messages")).hasSize(2);
        assertThat(second.get("next_page_token").asString()).isEmpty();

        JsonNode desc = get(messagesPath(id), Map.of("page_size", "1"), token).get("data");
        assertThat(desc.get("messages").get(0).get("content").asString()).contains("tin 11");
    }

    @Test
    void sendMessageStoresRobotReplyAndMatchesFixtures() throws Exception {
        String id = buyer("b", "hi").get("conversation_id").asString();
        JsonNode sent = sendText(id, "Dạ shop chào bạn", token);
        JsonNode fixture = json.readTree(new File(FIXTURES, "send-message.response.json"));
        assertThat(Fields.of(sent)).containsExactlyInAnyOrderElementsOf(Fields.of(fixture));
        assertThat(Fields.of(sent.get("data"))).containsExactlyInAnyOrderElementsOf(Fields.of(fixture.get("data")));

        JsonNode messages = get(messagesPath(id), Map.of("page_size", "10"), token);
        JsonNode latest = messages.get("data").get("messages").get(0);
        assertThat(latest.get("sender").get("role").asString()).isEqualTo("ROBOT");
        assertThat(latest.get("id").asString()).isEqualTo(sent.get("data").get("message_id").asString());

        // Mọi trường simulator trả đều có trong ví dụ docv2 (docv2 có thêm data/plaintext khi need_data=true)
        JsonNode docMessage = json.readTree(new File(FIXTURES, "get-conversation-messages.response.json"))
                .get("data").get("messages").get(0);
        assertThat(Fields.of(docMessage)).containsAll(Fields.of(latest));
        assertThat(Fields.of(docMessage.get("sender"))).containsAll(Fields.of(latest.get("sender")));
    }

    @Test
    void sendMessageRejectsInvalidParams() throws Exception {
        String path = "/customer_service/202606/conversations/" + buyer("b", "hi").get("conversation_id").asString() + "/messages";
        assertThat(signed("POST", path, Map.of(), "{\"type\":\"TEXT\"}", token).get("code").asInt()).isEqualTo(45101002);
        assertThat(signed("POST", path, Map.of(), "{\"content\":\"{}\"}", token).get("code").asInt()).isEqualTo(45101002);
        assertThat(signed("POST", path, Map.of(), "{\"type\":\"STICKER\",\"content\":\"{}\"}", token).get("code").asInt())
                .isEqualTo(45101002);
        assertThat(signed("POST", path, Map.of(), "{\"type\":\"TEXT\",\"content\":\"không phải json\"}", token)
                .get("code").asInt()).isEqualTo(45101002);
        assertThat(signed("POST", path, Map.of(), "{\"type\":\"TEXT\",\"content\":\"{}\",\"sender_role\":\"BUYER\"}", token)
                .get("code").asInt()).isEqualTo(45101002);
        assertThat(signed("POST", path, Map.of(), "{malformed", token).get("code").asInt()).isEqualTo(45101002);
        String id = path.split("/")[4];
        assertThat(sendText(id, "a".repeat(2001), token).get("code").asInt()).isEqualTo(45101002);
        assertThat(sendText(id, "a".repeat(2000), token).get("code").asInt()).isZero();
    }

    @Test
    void businessErrors() throws Exception {
        String id = buyer("b", "hi").get("conversation_id").asString();
        assertThat(sendText("123", "x", token).get("code").asInt()).isEqualTo(45101003);

        post("/sim/faults", "{\"sensitive_words\":[\"zalo\"]}");
        assertThat(sendText(id, "Bạn nhắn Zalo shop nhé", token).get("code").asInt()).isEqualTo(45101006);

        post("/sim/faults", "{\"block_sending\":true}");
        assertThat(sendText(id, "hello", token).get("code").asInt()).isEqualTo(45109001);
    }

    @Test
    void createConversationOnlyForKnownBuyerAndMarkReadResetsUnread() throws Exception {
        String body = json.writeValueAsString(Map.of("buyer_user_id", "stranger"));
        assertThat(signed("POST", CONVERSATIONS, Map.of(), body, token).get("code").asInt()).isEqualTo(45109003);
        assertThat(signed("POST", CONVERSATIONS, Map.of(), "{}", token).get("code").asInt()).isEqualTo(45101002);

        String id = buyer("known", "hi").get("conversation_id").asString();
        JsonNode created = signed("POST", CONVERSATIONS, Map.of(), json.writeValueAsString(Map.of("buyer_user_id", "known")), token);
        assertThat(created.get("data").get("conversation_id").asString()).isEqualTo(id);

        assertThat(signed("POST", messagesPath(id) + "/read", Map.of(), null, token).get("code").asInt()).isZero();
        JsonNode c = get(CONVERSATIONS, Map.of("page_size", "1"), token).get("data").get("conversations").get(0);
        assertThat(c.get("unread_count").asInt()).isZero();
    }

    /** Lấy tên trường của 1 object JSON. */
    static final class Fields {
        static List<String> of(JsonNode node) {
            return node.propertyNames().stream().toList();
        }
    }
}

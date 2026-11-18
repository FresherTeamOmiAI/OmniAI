package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.AuthorizedShops;
import com.omniai.channel.tiktok.TiktokModels.ConversationsPage;
import com.omniai.channel.tiktok.TiktokModels.MessagesPage;
import com.omniai.channel.tiktok.TiktokModels.NewMessageEvent;
import com.omniai.channel.tiktok.TiktokModels.SendMessageRequest;
import com.omniai.channel.tiktok.TiktokModels.SendMessageResponse;
import com.omniai.channel.tiktok.TiktokModels.TokenData;
import com.omniai.channel.tiktok.TiktokModels.WebhookEnvelope;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hub đọc được đúng các JSON mẫu trong shared/channel-fixtures/tiktok (chép từ docv2, hoặc ghi "chưa đối chiếu").
 * Simulator test chiều ngược lại: JSON nó sinh ra có cùng trường với fixture.
 */
class TiktokContractTest {

    private static final File FIXTURES = new File("../shared/channel-fixtures/tiktok");

    private static JsonNode fixture(String name) {
        return TiktokModels.JSON.readTree(new File(FIXTURES, name));
    }

    private static <T> T data(String name, Class<T> type) {
        return TiktokModels.JSON.treeToValue(fixture(name).get("data"), type);
    }

    @Test
    void readsDocv2Responses() {
        MessagesPage page = data("get-conversation-messages.response.json", MessagesPage.class);
        assertThat(page.nextPageToken()).isEqualTo("162312320");
        assertThat(page.messages().getFirst().sender().role()).isEqualTo("BUYER");
        assertThat(page.messages().getFirst().isVisible()).isTrue();
        assertThat(page.messages().getFirst().createTime()).isEqualTo(1691411573L);

        ConversationsPage conversations = data("get-conversations.response.json", ConversationsPage.class);
        assertThat(conversations.conversations().getFirst().participants().getFirst().imUserId())
                .isEqualTo("7494560109732334261");
        assertThat(conversations.conversations().getFirst().canSendMessage()).isTrue();

        assertThat(data("send-message.response.json", SendMessageResponse.class).messageId())
                .isEqualTo("7494560109732334261");
        assertThat(data("get-authorized-shops.response.json", AuthorizedShops.class).shops().getFirst().cipher())
                .isEqualTo("GCP_XF90igAAAABh00qsWgtvOiGFNqyubMt3");

        TokenData token = data("token-get.response.json", TokenData.class);
        assertThat(token.grantedScopes()).contains("seller.customer_service");
        assertThat(token.accessTokenExpireIn()).isPositive();
    }

    @Test
    void sendMessageRequestSerializesLikeDocv2Example() {
        JsonNode expected = fixture("send-message.request.json");
        JsonNode actual = TiktokModels.JSON.valueToTree(
                new SendMessageRequest("TEXT", "{\"content\": \"test\"}", "CUSTOMER_SERVICE"));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void webhookFixtureBecomesCanonicalMessage() {
        WebhookEnvelope envelope = TiktokModels.JSON.treeToValue(fixture("webhook-new-message.json"), WebhookEnvelope.class);
        assertThat(envelope.type()).isEqualTo(TiktokWebhookType.NEW_MESSAGE);
        NewMessageEvent event = TiktokModels.JSON.treeToValue(envelope.data(), NewMessageEvent.class);
        assertThat(TiktokChatAdapter.isBuyerText(event)).isTrue();

        CanonicalMessage m = new TiktokChatAdapter().toCanonical(
                new TiktokChatAdapter.Inbound(envelope.shopId(), envelope.ttsNotificationId(), event));
        assertThat(m.channel()).isEqualTo(ChannelType.TIKTOK);
        assertThat(m.messageId()).isEqualTo("7494560109732334263");
        assertThat(m.channelUserId()).isEqualTo("7494560109732334262");
        assertThat(m.conversationId()).isEqualTo("tiktok:7494560109732334261");
        assertThat(m.text()).isEqualTo("Shop ơi, da dầu nên dùng kem chống nắng nào?");
        assertThat(m.receivedAt()).isEqualTo(Instant.ofEpochSecond(1762845600L));
        assertThat(m.metadata()).containsEntry("shopId", "7494049642642441621")
                .containsEntry("notificationId", "7327112393057371910");
    }

    @Test
    void onlyVisibleBuyerTextIsAnswered() {
        TiktokModels.Sender buyer = new TiktokModels.Sender("1", "BUYER", null, null);
        TiktokModels.Sender robot = new TiktokModels.Sender("2", "ROBOT", null, null);
        TiktokModels.Sender shop = new TiktokModels.Sender("2", "SHOP", null, null);
        assertThat(TiktokChatAdapter.isBuyerText(event("TEXT", true, buyer))).isTrue();
        assertThat(TiktokChatAdapter.isBuyerText(event("TEXT", true, robot))).isFalse();
        assertThat(TiktokChatAdapter.isBuyerText(event("TEXT", true, shop))).isFalse();
        assertThat(TiktokChatAdapter.isBuyerText(event("IMAGE", true, buyer))).isFalse();
        assertThat(TiktokChatAdapter.isBuyerText(event("TEXT", false, buyer))).isFalse();
        assertThat(TiktokChatAdapter.isBuyerText(event("TEXT", true, null))).isFalse();
    }

    @Test
    void brokenContentBecomesEmptyText() {
        assertThat(TiktokChatAdapter.textOf("không phải json")).isEmpty();
        assertThat(TiktokChatAdapter.textOf(null)).isEmpty();
        assertThat(TiktokChatAdapter.textOf("{\"content\":\"hi\"}")).isEqualTo("hi");
    }

    private static NewMessageEvent event(String type, boolean visible, TiktokModels.Sender sender) {
        return new NewMessageEvent("m1", "c1", type, "{\"content\":\"hi\"}", 1L, visible, "1", sender);
    }
}

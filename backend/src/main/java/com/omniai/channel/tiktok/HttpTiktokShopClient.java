package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.Conversation;
import com.omniai.channel.tiktok.TiktokModels.ConversationsPage;
import com.omniai.channel.tiktok.TiktokModels.Message;
import com.omniai.channel.tiktok.TiktokModels.MessagesPage;
import com.omniai.channel.tiktok.TiktokModels.SendMessageRequest;
import com.omniai.channel.tiktok.TiktokModels.SendMessageResponse;
import com.omniai.channel.tiktok.TiktokModels.UpdateWebhookRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Path và version theo docv2 (docs/channel-api/tiktok.md mục 7). Mọi call đi qua {@link TiktokTokenManager}. */
@Component
public class HttpTiktokShopClient implements TiktokShopClient {

    static final String CONVERSATIONS = "/customer_service/202309/conversations";
    static final String SEND_MESSAGE = "/customer_service/202606/conversations/%s/messages";
    static final String MESSAGES = "/customer_service/202309/conversations/%s/messages";
    static final String MARK_READ = "/customer_service/202309/conversations/%s/messages/read";
    static final String WEBHOOKS = "/event/202309/webhooks";
    /** Giới hạn page_size theo docv2. */
    static final int CONVERSATIONS_PAGE = 20;
    static final int MESSAGES_PAGE = 10;
    /** Chặn vòng lặp vô hạn nếu sàn trả next_page_token lỗi. */
    private static final int MAX_PAGES = 1_000;

    private final TiktokApi api;
    private final TiktokTokenManager tokens;

    public HttpTiktokShopClient(TiktokApi api, TiktokTokenManager tokens) {
        this.api = api;
        this.tokens = tokens;
    }

    @Override
    public String sendText(String shopId, String conversationId, String text) {
        String content = TiktokModels.JSON.writeValueAsString(Map.of("content", text));
        SendMessageRequest body = new SendMessageRequest("TEXT", content, "ROBOT");
        return tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, SEND_MESSAGE.formatted(conversationId),
                Map.of(), body, c.getAccessToken(), c.getShopCipher(), SendMessageResponse.class)).messageId();
    }

    @Override
    public List<Message> listMessages(String shopId, String conversationId) {
        return allPages(pageToken -> {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("page_size", Integer.toString(MESSAGES_PAGE));
            params.put("sort_field", "index");
            params.put("sort_order", "ASC");
            putToken(params, pageToken);
            MessagesPage page = tokens.withCredential(shopId, c -> api.call(HttpMethod.GET,
                    MESSAGES.formatted(conversationId), params, null, c.getAccessToken(), c.getShopCipher(),
                    MessagesPage.class));
            return new Page<>(page.messages(), page.nextPageToken());
        });
    }

    @Override
    public List<Conversation> listConversations(String shopId) {
        return allPages(pageToken -> {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("page_size", Integer.toString(CONVERSATIONS_PAGE));
            putToken(params, pageToken);
            ConversationsPage page = tokens.withCredential(shopId, c -> api.call(HttpMethod.GET, CONVERSATIONS,
                    params, null, c.getAccessToken(), c.getShopCipher(), ConversationsPage.class));
            return new Page<>(page.conversations(), page.nextPageToken());
        });
    }

    @Override
    public void markRead(String shopId, String conversationId) {
        tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, MARK_READ.formatted(conversationId),
                Map.of(), null, c.getAccessToken(), c.getShopCipher(), Void.class));
    }

    @Override
    public void registerWebhooks(String shopId, String address, List<String> eventTypes) {
        for (String eventType : eventTypes) {
            tokens.withCredential(shopId, c -> api.call(HttpMethod.PUT, WEBHOOKS, Map.of(),
                    new UpdateWebhookRequest(address, eventType), c.getAccessToken(), c.getShopCipher(), Void.class));
        }
    }

    private record Page<T>(List<T> items, String nextPageToken) {
    }

    private static void putToken(Map<String, String> params, String pageToken) {
        if (pageToken != null && !pageToken.isEmpty()) {
            params.put("page_token", pageToken);
        }
    }

    /** Đi theo next_page_token tới khi rỗng (docv2: hết trang thì next_page_token = ""). */
    private static <T> List<T> allPages(Function<String, Page<T>> fetch) {
        List<T> all = new ArrayList<>();
        String token = "";
        for (int i = 0; i < MAX_PAGES; i++) {
            Page<T> page = fetch.apply(token);
            if (page.items() != null) {
                all.addAll(page.items());
            }
            token = page.nextPageToken();
            if (token == null || token.isEmpty()) {
                return all;
            }
        }
        throw new IllegalStateException("Quá " + MAX_PAGES + " trang, next_page_token có thể bị lặp");
    }
}

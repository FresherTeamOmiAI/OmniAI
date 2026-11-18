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
    static final String IMAGE_UPLOAD = "/product/202309/images/upload";
    static final String CREATE_PRODUCT = "/product/202309/products";
    static final String EDIT_PRODUCT = "/product/202509/products/%s";
    static final String UPDATE_PRICE = "/product/202309/products/%s/prices/update";
    static final String UPDATE_INVENTORY = "/product/202309/products/%s/inventory/update";
    static final String ACTIVATE = "/product/202309/products/activate";
    static final String DEACTIVATE = "/product/202309/products/deactivate";
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

    // ---------- Products (US-50) ----------

    @Override
    public String uploadProductImage(String shopId, byte[] data, String fileName) {
        return tokens.withCredential(shopId, c -> api.upload(IMAGE_UPLOAD, data, fileName,
                Map.of("use_case", "MAIN_IMAGE"), c.getAccessToken(), TiktokModels.UploadedImage.class)).uri();
    }

    @Override
    public TiktokModels.ProductResult createProduct(String shopId, TiktokModels.ProductBody body) {
        return tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, CREATE_PRODUCT, Map.of(), body,
                c.getAccessToken(), c.getShopCipher(), TiktokModels.ProductResult.class));
    }

    @Override
    public TiktokModels.ProductResult editProduct(String shopId, String productId, TiktokModels.ProductBody body) {
        return tokens.withCredential(shopId, c -> api.call(HttpMethod.PUT, EDIT_PRODUCT.formatted(productId), Map.of(),
                body, c.getAccessToken(), c.getShopCipher(), TiktokModels.ProductResult.class));
    }

    @Override
    public void setProductActive(String shopId, String productId, boolean active) {
        tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, active ? ACTIVATE : DEACTIVATE, Map.of(),
                new TiktokModels.ProductIdsBody(List.of(productId)), c.getAccessToken(), c.getShopCipher(), Void.class));
    }

    @Override
    public void updatePrice(String shopId, String productId, String skuId, long amount) {
        var sku = new TiktokModels.ProductSku(skuId, null,
                new TiktokModels.ProductPrice(Long.toString(amount), "VND"), null);
        tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, UPDATE_PRICE.formatted(productId), Map.of(),
                new TiktokModels.SkuUpdateBody(List.of(sku)), c.getAccessToken(), c.getShopCipher(), Void.class));
    }

    @Override
    public void updateInventory(String shopId, String productId, String skuId, String warehouseId, int quantity) {
        var sku = new TiktokModels.ProductSku(skuId, null, null,
                List.of(new TiktokModels.ProductInventory(warehouseId, quantity)));
        tokens.withCredential(shopId, c -> api.call(HttpMethod.POST, UPDATE_INVENTORY.formatted(productId), Map.of(),
                new TiktokModels.SkuUpdateBody(List.of(sku)), c.getAccessToken(), c.getShopCipher(), Void.class));
    }
}

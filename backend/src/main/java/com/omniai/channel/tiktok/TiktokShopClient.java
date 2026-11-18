package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.Conversation;
import com.omniai.channel.tiktok.TiktokModels.Message;

import java.util.List;

/**
 * Hub gửi dữ liệu ra TikTok Shop (Customer Service, Event). Bản duy nhất là {@link HttpTiktokShopClient}, gọi theo schema
 * thật tới {@code omniai.tiktok.base-url} (S2: simulator, decisions #22). Interface để test mock và để thêm Products,
 * Orders, Promotion ở US-50..53.
 */
public interface TiktokShopClient {

    /** Send Message (202606), type TEXT, sender_role ROBOT. Trả message_id. */
    String sendText(String shopId, String conversationId, String text);

    /** Get Conversation Messages, đi hết các trang, sắp tăng dần theo index. */
    List<Message> listMessages(String shopId, String conversationId);

    /** Get Conversations, đi hết các trang. */
    List<Conversation> listConversations(String shopId);

    /** Mark Buyer Messages as Read. */
    void markRead(String shopId, String conversationId);

    /** Update Shop Webhook cho từng event_type. */
    void registerWebhooks(String shopId, String address, List<String> eventTypes);

    // ---------- Products (US-50) ----------

    /** Upload Product Image (multipart, use_case MAIN_IMAGE). Trả uri để dùng trong main_images. */
    String uploadProductImage(String shopId, byte[] data, String fileName);

    /** Create Product (202309). */
    TiktokModels.ProductResult createProduct(String shopId, TiktokModels.ProductBody body);

    /** Edit Product (202509): thay toàn bộ nội dung. */
    TiktokModels.ProductResult editProduct(String shopId, String productId, TiktokModels.ProductBody body);

    /** Activate (true) / Deactivate (false) Products. */
    void setProductActive(String shopId, String productId, boolean active);

    /** Update Price (202309), VND. */
    void updatePrice(String shopId, String productId, String skuId, long amount);

    /** Update Inventory (202309), 1 kho. */
    void updateInventory(String shopId, String productId, String skuId, String warehouseId, int quantity);
}

package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.ProductBody;
import com.omniai.channel.tiktok.TiktokModels.ProductImage;
import com.omniai.channel.tiktok.TiktokModels.ProductInventory;
import com.omniai.channel.tiktok.TiktokModels.ProductPrice;
import com.omniai.channel.tiktok.TiktokModels.ProductResult;
import com.omniai.channel.tiktok.TiktokModels.ProductSku;
import com.omniai.channel.tiktok.TiktokModels.ProductWeight;
import com.omniai.channel.tiktok.TiktokTokenManager.NotAuthorizedException;
import com.omniai.sync.ProductChannelConnector;
import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncAspect;
import com.omniai.sync.SyncException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đẩy sản phẩm hub ra TikTok Shop (US-50, T-50.1, T-50.2) qua API Products theo docv2. Owner: TuTM.
 * <ul>
 *   <li>PRODUCT: tải ảnh từ Supabase rồi Upload Product Image, sau đó Create Product (lần đầu, có idempotency_key)
 *   hoặc Edit Product, rồi Activate/Deactivate theo trạng thái ẩn/hiện trên hub.</li>
 *   <li>PRICE: Update Price với giá của kênh TIKTOK. STOCK: Update Inventory, 1 kho.</li>
 * </ul>
 * Mã phía sàn lưu dạng {@code <product_id>:<sku_id>} (mỗi sản phẩm hub là 1 SKU trên sàn).
 */
@Component
@ConditionalOnProperty(prefix = "omniai.tiktok.products", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TiktokProductConnector implements ProductChannelConnector {

    static final String CHANNEL = "TIKTOK";
    private static final int MAX_TITLE = 255;
    private static final int MAX_IMAGES = 9;

    private final TiktokShopClient client;
    private final TiktokTokenManager tokens;
    private final TiktokProductProperties props;
    private final RestClient http;
    /** storage_key của ảnh → uri trên TikTok, để sửa sản phẩm không phải upload lại. Mất khi restart (chỉ tốn upload). */
    private final Map<String, String> uploaded = new ConcurrentHashMap<>();

    @Autowired
    public TiktokProductConnector(TiktokShopClient client, TiktokTokenManager tokens, TiktokProductProperties props) {
        this(client, tokens, props, RestClient.create());
    }

    TiktokProductConnector(TiktokShopClient client, TiktokTokenManager tokens, TiktokProductProperties props,
                           RestClient http) {
        this.client = client;
        this.tokens = tokens;
        this.props = props;
        this.http = http;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public Set<SyncAspect> aspects() {
        return Set.of(SyncAspect.PRODUCT, SyncAspect.PRICE, SyncAspect.STOCK);
    }

    /** Chỉ xếp hàng khi đã có shop ủy quyền, tránh cả catalog thành FAILED trước khi seller bấm Đồng ý. */
    @Override
    public boolean ready() {
        return tokens.activeShopId().isPresent();
    }

    @Override
    public String push(ProductSnapshot p, String externalId) {
        return call(() -> {
            String shopId = shopId();
            ExternalId ext = ExternalId.parse(externalId);
            ProductBody body = body(shopId, p, ext);
            ProductResult result = ext == null ? client.createProduct(shopId, body)
                    : client.editProduct(shopId, ext.productId(), body);
            if (result == null || result.productId() == null || result.skus() == null || result.skus().isEmpty()) {
                throw new SyncException("TikTok không trả product_id/sku id", true);
            }
            if (ext != null || !p.active()) {
                client.setProductActive(shopId, result.productId(), p.active());
            }
            return result.productId() + ":" + result.skus().get(0).id();
        });
    }

    @Override
    public void pushPrice(ProductSnapshot p, String externalId) {
        call(() -> {
            ExternalId ext = requireExternal(externalId);
            client.updatePrice(shopId(), ext.productId(), ext.skuId(), requirePrice(p));
            return null;
        });
    }

    @Override
    public void pushStock(ProductSnapshot p, String externalId) {
        call(() -> {
            ExternalId ext = requireExternal(externalId);
            client.updateInventory(shopId(), ext.productId(), ext.skuId(), props.warehouseId(), p.stock());
            return null;
        });
    }

    // ---------- dựng body ----------

    private ProductBody body(String shopId, ProductSnapshot p, ExternalId ext) {
        String categoryId = props.categories().get(p.category());
        if (categoryId == null) {
            throw new SyncException("Chưa map danh mục '" + p.category() + "' sang category_id TikTok "
                    + "(omniai.tiktok.products.categories)", false);
        }
        String title = p.name().length() > MAX_TITLE ? p.name().substring(0, MAX_TITLE) : p.name();
        ProductSku sku = new ProductSku(ext == null ? null : ext.skuId(), p.sku(),
                new ProductPrice(Long.toString(requirePrice(p)), "VND"),
                List.of(new ProductInventory(props.warehouseId(), p.stock())));
        int grams = (p.sizeMl() == null ? 0 : p.sizeMl()) + props.packagingGrams();
        return new ProductBody(title, description(p), categoryId, images(shopId, p), List.of(sku),
                new ProductWeight(Integer.toString(grams), "GRAM"), "LISTING",
                ext == null ? "omniai-product-" + p.productId() : null);
    }

    /** Mô tả HTML theo docv2: đoạn mô tả, hãng, thành phần INCI (đã escape). */
    static String description(ProductSnapshot p) {
        StringBuilder html = new StringBuilder();
        if (p.description() != null && !p.description().isBlank()) {
            html.append("<p>").append(HtmlUtils.htmlEscape(p.description())).append("</p>");
        }
        html.append("<p>Thương hiệu: ").append(HtmlUtils.htmlEscape(String.valueOf(p.brand()))).append("</p>");
        if (p.ingredientsInci() != null && !p.ingredientsInci().isBlank()) {
            html.append("<p>Thành phần: ").append(HtmlUtils.htmlEscape(p.ingredientsInci())).append("</p>");
        }
        return html.toString();
    }

    private List<ProductImage> images(String shopId, ProductSnapshot p) {
        if (p.imageUrls().isEmpty()) {
            throw new SyncException("Sản phẩm " + p.sku() + " chưa có ảnh công khai (thiếu ảnh hoặc SUPABASE_URL), "
                    + "TikTok bắt buộc main_images", false);
        }
        List<ProductImage> result = new ArrayList<>();
        for (int i = 0; i < Math.min(p.imageUrls().size(), MAX_IMAGES); i++) {
            String key = i < p.imageKeys().size() ? p.imageKeys().get(i) : p.imageUrls().get(i);
            String url = p.imageUrls().get(i);
            String uri = uploaded.computeIfAbsent(key, k -> {
                byte[] data = http.get().uri(url).retrieve().body(byte[].class);
                return client.uploadProductImage(shopId, data, fileName(url));
            });
            result.add(new ProductImage(uri));
        }
        return result;
    }

    private static String fileName(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        return name.isBlank() ? "image.webp" : name;
    }

    // ---------- tiện ích ----------

    private String shopId() {
        return tokens.activeShopId()
                .orElseThrow(() -> new SyncException("Chưa có shop TikTok nào ủy quyền ACTIVE", false));
    }

    private static long requirePrice(ProductSnapshot p) {
        if (p.price() == null || p.price() <= 0) {
            throw new SyncException("Sản phẩm " + p.sku() + " chưa có giá kênh TIKTOK", false);
        }
        return p.price();
    }

    private static ExternalId requireExternal(String externalId) {
        ExternalId ext = ExternalId.parse(externalId);
        if (ext == null) {
            throw new SyncException("Mã TikTok không hợp lệ: " + externalId, false);
        }
        return ext;
    }

    /** {@code <product_id>:<sku_id>}. */
    record ExternalId(String productId, String skuId) {
        static ExternalId parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            int i = raw.indexOf(':');
            if (i <= 0 || i == raw.length() - 1) {
                throw new SyncException("Mã TikTok phải có dạng <product_id>:<sku_id>, đang là " + raw, false);
            }
            return new ExternalId(raw.substring(0, i), raw.substring(i + 1));
        }
    }

    private interface Call<T> {
        T run();
    }

    /** Đổi lỗi TikTok sang SyncException: lỗi tạm thời thì thử lại, lỗi dữ liệu / quyền thì không. */
    private static <T> T call(Call<T> body) {
        try {
            return body.run();
        } catch (TiktokApiException e) {
            boolean transientError = e.isRateLimited() || e.is(TiktokErrorCode.INTERNAL_ERROR) || e.httpStatus() >= 500
                    || e.code() == -1;
            throw new SyncException(e.getMessage(), transientError, e);
        } catch (NotAuthorizedException e) {
            throw new SyncException(e.getMessage(), false, e);
        }
    }
}

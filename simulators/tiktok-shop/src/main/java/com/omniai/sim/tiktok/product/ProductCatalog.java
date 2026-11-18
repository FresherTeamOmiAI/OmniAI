package com.omniai.sim.tiktok.product;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.Ids;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.product.ProductModels.Image;
import com.omniai.sim.tiktok.product.ProductModels.Inventory;
import com.omniai.sim.tiktok.product.ProductModels.ProductRequest;
import com.omniai.sim.tiktok.product.ProductModels.ProductResponse;
import com.omniai.sim.tiktok.product.ProductModels.Sku;
import com.omniai.sim.tiktok.product.ProductModels.SkuRef;
import com.omniai.sim.tiktok.product.ProductModels.StoredProduct;
import com.omniai.sim.tiktok.product.ProductModels.StoredSku;
import com.omniai.sim.tiktok.product.ProductModels.UploadedImage;
import com.omniai.sim.tiktok.product.ProductModels.Weight;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sản phẩm của shop giả lập, giữ trong bộ nhớ (simulator không có DB, decisions #28). Kiểm dữ liệu theo các mã lỗi
 * docv2 của Create/Edit Product, Update Price, Update Inventory; mỗi sản phẩm 1 hoặc nhiều SKU, 1 kho.
 */
@Component
public class ProductCatalog {

    static final String VND = "VND";
    private static final int MAX_DESCRIPTION = 10_000;
    private static final int MAX_TITLE = 255;

    private final SimProperties.Products config;
    private final Clock clock;
    private final Map<String, StoredProduct> products = new ConcurrentHashMap<>();
    private final Map<String, String> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, UploadedImage> images = new ConcurrentHashMap<>();

    public ProductCatalog(SimProperties props, Clock clock) {
        this.config = props.products();
        this.clock = clock;
    }

    // ---------- ảnh ----------

    public UploadedImage upload(byte[] data, String useCase) {
        if (data == null || data.length == 0) {
            throw new ApiException(TiktokErrorCode.IMAGE_INVALID_FILE, "Zero-byte files are rejected.");
        }
        if (data.length > 10 * 1024 * 1024) {
            throw new ApiException(TiktokErrorCode.IMAGE_INVALID_FILE, "Max size: 10MB.");
        }
        String kind = useCase == null || useCase.isBlank() ? "MAIN_IMAGE" : useCase;
        if (!List.of("MAIN_IMAGE", "ATTRIBUTE_IMAGE", "DESCRIPTION_IMAGE").contains(kind)) {
            throw new ApiException(TiktokErrorCode.IMAGE_INVALID_PARAM, "use_case " + kind + ".");
        }
        String uri = "tos-sim-i-omniai/" + UUID.randomUUID().toString().replace("-", "");
        UploadedImage image = new UploadedImage(uri, "http://localhost/sim-images/" + uri, 720, 720, kind);
        images.put(uri, image);
        return image;
    }

    // ---------- tạo / sửa ----------

    public synchronized ProductResponse create(ProductRequest body) {
        ProductRequest r = require(body);
        if (r.idempotencyKey() != null && byIdempotencyKey.containsKey(r.idempotencyKey())) {
            return response(products.get(byIdempotencyKey.get(r.idempotencyKey())));
        }
        long now = clock.instant().getEpochSecond();
        StoredProduct p = build(Ids.next(), r, List.of(), "ACTIVATE", now, now);
        products.put(p.id(), p);
        if (r.idempotencyKey() != null) {
            byIdempotencyKey.put(r.idempotencyKey(), p.id());
        }
        return response(p);
    }

    /** Edit Product: thay toàn bộ nội dung; SKU giữ id nếu gửi kèm id hoặc trùng seller_sku. */
    public synchronized ProductResponse edit(String productId, ProductRequest body) {
        StoredProduct old = existing(productId, TiktokErrorCode.PRODUCT_ID_NOT_EXIST);
        ProductRequest r = require(body);
        StoredProduct p = build(productId, r, old.skus(), old.status(), old.createTime(),
                clock.instant().getEpochSecond());
        products.put(productId, p);
        return response(p);
    }

    public synchronized void updatePrice(String productId, List<Sku> skus) {
        StoredProduct p = existing(productId, TiktokErrorCode.PRODUCT_NOT_EXIST);
        List<StoredSku> updated = new ArrayList<>(p.skus());
        for (Sku s : requireSkus(skus)) {
            int i = indexOf(updated, s.id());
            String amount = price(s);
            StoredSku old = updated.get(i);
            updated.set(i, new StoredSku(old.id(), old.sellerSku(), amount, VND, old.warehouseId(), old.quantity()));
        }
        products.put(productId, withSkus(p, updated));
    }

    public synchronized void updateInventory(String productId, List<Sku> skus) {
        StoredProduct p = existing(productId, TiktokErrorCode.PRODUCT_NOT_EXIST);
        List<StoredSku> updated = new ArrayList<>(p.skus());
        for (Sku s : requireSkus(skus)) {
            int i = indexOf(updated, s.id());
            Inventory inv = inventory(s, true);
            StoredSku old = updated.get(i);
            updated.set(i, new StoredSku(old.id(), old.sellerSku(), old.priceAmount(), old.currency(),
                    inv.warehouseId(), inv.quantity()));
        }
        products.put(productId, withSkus(p, updated));
    }

    public synchronized void setStatus(List<String> productIds, String status) {
        if (productIds == null || productIds.isEmpty()) {
            throw new ApiException(TiktokErrorCode.INVALID_PARAMS, "product_ids is required.");
        }
        for (String id : productIds) {
            existing(id, TiktokErrorCode.PRODUCT_NOT_EXIST);
        }
        long now = clock.instant().getEpochSecond();
        for (String id : productIds) {
            StoredProduct p = products.get(id);
            products.put(id, new StoredProduct(p.id(), p.title(), p.description(), p.categoryId(), p.brandId(),
                    p.mainImages(), p.skus(), p.packageWeight(), status, p.createTime(), now));
        }
    }

    public Optional<StoredProduct> get(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public List<StoredProduct> all() {
        return products.values().stream().sorted(Comparator.comparing(StoredProduct::createTime)
                .thenComparing(StoredProduct::id)).toList();
    }

    public void clear() {
        products.clear();
        byIdempotencyKey.clear();
        images.clear();
    }

    // ---------- kiểm dữ liệu ----------

    private ProductRequest require(ProductRequest r) {
        if (r == null || r.title() == null || r.title().isBlank() || r.title().length() > MAX_TITLE) {
            throw new ApiException(TiktokErrorCode.INVALID_PARAMS, "title is required, 1-" + MAX_TITLE + " characters.");
        }
        if (r.description() != null && r.description().length() > MAX_DESCRIPTION) {
            throw new ApiException(TiktokErrorCode.PRODUCT_DESCRIPTION_INVALID);
        }
        if (r.categoryId() == null || !config.categories().contains(r.categoryId())) {
            throw new ApiException(TiktokErrorCode.PRODUCT_CATEGORY_NOT_EXIST, "category_id " + r.categoryId() + ".");
        }
        if (r.mainImages() == null || r.mainImages().isEmpty()) {
            throw new ApiException(TiktokErrorCode.PRODUCT_MAIN_IMAGE_REQUIRED);
        }
        for (Image image : r.mainImages()) {
            if (image == null || image.uri() == null || !images.containsKey(image.uri())) {
                throw new ApiException(TiktokErrorCode.PRODUCT_MAIN_IMAGE_URI_ILLEGAL,
                        "uri " + (image == null ? null : image.uri()) + ".");
            }
        }
        Weight w = r.packageWeight();
        if (w == null || !List.of("GRAM", "KILOGRAM", "POUND").contains(w.unit()) || !positive(w.value())) {
            throw new ApiException(TiktokErrorCode.PRODUCT_PACKAGE_WEIGHT_INVALID);
        }
        for (Sku s : requireSkus(r.skus())) {
            price(s);
            inventory(s, false);
        }
        return r;
    }

    private static List<Sku> requireSkus(List<Sku> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new ApiException(TiktokErrorCode.PRODUCT_SKU_LIST_EMPTY);
        }
        return skus;
    }

    private static String price(Sku s) {
        if (s.price() == null || s.price().currency() == null) {
            throw new ApiException(TiktokErrorCode.PRODUCT_PRICE_INVALID, "price.currency is required.");
        }
        if (!VND.equals(s.price().currency())) {
            throw new ApiException(TiktokErrorCode.PRODUCT_CURRENCY_NOT_AVAILABLE, s.price().currency() + ".");
        }
        String amount = s.price().amount();
        if (amount == null || !amount.matches("[1-9]\\d{0,11}")) {
            throw new ApiException(TiktokErrorCode.PRODUCT_PRICE_INVALID, "amount " + amount + ".");
        }
        return amount;
    }

    private Inventory inventory(Sku s, boolean update) {
        if (s.inventory() == null || s.inventory().size() != 1) {
            throw new ApiException(update ? TiktokErrorCode.PRODUCT_INVENTORY_INVALID : TiktokErrorCode.PRODUCT_WAREHOUSE_NOT_EXIST,
                    "exactly 1 warehouse.");
        }
        Inventory inv = s.inventory().get(0);
        if (!config.warehouseId().equals(inv.warehouseId())) {
            throw new ApiException(TiktokErrorCode.PRODUCT_WAREHOUSE_NOT_EXIST, "warehouse_id " + inv.warehouseId() + ".");
        }
        if (inv.quantity() == null || inv.quantity() < 0 || inv.quantity() > config.maxQuantity()) {
            throw new ApiException(TiktokErrorCode.PRODUCT_INVENTORY_OUT_OF_RANGE, "quantity " + inv.quantity() + ".");
        }
        return inv;
    }

    private StoredProduct build(String id, ProductRequest r, List<StoredSku> oldSkus, String status, long created,
                                long updated) {
        List<StoredSku> skus = new ArrayList<>();
        for (Sku s : r.skus()) {
            String skuId = s.id() != null ? s.id() : oldSkus.stream()
                    .filter(o -> o.sellerSku() != null && o.sellerSku().equals(s.sellerSku()))
                    .map(StoredSku::id).findFirst().orElse(null);
            if (s.id() != null && oldSkus.stream().noneMatch(o -> o.id().equals(s.id()))) {
                throw new ApiException(TiktokErrorCode.PRODUCT_SKU_NOT_EXIST, "sku id " + s.id() + ".");
            }
            Inventory inv = s.inventory().get(0);
            skus.add(new StoredSku(skuId != null ? skuId : Ids.next(), s.sellerSku(), s.price().amount(), VND,
                    inv.warehouseId(), inv.quantity()));
        }
        List<String> imageUris = r.mainImages().stream().map(Image::uri).toList();
        return new StoredProduct(id, r.title(), r.description(), r.categoryId(), r.brandId(), imageUris, skus,
                r.packageWeight(), status, created, updated);
    }

    private StoredProduct existing(String productId, TiktokErrorCode notFound) {
        StoredProduct p = products.get(productId);
        if (p == null) {
            throw new ApiException(notFound, "product_id " + productId + ".");
        }
        return p;
    }

    private static int indexOf(List<StoredSku> skus, String skuId) {
        if (skuId == null) {
            throw new ApiException(TiktokErrorCode.PRODUCT_SKU_IDS_REQUIRED);
        }
        for (int i = 0; i < skus.size(); i++) {
            if (skus.get(i).id().equals(skuId)) {
                return i;
            }
        }
        throw new ApiException(TiktokErrorCode.PRODUCT_SKU_NOT_EXIST, "sku id " + skuId + ".");
    }

    private StoredProduct withSkus(StoredProduct p, List<StoredSku> skus) {
        return new StoredProduct(p.id(), p.title(), p.description(), p.categoryId(), p.brandId(), p.mainImages(),
                List.copyOf(skus), p.packageWeight(), p.status(), p.createTime(), clock.instant().getEpochSecond());
    }

    private static ProductResponse response(StoredProduct p) {
        return new ProductResponse(p.id(), p.skus().stream().map(s -> new SkuRef(s.id(), s.sellerSku())).toList(),
                List.of());
    }

    private static boolean positive(String value) {
        try {
            return value != null && Double.parseDouble(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

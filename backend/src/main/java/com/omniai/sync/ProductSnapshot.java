package com.omniai.sync;

import com.omniai.catalog.Product;
import com.omniai.catalog.ProductImage;
import com.omniai.catalog.ProductImages;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Nội dung 1 sản phẩm gửi ra 1 kênh. Connector đổi bản này sang schema của sàn.
 * <p>
 * {@link #contentHash()} chỉ tính trên nội dung sản phẩm của US-49 (tên, mô tả, hãng, loại, INCI, dung tích, ảnh,
 * đang bán hay ẩn). Giá và tồn kho có trong snapshot vì sàn bắt buộc khi tạo sản phẩm, nhưng không nằm trong hash:
 * đổi giá/tồn đi đường riêng (update_price, update_stock) ở US-50, không đẩy lại cả sản phẩm.
 *
 * @param imageUrls URL public của ảnh theo thứ tự (ảnh chính trước); rỗng khi chưa cấu hình Supabase
 * @param price     giá niêm yết của kênh này (VND)
 */
public record ProductSnapshot(
        Long productId,
        String channel,
        String sku,
        String name,
        String brand,
        String category,
        String description,
        String ingredientsInci,
        Integer sizeMl,
        List<String> imageKeys,
        List<String> imageUrls,
        Long price,
        int stock,
        boolean active) {

    static ProductSnapshot of(Product p, String channel, ProductImages images) {
        List<ProductImage> ordered = p.getImages().stream()
                .sorted((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()))
                .toList();
        List<String> keys = ordered.stream().map(ProductImage::getStorageKey).toList();
        List<String> urls = keys.stream().map(images::urlFor).filter(Objects::nonNull).toList();
        return new ProductSnapshot(p.getId(), channel, p.getSku(), p.getName(), p.getBrand(), p.getCategory(),
                p.getDescription(), p.getIngredientsInci(), p.getSizeMl(), keys, urls, p.getPrices().get(channel),
                p.getStock(), p.isActive());
    }

    /** SHA-256 (hex) của nội dung US-49. Hai snapshot cùng hash thì sàn không cần nhận lại. */
    public String contentHash() {
        StringBuilder sb = new StringBuilder();
        for (Object field : List.of(channel, sku, String.valueOf(name), String.valueOf(brand), String.valueOf(category),
                String.valueOf(description), String.valueOf(ingredientsInci), String.valueOf(sizeMl),
                String.join("\n", imageKeys), Boolean.toString(active))) {
            sb.append(field).append('\u0000');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

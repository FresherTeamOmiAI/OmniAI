package com.omniai.catalog;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ghép URL public của ảnh sản phẩm trên Supabase Storage (decisions #26, T-47.1). Owner: LucVT.
 *
 * <p>DB chỉ lưu {@code image_key} ({@code <SKU>.webp}); URL = {@code <SUPABASE_URL>/storage/v1/object/public/<bucket>/<key>}.
 * Không gọi mạng. Thiếu {@code SUPABASE_URL} hoặc sản phẩm chưa có ảnh thì {@code imageUrl = null}, web hiện khung trống.
 * Là entity listener của {@link Product}; Hibernate lấy bean này từ Spring nên đọc được cấu hình.
 */
@Component
public class ProductImages {

    private final String publicBase;

    public ProductImages(@Value("${omniai.images.base-url:}") String baseUrl,
                         @Value("${omniai.images.bucket:product-images}") String bucket) {
        String base = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        this.publicBase = base.isEmpty() ? "" : base + "/storage/v1/object/public/" + bucket + "/";
    }

    public String urlFor(String imageKey) {
        if (publicBase.isEmpty() || imageKey == null || imageKey.isBlank()) {
            return null;
        }
        return publicBase + imageKey;
    }

    @PostLoad
    @PostPersist
    @PostUpdate
    void fill(Product product) {
        product.setImageUrl(urlFor(product.getImageKey()));
    }
}

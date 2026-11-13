package com.omniai.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.ListIndexBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sản phẩm mỹ phẩm. Owner: LucVT. Schema: V1 + V4 + V9 + V13 (giá theo kênh) + V15 (chuẩn hoá, decisions #35).
 *
 * <p>Từ V15, các giá trị nhiều phần tử nằm ở bảng riêng: hãng ({@link Brand}), loại da / vấn đề da (bảng nối),
 * thành phần INCI có thứ tự ({@link Ingredient}), ảnh ({@link ProductImage}). JSON của API giữ nguyên hình dạng cũ
 * ({@code brand}, {@code skinTypes} "oily,dry", {@code usageTime} "am,pm", {@code ingredientsInci}, {@code imageKey}...)
 * qua các getter {@code @JsonProperty}, nên web, Postman, ai-brain không phải đổi.
 *
 * <p>Mọi collection EAGER (open-in-view tắt, API trả thẳng entity) nhưng nạp bằng câu SELECT riêng theo lô 50
 * ({@code FetchMode.SELECT} + {@code BatchSize}) để không nhân bản dòng và không 1 query / sản phẩm.
 */
@Entity
@Table(name = "product")
@EntityListeners(ProductImages.class)
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    @JsonIgnore
    private Brand brandRef;

    /** Mã danh mục, FK tới bảng category (V15): cleanser, toner, serum, moisturizer, sunscreen... */
    @Column(nullable = false, length = 32)
    private String category;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "product_skin_type", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "skin_type_code"))
    @OrderBy("sortOrder")
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    @JsonIgnore
    private Set<SkinType> skinTypeRefs = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "product_skin_concern", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "skin_concern_code"))
    @OrderBy("sortOrder")
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    @JsonIgnore
    private Set<SkinConcern> skinConcernRefs = new LinkedHashSet<>();

    /** Buổi dùng trong routine (V15 thay chuỗi 'am,pm'). Cả 2 false = chưa ghi. */
    @Column(name = "use_am", nullable = false)
    @JsonIgnore
    private boolean useAm;

    @Column(name = "use_pm", nullable = false)
    @JsonIgnore
    private boolean usePm;

    /** Mô tả ngắn tiếng Việt (công dụng, kết cấu, cách dùng), dùng cho trang chi tiết và RAG. */
    @Column(length = 2000)
    private String description;

    /** Thành phần INCI theo đúng thứ tự trên nhãn (position từ 1). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "product_ingredient", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id"))
    @OrderColumn(name = "position")
    @ListIndexBase(1)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    @JsonIgnore
    private List<Ingredient> ingredients = new ArrayList<>();

    @Column(name = "size_ml", nullable = false)
    private Integer sizeMl;

    /** Period After Opening: dùng trong bao nhiêu tháng sau khi mở. */
    @Column(name = "pao_months", nullable = false)
    private Integer paoMonths;

    /**
     * Giá niêm yết theo kênh (VND), khoá = sales_channel.code (WEB, SHOPEE, TIKTOK...). Bảng product_channel (V13).
     * Không còn 1 cột / kênh, nên thêm sàn không phải sửa bảng này (decisions #32).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_channel", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "channel_code", length = 20)
    @Column(name = "price", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    private Map<String, Long> prices = new LinkedHashMap<>();

    private int stock;

    private boolean active = true;

    /** Mã vạch trên Open Beauty Facts (T-47.1). NULL với sản phẩm admin tự tạo. */
    @Column(length = 32, unique = true)
    private String barcode;

    /** Trang nguồn của dữ liệu, vd. https://world.openbeautyfacts.org/product/<barcode>. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** Ảnh sản phẩm (V15), sort_order từ 0. Admin chưa sửa được ảnh qua API. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    @JsonIgnore
    private List<ProductImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** URL public của ảnh chính, do {@link ProductImages} ghép từ cấu hình Supabase + imageKey. Không lưu DB. */
    @Transient
    private String imageUrl;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    // ---- JSON giữ hình dạng cũ (trước V15) ----

    @JsonProperty("brand")
    public String getBrand() {
        return brandRef == null ? null : brandRef.getName();
    }

    /** Mã loại da nối bằng dấu phẩy, theo sort_order của bảng skin_type, vd. "oily,combination". */
    @JsonProperty("skinTypes")
    public String getSkinTypes() {
        return skinTypeRefs.stream().sorted(Comparator.comparingInt(SkinType::getSortOrder))
                .map(SkinType::getCode).collect(Collectors.joining(","));
    }

    /** Như {@link #getSkinTypes()}; null khi không có vấn đề da nào. */
    @JsonProperty("skinConcerns")
    public String getSkinConcerns() {
        String joined = skinConcernRefs.stream().sorted(Comparator.comparingInt(SkinConcern::getSortOrder))
                .map(SkinConcern::getCode).collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    /** am | pm | am,pm | null. */
    @JsonProperty("usageTime")
    public String getUsageTime() {
        if (useAm && usePm) {
            return "am,pm";
        }
        return useAm ? "am" : usePm ? "pm" : null;
    }

    public void setUsageTime(String usageTime) {
        this.useAm = "am".equals(usageTime) || "am,pm".equals(usageTime);
        this.usePm = "pm".equals(usageTime) || "am,pm".equals(usageTime);
    }

    /** INCI nối bằng ", " theo thứ tự trên nhãn. */
    @JsonProperty("ingredientsInci")
    public String getIngredientsInci() {
        return ingredients.stream().map(Ingredient::getInciName).collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public ProductImage getPrimaryImage() {
        return images.stream().filter(ProductImage::isPrimary).findFirst()
                .orElse(images.isEmpty() ? null : images.getFirst());
    }

    @JsonProperty("imageKey")
    public String getImageKey() {
        ProductImage image = getPrimaryImage();
        return image == null ? null : image.getStorageKey();
    }

    @JsonProperty("imageSourceUrl")
    public String getImageSourceUrl() {
        ProductImage image = getPrimaryImage();
        return image == null ? null : image.getSourceUrl();
    }

    @JsonProperty("imageLicenseNote")
    public String getImageLicenseNote() {
        ProductImage image = getPrimaryImage();
        return image == null ? null : image.getLicenseNote();
    }
}

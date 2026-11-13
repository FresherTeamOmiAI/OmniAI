package com.omniai.catalog;

import com.omniai.catalog.ProductErrors.DuplicateSku;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.catalog.ProductErrors.NotFound;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.omniai.catalog.CatalogVocabulary.MEDICAL_CLAIM;
import static com.omniai.catalog.CatalogVocabulary.MIN_INCI;
import static com.omniai.catalog.CatalogVocabulary.SKU;
import static com.omniai.catalog.CatalogVocabulary.USAGE_TIMES;
import static com.omniai.catalog.CatalogVocabulary.split;

/**
 * Nghiệp vụ quản lý sản phẩm cho admin (US-08, UC-20) và danh sách store (T-09.2). Owner: LucVT.
 * Không xóa cứng, chỉ ẩn bằng active=false (G-07). Chỉ quản lý giá niêm yết, khuyến mãi thuộc US-33 (G-08).
 * Từ V15 (decisions #35) bộ từ vựng đọc từ bảng tra cứu; hãng và thành phần INCI mới được tự thêm vào bảng.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    /** Tên ràng buộc UNIQUE mà Postgres tự đặt cho cột sku ở V1. */
    private static final String SKU_UNIQUE_CONSTRAINT = "product_sku_key";

    private final ProductRepository repository;
    private final SalesChannelRepository channels;
    private final CategoryRepository categories;
    private final SkinTypeRepository skinTypes;
    private final SkinConcernRepository skinConcerns;
    private final BrandRepository brands;
    private final IngredientRepository ingredients;

    public ProductService(ProductRepository repository, SalesChannelRepository channels, CategoryRepository categories,
                          SkinTypeRepository skinTypes, SkinConcernRepository skinConcerns, BrandRepository brands,
                          IngredientRepository ingredients) {
        this.repository = repository;
        this.channels = channels;
        this.categories = categories;
        this.skinTypes = skinTypes;
        this.skinConcerns = skinConcerns;
        this.brands = brands;
        this.ingredients = ingredients;
    }

    /** Bộ từ vựng hiện hành, đọc từ các bảng tra cứu (V15). Mỗi map giữ thứ tự sort_order. */
    record Vocabulary(Map<String, Category> categories, Map<String, SkinType> skinTypes,
                      Map<String, SkinConcern> skinConcerns, List<SalesChannel> channels) {
    }

    Vocabulary vocabulary() {
        return new Vocabulary(
                byCode(categories.findAllByOrderBySortOrderAsc(), Category::getCode),
                byCode(skinTypes.findAllByOrderBySortOrderAsc(), SkinType::getCode),
                byCode(skinConcerns.findAllByOrderBySortOrderAsc(), SkinConcern::getCode),
                channels());
    }

    private static <T> Map<String, T> byCode(List<T> rows, Function<T, String> code) {
        return rows.stream().collect(Collectors.toMap(code, r -> r, (a, b) -> a, LinkedHashMap::new));
    }

    /** Danh sách kênh bán, kênh sở hữu trước. Form admin sinh ô giá theo danh sách này. */
    public List<SalesChannel> channels() {
        return channels.findAllByOrderByOwnedDescCodeAsc();
    }

    /** Danh sách cho admin, gồm cả sản phẩm ẩn. {@code q} tìm trong sku và tên; {@code active} null = tất cả. */
    public Page<Product> search(String q, Boolean active, Pageable pageable) {
        return repository.findAll(filter(q, active), pageable);
    }

    /**
     * Danh sách cho web store (T-09.2): chỉ sản phẩm active (G-06), lọc theo loại da, danh mục, khoảng giá web.
     * Tham số sai (loại da / danh mục ngoài từ vựng, giá âm, min > max) thì 400 kèm field.
     */
    public Page<Product> storeSearch(StoreFilter filter, Pageable pageable) {
        StoreFilter f = filter.normalized();
        checkStoreFilter(f, vocabulary());
        return repository.findAll(storeSpec(f), pageable);
    }

    public Product get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFound(id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        ProductRequest r = request.normalized();
        Vocabulary v = vocabulary();
        check(r, v);
        if (repository.existsBySku(r.sku())) {
            throw new DuplicateSku(r.sku());
        }
        Product product = new Product();
        product.setSku(r.sku());
        apply(r, product, v);
        product.setActive(r.active() == null || r.active());
        return save(product);
    }

    /** Sửa toàn bộ trường. SKU không được đổi vì bench và báo cáo tham chiếu theo SKU. */
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        ProductRequest r = request.normalized();
        Vocabulary v = vocabulary();
        check(r, v);
        if (!product.getSku().equals(r.sku())) {
            throw new Invalid(List.of(new FieldViolation("sku", "không được đổi SKU (đang là " + product.getSku() + ")")));
        }
        apply(r, product, v);
        if (r.active() != null) {
            product.setActive(r.active());
        }
        return save(product);
    }

    /** Ẩn (false) hoặc hiện lại (true). Sản phẩm ẩn biến khỏi store và AI ngay (G-06). */
    @Transactional
    public Product setActive(Long id, boolean active) {
        Product product = get(id);
        product.setActive(active);
        return save(product);
    }

    private Product save(Product product) {
        try {
            return repository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            // Hai request tạo cùng SKU một lúc: existsBySku đều qua, DB chặn bản thứ hai.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(SKU_UNIQUE_CONSTRAINT)) {
                throw new DuplicateSku(product.getSku());
            }
            throw e;
        }
    }

    private void apply(ProductRequest r, Product p, Vocabulary v) {
        boolean changed = false;
        p.setName(r.name());
        p.setBrandRef(brandFor(r.brand(), r.sku()));
        p.setCategory(r.category());
        // Gán set/list MỚI (không sửa tại chỗ) khi khác: Hibernate xoá rồi chèn lại bảng nối, không vướng UNIQUE tạm thời.
        Set<SkinType> skins = split(r.skinTypes()).stream().map(v.skinTypes()::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!codes(skins, SkinType::getCode).equals(codes(p.getSkinTypeRefs(), SkinType::getCode))) {
            p.setSkinTypeRefs(skins);
            changed = true;
        }
        Set<SkinConcern> concerns = split(r.skinConcerns()).stream().map(v.skinConcerns()::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!codes(concerns, SkinConcern::getCode).equals(codes(p.getSkinConcernRefs(), SkinConcern::getCode))) {
            p.setSkinConcernRefs(concerns);
            changed = true;
        }
        p.setUsageTime(r.usageTime());
        p.setDescription(r.description());
        List<Ingredient> inci = ingredientsFor(r.ingredientsInci());
        if (!inci.stream().map(Ingredient::getId).toList().equals(p.getIngredients().stream().map(Ingredient::getId).toList())) {
            p.setIngredients(inci);
            changed = true;
        }
        p.setSizeMl(r.sizeMl());
        p.setPaoMonths(r.paoMonths());
        p.setStock(r.stock());
        // Chỉ ghi đè kênh có trong request: giá ở kênh đang tạm ngừng (active=false) không bị mất khi admin sửa.
        Map<String, Long> prices = new LinkedHashMap<>(p.getPrices());
        prices.putAll(r.prices());
        if (!prices.equals(p.getPrices())) {
            p.getPrices().putAll(r.prices()); // Hibernate chỉ UPDATE/INSERT đúng dòng product_channel đổi
            changed = true;
        }
        if (changed) {
            p.setUpdatedAt(Instant.now()); // chỉ đổi bảng con thì @PreUpdate không chạy (product không đổi cột nào)
        }
    }

    private static <T> Set<String> codes(Set<T> rows, Function<T, String> code) {
        return rows.stream().map(code).collect(Collectors.toSet());
    }

    /**
     * Hãng theo tên (không phân biệt hoa thường). Chưa có thì thêm, code = tiền tố SKU nếu tiền tố đó chưa thuộc hãng khác.
     */
    private Brand brandFor(String name, String sku) {
        return brands.findByNameIgnoreCase(name).orElseGet(() -> {
            String prefix = sku.contains("-") ? sku.substring(0, sku.indexOf('-')) : null;
            String code = prefix != null && prefix.length() <= 8 && !brands.existsByCode(prefix) ? prefix : null;
            return brands.save(new Brand(code, name));
        });
    }

    /**
     * Thành phần theo thứ tự nhập, bỏ trùng không phân biệt hoa thường (giữ lần đầu, giống V15).
     * Thành phần chưa có trong bảng ingredient thì thêm, giữ cách viết admin nhập.
     */
    private List<Ingredient> ingredientsFor(String inci) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String name : split(inci)) {
            names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        Map<String, Ingredient> known = new LinkedHashMap<>();
        for (Ingredient i : ingredients.findByLowerNames(names.keySet())) {
            known.put(i.getInciName().toLowerCase(Locale.ROOT), i);
        }
        List<Ingredient> result = new ArrayList<>();
        names.forEach((key, name) -> result.add(known.computeIfAbsent(key, k -> ingredients.save(new Ingredient(name)))));
        return result;
    }

    /** Luật giống tools/catalog/validate_catalog.py, chạy trên dữ liệu đã chuẩn hóa. Từ vựng đọc từ DB. */
    static void check(ProductRequest r, Vocabulary v) {
        List<FieldViolation> errors = new ArrayList<>();
        checkPrices(errors, r.prices(), v.channels());
        if (!SKU.matcher(r.sku()).matches()) {
            errors.add(new FieldViolation("sku", "phải có dạng BRAND-LOAI-NN, vd. CERA-CLE-01"));
        }
        if (!v.categories().containsKey(r.category())) {
            errors.add(new FieldViolation("category", "không hợp lệ, chọn một trong " + sorted(v.categories().keySet())));
        }
        checkCsv(errors, "skinTypes", r.skinTypes(), v.skinTypes().keySet());
        checkCsv(errors, "skinConcerns", r.skinConcerns(), v.skinConcerns().keySet());
        if (r.usageTime() != null && !USAGE_TIMES.contains(r.usageTime())) {
            errors.add(new FieldViolation("usageTime", "phải là am, pm hoặc am,pm"));
        }
        if (split(r.ingredientsInci()).size() < MIN_INCI) {
            errors.add(new FieldViolation("ingredientsInci", "cần ít nhất " + MIN_INCI + " thành phần, cách nhau bằng dấu phẩy"));
        }
        if (split(r.ingredientsInci()).stream().anyMatch(name -> name.length() > 200)) {
            errors.add(new FieldViolation("ingredientsInci", "mỗi thành phần tối đa 200 ký tự"));
        }
        claim(errors, "name", r.name());
        claim(errors, "description", r.description());
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }
    }

    /** AC US-04: có giá cho mọi kênh đang active. Kênh lạ bị từ chối. DB không tự ép được điều này (V13). */
    private static void checkPrices(List<FieldViolation> errors, Map<String, Long> prices, List<SalesChannel> channels) {
        Set<String> known = new HashSet<>();
        for (SalesChannel channel : channels) {
            known.add(channel.getCode());
            if (channel.isActive() && !prices.containsKey(channel.getCode())) {
                errors.add(new FieldViolation("prices[" + channel.getCode() + "]", "bắt buộc (kênh " + channel.getName() + ")"));
            }
        }
        for (String code : prices.keySet()) {
            if (!known.contains(code)) {
                errors.add(new FieldViolation("prices[" + code + "]", "kênh không tồn tại, chọn trong " + known.stream().sorted().toList()));
            }
        }
    }

    private static void checkCsv(List<FieldViolation> errors, String field, String value, Set<String> allowed) {
        Set<String> bad = new HashSet<>(split(value));
        bad.removeAll(allowed);
        if (!bad.isEmpty()) {
            errors.add(new FieldViolation(field, "giá trị không hợp lệ " + sorted(bad) + ", chọn trong " + sorted(allowed)));
        }
    }

    private static void claim(List<FieldViolation> errors, String field, String value) {
        if (value == null) {
            return;
        }
        Matcher m = MEDICAL_CLAIM.matcher(value);
        if (m.find()) {
            errors.add(new FieldViolation(field, "không được có claim y khoa '" + m.group() + "'"));
        }
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static void checkStoreFilter(StoreFilter f, Vocabulary v) {
        List<FieldViolation> errors = new ArrayList<>();
        if (f.skinType() != null && !v.skinTypes().containsKey(f.skinType())) {
            errors.add(new FieldViolation("skinType", "không hợp lệ, chọn một trong " + sorted(v.skinTypes().keySet())));
        }
        if (f.category() != null && !v.categories().containsKey(f.category())) {
            errors.add(new FieldViolation("category", "không hợp lệ, chọn một trong " + sorted(v.categories().keySet())));
        }
        if (f.minPrice() != null && f.minPrice() < 0) {
            errors.add(new FieldViolation("minPrice", "phải >= 0"));
        }
        if (f.maxPrice() != null && f.maxPrice() < 0) {
            errors.add(new FieldViolation("maxPrice", "phải >= 0"));
        }
        if (f.minPrice() != null && f.maxPrice() != null && f.minPrice() > f.maxPrice()) {
            errors.add(new FieldViolation("minPrice", "phải <= maxPrice"));
        }
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }
    }

    /** AND các điều kiện có mặt. Loại da: join bảng nối product_skin_type (V15), mỗi sản phẩm tối đa 1 dòng khớp. */
    private static Specification<Product> storeSpec(StoreFilter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (f.skinType() != null) {
                Join<Product, SkinType> skin = root.join("skinTypeRefs");
                where.add(cb.equal(skin.get("code"), f.skinType()));
            }
            if (f.category() != null) {
                where.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.minPrice() != null || f.maxPrice() != null) {
                // Giá nằm ở product_channel (V13): join đúng dòng của kênh web, mỗi sản phẩm tối đa 1 dòng.
                MapJoin<Product, String, Long> price = root.joinMap("prices");
                where.add(cb.equal(price.key(), StoreFilter.STORE_CHANNEL));
                if (f.minPrice() != null) {
                    where.add(cb.greaterThanOrEqualTo(price.value(), f.minPrice()));
                }
                if (f.maxPrice() != null) {
                    where.add(cb.lessThanOrEqualTo(price.value(), f.maxPrice()));
                }
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private static Specification<Product> filter(String q, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (active != null) {
                where.add(cb.equal(root.get("active"), active));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("sku")), like), cb.like(cb.lower(root.get("name")), like)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }
}

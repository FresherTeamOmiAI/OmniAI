package com.omniai.catalog;

import com.omniai.catalog.ProductErrors.DuplicateSku;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.catalog.ProductErrors.NotFound;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

import static com.omniai.catalog.CatalogVocabulary.CATEGORIES;
import static com.omniai.catalog.CatalogVocabulary.MEDICAL_CLAIM;
import static com.omniai.catalog.CatalogVocabulary.MIN_INCI;
import static com.omniai.catalog.CatalogVocabulary.SKIN_CONCERNS;
import static com.omniai.catalog.CatalogVocabulary.SKIN_TYPES;
import static com.omniai.catalog.CatalogVocabulary.SKU;
import static com.omniai.catalog.CatalogVocabulary.USAGE_TIMES;
import static com.omniai.catalog.CatalogVocabulary.split;

/**
 * Nghiệp vụ quản lý sản phẩm cho admin (US-08, UC-20). Owner: LucVT.
 * Không xóa cứng, chỉ ẩn bằng active=false (G-07). Chỉ quản lý giá niêm yết, khuyến mãi thuộc US-33 (G-08).
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    /** Tên ràng buộc UNIQUE mà Postgres tự đặt cho cột sku ở V1. */
    private static final String SKU_UNIQUE_CONSTRAINT = "product_sku_key";

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Danh sách cho admin, gồm cả sản phẩm ẩn. {@code q} tìm trong sku và tên; {@code active} null = tất cả. */
    public Page<Product> search(String q, Boolean active, Pageable pageable) {
        return repository.findAll(filter(q, active), pageable);
    }

    public Product get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFound(id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        ProductRequest r = request.normalized();
        check(r);
        if (repository.existsBySku(r.sku())) {
            throw new DuplicateSku(r.sku());
        }
        Product product = new Product();
        product.setSku(r.sku());
        apply(r, product);
        product.setActive(r.active() == null || r.active());
        return save(product);
    }

    /** Sửa toàn bộ trường. SKU không được đổi vì bench và báo cáo tham chiếu theo SKU. */
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        ProductRequest r = request.normalized();
        check(r);
        if (!product.getSku().equals(r.sku())) {
            throw new Invalid(List.of(new FieldViolation("sku", "không được đổi SKU (đang là " + product.getSku() + ")")));
        }
        apply(r, product);
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

    private static void apply(ProductRequest r, Product p) {
        p.setName(r.name());
        p.setBrand(r.brand());
        p.setCategory(r.category());
        p.setSkinTypes(r.skinTypes());
        p.setSkinConcerns(r.skinConcerns());
        p.setUsageTime(r.usageTime());
        p.setDescription(r.description());
        p.setIngredientsInci(r.ingredientsInci());
        p.setSizeMl(r.sizeMl());
        p.setPaoMonths(r.paoMonths());
        p.setPriceWeb(r.priceWeb());
        p.setPriceShopee(r.priceShopee());
        p.setPriceTiktok(r.priceTiktok());
        p.setStock(r.stock());
    }

    /** Luật giống tools/catalog/validate_catalog.py, chạy trên dữ liệu đã chuẩn hóa. */
    static void check(ProductRequest r) {
        List<FieldViolation> errors = new ArrayList<>();
        if (!SKU.matcher(r.sku()).matches()) {
            errors.add(new FieldViolation("sku", "phải có dạng BRAND-LOAI-NN, vd. CERA-CLE-01"));
        }
        if (!CATEGORIES.contains(r.category())) {
            errors.add(new FieldViolation("category", "không hợp lệ, chọn một trong " + sorted(CATEGORIES)));
        }
        checkCsv(errors, "skinTypes", r.skinTypes(), SKIN_TYPES);
        checkCsv(errors, "skinConcerns", r.skinConcerns(), SKIN_CONCERNS);
        if (r.usageTime() != null && !USAGE_TIMES.contains(r.usageTime())) {
            errors.add(new FieldViolation("usageTime", "phải là am, pm hoặc am,pm"));
        }
        if (split(r.ingredientsInci()).size() < MIN_INCI) {
            errors.add(new FieldViolation("ingredientsInci", "cần ít nhất " + MIN_INCI + " thành phần, cách nhau bằng dấu phẩy"));
        }
        claim(errors, "name", r.name());
        claim(errors, "description", r.description());
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
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

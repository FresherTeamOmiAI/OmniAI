package com.omniai.promotion;

import com.omniai.catalog.Product;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductRepository;
import com.omniai.catalog.SalesChannel;
import com.omniai.catalog.SalesChannelRepository;
import com.omniai.promotion.Promotion.Status;
import com.omniai.promotion.Promotion.Type;
import com.omniai.promotion.PromotionErrors.DuplicateVoucher;
import com.omniai.promotion.PromotionErrors.Invalid;
import com.omniai.promotion.PromotionErrors.NotFound;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Khuyến mãi theo kênh (US-33, UC-21, decisions #53). Owner: LucVT.
 * <ul>
 *   <li>Admin: tạo, sửa, bật/tắt, xoá. Mỗi thay đổi phát {@link PromotionChanged} (US-52 đẩy ra sàn).</li>
 *   <li>Công khai: khuyến mãi đang chạy của 1 kênh, thêm khuyến mãi sắp diễn ra trong {@code upcomingDays} ngày
 *       (UC-17: bot chỉ khuyên "đợi" khi khuyến mãi bắt đầu trong ≤ 7 ngày). Hết hạn hoặc tắt thì không trả.</li>
 *   <li>Flash sale: mỗi sản phẩm chỉ có 1 giá sale tại 1 thời điểm trên 1 kênh (chặn chồng thời gian).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PromotionService {

    /** Mọi mốc giờ khuyến mãi theo giờ Việt Nam (UC-21 BR). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_UPCOMING_DAYS = 30;
    static final int MAX_ITEMS = 200;
    private static final Pattern VOUCHER_CODE = Pattern.compile("^[A-Z0-9]{3,20}$");
    private static final String VOUCHER_UNIQUE_CONSTRAINT = "uq_promotion_voucher_code";

    private final PromotionRepository promotions;
    private final SalesChannelRepository channels;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PromotionService(PromotionRepository promotions, SalesChannelRepository channels,
                            ProductRepository products, ApplicationEventPublisher events, Clock clock) {
        this.promotions = promotions;
        this.channels = channels;
        this.products = products;
        this.events = events;
        this.clock = clock;
    }

    // --- Đọc công khai (store, ai-brain) ---

    /**
     * Khuyến mãi đang chạy của {@code channel}, cộng khuyến mãi bắt đầu trong {@code upcomingDays} ngày tới (mặc
     * định 0). {@code sku}: chỉ giữ flash sale có sản phẩm đó (dòng lọc còn sản phẩm đó) và voucher của kênh.
     * Dòng của sản phẩm đã ẩn bị bỏ; flash sale không còn dòng nào thì bỏ. Kênh tạm ngừng thì rỗng.
     */
    public List<PromotionView> visible(String channel, String sku, Integer upcomingDays) {
        List<FieldViolation> errors = new ArrayList<>();
        Optional<SalesChannel> ch = channel(channel, errors);
        int days = upcomingDays == null ? 0 : upcomingDays;
        if (days < 0 || days > MAX_UPCOMING_DAYS) {
            errors.add(new FieldViolation("upcomingDays", "trong khoảng 0–" + MAX_UPCOMING_DAYS));
        }
        Product only = null;
        if (sku != null && !sku.isBlank()) {
            only = products.findBySkuAndActiveTrue(sku.trim().toUpperCase(Locale.ROOT)).orElse(null);
            if (only == null) {
                errors.add(new FieldViolation("sku", "không có sản phẩm đang bán với SKU " + sku.trim()));
            }
        }
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }
        if (!ch.orElseThrow().isActive()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<Promotion> rows = promotions.findVisible(ch.get().getCode(), now, now.plus(Duration.ofDays(days)));
        Map<Long, Product> byId = only != null
                ? Map.of(only.getId(), only)
                : byId(products.findAllByIdInAndActiveTrue(productIds(rows)));
        List<PromotionView> result = new ArrayList<>();
        for (Promotion p : rows) {
            PromotionView view = view(p, byId, now);
            if (p.getType() == Type.FLASH_SALE && view.items().isEmpty()) {
                continue;
            }
            result.add(view);
        }
        return result;
    }

    // --- Admin ---

    /** Danh sách admin, gồm cả hết hạn và đã tắt. {@code channel}, {@code status} null = tất cả. */
    public Page<PromotionView> search(String channel, Status status, Pageable pageable) {
        Instant now = clock.instant();
        Page<Promotion> page = promotions.findAll(filter(channel, status, now), pageable);
        Map<Long, Product> byId = byId(products.findAllById(productIds(page.getContent())));
        return page.map(p -> view(p, byId, now));
    }

    public PromotionView get(Long id) {
        return view(find(id));
    }

    @Transactional
    public PromotionView create(PromotionRequest request) {
        Promotion p = new Promotion();
        apply(p, request, true);
        return save(p);
    }

    /** Sửa toàn bộ trường (PUT). Sửa được cả khuyến mãi đã hết hạn, ví dụ để gia hạn. */
    @Transactional
    public PromotionView update(Long id, PromotionRequest request) {
        Promotion p = find(id);
        apply(p, request, false);
        return save(p);
    }

    /** Bật lại flash sale vẫn phải qua kiểm tra chồng thời gian. */
    @Transactional
    public PromotionView setEnabled(Long id, boolean enabled) {
        Promotion p = find(id);
        if (enabled && !p.isEnabled() && p.getType() == Type.FLASH_SALE) {
            List<FieldViolation> errors = new ArrayList<>();
            List<Long> ids = new ArrayList<>(p.getItems().keySet());
            checkOverlap(p.getId(), p.getChannelCode(), p.getStartsAt(), p.getEndsAt(), ids, errors);
            if (!errors.isEmpty()) {
                throw new Invalid(errors);
            }
        }
        p.setEnabled(enabled);
        return save(p);
    }

    /** Xoá cứng (T-33.2 có nút xoá). Muốn giữ lại thì tắt ({@link #setEnabled}). */
    @Transactional
    public void delete(Long id) {
        Promotion p = find(id);
        promotions.delete(p);
        promotions.flush();
        events.publishEvent(new PromotionChanged(id, p.getChannelCode(), true));
    }

    // --- Nội bộ ---

    private Promotion find(Long id) {
        return promotions.findById(id).orElseThrow(() -> new NotFound(id));
    }

    private PromotionView save(Promotion p) {
        Promotion saved;
        try {
            saved = promotions.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // 2 request cùng mã một lúc: kiểm trước đều qua, DB chặn bản thứ hai.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(VOUCHER_UNIQUE_CONSTRAINT)) {
                throw new DuplicateVoucher(p.getChannelCode(), p.getVoucherCode());
            }
            throw e;
        }
        events.publishEvent(new PromotionChanged(saved.getId(), saved.getChannelCode(), false));
        return view(saved);
    }

    private void apply(Promotion p, PromotionRequest r, boolean creating) {
        List<FieldViolation> errors = new ArrayList<>();
        Optional<SalesChannel> ch = channel(r.channel(), errors);
        Type type = type(r.type(), errors);
        String name = r.name() == null ? "" : r.name().strip();
        if (name.isEmpty()) {
            errors.add(new FieldViolation("name", "bắt buộc"));
        } else if (name.length() > 200) {
            errors.add(new FieldViolation("name", "tối đa 200 ký tự"));
        }
        Instant starts = time("startsAt", r.startsAt(), errors);
        Instant ends = time("endsAt", r.endsAt(), errors);
        if (starts != null && ends != null && !ends.isAfter(starts)) {
            errors.add(new FieldViolation("endsAt", "phải sau startsAt"));
        } else if (creating && ends != null && !ends.isAfter(clock.instant())) {
            errors.add(new FieldViolation("endsAt", "phải sau thời điểm hiện tại"));
        }
        boolean enabled = r.enabled() == null || r.enabled();

        String code = null;
        Long minOrder = null;
        Map<Long, Long> items = new LinkedHashMap<>();
        if (type == Type.VOUCHER) {
            code = r.voucherCode() == null ? "" : r.voucherCode().strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                errors.add(new FieldViolation("voucherCode", "bắt buộc với VOUCHER"));
            } else if (!VOUCHER_CODE.matcher(code).matches()) {
                errors.add(new FieldViolation("voucherCode", "3–20 ký tự, chỉ chữ không dấu và số"));
            }
            if (r.discountAmount() == null || r.discountAmount() <= 0) {
                errors.add(new FieldViolation("discountAmount", "bắt buộc với VOUCHER, phải > 0"));
            }
            minOrder = r.minOrderAmount() == null ? 0L : r.minOrderAmount();
            if (minOrder < 0) {
                errors.add(new FieldViolation("minOrderAmount", "phải >= 0"));
            }
            if (r.items() != null && !r.items().isEmpty()) {
                errors.add(new FieldViolation("items", "VOUCHER áp cả shop trên kênh, không nhận items"));
            }
        } else if (type == Type.FLASH_SALE) {
            if (r.voucherCode() != null && !r.voucherCode().isBlank()) {
                errors.add(new FieldViolation("voucherCode", "chỉ dùng cho VOUCHER"));
            }
            if (r.discountAmount() != null) {
                errors.add(new FieldViolation("discountAmount", "chỉ dùng cho VOUCHER"));
            }
            if (r.minOrderAmount() != null) {
                errors.add(new FieldViolation("minOrderAmount", "chỉ dùng cho VOUCHER"));
            }
            items = flashSaleItems(r.items(), ch.orElse(null), errors);
        }
        if (errors.isEmpty() && type == Type.FLASH_SALE && enabled) {
            checkOverlap(p.getId(), ch.orElseThrow().getCode(), starts, ends, new ArrayList<>(items.keySet()), errors);
        }
        if (errors.isEmpty() && type == Type.VOUCHER) {
            boolean taken = p.getId() == null
                    ? promotions.existsByChannelCodeAndVoucherCode(ch.orElseThrow().getCode(), code)
                    : promotions.existsByChannelCodeAndVoucherCodeAndIdNot(ch.orElseThrow().getCode(), code, p.getId());
            if (taken) {
                throw new DuplicateVoucher(ch.get().getCode(), code);
            }
        }
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }

        p.setChannelCode(ch.orElseThrow().getCode());
        p.setType(type);
        p.setName(name);
        p.setVoucherCode(code);
        p.setDiscountAmount(type == Type.VOUCHER ? r.discountAmount() : null);
        p.setMinOrderAmount(minOrder);
        p.setStartsAt(starts);
        p.setEndsAt(ends);
        p.setEnabled(enabled);
        if (!items.equals(p.getItems())) {
            p.getItems().clear();
            p.getItems().putAll(items);
            p.setUpdatedAt(Instant.now()); // chỉ đổi bảng con thì @PreUpdate không chạy
        }
    }

    /** Dòng flash sale: SKU đang bán, không lặp, 0 &lt; giá sale &lt; giá niêm yết ở kênh. Kết quả theo thứ tự gửi lên. */
    private Map<Long, Long> flashSaleItems(List<PromotionRequest.Item> items, SalesChannel channel,
                                           List<FieldViolation> errors) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            errors.add(new FieldViolation("items", "FLASH_SALE cần ít nhất 1 sản phẩm"));
            return result;
        }
        if (items.size() > MAX_ITEMS) {
            errors.add(new FieldViolation("items", "tối đa " + MAX_ITEMS + " sản phẩm"));
            return result;
        }
        Set<String> skus = items.stream().filter(i -> i != null && i.sku() != null)
                .map(i -> i.sku().strip().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, Product> bySku = products.findAllBySkuInAndActiveTrue(skus).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            PromotionRequest.Item item = items.get(i);
            String field = "items[" + i + "]";
            String sku = item == null || item.sku() == null ? "" : item.sku().strip().toUpperCase(Locale.ROOT);
            Product product = bySku.get(sku);
            if (sku.isEmpty()) {
                errors.add(new FieldViolation(field + ".sku", "bắt buộc"));
                continue;
            }
            if (!seen.add(sku)) {
                errors.add(new FieldViolation(field + ".sku", "SKU " + sku + " bị lặp"));
                continue;
            }
            if (product == null) {
                errors.add(new FieldViolation(field + ".sku", "không có sản phẩm đang bán với SKU " + sku));
                continue;
            }
            Long sale = item.salePrice();
            if (sale == null || sale <= 0) {
                errors.add(new FieldViolation(field + ".salePrice", "bắt buộc, phải > 0"));
                continue;
            }
            if (channel != null) {
                Long list = product.getPrices().get(channel.getCode());
                if (list == null) {
                    errors.add(new FieldViolation(field + ".sku", "sản phẩm chưa có giá ở kênh " + channel.getCode()));
                    continue;
                }
                if (sale >= list) {
                    errors.add(new FieldViolation(field + ".salePrice",
                            "phải thấp hơn giá niêm yết " + list + " ở kênh " + channel.getCode()));
                    continue;
                }
            }
            result.put(product.getId(), sale);
        }
        return result;
    }

    /** Sản phẩm đã nằm trong flash sale khác đang bật, cùng kênh, giao thời gian thì báo lỗi ở {@code items}. */
    private void checkOverlap(Long selfId, String channel, Instant starts, Instant ends, List<Long> productIds,
                              List<FieldViolation> errors) {
        for (Promotion other : promotions.findOverlappingFlashSales(channel, starts, ends)) {
            if (other.getId().equals(selfId)) {
                continue;
            }
            for (int i = 0; i < productIds.size(); i++) {
                if (other.getItems().containsKey(productIds.get(i))) {
                    errors.add(new FieldViolation("items[" + i + "].sku",
                            "đã có trong flash sale #" + other.getId() + " (" + other.getName() + ") cùng thời gian"));
                }
            }
        }
    }

    private Optional<SalesChannel> channel(String code, List<FieldViolation> errors) {
        if (code == null || code.isBlank()) {
            errors.add(new FieldViolation("channel", "bắt buộc"));
            return Optional.empty();
        }
        Optional<SalesChannel> ch = channels.findById(code.strip().toUpperCase(Locale.ROOT));
        if (ch.isEmpty()) {
            List<String> known = channels.findAllByOrderByOwnedDescCodeAsc().stream().map(SalesChannel::getCode).toList();
            errors.add(new FieldViolation("channel", "kênh không tồn tại, chọn trong " + known));
        }
        return ch;
    }

    private static Type type(String value, List<FieldViolation> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldViolation("type", "bắt buộc: VOUCHER hoặc FLASH_SALE"));
            return null;
        }
        try {
            return Type.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(new FieldViolation("type", "chỉ nhận VOUCHER hoặc FLASH_SALE"));
            return null;
        }
    }

    /** ISO-8601 có offset, hoặc không offset thì hiểu theo giờ Việt Nam (ô datetime-local của form admin). */
    static Instant time(String field, String value, List<FieldViolation> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldViolation(field, "bắt buộc"));
            return null;
        }
        String v = value.strip();
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeException withOffset) {
            try {
                return LocalDateTime.parse(v).atZone(ZONE).toInstant();
            } catch (DateTimeException local) {
                errors.add(new FieldViolation(field, "sai định dạng, dùng 2026-12-12T00:00 hoặc 2026-12-12T00:00:00+07:00"));
                return null;
            }
        }
    }

    private static Specification<Promotion> filter(String channel, Status status, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (channel != null && !channel.isBlank()) {
                where.add(cb.equal(root.get("channelCode"), channel.strip().toUpperCase(Locale.ROOT)));
            }
            if (status != null) {
                var enabled = root.<Boolean>get("enabled");
                var starts = root.<Instant>get("startsAt");
                var ends = root.<Instant>get("endsAt");
                where.add(switch (status) {
                    case DISABLED -> cb.isFalse(enabled);
                    case SCHEDULED -> cb.and(cb.isTrue(enabled), cb.greaterThan(starts, now));
                    case ACTIVE -> cb.and(cb.isTrue(enabled), cb.lessThanOrEqualTo(starts, now), cb.greaterThan(ends, now));
                    case EXPIRED -> cb.and(cb.isTrue(enabled), cb.lessThanOrEqualTo(ends, now));
                });
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private PromotionView view(Promotion p) {
        return view(p, byId(products.findAllById(p.getItems().keySet())), clock.instant());
    }

    /** Dòng flash sale có sản phẩm trong {@code byId} mới được trả (công khai: chỉ sản phẩm đang bán). */
    private static PromotionView view(Promotion p, Map<Long, Product> byId, Instant now) {
        List<PromotionView.Item> items = new ArrayList<>();
        p.getItems().forEach((productId, sale) -> {
            Product product = byId.get(productId);
            if (product != null) {
                Long list = product.getPrices().get(p.getChannelCode());
                // Giá niêm yết có thể bị hạ sau khi tạo flash sale: không trả % âm.
                Integer percent = list == null || list <= 0 ? null : (int) Math.max(0, (list - sale) * 100 / list);
                items.add(new PromotionView.Item(productId, product.getSku(), product.getName(), list, sale, percent));
            }
        });
        return new PromotionView(p.getId(), p.getChannelCode(), p.getType(), p.getName(), p.getVoucherCode(),
                p.getDiscountAmount(), p.getMinOrderAmount(), local(p.getStartsAt()), local(p.getEndsAt()),
                p.status(now), p.isEnabled(), items);
    }

    static OffsetDateTime local(Instant instant) {
        return instant.atZone(ZONE).toOffsetDateTime();
    }

    private static Set<Long> productIds(Collection<Promotion> rows) {
        return rows.stream().flatMap(p -> p.getItems().keySet().stream()).collect(Collectors.toSet());
    }

    private static Map<Long, Product> byId(List<Product> rows) {
        return rows.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}

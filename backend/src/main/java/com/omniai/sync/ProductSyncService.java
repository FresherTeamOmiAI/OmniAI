package com.omniai.sync;

import com.omniai.catalog.Product;
import com.omniai.catalog.ProductChanged;
import com.omniai.catalog.ProductImages;
import com.omniai.catalog.ProductRepository;
import com.omniai.catalog.SalesChannel;
import com.omniai.catalog.SalesChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Khung đồng bộ sản phẩm chiều đi (US-49, T-49.1). Owner: TuTM.
 * <ul>
 *   <li><b>Ghi thay đổi</b>: nghe {@link ProductChanged} trong transaction của catalog, ghi/cập nhật dòng
 *   product_channel_sync PENDING cho mỗi kênh có connector. Nội dung không đổi so với bản đã gửi (cùng hash) thì
 *   bỏ qua: sự kiện trùng không gửi 2 lần. Đổi nhiều lần trước khi gửi thì chỉ gửi bản mới nhất.</li>
 *   <li><b>Gửi</b> ({@link #deliver}): đọc sản phẩm hiện tại, gọi connector ngoài transaction, ghi kết quả.</li>
 *   <li><b>Thử lại</b>: lỗi tạm thời thì chờ {@code retry-backoff} gấp đôi mỗi lần; quá {@code max-attempts} hoặc
 *   lỗi không thử lại được thì FAILED.</li>
 *   <li><b>Giá, tồn</b> (US-50, V19): mỗi kênh có thêm dòng PRICE (giá của đúng kênh đó) và STOCK, gửi riêng bằng
 *   API giá/tồn của sàn khi sản phẩm đã có mã trên sàn. Lần tạo sản phẩm đã kèm giá, tồn nên 2 dòng này được ghi
 *   SYNCED theo đúng giá trị đã gửi.</li>
 * </ul>
 */
@Service
public class ProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final ProductSyncRepository states;
    private final ProductRepository products;
    private final SalesChannelRepository channels;
    private final ProductImages images;
    private final Map<String, ProductChannelConnector> connectors;
    private final SyncProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ProductSyncService(ProductSyncRepository states, ProductRepository products, SalesChannelRepository channels,
                              ProductImages images, List<ProductChannelConnector> connectors, SyncProperties props,
                              PlatformTransactionManager txManager) {
        this.states = states;
        this.products = products;
        this.channels = channels;
        this.images = images;
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(ProductChannelConnector::channel, Function.identity()));
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        this.clock = Clock.systemUTC();
    }

    /** Kênh có connector, cho admin biết cột nào có trạng thái đồng bộ. */
    public List<String> connectedChannels() {
        return connectors.keySet().stream().sorted().toList();
    }

    // ---------- ghi thay đổi ----------

    /** Chạy đồng bộ trong transaction của ProductService: product rollback thì hàng đợi cũng rollback. */
    @EventListener
    @Transactional
    public void onProductChanged(ProductChanged event) {
        products.findById(event.productId()).ifPresent(p -> targetChannels().forEach(c -> enqueueAll(p, c, false)));
    }

    /** Xếp lại mọi sản phẩm của 1 kênh (vd. lần đầu nối kênh, hoặc dữ liệu trên sàn bị xoá). Trả số dòng xếp. */
    @Transactional
    public int resync(String channel) {
        if (!targetChannels().contains(channel)) {
            throw new IllegalArgumentException("Kênh " + channel + " chưa có connector hoặc đang tạm ngừng");
        }
        int queued = 0;
        for (Product p : products.findAll()) {
            if (enqueueAll(p, channel, true)) {
                queued++;
            }
        }
        return queued;
    }

    /** Admin bấm thử lại 1 dòng (thường là FAILED): về PENDING, đếm lại số lần từ 0. */
    @Transactional
    public Optional<ProductSyncState> retry(Long productId, String channel, SyncAspect aspect) {
        return states.findById(new ProductSyncState.Key(productId, channel, aspect)).map(s -> {
            Instant now = clock.instant();
            s.setStatus(SyncStatus.PENDING);
            s.setAttempts(0);
            s.setNextAttemptAt(now);
            s.setUpdatedAt(now);
            return s;
        });
    }

    private List<String> targetChannels() {
        return channels.findAll().stream()
                .filter(c -> c.isActive() && !c.isOwned() && connectors.containsKey(c.getCode())
                        && connectors.get(c.getCode()).ready())
                .map(SalesChannel::getCode)
                .toList();
    }

    /**
     * Liên kết sản phẩm hub với sản phẩm đã có sẵn trên sàn (mã phía sàn), rồi xếp gửi giá, tồn hiện tại.
     * Dùng cho kênh chưa tạo được sản phẩm qua API (Shopee chờ add_item, T-49.2) hoặc shop đã đăng tay từ trước.
     */
    @Transactional
    public List<ProductSyncState> link(Long productId, String channel, String externalId) {
        ProductChannelConnector connector = connectors.get(channel);
        if (connector == null) {
            throw new IllegalArgumentException("Kênh " + channel + " chưa có connector");
        }
        if (externalId == null || externalId.isBlank() || externalId.length() > 64) {
            throw new IllegalArgumentException("externalId phải có 1-64 ký tự");
        }
        Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không có sản phẩm " + productId));
        if (!product.getPrices().containsKey(channel)) {
            throw new IllegalArgumentException("Sản phẩm " + productId + " không bán ở kênh " + channel);
        }
        ProductSnapshot snapshot = ProductSnapshot.of(product, channel, images);
        Instant now = clock.instant();
        ProductSyncState.Key key = new ProductSyncState.Key(productId, channel, SyncAspect.PRODUCT);
        ProductSyncState listing = states.findById(key).orElseGet(() -> new ProductSyncState(key));
        String hash = snapshot.contentHash();
        listing.setExternalId(externalId.trim());
        listing.setStatus(SyncStatus.SYNCED);
        listing.setContentHash(hash);
        listing.setSyncedHash(hash);
        listing.setAttempts(0);
        listing.setLastError(null);
        listing.setNextAttemptAt(now);
        listing.setSyncedAt(now);
        listing.setUpdatedAt(now);
        states.save(listing);
        for (SyncAspect aspect : List.of(SyncAspect.PRICE, SyncAspect.STOCK)) {
            if (connector.aspects().contains(aspect)) {
                enqueue(product.getId(), channel, aspect, snapshot.hashOf(aspect), true, true);
            }
        }
        return states.findByIdProductIdIn(List.of(productId)).stream()
                .filter(st -> st.getId().getChannel().equals(channel)).toList();
    }

    /** Xếp mọi phần connector của kênh hỗ trợ. @return true nếu có dòng được xếp. */
    private boolean enqueueAll(Product product, String channel, boolean force) {
        if (!product.getPrices().containsKey(channel)) {
            return false; // không bán ở kênh này
        }
        ProductChannelConnector connector = connectors.get(channel);
        ProductSnapshot snapshot = ProductSnapshot.of(product, channel, images);
        boolean queued = false;
        if (connector.aspects().contains(SyncAspect.PRODUCT)) {
            queued = enqueue(product.getId(), channel, SyncAspect.PRODUCT, snapshot.contentHash(), force,
                    product.isActive());
        }
        // Giá, tồn chỉ gửi riêng khi sàn đã có sản phẩm; chưa có thì lần tạo sẽ mang theo.
        if (listingId(product.getId(), channel) != null) {
            for (SyncAspect aspect : List.of(SyncAspect.PRICE, SyncAspect.STOCK)) {
                if (connector.aspects().contains(aspect)) {
                    queued |= enqueue(product.getId(), channel, aspect, snapshot.hashOf(aspect), force, true);
                }
            }
        }
        return queued;
    }

    /** Mã sản phẩm phía sàn (dòng PRODUCT), null nếu chưa có. */
    private String listingId(Long productId, String channel) {
        return states.findById(new ProductSyncState.Key(productId, channel, SyncAspect.PRODUCT))
                .map(ProductSyncState::getExternalId).orElse(null);
    }

    /**
     * @param createIfMissing false = chưa có dòng thì bỏ qua (vd. sản phẩm ẩn chưa từng lên sàn)
     * @return true nếu dòng được xếp (mới hoặc đổi sang PENDING).
     */
    private boolean enqueue(Long productId, String channel, SyncAspect aspect, String hash, boolean force,
                            boolean createIfMissing) {
        Instant now = clock.instant();
        ProductSyncState.Key key = new ProductSyncState.Key(productId, channel, aspect);
        ProductSyncState state = states.findById(key).orElse(null);
        if (state == null) {
            if (!createIfMissing) {
                return false; // chưa từng lên sàn và đang ẩn: không có gì để gửi
            }
            state = new ProductSyncState(key);
        } else if (!force && (hash.equals(state.getSyncedHash()) && state.getStatus() == SyncStatus.SYNCED
                || hash.equals(state.getContentHash()) && state.getStatus() == SyncStatus.PENDING)) {
            return false; // sự kiện trùng: sàn đã có (hoặc sắp nhận) đúng nội dung này
        }
        state.setStatus(SyncStatus.PENDING);
        state.setContentHash(hash);
        state.setAttempts(0);
        state.setLastError(null);
        state.setNextAttemptAt(now);
        state.setUpdatedAt(now);
        states.save(state);
        return true;
    }

    // ---------- gửi ----------

    /** Nhận các dòng đến hạn (transaction ngắn, giữ chỗ bằng lease). */
    public List<ProductSyncRepository.Claimed> claimDue() {
        Instant now = clock.instant();
        List<ProductSyncRepository.Claimed> claimed =
                tx.execute(s -> states.claimDue(now, now.plus(props.lease()), props.batchSize()));
        return claimed == null ? List.of() : claimed;
    }

    private record Attempt(ProductSnapshot snapshot, String externalId, int attempts, String hash) {
    }

    /** Gửi 1 dòng đã nhận. Gọi connector ngoài transaction để không giữ kết nối DB trong lúc chờ sàn. */
    public void deliver(ProductSyncRepository.Claimed claimed) {
        SyncAspect aspect = SyncAspect.valueOf(claimed.getAspect());
        ProductSyncState.Key key = new ProductSyncState.Key(claimed.getProductId(), claimed.getChannelCode(), aspect);
        Attempt attempt = tx.execute(s -> states.findById(key)
                .filter(st -> st.getStatus() == SyncStatus.PENDING)
                .flatMap(st -> products.findById(key.getProductId()).map(p -> {
                    ProductSnapshot snapshot = ProductSnapshot.of(p, key.getChannel(), images);
                    String externalId = aspect == SyncAspect.PRODUCT ? st.getExternalId()
                            : listingId(key.getProductId(), key.getChannel());
                    return new Attempt(snapshot, externalId, st.getAttempts() + 1, snapshot.hashOf(aspect));
                }))
                .orElse(null));
        if (attempt == null) {
            return; // dòng đã bị xử lý / sản phẩm đã xoá
        }
        ProductChannelConnector connector = connectors.get(key.getChannel());
        try {
            if (connector == null) {
                throw new SyncException("Không có connector cho kênh " + key.getChannel(), false);
            }
            String externalId = attempt.externalId();
            switch (aspect) {
                case PRODUCT -> externalId = connector.push(attempt.snapshot(), externalId);
                case PRICE, STOCK -> {
                    if (externalId == null) {
                        throw new SyncException("Sản phẩm chưa có trên " + key.getChannel()
                                + " (chưa tạo hoặc chưa liên kết mã sàn)", false);
                    }
                    if (aspect == SyncAspect.PRICE) {
                        connector.pushPrice(attempt.snapshot(), externalId);
                    } else {
                        connector.pushStock(attempt.snapshot(), externalId);
                    }
                }
            }
            String sentId = externalId;
            tx.executeWithoutResult(s -> markSynced(key, attempt, sentId));
        } catch (RuntimeException e) {
            boolean retryable = !(e instanceof SyncException se) || se.retryable();
            tx.executeWithoutResult(s -> markFailed(key, attempt, e, retryable));
        }
    }

    private void markSynced(ProductSyncState.Key key, Attempt attempt, String externalId) {
        states.findById(key).ifPresent(s -> {
            Instant now = clock.instant();
            s.setSyncedHash(attempt.hash());
            if (externalId != null && !externalId.isBlank()) {
                s.setExternalId(externalId);
            }
            s.setSyncedAt(now);
            s.setUpdatedAt(now);
            s.setLastError(null);
            s.setAttempts(attempt.attempts());
            if (attempt.hash().equals(s.getContentHash())) {
                s.setStatus(SyncStatus.SYNCED);
            } else {
                // Admin sửa tiếp trong lúc đang gửi: còn bản mới hơn, gửi tiếp ngay.
                s.setStatus(SyncStatus.PENDING);
                s.setAttempts(0);
                s.setNextAttemptAt(now);
            }
            log.info("Đồng bộ {} {} lên {}: xong sau {} lần (external_id={})", key.getAspect(), attempt.snapshot().sku(),
                    key.getChannel(), attempt.attempts(), attempt.externalId() != null ? attempt.externalId() : externalId);
        });
        if (key.getAspect() == SyncAspect.PRODUCT) {
            recordPriceStockSentWithProduct(key, attempt.snapshot());
        }
    }

    /**
     * Tạo/sửa sản phẩm gửi kèm giá, tồn: ghi dòng PRICE, STOCK là đã gửi đúng giá trị trong snapshot. Nếu giá/tồn
     * hiện tại đã khác (đổi trong lúc đang gửi) thì dòng đó về PENDING để gửi tiếp.
     */
    private void recordPriceStockSentWithProduct(ProductSyncState.Key key, ProductSnapshot sent) {
        ProductChannelConnector connector = connectors.get(key.getChannel());
        Product current = products.findById(key.getProductId()).orElse(null);
        if (connector == null || current == null) {
            return;
        }
        ProductSnapshot now = ProductSnapshot.of(current, key.getChannel(), images);
        Instant at = clock.instant();
        for (SyncAspect aspect : List.of(SyncAspect.PRICE, SyncAspect.STOCK)) {
            if (!connector.aspects().contains(aspect)) {
                continue;
            }
            ProductSyncState.Key k = new ProductSyncState.Key(key.getProductId(), key.getChannel(), aspect);
            ProductSyncState st = states.findById(k).orElseGet(() -> new ProductSyncState(k));
            String sentHash = sent.hashOf(aspect);
            String currentHash = now.hashOf(aspect);
            st.setSyncedHash(sentHash);
            st.setSyncedAt(at);
            st.setContentHash(currentHash);
            st.setStatus(sentHash.equals(currentHash) ? SyncStatus.SYNCED : SyncStatus.PENDING);
            st.setAttempts(0);
            st.setLastError(null);
            st.setNextAttemptAt(at);
            st.setUpdatedAt(at);
            states.save(st);
        }
    }

    private void markFailed(ProductSyncState.Key key, Attempt attempt, RuntimeException error, boolean retryable) {
        states.findById(key).ifPresent(s -> {
            Instant now = clock.instant();
            s.setAttempts(attempt.attempts());
            s.setLastError(truncate(error.getMessage() == null ? error.toString() : error.getMessage()));
            s.setUpdatedAt(now);
            if (!attempt.hash().equals(s.getContentHash())) {
                // Admin đã sửa tiếp trong lúc đang gửi: bản mới chưa gửi lần nào, đếm lại từ đầu.
                s.setStatus(SyncStatus.PENDING);
                s.setAttempts(0);
                s.setNextAttemptAt(now);
            } else if (retryable && attempt.attempts() < props.maxAttempts()) {
                long delay = props.retryBackoff().toMillis() << (attempt.attempts() - 1);
                s.setStatus(SyncStatus.PENDING);
                s.setNextAttemptAt(now.plusMillis(delay));
                log.warn("Đồng bộ {} lên {} lỗi lần {}/{}, thử lại sau {} ms: {}", attempt.snapshot().sku(),
                        key.getChannel(), attempt.attempts(), props.maxAttempts(), delay, s.getLastError());
            } else {
                s.setStatus(SyncStatus.FAILED);
                s.setNextAttemptAt(now);
                log.error("Đồng bộ {} lên {} thất bại sau {} lần: {}", attempt.snapshot().sku(), key.getChannel(),
                        attempt.attempts(), s.getLastError());
            }
        });
    }

    private static String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    // ---------- đọc cho admin ----------

    @Transactional(readOnly = true)
    public List<SyncView> ofProducts(Collection<Long> productIds) {
        return states.findByIdProductIdIn(productIds).stream().map(SyncView::of).toList();
    }

    /** Danh sách theo trạng thái / kênh (null = mọi), mới cập nhật trước. */
    @Transactional(readOnly = true)
    public Page<SyncView> list(SyncStatus status, String channel, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt")));
        Page<ProductSyncState> result;
        if (status != null && channel != null) {
            result = states.findByStatusAndIdChannel(status, channel, pageable);
        } else if (status != null) {
            result = states.findByStatus(status, pageable);
        } else if (channel != null) {
            result = states.findByIdChannel(channel, pageable);
        } else {
            result = states.findAll(pageable);
        }
        return result.map(SyncView::of);
    }
}

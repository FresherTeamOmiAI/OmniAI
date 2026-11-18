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
        products.findById(event.productId()).ifPresent(p -> targetChannels().forEach(c -> enqueue(p, c, false)));
    }

    /** Xếp lại mọi sản phẩm của 1 kênh (vd. lần đầu nối kênh, hoặc dữ liệu trên sàn bị xoá). Trả số dòng xếp. */
    @Transactional
    public int resync(String channel) {
        if (!targetChannels().contains(channel)) {
            throw new IllegalArgumentException("Kênh " + channel + " chưa có connector hoặc đang tạm ngừng");
        }
        int queued = 0;
        for (Product p : products.findAll()) {
            if (enqueue(p, channel, true)) {
                queued++;
            }
        }
        return queued;
    }

    /** Admin bấm thử lại 1 dòng (thường là FAILED): về PENDING, đếm lại số lần từ 0. */
    @Transactional
    public Optional<ProductSyncState> retry(Long productId, String channel) {
        return states.findById(new ProductSyncState.Key(productId, channel)).map(s -> {
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
                .filter(c -> c.isActive() && !c.isOwned() && connectors.containsKey(c.getCode()))
                .map(SalesChannel::getCode)
                .toList();
    }

    /** @return true nếu dòng được xếp (mới hoặc đổi sang PENDING). */
    private boolean enqueue(Product product, String channel, boolean force) {
        if (!product.getPrices().containsKey(channel)) {
            return false; // không bán ở kênh này
        }
        String hash = ProductSnapshot.of(product, channel, images).contentHash();
        Instant now = clock.instant();
        ProductSyncState.Key key = new ProductSyncState.Key(product.getId(), channel);
        ProductSyncState state = states.findById(key).orElse(null);
        if (state == null) {
            if (!product.isActive()) {
                return false; // chưa từng lên sàn và đang ẩn: không có gì để gửi
            }
            state = new ProductSyncState(product.getId(), channel);
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
        ProductSyncState.Key key = new ProductSyncState.Key(claimed.getProductId(), claimed.getChannelCode());
        Attempt attempt = tx.execute(s -> states.findById(key)
                .filter(st -> st.getStatus() == SyncStatus.PENDING)
                .flatMap(st -> products.findById(key.getProductId()).map(p -> {
                    ProductSnapshot snapshot = ProductSnapshot.of(p, key.getChannel(), images);
                    return new Attempt(snapshot, st.getExternalId(), st.getAttempts() + 1, snapshot.contentHash());
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
            String externalId = connector.push(attempt.snapshot(), attempt.externalId());
            tx.executeWithoutResult(s -> markSynced(key, attempt, externalId));
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
            log.info("Đồng bộ {} lên {}: xong sau {} lần (external_id={})", attempt.snapshot().sku(), key.getChannel(),
                    attempt.attempts(), s.getExternalId());
        });
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

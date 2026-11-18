package com.omniai.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Quét hàng đợi product_channel_sync mỗi {@code omniai.sync.poll-interval-ms} và gửi các dòng đến hạn. */
@Component
public class ProductSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncWorker.class);

    private final ProductSyncService service;
    private final SyncProperties props;

    public ProductSyncWorker(ProductSyncService service, SyncProperties props) {
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${omniai.sync.poll-interval-ms:1000}",
            initialDelayString = "${omniai.sync.initial-delay-ms:5000}")
    public void tick() {
        if (!props.enabled()) {
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException e) {
            // DB tạm lỗi: lượt sau quét lại, không để scheduler dừng.
            log.error("Lượt đồng bộ sản phẩm lỗi: {}", e.toString());
        }
    }

    /** 1 lượt: nhận các dòng đến hạn rồi gửi lần lượt. Trả số dòng đã xử lý. */
    public int runOnce() {
        List<ProductSyncRepository.Claimed> due = service.claimDue();
        due.forEach(service::deliver);
        return due.size();
    }
}

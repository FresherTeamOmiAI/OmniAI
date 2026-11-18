package com.omniai.sync;

import com.omniai.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trạng thái đồng bộ sản phẩm ra sàn cho admin (US-49, T-49.1; màn ở T-49.3). Nằm dưới /api/admin/** nên cần
 * Basic ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/sync")
public class AdminSyncController {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_IDS = 100;

    private final ProductSyncService service;

    public AdminSyncController(ProductSyncService service) {
        this.service = service;
    }

    /** Kênh có connector đồng bộ (mã sales_channel). */
    @GetMapping("/channels")
    public List<String> channels() {
        return service.connectedChannels();
    }

    /** Trạng thái của các sản phẩm {@code ids=1,2,3} (cho 1 trang bảng sản phẩm, tối đa 100 id). */
    @GetMapping("/products/by-ids")
    public List<SyncView> byProducts(@RequestParam List<Long> ids) {
        if (ids.isEmpty() || ids.size() > MAX_IDS) {
            throw bad("ids phải có 1 đến " + MAX_IDS + " id");
        }
        return service.ofProducts(ids);
    }

    /** Danh sách phân trang, lọc {@code status}, {@code channel}, mới cập nhật trước. */
    @GetMapping("/products")
    public PageResponse<SyncView> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String channel,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw bad("page phải >= 0, size từ 1 đến " + MAX_PAGE_SIZE);
        }
        return PageResponse.of(service.list(parseStatus(status), normalize(channel), page, size));
    }

    /** Đưa 1 dòng về PENDING để gửi lại ngay (thường sau khi admin sửa lỗi dữ liệu). aspect mặc định PRODUCT. */
    @PostMapping("/products/{productId}/{channel}/retry")
    public SyncView retry(@PathVariable Long productId, @PathVariable String channel,
                          @RequestParam(defaultValue = "PRODUCT") String aspect) {
        return service.retry(productId, normalize(channel), parseAspect(aspect)).map(SyncView::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sản phẩm " + productId + " chưa có trạng thái đồng bộ trên " + channel));
    }

    public record LinkRequest(String externalId) {
    }

    /**
     * Liên kết sản phẩm hub với mã sản phẩm đã có trên sàn (US-50): sau đó hub gửi giá, tồn hiện tại và mọi lần đổi
     * giá/tồn sau này. Trả các dòng trạng thái của sản phẩm trên kênh đó.
     */
    @PostMapping("/products/{productId}/{channel}/link")
    public List<SyncView> link(@PathVariable Long productId, @PathVariable String channel,
                               @RequestBody LinkRequest body) {
        try {
            return service.link(productId, normalize(channel), body == null ? null : body.externalId()).stream()
                    .map(SyncView::of).toList();
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
    }

    /** Xếp lại mọi sản phẩm của 1 kênh. */
    @PostMapping("/products/resync")
    public Map<String, Integer> resync(@RequestParam String channel) {
        try {
            return Map.of("queued", service.resync(normalize(channel)));
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
    }

    private static SyncStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("status phải là PENDING, SYNCED hoặc FAILED");
        }
    }

    private static SyncAspect parseAspect(String aspect) {
        try {
            return SyncAspect.valueOf(aspect.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("aspect phải là PRODUCT, PRICE hoặc STOCK");
        }
    }

    private static String normalize(String channel) {
        return channel == null || channel.isBlank() ? null : channel.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

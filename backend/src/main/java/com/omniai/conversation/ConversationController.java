package com.omniai.conversation;

import com.omniai.common.ChannelType;
import com.omniai.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Xem lại hội thoại mọi kênh (US-11, T-11.2). Cần đăng nhập ADMIN (SecurityConfig) vì là dữ liệu cá nhân của khách.
 * Màn admin xem hội thoại ở US-14. Owner: TuTM.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    static final int MAX_PAGE_SIZE = 100;

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    /**
     * Lọc (T-14.2), kết hợp AND: {@code channel}; {@code from}/{@code to} là ngày {@code yyyy-MM-dd} giờ VN, gồm cả 2
     * mốc, lấy hội thoại có hoạt động trong khoảng; {@code hasViolation=true} chỉ lấy hội thoại có câu bot bị policy
     * can thiệp; {@code handoff=true} chỉ lấy hội thoại có câu chuyển nhân viên. Sai định dạng hoặc from > to: 400.
     */
    @GetMapping
    public PageResponse<ConversationView> list(@RequestParam(required = false) String channel,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "false") boolean hasViolation,
                                               @RequestParam(defaultValue = "false") boolean handoff,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from phải <= to");
        }
        ConversationFilter filter = new ConversationFilter(parseChannel(channel), from, to, hasViolation, handoff);
        return PageResponse.of(service.list(filter, checkPage(page), checkSize(size)));
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageView> messages(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return service.messages(id, checkPage(page), checkSize(size))
                .map(PageResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có hội thoại " + id));
    }

    /** Không phân biệt hoa thường (web, WEB); bỏ trống = mọi kênh. */
    static ChannelType parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return ChannelType.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh không hợp lệ: " + channel);
        }
    }

    private static int checkPage(int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page phải >= 0");
        }
        return page;
    }

    private static int checkSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        return size;
    }
}

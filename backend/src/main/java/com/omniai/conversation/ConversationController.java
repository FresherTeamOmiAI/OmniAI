package com.omniai.conversation;

import com.omniai.catalog.ProductController.PageResponse;
import com.omniai.common.ChannelType;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping
    public PageResponse<ConversationView> list(@RequestParam(required = false) String channel,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return toResponse(service.list(parseChannel(channel), checkPage(page), checkSize(size)));
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageView> messages(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return service.messages(id, checkPage(page), checkSize(size))
                .map(ConversationController::toResponse)
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

    private static <T> PageResponse<T> toResponse(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}

package com.omniai.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Một trang kết quả cho mọi API danh sách (catalog, hội thoại...). JSON: {@code {items, page, size, total}}. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}

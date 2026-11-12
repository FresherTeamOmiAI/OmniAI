package com.omniai.sim.tiktok.cs;

import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.TiktokErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Cursor phân trang page_token / next_page_token. Hết trang thì next_page_token = "" (docv2). */
final class PageToken {

    private static final String PREFIX = "off:";

    private PageToken() {
    }

    record Page<T>(List<T> items, String nextPageToken) {
    }

    static <T> Page<T> slice(List<T> all, String pageToken, int pageSize) {
        int offset = decode(pageToken);
        if (offset > all.size()) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "page_token is out of range.");
        }
        int end = Math.min(all.size(), offset + pageSize);
        String next = end < all.size() ? encode(end) : "";
        return new Page<>(List.copyOf(all.subList(offset, end)), next);
    }

    static void requirePageSize(Integer pageSize, int max) {
        if (pageSize == null || pageSize < 1 || pageSize > max) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "page_size must be between 1 and " + max + ".");
        }
    }

    private static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decode(String token) {
        if (token == null || token.isEmpty()) {
            return 0;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (!raw.startsWith(PREFIX)) {
                throw new IllegalArgumentException(raw);
            }
            int offset = Integer.parseInt(raw.substring(PREFIX.length()));
            if (offset < 0) {
                throw new IllegalArgumentException(raw);
            }
            return offset;
        } catch (IllegalArgumentException e) {
            throw new ApiException(TiktokErrorCode.CS_INVALID_PARAMS, "page_token is invalid.");
        }
    }
}

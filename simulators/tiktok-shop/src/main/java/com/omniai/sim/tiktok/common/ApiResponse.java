package com.omniai.sim.tiktok.common;

import java.util.Map;
import java.util.UUID;

/** Envelope chung của mọi API TikTok Shop: {code, message, request_id, data}. code = 0 là thành công. */
public record ApiResponse<T>(int code, String message, String requestId, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "Success", newRequestId(), data);
    }

    public static ApiResponse<Map<String, Object>> okEmpty() {
        return ok(Map.of());
    }

    public static ApiResponse<Map<String, Object>> error(TiktokErrorCode error, String detail) {
        String message = detail == null || detail.isBlank() ? error.message() : error.message() + " " + detail;
        return new ApiResponse<>(error.code(), message, newRequestId(), Map.of());
    }

    /** Giống dạng request_id thật: chuỗi hex in hoa 34 ký tự. */
    static String newRequestId() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 34).toUpperCase();
    }
}

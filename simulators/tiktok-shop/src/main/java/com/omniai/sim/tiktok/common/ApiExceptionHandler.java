package com.omniai.sim.tiktok.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

/** Đổi lỗi thành envelope TikTok. Thiếu/sai tham số thì trả mã invalid_params của nhóm API (CS có mã riêng). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Map<String, Object>>> api(ApiException e) {
        return toResponse(e.error(), e.detail());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, Object>>> invalidBody(MethodArgumentNotValidException e,
                                                                 HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return toResponse(invalidParamsFor(request), detail);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Map<String, Object>>> invalidRequest(Exception e, HttpServletRequest request) {
        String detail = e instanceof MissingServletRequestParameterException m
                ? m.getParameterName() + " is required"
                : e instanceof MethodArgumentTypeMismatchException t ? t.getName() + " has wrong type" : "Malformed body";
        return toResponse(invalidParamsFor(request), detail);
    }

    static TiktokErrorCode invalidParamsFor(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/customer_service/")
                ? TiktokErrorCode.CS_INVALID_PARAMS
                : TiktokErrorCode.INVALID_PARAMS;
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> toResponse(TiktokErrorCode error, String detail) {
        return ResponseEntity.status(error.httpStatus()).body(ApiResponse.error(error, detail));
    }
}

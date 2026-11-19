package com.omniai.order;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Lỗi của /api/orders, /api/me/orders, /api/internal/customers/{id}/orders theo ProblemDetail (RFC 9457), cùng dạng với
 * API sản phẩm: {@code errors: [{field, message}]}.
 */
@RestControllerAdvice(assignableTypes = {OrderController.class, MeOrderController.class, InternalOrderController.class})
class OrderExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return withErrors("Không đặt được đơn", e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalidOrder(Invalid e, HttpServletRequest request) {
        String title = "GET".equals(request.getMethod()) ? "Tham số không hợp lệ" : "Không đặt được đơn";
        return withErrors(title, e.violations());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(OrderService.OrderNotFound.class)
    ProblemDetail notFound(OrderService.OrderNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    @ExceptionHandler(OrderService.CustomerGone.class)
    ProblemDetail customerGone(OrderService.CustomerGone e) {
        return problem(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập", e.getMessage());
    }

    /** Khách không còn: /api/me trả 401 như token hết hiệu lực (giống hồ sơ da), API nội bộ trả 404. */
    @ExceptionHandler(OrderHistoryService.CustomerNotFound.class)
    ProblemDetail customerNotFound(OrderHistoryService.CustomerNotFound e, HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/me")) {
            return problem(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập", "Tài khoản không còn, hãy đăng nhập lại");
        }
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    private static ProblemDetail withErrors(String title, List<FieldViolation> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, title, "Có " + errors.size() + " lỗi");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

package com.omniai.order;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/** Lỗi của /api/orders theo ProblemDetail (RFC 9457), cùng dạng với API sản phẩm: {@code errors: [{field, message}]}. */
@RestControllerAdvice(assignableTypes = OrderController.class)
class OrderExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return withErrors(e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalidOrder(Invalid e) {
        return withErrors(e.violations());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(OrderService.OrderNotFound.class)
    ProblemDetail notFound(OrderService.OrderNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    private static ProblemDetail withErrors(List<FieldViolation> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Không đặt được đơn", "Có " + errors.size() + " lỗi");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

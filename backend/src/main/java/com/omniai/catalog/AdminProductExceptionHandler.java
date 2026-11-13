package com.omniai.catalog;

import com.omniai.catalog.ProductErrors.DuplicateSku;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.catalog.ProductErrors.NotFound;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Lỗi của API sản phẩm (admin, và bộ lọc store T-09.2) theo ProblemDetail (RFC 9457):
 * {@code {type, title, status, detail, instance, errors?: [{field, message}]}}.
 */
@RestControllerAdvice(assignableTypes = {AdminProductController.class, ProductController.class})
class AdminProductExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        List<FieldViolation> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return withErrors(errors);
    }

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalidProduct(Invalid e) {
        return withErrors(e.violations());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            PropertyReferenceException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ",
                e instanceof HttpMessageNotReadableException ? "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu" : e.getMessage());
    }

    @ExceptionHandler(NotFound.class)
    ProblemDetail notFound(NotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    @ExceptionHandler(DuplicateSku.class)
    ProblemDetail duplicateSku(DuplicateSku e) {
        return problem(HttpStatus.CONFLICT, "Trùng SKU", e.getMessage());
    }

    private static ProblemDetail withErrors(List<FieldViolation> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", "Có " + errors.size() + " trường sai");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

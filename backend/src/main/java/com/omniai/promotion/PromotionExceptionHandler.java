package com.omniai.promotion;

import com.omniai.catalog.ProductErrors;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.promotion.PromotionErrors.DuplicateVoucher;
import com.omniai.promotion.PromotionErrors.Invalid;
import com.omniai.promotion.PromotionErrors.NotFound;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/** Lỗi của API khuyến mãi và lịch sử giá theo ProblemDetail, cùng hình dạng với API sản phẩm. */
@RestControllerAdvice(assignableTypes = {AdminPromotionController.class, PromotionController.class,
        AdminPriceHistoryController.class})
class PromotionExceptionHandler {

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalid(Invalid e) {
        return withErrors(e.violations());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return withErrors(e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            PropertyReferenceException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ",
                e instanceof HttpMessageNotReadableException ? "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu" : e.getMessage());
    }

    @ExceptionHandler({NotFound.class, ProductErrors.NotFound.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    @ExceptionHandler(DuplicateVoucher.class)
    ProblemDetail duplicate(DuplicateVoucher e) {
        return problem(HttpStatus.CONFLICT, "Trùng mã voucher", e.getMessage());
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

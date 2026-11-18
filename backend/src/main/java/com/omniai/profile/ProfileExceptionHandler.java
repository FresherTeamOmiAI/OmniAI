package com.omniai.profile;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Lỗi của API hồ sơ da theo ProblemDetail, cùng dạng {@code errors: [{field, message}]} như đơn hàng, sản phẩm.
 * Khách không còn trong DB: với /api/me (token của khách đã xoá) là 401, với /api/internal là 404.
 */
@RestControllerAdvice(assignableTypes = {MeProfileController.class, InternalProfileController.class})
class ProfileExceptionHandler {

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalid(Invalid e) {
        return withErrors(e.violations());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(SkinProfileService.ProfileNotFound.class)
    ProblemDetail notFound(SkinProfileService.ProfileNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Chưa có hồ sơ", e.getMessage());
    }

    @ExceptionHandler(SkinProfileService.CustomerNotFound.class)
    ProblemDetail customerNotFound(SkinProfileService.CustomerNotFound e,
                                   HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/me")) {
            return problem(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập", "Tài khoản không còn, hãy đăng nhập lại");
        }
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    private static ProblemDetail withErrors(List<FieldViolation> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Hồ sơ da không hợp lệ", "Có " + errors.size() + " lỗi");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

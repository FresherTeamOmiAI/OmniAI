package com.omniai.identity;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.identity.LinkErrors.CustomerGone;
import com.omniai.identity.LinkErrors.IdentityAlreadyLinked;
import com.omniai.identity.LinkErrors.LinkCodeExpired;
import com.omniai.identity.LinkErrors.LinkCodeInvalid;
import com.omniai.identity.LinkErrors.LinkError;
import com.omniai.identity.LinkErrors.LinkLocked;
import com.omniai.identity.LinkErrors.LinkRequestNotFound;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Lỗi của mã liên kết OMNI theo ProblemDetail, có {@code code} như /api/auth:
 * {@code link_code_invalid} (404, kèm {@code attemptsLeft}), {@code link_code_expired} (410),
 * {@code link_locked} (423, kèm {@code retryAfterSeconds} và header {@code Retry-After}),
 * {@code identity_already_linked} (409). Sai dữ liệu là 400 {@code errors[{field, message}]}.
 */
@RestControllerAdvice(assignableTypes = {MeLinkController.class, InternalLinkController.class})
class LinkExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return withErrors(e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(Invalid.class)
    ProblemDetail invalid(Invalid e) {
        return withErrors(e.violations());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(LinkError.class)
    ResponseEntity<ProblemDetail> linkError(LinkError e) {
        HttpStatus status = switch (e) {
            case LinkCodeInvalid ignored -> HttpStatus.NOT_FOUND;
            case LinkCodeExpired ignored -> HttpStatus.GONE;
            case LinkLocked ignored -> HttpStatus.LOCKED;
            case IdentityAlreadyLinked ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = problem(status, "Không liên kết được", e.getMessage());
        pd.setProperty("code", e.code());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (e instanceof LinkCodeInvalid i) {
            pd.setProperty("attemptsLeft", i.attemptsLeft());
        } else if (e instanceof LinkCodeExpired x && x.attemptsLeft() != null) {
            pd.setProperty("attemptsLeft", x.attemptsLeft());
        } else if (e instanceof LinkLocked l) {
            pd.setProperty("retryAfterSeconds", l.retryAfterSeconds());
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(l.retryAfterSeconds()));
        }
        return builder.body(pd);
    }

    /** 2 khách cùng xác nhận 1 danh tính sàn cùng lúc: người sau dính unique (channel, external_id). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return linkError(new IdentityAlreadyLinked()).getBody();
    }

    @ExceptionHandler(LinkRequestNotFound.class)
    ProblemDetail notFound(LinkRequestNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy", e.getMessage());
    }

    @ExceptionHandler(CustomerGone.class)
    ProblemDetail customerGone(CustomerGone e) {
        return problem(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập", e.getMessage());
    }

    private static ProblemDetail withErrors(List<FieldViolation> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", "Có " + errors.size() + " lỗi");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

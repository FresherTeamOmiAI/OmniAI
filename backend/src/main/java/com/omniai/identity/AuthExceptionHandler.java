package com.omniai.identity;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.identity.AuthErrors.AuthError;
import com.omniai.identity.AuthErrors.OtpInvalid;
import com.omniai.identity.AuthErrors.OtpLocked;
import com.omniai.identity.AuthErrors.OtpRateLimited;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Lỗi của /api/auth theo ProblemDetail (RFC 9457). Lỗi nghiệp vụ có {@code code} để web hiện đúng câu:
 * {@code otp_invalid} (401, kèm {@code attemptsLeft}), {@code otp_expired} (401), {@code otp_locked} (423),
 * {@code otp_rate_limited} (429). 423 và 429 có {@code retryAfterSeconds} và header {@code Retry-After}.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        List<FieldViolation> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", "Có " + errors.size() + " lỗi");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail badRequest(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", "Body không phải JSON hợp lệ hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(AuthError.class)
    ResponseEntity<ProblemDetail> authError(AuthError e) {
        HttpStatus status;
        Long retryAfter = null;
        if (e instanceof OtpRateLimited r) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            retryAfter = r.retryAfterSeconds();
        } else if (e instanceof OtpLocked l) {
            status = HttpStatus.LOCKED;
            retryAfter = l.retryAfterSeconds();
        } else {
            status = HttpStatus.UNAUTHORIZED;
        }
        ProblemDetail pd = problem(status, "Không đăng nhập được", e.getMessage());
        pd.setProperty("code", e.code());
        if (e instanceof OtpInvalid i) {
            pd.setProperty("attemptsLeft", i.attemptsLeft());
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (retryAfter != null) {
            pd.setProperty("retryAfterSeconds", retryAfter);
            builder.header(HttpHeaders.RETRY_AFTER, retryAfter.toString());
        }
        return builder.body(pd);
    }

    @ExceptionHandler(MailException.class)
    ProblemDetail mailFailed(MailException e) {
        log.error("Không gửi được email OTP: {}", e.getMessage());
        ProblemDetail pd = problem(HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được mã", "Chưa gửi được email, hãy thử lại sau");
        pd.setProperty("code", "otp_send_failed");
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}

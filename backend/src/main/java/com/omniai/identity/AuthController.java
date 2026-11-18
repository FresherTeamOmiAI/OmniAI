package com.omniai.identity;

import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Đăng ký/đăng nhập người mua bằng email + OTP (US-13). {@code /api/auth/**} mở công khai;
 * {@code /api/me} cần {@code Authorization: Bearer <token>} (SecurityConfig).
 */
@RestController
public class AuthController {

    public record OtpRequest(
            @NotBlank(message = "Nhập email") @Email(message = "Email không hợp lệ") @Size(max = 255) String email,
            @NotNull(message = "Cần đồng ý xử lý dữ liệu cá nhân")
            @AssertTrue(message = "Cần đồng ý xử lý dữ liệu cá nhân") Boolean consent) {
    }

    public record VerifyRequest(
            @NotBlank(message = "Nhập email") @Email(message = "Email không hợp lệ") @Size(max = 255) String email,
            @NotBlank(message = "Nhập mã") @Pattern(regexp = "\\d{6}", message = "Mã gồm 6 chữ số") String code) {
    }

    public record CustomerView(Long id, String email, String displayName) {
        static CustomerView of(Customer c) {
            return new CustomerView(c.getId(), c.getEmail(), c.getDisplayName());
        }
    }

    public record LoginView(String token, Instant expiresAt, CustomerView customer, boolean newCustomer) {
    }

    private final OtpService otp;
    private final CustomerRepository customers;

    public AuthController(OtpService otp, CustomerRepository customers) {
        this.otp = otp;
        this.customers = customers;
    }

    @PostMapping("/api/auth/otp")
    public ResponseEntity<OtpService.OtpIssued> requestOtp(@Valid @RequestBody OtpRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(otp.request(request.email()));
    }

    @PostMapping("/api/auth/otp/verify")
    public LoginView verify(@Valid @RequestBody VerifyRequest request) {
        OtpService.LoginResult result = otp.verify(request.email(), request.code());
        return new LoginView(result.token().token(), result.token().expiresAt(),
                CustomerView.of(result.customer()), result.newCustomer());
    }

    @GetMapping("/api/me")
    public ResponseEntity<CustomerView> me(@AuthenticationPrincipal Jwt jwt) {
        return customers.findById(Long.valueOf(jwt.getSubject()))
                .map(CustomerView::of)
                .map(ResponseEntity::ok)
                // Token hợp lệ nhưng khách đã bị xoá (US-30): coi như chưa đăng nhập.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}

package com.omniai.promotion;

import com.omniai.catalog.ProductErrors.FieldViolation;

import java.util.List;

/** Lỗi nghiệp vụ của khuyến mãi. {@link PromotionExceptionHandler} đổi sang ProblemDetail 400/404/409. */
public final class PromotionErrors {

    private PromotionErrors() {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(Long id) {
            super("Không có khuyến mãi id=" + id);
        }
    }

    public static class DuplicateVoucher extends RuntimeException {
        public DuplicateVoucher(String channel, String code) {
            super("Kênh " + channel + " đã có voucher " + code);
        }
    }

    public static class Invalid extends RuntimeException {
        private final transient List<FieldViolation> violations;

        public Invalid(List<FieldViolation> violations) {
            super("Dữ liệu khuyến mãi không hợp lệ");
            this.violations = List.copyOf(violations);
        }

        public List<FieldViolation> violations() {
            return violations;
        }
    }
}

package com.omniai.catalog;

import java.util.List;

/** Lỗi nghiệp vụ của catalog. AdminProductExceptionHandler đổi sang ProblemDetail 400/404/409. */
public final class ProductErrors {

    private ProductErrors() {
    }

    /** Một lỗi gắn với một field của body, trả về trong {@code errors[]}. */
    public record FieldViolation(String field, String message) {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(Long id) {
            super("Không có sản phẩm id=" + id);
        }
    }

    public static class DuplicateSku extends RuntimeException {
        public DuplicateSku(String sku) {
            super("SKU " + sku + " đã tồn tại");
        }
    }

    public static class Invalid extends RuntimeException {
        private final transient List<FieldViolation> violations;

        public Invalid(List<FieldViolation> violations) {
            super("Dữ liệu sản phẩm không hợp lệ");
            this.violations = List.copyOf(violations);
        }

        public List<FieldViolation> violations() {
            return violations;
        }
    }
}

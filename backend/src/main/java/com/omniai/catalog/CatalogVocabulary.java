package com.omniai.catalog;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Luật dữ liệu của catalog (docs/specs/data-model.md). Owner: LucVT.
 * Từ V15 (decisions #35), bộ từ vựng danh mục / loại da / vấn đề da là các bảng tra cứu trong DB
 * (category, skin_type, skin_concern) và ProductService đọc từ đó. Ở đây chỉ còn luật không nằm trong DB.
 * tools/catalog/validate_catalog.py (công cụ offline) giữ bản từ vựng riêng, phải khớp các bảng tra cứu.
 */
final class CatalogVocabulary {

    static final Set<String> USAGE_TIMES = Set.of("am", "pm", "am,pm");

    static final Pattern SKU = Pattern.compile("^[A-Z]{3,4}-[A-Z]{3}-\\d{2}$");
    /** Cùng ý với luật no-medical-claim trong shared/policies/common.yaml: mô tả sản phẩm không được nói như thuốc. */
    static final Pattern MEDICAL_CLAIM = Pattern.compile(
            "\\b(điều trị|chữa khỏi|chữa|đặc trị|trị (mụn|nám|thâm|sẹo|tàn nhang))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    static final int MIN_INCI = 2;

    private CatalogVocabulary() {
    }

    /** Tách chuỗi CSV, bỏ khoảng trắng và phần tử rỗng. */
    static List<String> split(String csv) {
        if (csv == null) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}

package com.omniai.catalog;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bộ từ vựng và luật dữ liệu của catalog (docs/specs/data-model.md). Owner: LucVT.
 * Giữ khớp với tools/catalog/validate_catalog.py để CSV và API admin cùng một luật.
 */
final class CatalogVocabulary {

    static final Set<String> CATEGORIES = Set.of("cleanser", "toner", "serum", "moisturizer", "sunscreen",
            "exfoliant", "spot-treatment", "mask", "eye-cream", "lip-care");
    static final Set<String> SKIN_TYPES = Set.of("oily", "dry", "combination", "sensitive", "normal");
    static final Set<String> SKIN_CONCERNS = Set.of("acne", "oil-control", "pores", "hydration", "dullness",
            "dark-spots", "aging", "sensitivity", "barrier", "sun-protection");
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

package com.omniai.brain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** JSON ChatResponse của ai-brain, có và không có {@code products} (T-43.3). */
class BrainResponseJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsSuggestedProducts() {
        BrainResponse response = json.readValue("""
                {"reply": "1. Nivea — Nivea Derma Skin Clear UV Fluid SPF50 — 201.000đ", "provider": "gemini",
                 "warnings": ["grounding:price"], "handoff": false,
                 "products": [{"sku": "NIVE-SUN-02", "brand": "Nivea", "name": "Nivea Derma Skin Clear UV Fluid SPF50",
                               "price": 201000, "category": "sunscreen"}]}
                """, BrainResponse.class);
        assertThat(response.products()).containsExactly(
                new BrainProduct("NIVE-SUN-02", "Nivea", "Nivea Derma Skin Clear UV Fluid SPF50", 201_000L, "sunscreen"));
        assertThat(response.warnings()).containsExactly("grounding:price");
    }

    @Test
    void olderBrainWithoutProductsStillWorks() {
        BrainResponse response = json.readValue("""
                {"reply": "Dạ chào bạn", "provider": "gemini", "warnings": [], "handoff": false}
                """, BrainResponse.class);
        assertThat(response.products()).isEmpty();
        assertThat(response.reply()).isEqualTo("Dạ chào bạn");
    }
}

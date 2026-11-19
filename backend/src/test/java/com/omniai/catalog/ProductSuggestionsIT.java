package com.omniai.catalog;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Thẻ sản phẩm cho chat widget theo SKU AI Brain gợi ý (T-43.3), trên catalog thật của migration. */
@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "omniai.images.base-url=https://test.supabase.co/")
@Transactional
class ProductSuggestionsIT {

    @Autowired
    ProductSuggestions suggestions;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    BrainClient brainClient;

    @Test
    void cardsFollowTheBrainOrderAndSkipHiddenOrUnknownSkus() {
        List<String> active = jdbc.queryForList(
                "SELECT sku FROM product WHERE active ORDER BY id LIMIT 3", String.class);
        String hidden = active.get(2);
        jdbc.update("UPDATE product SET active = false WHERE sku = ?", hidden);

        List<ProductCard> cards = suggestions.cards(List.of(active.get(1), "KHONG-CO-SKU", hidden, active.get(0), active.get(1)));

        assertThat(cards).extracting(ProductCard::sku).containsExactly(active.get(1), active.get(0));
        ProductCard first = cards.getFirst();
        assertThat(first.id()).isNotNull();
        assertThat(first.prices()).containsKey("WEB");
        assertThat(first.brand()).isNotBlank();
    }

    @Test
    void emptyOrBlankInputNeedsNoQuery() {
        assertThat(suggestions.cards(List.of())).isEmpty();
        assertThat(suggestions.cards(null)).isEmpty();
        assertThat(suggestions.cards(List.of(" "))).isEmpty();
    }

    @Test
    void atMostFiveCards() {
        List<String> skus = jdbc.queryForList("SELECT sku FROM product WHERE active ORDER BY id LIMIT 8", String.class);
        assertThat(suggestions.cards(skus)).hasSize(ProductSuggestions.MAX_CARDS);
    }
}

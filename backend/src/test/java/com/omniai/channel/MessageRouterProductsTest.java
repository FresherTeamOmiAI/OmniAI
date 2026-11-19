package com.omniai.channel;

import com.omniai.brain.BrainClient;
import com.omniai.brain.BrainProduct;
import com.omniai.brain.BrainResponse;
import com.omniai.catalog.ProductCard;
import com.omniai.catalog.ProductSuggestions;
import com.omniai.channel.simulator.SimulatorAdapter;
import com.omniai.channel.simulator.SimulatorRequest;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.conversation.TurnHistory;
import com.omniai.policy.PolicyEngine;
import com.omniai.policy.PolicyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Thẻ sản phẩm trong câu trả lời chỉ đi xuống widget WEB (T-43.3). */
class MessageRouterProductsTest {

    private static final String REPLY = "Gợi ý trong shop:\n1. Nivea — Nivea Derma Skin Clear UV Fluid SPF50 — 201.000đ\nBạn thoa mỗi sáng nhé.";
    private static final BrainProduct NIVEA = new BrainProduct("NIVE-SUN-02", "Nivea", "Nivea Derma Skin Clear UV Fluid SPF50", 201_000L, "sunscreen");
    private static final ProductCard CARD = new ProductCard(42L, "NIVE-SUN-02", "Nivea", "Nivea Derma Skin Clear UV Fluid SPF50",
            "sunscreen", "oily", "https://img.test/nive.jpg", Map.of("WEB", 201_000L, "SHOPEE", 210_000L, "TIKTOK", 199_000L), 12);

    private final BrainClient brain = (message, history) -> new BrainResponse(REPLY, "test", List.of(), false, List.of(NIVEA));
    private final PolicyEngine policy = new PolicyEngine("../shared/policies");

    private static CanonicalMessage message(ChannelType channel, String id) {
        return new SimulatorAdapter(channel).toCanonical(new SimulatorRequest(id, "u1", "kem chống nắng cho da dầu"));
    }

    private static ProductSuggestions suggestions() {
        ProductSuggestions suggestions = mock(ProductSuggestions.class);
        when(suggestions.cards(List.of("NIVE-SUN-02"))).thenReturn(List.of(CARD));
        return suggestions;
    }

    @Test
    void webReplyCarriesProductCards() {
        MessageRouter router = new MessageRouter(brain, policy, ConversationRecorder.NOOP, TurnHistory.NONE, suggestions());
        BotReply reply = router.handle(message(ChannelType.WEB, "p-web"));
        assertThat(reply.products()).containsExactly(CARD);
        assertThat(reply.finalText()).isEqualTo(REPLY);
    }

    @Test
    void marketplaceRepliesNeverLookUpProducts() {
        ProductSuggestions suggestions = suggestions();
        MessageRouter router = new MessageRouter(brain, policy, ConversationRecorder.NOOP, TurnHistory.NONE, suggestions);
        assertThat(router.handle(message(ChannelType.SHOPEE, "p-shopee")).products()).isEmpty();
        assertThat(router.handle(message(ChannelType.TIKTOK, "p-tiktok")).products()).isEmpty();
        verify(suggestions, never()).cards(any());
    }

    @Test
    void blockedReplyHasNoCards() {
        PolicyEngine blocking = new PolicyEngine("../shared/policies") {
            @Override
            public PolicyResult apply(ChannelType channel, String text) {
                return new PolicyResult("Dạ shop chưa tư vấn được nội dung này ạ.", List.of(), true);
            }
        };
        ProductSuggestions suggestions = suggestions();
        MessageRouter router = new MessageRouter(brain, blocking, ConversationRecorder.NOOP, TurnHistory.NONE, suggestions);
        assertThat(router.handle(message(ChannelType.WEB, "p-blocked")).products()).isEmpty();
        verify(suggestions, never()).cards(any());
    }

    @Test
    void lookupFailureStillDeliversTheReply() {
        ProductSuggestions suggestions = mock(ProductSuggestions.class);
        when(suggestions.cards(any())).thenThrow(new IllegalStateException("db down"));
        MessageRouter router = new MessageRouter(brain, policy, ConversationRecorder.NOOP, TurnHistory.NONE, suggestions);
        BotReply reply = router.handle(message(ChannelType.WEB, "p-fail"));
        assertThat(reply.products()).isEmpty();
        assertThat(reply.finalText()).isEqualTo(REPLY);
    }

    @Test
    void brainWithoutProductsSkipsTheLookup() {
        ProductSuggestions suggestions = suggestions();
        BrainClient plain = (message, history) -> new BrainResponse("Dạ shop chào bạn ạ.", "test", List.of(), false);
        MessageRouter router = new MessageRouter(plain, policy, ConversationRecorder.NOOP, TurnHistory.NONE, suggestions);
        assertThat(router.handle(message(ChannelType.WEB, "p-plain")).products()).isEmpty();
        verify(suggestions, never()).cards(any());
    }
}

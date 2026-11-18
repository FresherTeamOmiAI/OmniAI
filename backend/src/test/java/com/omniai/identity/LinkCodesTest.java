package com.omniai.identity;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Dạng mã OMNI-XXXXXX (SRS UC-08, G-01). */
class LinkCodesTest {

    @Test
    void alphabetSkipsLookAlikeCharacters() {
        assertThat(LinkCodes.ALPHABET).hasSize(32).doesNotContain("O", "I", "0", "1");
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 200; i++) {
            String code = LinkCodes.generate(random);
            assertThat(code).matches("OMNI-[A-HJ-NP-Z2-9]{6}");
            assertThat(LinkCodes.PATTERN.matcher(code).matches()).isTrue();
        }
    }

    @Test
    void normalizeAcceptsCommonTypingVariants() {
        assertThat(LinkCodes.normalize("OMNI-ABC234")).isEqualTo("OMNI-ABC234");
        assertThat(LinkCodes.normalize("  omni-abc234 ")).isEqualTo("OMNI-ABC234");
        assertThat(LinkCodes.normalize("abc234")).isEqualTo("OMNI-ABC234");
        assertThat(LinkCodes.normalize("OMNI - ABC 234")).isEqualTo("OMNI-ABC234");

        assertThat(LinkCodes.normalize(null)).isNull();
        assertThat(LinkCodes.normalize("")).isNull();
        assertThat(LinkCodes.normalize("OMNI-ABC23")).isNull();
        assertThat(LinkCodes.normalize("OMNI-ABC2345")).isNull();
        assertThat(LinkCodes.normalize("OMNI-ABCO34")).as("chữ O bị loại").isNull();
        assertThat(LinkCodes.normalize("OMNI-ABC134")).as("số 1 bị loại").isNull();
    }

    @Test
    void patternFindsCodeInsideChatText() {
        assertThat(LinkCodes.PATTERN.matcher("mã của mình là omni-xyz789 nhé").find()).isTrue();
        assertThat(LinkCodes.PATTERN.matcher("OMNI-XYZ7890").find()).as("dài hơn 6 ký tự").isFalse();
        assertThat(LinkCodes.PATTERN.matcher("OMNI-XY Z789").find()).isFalse();
    }

    @Test
    void hashIsStableAndMaskHidesId() {
        assertThat(LinkCodes.hash("OMNI-ABC234")).hasSize(64).isEqualTo(LinkCodes.hash("OMNI-ABC234"))
                .isNotEqualTo(LinkCodes.hash("OMNI-ABC235"));
        assertThat(LinkCodes.mask("buyer-123456")).isEqualTo("***456");
        assertThat(LinkCodes.mask("ab")).isEqualTo("***");
    }
}

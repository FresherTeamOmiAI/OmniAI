package com.omniai.policy;

import com.omniai.common.ChannelType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    private static PolicyEngine engine;

    @BeforeAll
    static void loadRealPolicies() {
        // Test chạy với basedir = backend/, dùng đúng file YAML thật của dự án.
        engine = new PolicyEngine("../shared/policies");
    }

    @Test
    void shopeeRedactsExternalLink() {
        PolicyResult result = engine.apply(ChannelType.SHOPEE, "Bạn xem thêm tại https://omni-demo.local/p/1 nhé");

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalText()).doesNotContain("https://").contains(PolicyEngine.REDACTED);
        assertThat(result.violations()).extracting(PolicyViolation::ruleId).contains("no-external-link");
    }

    @Test
    void shopeeRedactsPhoneNumber() {
        PolicyResult result = engine.apply(ChannelType.SHOPEE, "Gọi shop qua số 0901 234 567 nha");

        assertThat(result.finalText()).doesNotContain("0901");
        assertThat(result.violations()).extracting(PolicyViolation::ruleId).contains("no-phone");
    }

    @Test
    void shopeeBlocksMentionOfOtherChannel() {
        PolicyResult result = engine.apply(ChannelType.SHOPEE, "Mua trên website của shop sẽ rẻ hơn ạ");

        assertThat(result.blocked()).isTrue();
        assertThat(result.finalText()).isEqualTo(engine.policyOf(ChannelType.SHOPEE).fallbackMessage());
    }

    @Test
    void tiktokBlocksMentionOfShopee() {
        PolicyResult result = engine.apply(ChannelType.TIKTOK, "Bên Shopee đang có voucher đó bạn");

        assertThat(result.blocked()).isTrue();
    }

    @Test
    void webKeepsLinkAndChannelComparison() {
        String text = "Trên website giá 289.000đ, xem tại https://omni-demo.local/p/1";
        PolicyResult result = engine.apply(ChannelType.WEB, text);

        assertThat(result.finalText()).isEqualTo(text);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void medicalClaimIsSoftenedOnEveryChannel() {
        for (ChannelType channel : ChannelType.values()) {
            PolicyResult result = engine.apply(channel, "Sản phẩm này đặc trị mụn rất tốt");

            assertThat(result.finalText()).as(channel.name()).doesNotContain("đặc trị").contains("hỗ trợ cải thiện");
        }
    }

    @Test
    void priceIsNotMistakenForPhoneNumber() {
        PolicyResult result = engine.apply(ChannelType.SHOPEE, "Giá hiện tại là 1.290.000đ ạ");

        assertThat(result.violations()).isEmpty();
    }

    @Test
    void wordGiaTriIsNotTreatedAsMedicalClaim() {
        PolicyResult result = engine.apply(ChannelType.WEB, "Combo này rất đáng giá trị đồng tiền");

        assertThat(result.violations()).isEmpty();
    }
}

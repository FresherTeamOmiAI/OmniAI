package com.omniai.policy;

import com.omniai.common.ChannelType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ---------- Luật từng kênh: bộ ca dùng chung với bench Python (shared/policies/tests/cases.yaml) ----------

    @SuppressWarnings("unchecked")
    static Stream<Arguments> sharedCases() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("../shared/policies/tests/cases.yaml"))) {
            Map<String, Object> root = new Yaml().load(in);
            return ((List<Map<String, Object>>) root.get("cases")).stream().map(c -> Arguments.of(c.get("id"), c));
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedCases")
    void sharedCaseBehavesAsExpectedOnEveryChannel(String id, Map<String, Object> testCase) {
        String text = (String) testCase.get("text");
        Map<String, List<String>> matches = (Map<String, List<String>>) testCase.getOrDefault("matches", Map.of());
        List<String> blocked = (List<String>) testCase.getOrDefault("blocked", List.of());

        for (ChannelType channel : ChannelType.values()) {
            Set<String> expected = new HashSet<>(matches.getOrDefault("*", List.of()));
            expected.addAll(matches.getOrDefault(channel.name(), List.of()));
            assertThat(matchingRuleIds(channel, text)).as("luật khớp: %s trên %s", id, channel)
                    .containsExactlyInAnyOrderElementsOf(expected);

            PolicyResult result = engine.apply(channel, text);
            assertThat(result.blocked()).as("blocked: %s trên %s", id, channel)
                    .isEqualTo(blocked.contains(channel.name()));
            assertThat(matchingRuleIds(channel, result.finalText())).as("finalText còn vi phạm: %s trên %s", id, channel)
                    .isEmpty();
        }
    }

    private static List<String> matchingRuleIds(ChannelType channel, String text) {
        return engine.policyOf(channel).rules().stream()
                .filter(rule -> rule.pattern().matcher(text).find())
                .map(PolicyRule::id)
                .toList();
    }

    @Test
    void fallbackMessageDoesNotBreakItsOwnChannelRules() {
        for (ChannelType channel : ChannelType.values()) {
            assertThat(matchingRuleIds(channel, engine.policyOf(channel).fallbackMessage())).as(channel.name()).isEmpty();
        }
    }

    @Test
    void channelRulesRunBeforeCommonRulesAndBlockStopsTheRest() {
        PolicyResult result = engine.apply(ChannelType.SHOPEE, "Bên website có kem đặc trị mụn");

        assertThat(result.blocked()).isTrue();
        assertThat(result.violations()).extracting(PolicyViolation::ruleId).containsExactly("no-other-channel");
    }

    @Test
    void redactAndReplaceAreAppliedTogether() {
        PolicyResult result = engine.apply(ChannelType.TIKTOK, "Gọi 0901234567 để mua kem đặc trị mụn");

        assertThat(result.blocked()).isFalse();
        assertThat(result.finalText()).isEqualTo("Gọi [đã ẩn] để mua kem hỗ trợ cải thiện mụn");
        assertThat(result.violations()).extracting(PolicyViolation::ruleId).containsExactly("no-phone", "no-medical-claim");
    }

    // ---------- Nạp file YAML: sai định dạng thì lỗi ngay lúc khởi động ----------

    /** Ghi đủ 3 kênh tối thiểu hợp lệ, để mỗi test chỉ cần ghi đè file đang thử. */
    private static void writeMinimalPolicies(Path dir) throws IOException {
        for (ChannelType channel : ChannelType.values()) {
            Files.writeString(dir.resolve(channel.name().toLowerCase() + ".yaml"),
                    "channel: " + channel + "\nrules: []\nfallback_message: \"Dạ, shop xin lỗi ạ.\"\n");
        }
    }

    static Stream<Arguments> brokenPolicyFiles() {
        return Stream.of(
                Arguments.of("luật thiếu pattern",
                        Map.of("web.yaml", "channel: WEB\nrules:\n  - id: r1\n    action: redact\n"),
                        "web.yaml: luật 'r1' thiếu 'pattern'"),
                Arguments.of("luật thiếu id",
                        Map.of("web.yaml", "channel: WEB\nrules:\n  - pattern: 'x'\n    action: redact\n"),
                        "web.yaml: luật #1 thiếu 'id'"),
                Arguments.of("action sai",
                        Map.of("web.yaml", "channel: WEB\nrules:\n  - id: r1\n    pattern: 'x'\n    action: redcat\n"),
                        "web.yaml: luật 'r1': action 'redcat' không hợp lệ"),
                Arguments.of("regex sai cú pháp",
                        Map.of("web.yaml", "channel: WEB\nrules:\n  - id: r1\n    pattern: '(abc'\n    action: redact\n"),
                        "web.yaml: luật 'r1': regex sai cú pháp"),
                Arguments.of("kênh không có trong ChannelType",
                        Map.of("zalo.yaml", "channel: ZALO\nrules: []\n"),
                        "zalo.yaml: 'channel: ZALO' không phải"),
                Arguments.of("hai file cùng một kênh",
                        Map.of("web2.yaml", "channel: WEB\nrules: []\n"),
                        "web2.yaml: kênh WEB đã có trong web.yaml"),
                Arguments.of("trùng id với luật chung",
                        Map.of("common.yaml", "channel: \"*\"\nrules:\n  - id: r1\n    pattern: 'x'\n    action: replace\n    replacement: y\n",
                                "web.yaml", "channel: WEB\nrules:\n  - id: r1\n    pattern: 'z'\n    action: redact\n"),
                        "web.yaml: trùng id luật 'r1'"),
                Arguments.of("max_length không dương",
                        Map.of("web.yaml", "channel: WEB\nmax_length: 0\nrules: []\n"),
                        "web.yaml: 'max_length' phải là số nguyên dương"),
                Arguments.of("có luật block nhưng thiếu fallback_message",
                        Map.of("shopee.yaml", "channel: SHOPEE\nrules:\n  - id: r1\n    pattern: 'x'\n    action: block\n"),
                        "shopee.yaml: có luật block nhưng thiếu 'fallback_message'"),
                Arguments.of("nội dung không phải object",
                        Map.of("web.yaml", "- a\n- b\n"),
                        "web.yaml: nội dung phải là một object YAML"),
                Arguments.of("YAML sai cú pháp",
                        Map.of("web.yaml", "channel: WEB\nrules: [\n"),
                        "web.yaml: YAML sai cú pháp"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenPolicyFiles")
    void brokenPolicyFileFailsAtStartupWithFileAndRule(String name, Map<String, String> files, String message,
                                                       @TempDir Path dir) throws IOException {
        writeMinimalPolicies(dir);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(dir.resolve(file.getKey()), file.getValue());
        }

        assertThatThrownBy(() -> new PolicyEngine(dir.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    @Test
    void missingChannelFileFailsAtStartup(@TempDir Path dir) throws IOException {
        writeMinimalPolicies(dir);
        Files.delete(dir.resolve("tiktok.yaml"));

        assertThatThrownBy(() -> new PolicyEngine(dir.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thiếu file policy cho kênh [TIKTOK]");
    }

    @Test
    void longAnswerIsCutAtMaxLengthWithoutSplittingEmoji(@TempDir Path dir) throws IOException {
        writeMinimalPolicies(dir);
        Files.writeString(dir.resolve("web.yaml"), "channel: WEB\nmax_length: 5\nrules: []\n");
        PolicyEngine small = new PolicyEngine(dir.toString());

        assertThat(small.apply(ChannelType.WEB, "abcde").finalText()).isEqualTo("abcde");
        assertThat(small.apply(ChannelType.WEB, "abc😊😊xyz").finalText()).isEqualTo("abc😊…");
    }
}

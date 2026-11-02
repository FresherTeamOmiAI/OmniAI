package com.omniai.policy;

import com.omniai.common.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Áp luật theo kênh lên câu trả lời của AI Brain. Owner: HungDQ.
 * Luật đọc từ shared/policies/*.yaml (dùng chung với bench Python).
 */
@Component
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);
    static final String REDACTED = "[đã ẩn]";

    private final Map<ChannelType, ChannelPolicy> policies = new EnumMap<>(ChannelType.class);

    public PolicyEngine(@Value("${omniai.policy-dir}") String policyDir) {
        load(Path.of(policyDir));
    }

    public PolicyResult apply(ChannelType channel, String text) {
        ChannelPolicy policy = policies.get(channel);
        if (policy == null) {
            throw new IllegalStateException("Chưa có file policy cho kênh " + channel);
        }
        List<PolicyViolation> violations = new ArrayList<>();
        String result = text;

        for (PolicyRule rule : policy.rules()) {
            Matcher matcher = rule.pattern().matcher(result);
            if (!matcher.find()) {
                continue;
            }
            violations.add(new PolicyViolation(rule.id(), rule.action(), matcher.group()));
            if (rule.action() == PolicyAction.BLOCK) {
                return new PolicyResult(policy.fallbackMessage(), violations, true);
            }
            String replacement = rule.action() == PolicyAction.REDACT ? REDACTED : rule.replacement();
            result = rule.pattern().matcher(result).replaceAll(Matcher.quoteReplacement(replacement));
        }

        if (result.length() > policy.maxLength()) {
            result = result.substring(0, policy.maxLength() - 1) + "…";
        }
        return new PolicyResult(result, violations, false);
    }

    public ChannelPolicy policyOf(ChannelType channel) {
        return policies.get(channel);
    }

    private void load(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Không tìm thấy thư mục policy: " + dir.toAbsolutePath()
                    + " (cấu hình omniai.policy-dir / OMNIAI_POLICY_DIR)");
        }
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".yaml")).sorted().forEach(p -> docs.add(readYaml(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<PolicyRule> commonRules = docs.stream()
                .filter(d -> "*".equals(d.get("channel")))
                .flatMap(d -> parseRules(d).stream())
                .toList();

        for (Map<String, Object> doc : docs) {
            if ("*".equals(doc.get("channel"))) {
                continue;
            }
            ChannelType channel = ChannelType.valueOf(String.valueOf(doc.get("channel")));
            // Luật riêng của kênh chạy trước (có thể BLOCK), luật chung chạy sau.
            List<PolicyRule> rules = new ArrayList<>(parseRules(doc));
            rules.addAll(commonRules);
            policies.put(channel, new ChannelPolicy(
                    channel,
                    Boolean.TRUE.equals(doc.get("owned")),
                    ((Number) doc.getOrDefault("max_length", 2000)).intValue(),
                    List.copyOf(rules),
                    String.valueOf(doc.getOrDefault("fallback_message", ""))));
        }
        log.info("Đã nạp policy cho {} kênh từ {}", policies.size(), dir.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<PolicyRule> parseRules(Map<String, Object> doc) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) doc.getOrDefault("rules", List.of());
        return raw.stream().map(r -> PolicyRule.of(
                String.valueOf(r.get("id")),
                String.valueOf(r.getOrDefault("description", "")),
                String.valueOf(r.get("pattern")),
                PolicyAction.valueOf(String.valueOf(r.get("action")).toUpperCase()),
                String.valueOf(r.getOrDefault("replacement", REDACTED)))).toList();
    }

    private static Map<String, Object> readYaml(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc " + path, e);
        }
    }
}

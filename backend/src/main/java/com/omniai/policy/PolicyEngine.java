package com.omniai.policy;

import com.omniai.common.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Áp luật theo kênh lên câu trả lời của AI Brain. Owner: HungDQ.
 * Luật đọc từ shared/policies/*.yaml (dùng chung với bench Python). File sai định dạng hoặc thiếu kênh
 * thì báo lỗi ngay lúc khởi động, kèm tên file và id luật, thay vì lỗi khi khách nhắn.
 */
@Component
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);
    static final String REDACTED = "[đã ẩn]";
    private static final String COMMON = "*";
    private static final int DEFAULT_MAX_LENGTH = 2000;

    private final Map<ChannelType, ChannelPolicy> policies = new EnumMap<>(ChannelType.class);

    public PolicyEngine(@Value("${omniai.policy-dir}") String policyDir) {
        load(Path.of(policyDir));
    }

    public PolicyResult apply(ChannelType channel, String text) {
        // load() bảo đảm kênh nào cũng có policy.
        ChannelPolicy policy = policies.get(channel);
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
        return new PolicyResult(truncate(result, policy.maxLength()), violations, false);
    }

    public ChannelPolicy policyOf(ChannelType channel) {
        return policies.get(channel);
    }

    /** Cắt theo ký tự Unicode (code point) để không tách đôi emoji, thêm "…" ở cuối. */
    static String truncate(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength - 1)) + "…";
    }

    private record PolicyFile(Path path, Map<String, Object> doc) {
        boolean isCommon() {
            return COMMON.equals(doc.get("channel"));
        }
    }

    private void load(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Không tìm thấy thư mục policy: " + dir.toAbsolutePath()
                    + " (cấu hình omniai.policy-dir / OMNIAI_POLICY_DIR)");
        }
        List<PolicyFile> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.toString().endsWith(".yaml")).sorted()
                    .map(p -> new PolicyFile(p, readYaml(p))).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<PolicyRule> commonRules = new ArrayList<>();
        for (PolicyFile file : files) {
            if (file.isCommon()) {
                commonRules.addAll(parseRules(file));
                requireUniqueIds(file.path(), commonRules);
            }
        }

        Map<ChannelType, Path> sources = new EnumMap<>(ChannelType.class);
        for (PolicyFile file : files) {
            if (file.isCommon()) {
                continue;
            }
            ChannelType channel = channelOf(file);
            if (sources.containsKey(channel)) {
                throw invalid(file.path(), "kênh " + channel + " đã có trong " + sources.get(channel).getFileName());
            }
            sources.put(channel, file.path());

            // Luật riêng của kênh chạy trước (có thể BLOCK), luật chung chạy sau.
            List<PolicyRule> rules = new ArrayList<>(parseRules(file));
            rules.addAll(commonRules);
            requireUniqueIds(file.path(), rules);

            String fallback = optional(file.doc(), "fallback_message", "");
            if (fallback.isBlank() && rules.stream().anyMatch(r -> r.action() == PolicyAction.BLOCK)) {
                throw invalid(file.path(), "có luật block nhưng thiếu 'fallback_message'");
            }
            policies.put(channel, new ChannelPolicy(
                    channel,
                    Boolean.TRUE.equals(file.doc().get("owned")),
                    maxLengthOf(file),
                    List.copyOf(rules),
                    fallback));
        }

        Set<ChannelType> missing = EnumSet.allOf(ChannelType.class);
        missing.removeAll(policies.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Thiếu file policy cho kênh " + missing + " trong " + dir.toAbsolutePath());
        }
        log.info("Đã nạp policy cho {} kênh từ {}", policies.size(), dir.toAbsolutePath());
    }

    private static List<PolicyRule> parseRules(PolicyFile file) {
        Object raw = file.doc().get("rules");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> items)) {
            throw invalid(file.path(), "'rules' phải là danh sách");
        }
        List<PolicyRule> rules = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> rule)) {
                throw invalid(file.path(), "luật #" + (i + 1) + " phải là một object");
            }
            String id = required(file.path(), rule, "id", "luật #" + (i + 1));
            String where = "luật '" + id + "'";
            String regex = required(file.path(), rule, "pattern", where);
            String actionName = required(file.path(), rule, "action", where);
            PolicyAction action = Arrays.stream(PolicyAction.values())
                    .filter(a -> a.name().equalsIgnoreCase(actionName))
                    .findFirst()
                    .orElseThrow(() -> invalid(file.path(),
                            where + ": action '" + actionName + "' không hợp lệ, chỉ nhận block | redact | replace"));
            try {
                rules.add(PolicyRule.of(id, optional(rule, "description", ""), regex, action,
                        optional(rule, "replacement", REDACTED)));
            } catch (PatternSyntaxException e) {
                throw invalid(file.path(), where + ": regex sai cú pháp (" + e.getDescription() + ")");
            }
        }
        return rules;
    }

    private static ChannelType channelOf(PolicyFile file) {
        Object name = file.doc().get("channel");
        return Arrays.stream(ChannelType.values())
                .filter(c -> c.name().equals(String.valueOf(name)))
                .findFirst()
                .orElseThrow(() -> invalid(file.path(), "'channel: " + name + "' không phải \"*\" hoặc một giá trị của ChannelType "
                        + Arrays.toString(ChannelType.values())));
    }

    private static int maxLengthOf(PolicyFile file) {
        Object value = file.doc().get("max_length");
        if (value == null) {
            return DEFAULT_MAX_LENGTH;
        }
        if (!(value instanceof Integer length) || length <= 0) {
            throw invalid(file.path(), "'max_length' phải là số nguyên dương, đang là '" + value + "'");
        }
        return length;
    }

    private static void requireUniqueIds(Path file, List<PolicyRule> rules) {
        Set<String> seen = new HashSet<>();
        for (PolicyRule rule : rules) {
            if (!seen.add(rule.id())) {
                throw invalid(file, "trùng id luật '" + rule.id() + "' (tính cả luật chung)");
            }
        }
    }

    private static String required(Path file, Map<?, ?> map, String key, String where) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw invalid(file, where + " thiếu '" + key + "'");
        }
        return String.valueOf(value);
    }

    /** Lấy giá trị dạng chuỗi; YAML để trống (null) cũng coi như không khai. */
    private static String optional(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static IllegalStateException invalid(Path file, String problem) {
        return new IllegalStateException("File policy " + file.getFileName() + ": " + problem);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path path) {
        Object loaded;
        try (InputStream in = Files.newInputStream(path)) {
            loaded = new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc " + path, e);
        } catch (YAMLException e) {
            throw invalid(path, "YAML sai cú pháp: " + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?>)) {
            throw invalid(path, "nội dung phải là một object YAML (channel, rules, ...)");
        }
        return (Map<String, Object>) loaded;
    }
}

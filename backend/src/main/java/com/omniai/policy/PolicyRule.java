package com.omniai.policy;

import java.util.regex.Pattern;

public record PolicyRule(String id, String description, Pattern pattern, PolicyAction action, String replacement) {

    static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    static PolicyRule of(String id, String description, String regex, PolicyAction action, String replacement) {
        return new PolicyRule(id, description, Pattern.compile(regex, FLAGS), action, replacement);
    }
}

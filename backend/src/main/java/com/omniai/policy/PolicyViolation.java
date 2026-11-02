package com.omniai.policy;

public record PolicyViolation(String ruleId, PolicyAction action, String matched) {
}

package com.omniai.policy;

import java.util.List;

public record PolicyResult(String finalText, List<PolicyViolation> violations, boolean blocked) {
}

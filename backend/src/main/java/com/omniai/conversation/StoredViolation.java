package com.omniai.conversation;

import com.omniai.policy.PolicyAction;
import com.omniai.policy.PolicyViolation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một luật policy đã kích hoạt trên câu bot (bảng message_violation, V11). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class StoredViolation {

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PolicyAction action;

    @Column(columnDefinition = "text")
    private String matched;

    static StoredViolation of(PolicyViolation v) {
        StoredViolation s = new StoredViolation();
        s.ruleId = v.ruleId();
        s.action = v.action();
        s.matched = v.matched();
        return s;
    }

    PolicyViolation toViolation() {
        return new PolicyViolation(ruleId, action, matched);
    }
}

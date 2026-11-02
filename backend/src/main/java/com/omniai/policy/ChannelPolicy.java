package com.omniai.policy;

import com.omniai.common.ChannelType;

import java.util.List;

public record ChannelPolicy(
        ChannelType channel,
        boolean owned,
        int maxLength,
        List<PolicyRule> rules,
        String fallbackMessage) {
}

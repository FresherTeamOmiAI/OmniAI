package com.omniai.channel.simulator;

import jakarta.validation.constraints.NotBlank;

/** Tin giả lập từ một kênh. messageId bỏ trống thì server tự sinh; gửi trùng messageId để thử idempotency. */
public record SimulatorRequest(String messageId, @NotBlank String userId, @NotBlank String text) {
}

package com.omniai.brain;

/** Một lượt đã xong, gửi kèm POST /chat. role là user hoặc assistant. */
public record BrainTurn(String role, String content) {
}

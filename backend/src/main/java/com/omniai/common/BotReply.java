package com.omniai.common;

import com.omniai.policy.PolicyViolation;

import java.util.List;

/**
 * Câu trả lời gửi ra kênh.
 *
 * @param rawText    câu trả lời gốc của AI Brain (trước policy)
 * @param finalText  câu trả lời sau khi qua policy của kênh (cái khách thực sự nhận)
 * @param violations các luật đã bị kích hoạt
 * @param blocked    true nếu cả câu bị thay bằng fallback của kênh
 * @param handoff    true nếu cần chuyển nhân viên (vd. AI Brain lỗi)
 */
public record BotReply(
        String messageId,
        ChannelType channel,
        String rawText,
        String finalText,
        List<PolicyViolation> violations,
        boolean blocked,
        boolean handoff) {
}

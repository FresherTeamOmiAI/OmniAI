package com.omniai.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.omniai.catalog.ProductCard;
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
 * @param products   thẻ sản phẩm (ảnh, SKU, giá các kênh) cho các dòng gợi ý trong câu. Chỉ kênh WEB và chỉ khi
 *                   câu không bị chặn; kênh sàn luôn rỗng (T-43.3)
 */
public record BotReply(
        String messageId,
        ChannelType channel,
        String rawText,
        String finalText,
        List<PolicyViolation> violations,
        boolean blocked,
        boolean handoff,
        List<ProductCard> products) {

    @JsonCreator
    public BotReply {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public BotReply(String messageId, ChannelType channel, String rawText, String finalText,
                    List<PolicyViolation> violations, boolean blocked, boolean handoff) {
        this(messageId, channel, rawText, finalText, violations, blocked, handoff, List.of());
    }
}

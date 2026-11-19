package com.omniai.brain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

/**
 * Câu trả lời của AI Brain ({@code ChatResponse} của ai-brain).
 *
 * @param products sản phẩm có dòng thẻ trong {@code reply}, đúng thứ tự (T-43.3). Brain cũ không gửi thì rỗng.
 */
public record BrainResponse(String reply, String provider, List<String> warnings, boolean handoff,
                            List<BrainProduct> products) {

    @JsonCreator
    public BrainResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        products = products == null ? List.of() : List.copyOf(products);
    }

    public BrainResponse(String reply, String provider, List<String> warnings, boolean handoff) {
        this(reply, provider, warnings, handoff, List.of());
    }

    public static BrainResponse unavailable() {
        return new BrainResponse("Dạ, hệ thống tư vấn đang bận, nhân viên của shop sẽ phản hồi bạn sớm nhất ạ.",
                "none", List.of(), true);
    }
}

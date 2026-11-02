package com.omniai.brain;

import java.util.List;

public record BrainResponse(String reply, String provider, List<String> warnings, boolean handoff) {

    public static BrainResponse unavailable() {
        return new BrainResponse("Dạ, hệ thống tư vấn đang bận, nhân viên của shop sẽ phản hồi bạn sớm nhất ạ.",
                "none", List.of(), true);
    }
}

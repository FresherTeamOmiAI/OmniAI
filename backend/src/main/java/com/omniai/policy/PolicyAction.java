package com.omniai.policy;

public enum PolicyAction {
    /** Thay toàn bộ câu trả lời bằng fallback_message của kênh. */
    BLOCK,
    /** Thay đoạn khớp bằng "[đã ẩn]". */
    REDACT,
    /** Thay đoạn khớp bằng replacement của luật. */
    REPLACE
}

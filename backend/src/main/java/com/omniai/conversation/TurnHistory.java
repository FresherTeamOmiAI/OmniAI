package com.omniai.conversation;

import com.omniai.brain.BrainTurn;
import com.omniai.common.CanonicalMessage;

import java.util.List;

/** Các lượt đã xong của đúng cuộc chat, không gồm câu đang xử lý. */
public interface TurnHistory {

    TurnHistory NONE = message -> List.of();

    List<BrainTurn> before(CanonicalMessage message);
}

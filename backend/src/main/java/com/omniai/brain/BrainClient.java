package com.omniai.brain;

import com.omniai.common.CanonicalMessage;

import java.util.List;

/** Gọi AI Brain (service Python). Tách interface để test router không cần chạy Python. */
public interface BrainClient {

    BrainResponse ask(CanonicalMessage message, List<BrainTurn> history);
}

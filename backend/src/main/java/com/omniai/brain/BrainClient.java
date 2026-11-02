package com.omniai.brain;

import com.omniai.common.CanonicalMessage;

/** Gọi AI Brain (service Python). Tách interface để test router không cần chạy Python. */
public interface BrainClient {

    BrainResponse ask(CanonicalMessage message);
}

package com.omniai.sim.tiktok.common;

import java.util.concurrent.atomic.AtomicLong;

/** Sinh ID dạng số 19 chữ số như ID thật của TikTok (conversation, message, notification...). */
public final class Ids {

    private static final AtomicLong NEXT = new AtomicLong(7_494_560_109_732_400_000L);

    private Ids() {
    }

    public static String next() {
        return Long.toString(NEXT.incrementAndGet());
    }
}

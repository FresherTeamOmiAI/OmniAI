package com.omniai.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tập ID đã xử lý gần đây (LRU, trong bộ nhớ) để chống trùng sự kiện từ kênh.
 * Giống idempotency của {@link MessageRouter}: chạy nhiều instance thì không đúng, S7 (US-29) chuyển sang Redis.
 */
public class RecentIds {

    private final Map<String, Boolean> ids;

    public RecentIds(int capacity) {
        this.ids = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        });
    }

    /** true nếu id mới gặp lần đầu (và ghi nhận luôn); false nếu đã có. */
    public boolean markIfNew(String id) {
        return ids.putIfAbsent(id, Boolean.TRUE) == null;
    }

    /** Bỏ ghi nhận, để lần gửi lại sau được xử lý (vd. không đưa được vào hàng đợi). */
    public void forget(String id) {
        ids.remove(id);
    }
}

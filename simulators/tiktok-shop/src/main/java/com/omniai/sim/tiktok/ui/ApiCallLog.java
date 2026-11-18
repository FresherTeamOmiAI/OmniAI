package com.omniai.sim.tiktok.ui;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nhật ký các request hub gọi vào API mở và API token của simulator (kể cả request bị cổng từ chối), cho trang /ui.
 * Chỉ giữ {@link #MAX_ENTRIES} bản mới nhất trong bộ nhớ; POST /sim/reset xóa hết.
 */
@Component
public class ApiCallLog {

    static final int MAX_ENTRIES = 500;

    /**
     * 1 lần gọi. {@code code}/{@code message}/{@code requestId} lấy từ envelope TikTok của response (null nếu body
     * không phải envelope). {@code time} tính bằng mili giây epoch.
     */
    public record Call(long id, long time, String method, String path, String query, String contentType,
                       int httpStatus, Integer code, String message, String requestId, long latencyMs,
                       String requestBody, String responseBody) {

        /** Lỗi = HTTP không 2xx hoặc envelope có code khác 0. */
        public boolean error() {
            return httpStatus / 100 != 2 || (code != null && code != 0);
        }
    }

    private final AtomicLong nextId = new AtomicLong();
    private final Deque<Call> calls = new ConcurrentLinkedDeque<>();

    long nextId() {
        return nextId.incrementAndGet();
    }

    void add(Call call) {
        calls.addLast(call);
        while (calls.size() > MAX_ENTRIES) {
            calls.pollFirst();
        }
    }

    /** Mới nhất trước; {@code afterId} > 0 thì chỉ lấy các lần gọi có id lớn hơn. */
    public List<Call> list(long afterId) {
        List<Call> out = new ArrayList<>();
        calls.descendingIterator().forEachRemaining(c -> {
            if (c.id() > afterId) {
                out.add(c);
            }
        });
        return out;
    }

    public void clear() {
        calls.clear();
    }
}

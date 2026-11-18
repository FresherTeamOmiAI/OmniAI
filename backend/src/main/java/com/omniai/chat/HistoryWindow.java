package com.omniai.chat;

import com.omniai.brain.BrainTurn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chọn phần lịch sử gần nhất để đưa vào prompt. Đây là giới hạn theo số message và số ký tự,
 * chưa phải bộ đếm token của model.
 */
public final class HistoryWindow {

    static final int MAX_MESSAGES = 8;
    static final int MAX_CHARS = 6_000;

    private HistoryWindow() {
    }

    public static List<BrainTurn> select(List<BrainTurn> oldestFirst) {
        List<BrainTurn> chosen = new ArrayList<>();
        int chars = 0;
        for (int i = oldestFirst.size() - 1; i >= 0 && chosen.size() < MAX_MESSAGES; i--) {
            BrainTurn turn = oldestFirst.get(i);
            int length = turn.content() == null ? 0 : turn.content().length();
            if (!chosen.isEmpty() && chars + length > MAX_CHARS) {
                break;
            }
            chars += length;
            chosen.add(turn);
        }
        Collections.reverse(chosen);
        return List.copyOf(chosen);
    }
}

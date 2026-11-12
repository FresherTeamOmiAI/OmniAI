package com.omniai.chat;

import com.omniai.brain.BrainTurn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWindowTest {

    @Test
    void keepsTheNewestTurnsInChronologicalOrder() {
        List<BrainTurn> turns = IntStream.range(0, 12)
                .mapToObj(i -> new BrainTurn(i % 2 == 0 ? "user" : "assistant", "m" + i))
                .toList();

        List<BrainTurn> selected = HistoryWindow.select(turns);

        assertThat(selected).hasSize(HistoryWindow.MAX_MESSAGES);
        assertThat(selected.getFirst().content()).isEqualTo("m4");
        assertThat(selected.getLast().content()).isEqualTo("m11");
    }

    @Test
    void dropsOlderTurnsWhenTheCharacterBudgetIsFull() {
        BrainTurn older = new BrainTurn("user", "x".repeat(HistoryWindow.MAX_CHARS));
        BrainTurn newer = new BrainTurn("assistant", "ngắn");

        List<BrainTurn> selected = HistoryWindow.select(List.of(older, newer));

        assertThat(selected).containsExactly(newer);
    }
}

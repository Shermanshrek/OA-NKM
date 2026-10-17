package ru.david.ui;

import org.junit.jupiter.api.Test;
import ru.david.io.LayoutData;
import ru.david.model.Concept;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {

    private static History.Snapshot state(String code) {
        return new History.Snapshot(List.of(new Concept(code, "")), List.of(), LayoutData.EMPTY);
    }

    @Test
    void newHistoryHasNothingToUndoOrRedo() {
        History history = new History();

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    void undoReturnsPreviousStateAndMakesCurrentRedoable() {
        History history = new History();
        history.record(state("A")); // было A, стало B

        History.Snapshot restored = history.undo(state("B"));

        assertEquals(state("A"), restored);
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());
        assertEquals(state("B"), history.redo(state("A")));
        assertTrue(history.canUndo());
    }

    @Test
    void severalStepsUndoInReverseOrder() {
        History history = new History();
        history.record(state("A"));
        history.record(state("B"));

        assertEquals(state("B"), history.undo(state("C")));
        assertEquals(state("A"), history.undo(state("B")));
        assertFalse(history.canUndo());
    }

    @Test
    void newActionClearsRedo() {
        History history = new History();
        history.record(state("A"));
        history.undo(state("B"));
        assertTrue(history.canRedo());

        history.record(state("A"));

        assertFalse(history.canRedo());
    }

    @Test
    void historyIsLimitedAndDropsOldestFirst() {
        History history = new History();
        for (int i = 0; i < 150; i++) {
            history.record(state("S" + i));
        }

        int steps = 0;
        History.Snapshot last = null;
        while (history.canUndo()) {
            last = history.undo(state("now"));
            steps++;
        }

        assertEquals(100, steps);
        assertEquals(state("S50"), last); // первые 50 состояний вытеснены
    }

    @Test
    void clearForgetsEverything() {
        History history = new History();
        history.record(state("A"));
        history.undo(state("B"));

        history.clear();

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}

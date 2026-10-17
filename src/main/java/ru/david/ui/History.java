package ru.david.ui;

import ru.david.io.LayoutData;
import ru.david.model.Concept;
import ru.david.model.Edge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * История для отмены и повтора действий. Хранит снимки состояния целиком: модели маленькие,
 * поэтому снимок дешевле и надёжнее, чем отдельная команда «отменить» для каждого вида правок.
 */
final class History {

    /**
     * Полное состояние редактора: вершины, связи и расположение. Это обычные неизменяемые данные,
     * поэтому снимки можно сравнивать через equals (так определяется, были ли несохранённые правки).
     */
    record Snapshot(List<Concept> concepts, List<Edge> edges, LayoutData layout) {
    }

    private static final int LIMIT = 100;

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();

    /** Запоминает состояние ДО изменения. Новое действие делает прежнюю цепочку повторов недействительной. */
    void record(Snapshot before) {
        undoStack.push(before);
        if (undoStack.size() > LIMIT) {
            undoStack.removeLast(); // самое старое
        }
        redoStack.clear();
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * @param current текущее состояние: оно уходит в стек повторов
     * @return состояние, к которому надо вернуться
     */
    Snapshot undo(Snapshot current) {
        redoStack.push(current);
        return undoStack.pop();
    }

    /**
     * @param current текущее состояние: оно снова становится отменяемым
     * @return состояние, к которому надо перейти вперёд
     */
    Snapshot redo(Snapshot current) {
        undoStack.push(current);
        return redoStack.pop();
    }

    void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}

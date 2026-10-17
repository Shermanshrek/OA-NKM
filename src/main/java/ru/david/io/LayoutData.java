package ru.david.io;

import java.util.List;

/**
 * Данные отображения, которые хранятся в файле рядом с моделью: где стоят вершины
 * и как изогнуты связи. Модель о них ничего не знает, а файл без них остаётся корректным
 * (вершины тогда расставляются автоматически).
 */
public record LayoutData(List<NodePosition> nodes, List<EdgeBend> bends) {

    public static final LayoutData EMPTY = new LayoutData(List.of(), List.of());

    public LayoutData {
        // В файле, написанном вручную, секции может не быть
        if (nodes == null) {
            nodes = List.of();
        }
        if (bends == null) {
            bends = List.of();
        }
    }

    /** Центр вершины на холсте. */
    public record NodePosition(String code, double x, double y) {
    }

    /**
     * Форма связи, заданная пользователем: для обычной связи это изгиб в пикселях,
     * для петли (from == to) угол направления в радианах.
     */
    public record EdgeBend(String from, String to, double bend) {
    }
}

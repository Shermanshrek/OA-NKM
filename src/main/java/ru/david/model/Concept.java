package ru.david.model;

/**
 * Вершина (параметр) когнитивной модели. Числовых характеристик у вершины нет:
 * вся количественная информация модели лежит в весах связей.
 *
 * @param code короткий уникальный код (A, B, X1...), по нему вершины связываются рёбрами
 * @param name человекочитаемое название
 */
public record Concept(String code, String name) {

    public Concept {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Код вершины не может быть пустым");
        }
        code = code.trim();
        name = (name == null) ? "" : name.trim();
    }
}

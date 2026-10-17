package ru.david.model;

/**
 * Направленная связь между вершинами: вершина {@code from} влияет на вершину {@code to}.
 * Вершины задаются кодами, поэтому ребро не хранит ссылок на объекты и его легко сохранять.
 *
 * @param from   код вершины-источника
 * @param to     код вершины-приёмника (совпадает с from для петли)
 * @param weight вес связи в [-1; 1], знак показывает характер влияния:
 *               плюс — усиливает, минус — ослабляет
 */
public record Edge(String from, String to, double weight) {

    public Edge {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("У связи должны быть заданы обе вершины");
        }
        // NaN тоже отсекается этой формой записи
        if (!(weight >= -1 && weight <= 1)) {
            throw new IllegalArgumentException(
                    "Вес связи " + from + "->" + to + " должен быть в [-1; 1], получено: " + weight);
        }
        // === ЗАПРЕТ НУЛЕВОГО ВЕСА === (чтобы разрешить вес 0, удалить этот блок if)
        // Знак цикла считается как произведение знаков дуг, у нуля знака нет.
        // Связь без влияния просто не создают.
        // Если вес 0 разрешат: поправить знак цикла в CycleFinder (этап 4) и тест
        // FuzzyCognitiveMapTest.zeroWeightIsRejected.
        if (weight == 0) {
            throw new IllegalArgumentException(
                    "Вес связи " + from + "->" + to + " не может быть нулевым");
        }
    }

    public boolean isLoop() {
        return from.equals(to);
    }

    public boolean isNegative() {
        return weight < 0;
    }
}

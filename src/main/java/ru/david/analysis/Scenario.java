package ru.david.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сценарий импульсного эксперимента: в какие вершины и с каким знаком подаётся
 * начальный импульс P(0) (строка таблицы плана эксперимента на слайде 12).
 *
 * @param number   номер сценария в плане, с единицы
 * @param impulses возмущения: в одну или в несколько вершин сразу
 */
public record Scenario(int number, List<Impulse> impulses) {

    /**
     * Возмущение q = +1 или -1 в одной вершине.
     * +1 даёт «положительный» (оптимистический) сценарий, -1 «отрицательный» (пессимистический).
     */
    public record Impulse(String vertex, int sign) {

        public Impulse {
            if (vertex == null || vertex.isBlank()) {
                throw new IllegalArgumentException("У возмущения должна быть задана вершина");
            }
            if (sign != 1 && sign != -1) {
                throw new IllegalArgumentException("Возмущение должно быть +1 или -1, получено: " + sign);
            }
        }
    }

    public Scenario {
        impulses = List.copyOf(impulses);
        if (impulses.isEmpty()) {
            throw new IllegalArgumentException("В сценарии нет ни одного возмущения");
        }
        Set<String> vertices = new HashSet<>();
        for (Impulse impulse : impulses) {
            if (!vertices.add(impulse.vertex())) {
                throw new IllegalArgumentException("Вершина " + impulse.vertex() + " указана в сценарии дважды");
            }
        }
    }

    /** Все возмущения положительные. */
    public boolean isPositive() {
        return impulses.stream().allMatch(i -> i.sign() > 0);
    }

    public String title() {
        return "Сценарий № " + number;
    }

    /** Возмущения в виде «q(C1) = -1, q(C2) = +1». */
    public String perturbationText() {
        return impulses.stream()
                .map(i -> "q(" + i.vertex() + ") = " + (i.sign() > 0 ? "+1" : "-1"))
                .collect(Collectors.joining(", "));
    }
}

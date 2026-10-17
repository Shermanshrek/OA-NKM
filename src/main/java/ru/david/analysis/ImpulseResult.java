package ru.david.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Результат одного сценария.
 *
 * @param scenario сценарий, который считали
 * @param codes    коды вершин в порядке строк и столбцов матрицы отношений
 * @param values   {@code values[t][i]} = V(t) для вершины i, t от 0 до числа шагов включительно;
 *                 это изменение значения относительно исходного (исходные значения нулевые)
 */
public record ImpulseResult(Scenario scenario, List<String> codes, double[][] values) {

    /** Число шагов t, на которых считался процесс (без нулевого). */
    public int steps() {
        return values.length - 1;
    }

    public double value(int t, int vertex) {
        return values[t][vertex];
    }

    /**
     * Номера вершин для графика, не больше {@code limit} (на слайде 12 четыре, для читаемости):
     * сначала вершины с импульсом, затем остальные по убыванию наибольшего |изменения| за все шаги.
     * Вершины, которые вообще не изменились, не берутся: прямая линия на нуле ничего не добавляет.
     */
    public List<Integer> chartVertices(int limit) {
        List<Integer> chosen = new ArrayList<>();
        for (Scenario.Impulse impulse : scenario.impulses()) {
            int vertex = codes.indexOf(impulse.vertex());
            if (chosen.size() < limit && vertex >= 0 && !chosen.contains(vertex)) {
                chosen.add(vertex);
            }
        }
        List<Integer> others = IntStream.range(0, codes.size()).boxed()
                .filter(v -> !chosen.contains(v) && maxAbsChange(v) > 1e-12)
                .sorted(Comparator.comparingDouble((Integer v) -> -maxAbsChange(v)).thenComparingInt(v -> v))
                .toList();
        for (int vertex : others) {
            if (chosen.size() >= limit) {
                break;
            }
            chosen.add(vertex);
        }
        return chosen;
    }

    private double maxAbsChange(int vertex) {
        double max = 0;
        for (double[] row : values) {
            max = Math.max(max, Math.abs(row[vertex]));
        }
        return max;
    }
}

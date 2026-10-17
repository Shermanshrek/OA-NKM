package ru.david.analysis;

import java.util.List;

/**
 * Результат анализа модели.
 *
 * @param cycles          найденные циклы (все или, если {@code cyclesTruncated}, первые по лимиту)
 * @param cyclesTruncated циклов больше лимита: список и вывод о структурной устойчивости неполные
 * @param eigenvalues     собственные числа матрицы отношений по убыванию модуля
 * @param status          устойчивость по возмущению и по начальному значению
 */
public record StabilityReport(List<Cycle> cycles, boolean cyclesTruncated,
                              List<EigenValue> eigenvalues, StabilityStatus status) {

    public int positiveCycleCount() {
        return (int) cycles.stream().filter(Cycle::isPositive).count();
    }

    public int negativeCycleCount() {
        return cycles.size() - positiveCycleCount();
    }

    public boolean structurallyStable() {
        return StabilityAnalyzer.isStructurallyStable(negativeCycleCount());
    }

    /** Максимальное по модулю собственное число M (список уже отсортирован по убыванию модуля). */
    public EigenValue maxEigenvalue() {
        return eigenvalues.get(0);
    }
}

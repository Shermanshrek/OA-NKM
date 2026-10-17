package ru.david.analysis;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import ru.david.model.FuzzyCognitiveMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Анализ устойчивости модели: структурной (по циклам) и по возмущению / начальному значению (по собственным числам). */
public final class StabilityAnalyzer {

    /** Допуск при сравнении |M| с единицей: собственные числа считаются приближённо. */
    static final double BOUNDARY_TOLERANCE = 1e-9;

    private StabilityAnalyzer() {
    }

    public static StabilityReport analyze(FuzzyCognitiveMap map) {
        return analyze(map, CycleFinder.DEFAULT_LIMIT);
    }

    /**
     * @param cycleLimit наибольшее число циклов, которое будет искаться
     * @throws IllegalArgumentException если в модели нет вершин
     * @throws IllegalStateException    если собственные числа не удалось вычислить
     */
    public static StabilityReport analyze(FuzzyCognitiveMap map, int cycleLimit) {
        if (map.concepts().isEmpty()) {
            throw new IllegalArgumentException("Модель пуста: добавьте вершины и связи");
        }
        CycleFinder.Result cycles = CycleFinder.find(map, cycleLimit);
        List<EigenValue> eigenvalues = eigenvalues(map.relationMatrix());
        StabilityStatus status = classify(eigenvalues.get(0).modulus());
        return new StabilityReport(cycles.cycles(), cycles.truncated(), eigenvalues, status);
    }

    /**
     * Критерий структурной устойчивости (слайд 9): модель структурно устойчива, если число
     * циклов с отрицательной обратной связью НЕЧЁТНОЕ.
     * <p>
     * Критерий вынесен в этот единственный метод: если преподаватель его уточнит, менять
     * нужно только здесь. Следствие критерия: у модели без циклов (0 отрицательных, а ноль чётный)
     * структурной устойчивости нет.
     */
    public static boolean isStructurallyStable(int negativeCycleCount) {
        return negativeCycleCount % 2 == 1;
    }

    /**
     * Устойчивость по возмущению и по начальному значению по M, максимальному по модулю
     * собственному числу матрицы отношений: |M| &lt; 1 устойчива, |M| &gt; 1 неустойчива.
     * Значение, равное единице с точностью до допуска, выделено в отдельный статус.
     */
    static StabilityStatus classify(double maxModulus) {
        if (Math.abs(maxModulus - 1) <= BOUNDARY_TOLERANCE) {
            return StabilityStatus.BOUNDARY;
        }
        return maxModulus < 1 ? StabilityStatus.STABLE : StabilityStatus.UNSTABLE;
    }

    /** Все собственные числа матрицы по убыванию модуля (первое является M). */
    private static List<EigenValue> eigenvalues(double[][] relationMatrix) {
        try {
            // Матрица W несимметричная: числа бывают комплексными, EigenDecomposition отдаёт
            // отдельно действительные и мнимые части
            EigenDecomposition eigen = new EigenDecomposition(new Array2DRowRealMatrix(relationMatrix));
            double[] re = eigen.getRealEigenvalues();
            double[] im = eigen.getImagEigenvalues();
            List<EigenValue> result = new ArrayList<>();
            for (int i = 0; i < re.length; i++) {
                result.add(new EigenValue(re[i], im[i]));
            }
            result.sort(Comparator.comparingDouble(EigenValue::modulus).reversed());
            return result;
        } catch (MathIllegalStateException | MathIllegalArgumentException e) {
            // Например, итерационный алгоритм не сошёлся
            throw new IllegalStateException("Не удалось вычислить собственные числа матрицы: " + e.getMessage(), e);
        }
    }
}

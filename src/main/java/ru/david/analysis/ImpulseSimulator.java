package ru.david.analysis;

import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.List;

/** Импульсное моделирование (слайды 13-14): как начальный импульс расходится по связям модели. */
public final class ImpulseSimulator {

    private ImpulseSimulator() {
    }

    /**
     * Считает импульсный процесс для сценария. A = W, веса дуг стоят в матрице как W[i][j] = вес i->j.
     * <pre>
     *   V(0) = V(исх) + P(0)        P(0) = возмущения сценария, V(исх) = 0 (слайд 15)
     *   P(t) = W^T * P(t-1)         импульс шага t: то, что вершины получили от изменений на шаге t-1
     *   V(t) = V(t-1) + P(t)        значение накапливает все импульсы
     * </pre>
     * Транспонирование нужно, потому что вершина i получает импульс от каждой вершины j по дуге j->i,
     * то есть P_i(t) = сумма по j от W[j][i] * P_j(t-1) (формула (1) на слайде 13).
     *
     * @param steps число шагов t после нулевого (не меньше 1)
     * @throws IllegalArgumentException если в сценарии есть вершина, которой нет в модели
     */
    public static ImpulseResult simulate(FuzzyCognitiveMap map, Scenario scenario, int steps) {
        if (steps < 1) {
            throw new IllegalArgumentException("Число шагов должно быть не меньше 1");
        }
        double[][] w = map.relationMatrix();
        int n = w.length;

        double[] p = new double[n]; // P(0)
        for (Scenario.Impulse impulse : scenario.impulses()) {
            int vertex = map.indexOf(impulse.vertex());
            if (vertex < 0) {
                throw new IllegalArgumentException("В модели нет вершины " + impulse.vertex());
            }
            p[vertex] = impulse.sign();
        }

        double[][] values = new double[steps + 1][];
        double[] v = p.clone(); // V(0), исходные значения нулевые
        values[0] = v.clone();
        for (int t = 1; t <= steps; t++) {
            p = multiplyTransposed(w, p);
            for (int i = 0; i < n; i++) {
                v[i] += p[i];
            }
            values[t] = v.clone();
        }

        List<String> codes = map.concepts().stream().map(Concept::code).toList();
        return new ImpulseResult(scenario, codes, values);
    }

    /** Произведение W^T * p без построения транспонированной матрицы. */
    private static double[] multiplyTransposed(double[][] w, double[] p) {
        int n = p.length;
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                result[i] += w[j][i] * p[j];
            }
        }
        return result;
    }
}

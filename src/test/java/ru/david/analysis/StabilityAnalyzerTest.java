package ru.david.analysis;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StabilityAnalyzerTest {

    private static FuzzyCognitiveMap mapOf(int n) {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        for (int i = 1; i <= n; i++) {
            map.addConcept(new Concept("C" + i, ""));
        }
        return map;
    }

    // ---------- структурная устойчивость ----------

    @Test
    void structuralCriterionIsOddNumberOfNegativeCycles() {
        assertFalse(StabilityAnalyzer.isStructurallyStable(0));
        assertTrue(StabilityAnalyzer.isStructurallyStable(1));
        assertFalse(StabilityAnalyzer.isStructurallyStable(2));
        assertTrue(StabilityAnalyzer.isStructurallyStable(3));
    }

    @Test
    void reportCountsCyclesBySign() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5);   // C1->C2->C1: положительный
        map.addEdge("C2", "C3", 0.5);
        map.addEdge("C3", "C2", -0.5);  // C2->C3->C2: отрицательный
        map.addEdge("C3", "C3", -0.5);  // петля: отрицательный

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(3, report.cycles().size());
        assertEquals(1, report.positiveCycleCount());
        assertEquals(2, report.negativeCycleCount());
        assertFalse(report.structurallyStable()); // отрицательных два, число чётное
    }

    @Test
    void singleNegativeCycleGivesStructuralStability() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", -0.5);

        assertTrue(StabilityAnalyzer.analyze(map).structurallyStable());
    }

    @Test
    void modelWithoutCyclesIsNotStructurallyStableByCriterion() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertTrue(report.cycles().isEmpty());
        assertFalse(report.structurallyStable()); // нуль чётный
    }

    @Test
    void truncatedCycleListIsReported() {
        FuzzyCognitiveMap map = mapOf(4);
        for (int i = 1; i <= 4; i++) {
            for (int j = 1; j <= 4; j++) {
                if (i != j) {
                    map.addEdge("C" + i, "C" + j, 0.5);
                }
            }
        }

        assertTrue(StabilityAnalyzer.analyze(map, 3).cyclesTruncated());
        assertFalse(StabilityAnalyzer.analyze(map, 100).cyclesTruncated());
    }

    // ---------- собственные числа ----------

    @Test
    void realEigenvaluesOfMutualPair() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5); // W = [[0, .5], [.5, 0]], числа +-0.5

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(2, report.eigenvalues().size());
        assertEquals(0.5, report.maxEigenvalue().modulus(), 1e-9);
        assertEquals(StabilityStatus.STABLE, report.status());
    }

    @Test
    void complexEigenvaluesAreHandled() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", -0.5); // W = [[0, .5], [-.5, 0]], числа +-0.5i

        StabilityReport report = StabilityAnalyzer.analyze(map);

        for (EigenValue value : report.eigenvalues()) {
            assertEquals(0, value.re(), 1e-9);
            assertEquals(0.5, Math.abs(value.im()), 1e-9);
            assertEquals(0.5, value.modulus(), 1e-9);
        }
        // Комплексно-сопряжённая пара
        assertEquals(0, report.eigenvalues().get(0).im() + report.eigenvalues().get(1).im(), 1e-9);
    }

    @Test
    void eigenvaluesAreSortedByDescendingModulus() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C1", 0.2);
        map.addEdge("C2", "C2", -0.9);
        map.addEdge("C3", "C3", 0.5); // диагональная матрица: числа 0.2, -0.9, 0.5

        List<EigenValue> values = StabilityAnalyzer.analyze(map).eigenvalues();

        assertEquals(0.9, values.get(0).modulus(), 1e-9);
        assertEquals(0.5, values.get(1).modulus(), 1e-9);
        assertEquals(0.2, values.get(2).modulus(), 1e-9);
        assertEquals(-0.9, values.get(0).re(), 1e-9); // знак сохранён, сортировка по модулю
    }

    @Test
    void acyclicModelHasZeroEigenvaluesAndIsStable() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.9);
        map.addEdge("C2", "C3", 0.9);

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(0, report.maxEigenvalue().modulus(), 1e-9);
        assertEquals(StabilityStatus.STABLE, report.status());
    }

    @Test
    void singleVertexWithoutEdgesIsStable() {
        StabilityReport report = StabilityAnalyzer.analyze(mapOf(1));

        assertEquals(1, report.eigenvalues().size());
        assertEquals(StabilityStatus.STABLE, report.status());
    }

    // ---------- статус по |M| ----------

    @Test
    void classifyUsesToleranceAroundOne() {
        assertEquals(StabilityStatus.STABLE, StabilityAnalyzer.classify(0.999));
        assertEquals(StabilityStatus.UNSTABLE, StabilityAnalyzer.classify(1.001));
        assertEquals(StabilityStatus.BOUNDARY, StabilityAnalyzer.classify(1.0));
        assertEquals(StabilityStatus.BOUNDARY, StabilityAnalyzer.classify(1 - 1e-12));
        assertEquals(StabilityStatus.BOUNDARY, StabilityAnalyzer.classify(1 + 1e-12));
    }

    @Test
    void largeEigenvalueMeansUnstable() {
        FuzzyCognitiveMap map = mapOf(3);
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                map.addEdge("C" + i, "C" + j, 1); // все единицы: наибольшее число равно 3
            }
        }

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(3, report.maxEigenvalue().modulus(), 1e-9);
        assertEquals(StabilityStatus.UNSTABLE, report.status());
    }

    @Test
    void twoCycleWithUnitWeightsIsOnBoundary() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 1);
        map.addEdge("C2", "C1", 1); // числа +-1

        assertEquals(StabilityStatus.BOUNDARY, StabilityAnalyzer.analyze(map).status());
    }

    @Test
    void threeCycleWithUnitWeightsIsOnBoundaryWithComplexRoots() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 1);
        map.addEdge("C2", "C3", 1);
        map.addEdge("C3", "C1", 1); // кубические корни из единицы: 1 и -1/2 +- i*sqrt(3)/2

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(StabilityStatus.BOUNDARY, report.status());
        assertTrue(report.eigenvalues().stream().anyMatch(v -> Math.abs(v.im()) > 0.8));
        report.eigenvalues().forEach(v -> assertEquals(1, v.modulus(), 1e-9));
    }

    // ---------- некорректные данные ----------

    @Test
    void emptyModelIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> StabilityAnalyzer.analyze(new FuzzyCognitiveMap()));

        assertTrue(e.getMessage().contains("пуста"));
    }

    @Test
    void largeModelIsAnalyzedWithoutFailure() {
        FuzzyCognitiveMap map = mapOf(25);
        for (int i = 1; i <= 25; i++) {
            map.addEdge("C" + i, "C" + (i % 25 + 1), (i % 2 == 0) ? 0.6 : -0.7);
            map.addEdge("C" + i, "C" + ((i + 6) % 25 + 1), 0.3);
        }

        StabilityReport report = StabilityAnalyzer.analyze(map);

        assertEquals(25, report.eigenvalues().size());
        assertFalse(report.cycles().isEmpty());
    }
}

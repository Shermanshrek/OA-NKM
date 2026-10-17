package ru.david.analysis;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpulseSimulatorTest {

    private static FuzzyCognitiveMap mapOf(int n) {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        for (int i = 1; i <= n; i++) {
            map.addConcept(new Concept("C" + i, ""));
        }
        return map;
    }

    private static Scenario single(String vertex, int sign) {
        return new Scenario(1, List.of(new Scenario.Impulse(vertex, sign)));
    }

    // ---------- формулы со слайдов 13-14 ----------

    @Test
    void slideExampleWithHalvedWeights() {
        // Слайды 15-16: V1->V3 +1, V1->V2 +2, V3->V4 +2, V2->V4 -1, V4->V1 +1. Веса в модели не
        // больше 1, поэтому берём все веса вдвое меньше: V(1) на слайде (1, 2, 1, 0) станет (1, 1, 0.5, 0)
        FuzzyCognitiveMap map = mapOf(4);
        map.addEdge("C1", "C3", 0.5);
        map.addEdge("C1", "C2", 1);
        map.addEdge("C3", "C4", 1);
        map.addEdge("C2", "C4", -0.5);
        map.addEdge("C4", "C1", 0.5);

        ImpulseResult result = ImpulseSimulator.simulate(map, single("C1", 1), 4);

        assertArrayEquals(new double[]{1, 0, 0, 0}, result.values()[0], 1e-12);   // V(0) = P(0)
        assertArrayEquals(new double[]{1, 1, 0.5, 0}, result.values()[1], 1e-12); // V(1) = V(0) + W^T P(0)
        // P(2): вершина 4 получает -0.5*1 от V2 и 1*0.5 от V3, итого 0 (на слайде 16 указано (0,0,0,1),
        // это расходится с формулами и весами того же слайда, см. CLAUDE.md), значит, процесс остановился
        assertArrayEquals(new double[]{1, 1, 0.5, 0}, result.values()[2], 1e-12);
        assertArrayEquals(new double[]{1, 1, 0.5, 0}, result.values()[4], 1e-12);
    }

    @Test
    void impulseTravelsAlongArcDirectionOnly() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5); // C1 влияет на C2, но не наоборот

        double[] fromSource = ImpulseSimulator.simulate(map, single("C1", 1), 1).values()[1];
        double[] fromTarget = ImpulseSimulator.simulate(map, single("C2", 1), 1).values()[1];

        assertArrayEquals(new double[]{1, 0.5}, fromSource, 1e-12);
        assertArrayEquals(new double[]{0, 1}, fromTarget, 1e-12); // обратно импульс не идёт
    }

    @Test
    void feedbackLoopAccumulatesAndConvergesToGeometricSeries() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5);

        ImpulseResult result = ImpulseSimulator.simulate(map, single("C1", 1), 60);

        // P(t): (1,0) -> (0,.5) -> (.25,0) -> (0,.125) ...; V(1) = (1, .5), V(2) = (1.25, .5)
        assertArrayEquals(new double[]{1, 0.5}, result.values()[1], 1e-12);
        assertArrayEquals(new double[]{1.25, 0.5}, result.values()[2], 1e-12);
        // Предел: 1 / (1 - 0.25) и 0.5 / (1 - 0.25)
        assertArrayEquals(new double[]{4.0 / 3, 2.0 / 3}, result.values()[60], 1e-9);
    }

    @Test
    void negativeLoopOscillates() {
        FuzzyCognitiveMap map = mapOf(1);
        map.addEdge("C1", "C1", -0.5);

        double[][] v = ImpulseSimulator.simulate(map, single("C1", 1), 3).values();

        // P(t) = (-0.5)^t; V(t) = частичные суммы: 1, 0.5, 0.75, 0.625
        assertEquals(1, v[0][0], 1e-12);
        assertEquals(0.5, v[1][0], 1e-12);
        assertEquals(0.75, v[2][0], 1e-12);
        assertEquals(0.625, v[3][0], 1e-12);
    }

    @Test
    void unstableModelGrowsWithoutBound() {
        FuzzyCognitiveMap map = mapOf(3);
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                map.addEdge("C" + i, "C" + j, 1); // все веса 1: наибольшее собственное число 3
            }
        }

        double[][] v = ImpulseSimulator.simulate(map, single("C1", 1), 10).values();

        assertTrue(v[10][0] > v[5][0] * 100);
    }

    // ---------- свойства линейной модели ----------

    @Test
    void negativeScenarioIsExactMirrorOfPositive() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.7);
        map.addEdge("C2", "C3", -0.4);
        map.addEdge("C3", "C1", 0.6);
        map.addEdge("C2", "C2", 0.3);

        double[][] positive = ImpulseSimulator.simulate(map, single("C2", 1), 15).values();
        double[][] negative = ImpulseSimulator.simulate(map, single("C2", -1), 15).values();

        for (int t = 0; t < positive.length; t++) {
            for (int i = 0; i < 3; i++) {
                assertEquals(-positive[t][i], negative[t][i], 1e-12);
            }
        }
    }

    @Test
    void scenarioWithTwoImpulsesIsSumOfSingleOnes() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.7);
        map.addEdge("C2", "C3", -0.4);
        map.addEdge("C3", "C1", 0.6);

        double[][] a = ImpulseSimulator.simulate(map, single("C1", 1), 10).values();
        double[][] b = ImpulseSimulator.simulate(map, single("C3", -1), 10).values();
        double[][] both = ImpulseSimulator.simulate(map, new Scenario(1,
                List.of(new Scenario.Impulse("C1", 1), new Scenario.Impulse("C3", -1))), 10).values();

        for (int t = 0; t <= 10; t++) {
            for (int i = 0; i < 3; i++) {
                assertEquals(a[t][i] + b[t][i], both[t][i], 1e-12);
            }
        }
    }

    // ---------- форма результата и ошибки ----------

    @Test
    void resultHasStepsPlusOneRowsAndAllVertices() {
        ImpulseResult result = ImpulseSimulator.simulate(mapOf(4), single("C2", 1), 7);

        assertEquals(7, result.steps());
        assertEquals(8, result.values().length);
        assertEquals(4, result.values()[0].length);
        assertEquals(List.of("C1", "C2", "C3", "C4"), result.codes());
    }

    @Test
    void unknownVertexInScenarioIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ImpulseSimulator.simulate(mapOf(2), single("Z", 1), 5));
    }

    @Test
    void zeroStepsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ImpulseSimulator.simulate(mapOf(2), single("C1", 1), 0));
    }

    @Test
    void scenarioValidatesItsImpulses() {
        assertThrows(IllegalArgumentException.class, () -> new Scenario.Impulse("C1", 0));
        assertThrows(IllegalArgumentException.class, () -> new Scenario.Impulse("C1", 2));
        assertThrows(IllegalArgumentException.class, () -> new Scenario.Impulse(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new Scenario(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Scenario(1,
                List.of(new Scenario.Impulse("C1", 1), new Scenario.Impulse("C1", -1))));
    }

    @Test
    void scenarioDescribesItself() {
        Scenario scenario = new Scenario(3,
                List.of(new Scenario.Impulse("C1", 1), new Scenario.Impulse("C2", -1)));

        assertEquals("Сценарий № 3", scenario.title());
        assertEquals("q(C1) = +1, q(C2) = -1", scenario.perturbationText());
        assertFalse(scenario.isPositive());
        assertTrue(single("C1", 1).isPositive());
    }

    // ---------- выбор вершин для графика ----------

    @Test
    void chartAlwaysContainsImpulseVertexThenLargestChanges() {
        FuzzyCognitiveMap map = mapOf(6);
        map.addEdge("C1", "C2", 0.9);  // C2 меняется сильнее всех остальных
        map.addEdge("C1", "C3", 0.5);
        map.addEdge("C1", "C4", 0.3);
        map.addEdge("C1", "C5", 0.1);

        ImpulseResult result = ImpulseSimulator.simulate(map, single("C1", 1), 3);

        // C1 (импульс), затем по убыванию изменения C2, C3, C4; C5 не влезает в четвёрку,
        // C6 вообще не менялась
        assertEquals(List.of(0, 1, 2, 3), result.chartVertices(4));
    }

    @Test
    void chartKeepsImpulseVertexEvenIfOthersChangeMore() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 1);
        map.addEdge("C2", "C2", 0.9); // C2 раскачивается и выходит далеко за 1

        ImpulseResult result = ImpulseSimulator.simulate(map, single("C1", 1), 20);

        assertEquals(List.of(0, 1), result.chartVertices(4));
        assertEquals(List.of(0), result.chartVertices(1)); // с лимитом 1 остаётся только вершина с импульсом
    }

    @Test
    void chartSkipsVerticesThatNeverChange() {
        ImpulseResult result = ImpulseSimulator.simulate(mapOf(5), single("C3", -1), 5);

        assertEquals(List.of(2), result.chartVertices(4));
    }

    @Test
    void chartIncludesAllImpulseVerticesUpToLimit() {
        FuzzyCognitiveMap map = mapOf(3);
        Scenario scenario = new Scenario(1, List.of(
                new Scenario.Impulse("C3", 1), new Scenario.Impulse("C1", -1)));

        ImpulseResult result = ImpulseSimulator.simulate(map, scenario, 3);

        assertEquals(List.of(2, 0), result.chartVertices(4)); // порядок как в сценарии
    }
}

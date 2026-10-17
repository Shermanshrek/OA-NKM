package ru.david.analysis;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CycleFinderTest {

    private static FuzzyCognitiveMap mapOf(int n) {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        for (int i = 1; i <= n; i++) {
            map.addConcept(new Concept("C" + i, ""));
        }
        return map;
    }

    /** Полный орграф: дуги между всеми парами вершин в обе стороны, при withLoops ещё и петли. */
    private static FuzzyCognitiveMap complete(int n, boolean withLoops) {
        FuzzyCognitiveMap map = mapOf(n);
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                if (i != j || withLoops) {
                    map.addEdge("C" + i, "C" + j, 0.5);
                }
            }
        }
        return map;
    }

    // ---------- простые случаи ----------

    @Test
    void graphWithoutEdgesHasNoCycles() {
        CycleFinder.Result result = CycleFinder.find(mapOf(3));

        assertTrue(result.cycles().isEmpty());
        assertFalse(result.truncated());
    }

    @Test
    void chainWithoutReturnHasNoCycles() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C3", 0.5);

        assertTrue(CycleFinder.find(map).cycles().isEmpty());
    }

    @Test
    void loopIsCycleOfOneEdge() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C2", "C2", 0.4);

        List<Cycle> cycles = CycleFinder.find(map).cycles();

        assertEquals(1, cycles.size());
        assertEquals(List.of("C2"), cycles.get(0).path());
        assertEquals("C2->C2", cycles.get(0).pathText());
        assertEquals(0.4, cycles.get(0).weightProduct(), 1e-12);
    }

    @Test
    void mutualPairIsOneCycleNotTwo() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5);

        List<Cycle> cycles = CycleFinder.find(map).cycles();

        assertEquals(1, cycles.size());
        assertEquals("C1->C2->C1", cycles.get(0).pathText());
    }

    @Test
    void cycleStartsFromItsSmallestVertex() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C3", "C1", 0.5); // обход «C3->C1->C2->C3» должен быть записан с C1
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C3", 0.5);

        assertEquals("C1->C2->C3->C1", CycleFinder.find(map).cycles().get(0).pathText());
    }

    @Test
    void cyclesSharingVerticesAreAllFound() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5);  // C1->C2->C1
        map.addEdge("C2", "C3", 0.5);
        map.addEdge("C3", "C1", 0.5);  // C1->C2->C3->C1

        Set<String> texts = new HashSet<>();
        CycleFinder.find(map).cycles().forEach(c -> texts.add(c.pathText()));

        assertEquals(Set.of("C1->C2->C1", "C1->C2->C3->C1"), texts);
    }

    @Test
    void cyclesInSeparateComponentsAreAllFound() {
        FuzzyCognitiveMap map = mapOf(4);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.5);
        map.addEdge("C3", "C4", 0.5);
        map.addEdge("C4", "C3", 0.5);

        assertEquals(2, CycleFinder.find(map).cycles().size());
    }

    // ---------- знак цикла ----------

    @Test
    void cycleWithNoNegativeEdgesIsPositive() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", 0.4);

        Cycle cycle = CycleFinder.find(map).cycles().get(0);

        assertTrue(cycle.isPositive());
        assertEquals(0, cycle.negativeEdges());
        assertEquals(0.2, cycle.weightProduct(), 1e-12);
    }

    @Test
    void cycleWithOneNegativeEdgeIsNegative() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C1", -0.4);

        Cycle cycle = CycleFinder.find(map).cycles().get(0);

        assertFalse(cycle.isPositive());
        assertEquals(-0.2, cycle.weightProduct(), 1e-12);
    }

    @Test
    void cycleWithTwoNegativeEdgesIsPositive() {
        FuzzyCognitiveMap map = mapOf(2);
        map.addEdge("C1", "C2", -0.5);
        map.addEdge("C2", "C1", -0.4);

        Cycle cycle = CycleFinder.find(map).cycles().get(0);

        assertTrue(cycle.isPositive());
        assertEquals(2, cycle.negativeEdges());
        assertEquals(0.2, cycle.weightProduct(), 1e-12);
    }

    @Test
    void negativeLoopIsNegativeCycle() {
        FuzzyCognitiveMap map = mapOf(1);
        map.addEdge("C1", "C1", -0.7);

        assertFalse(CycleFinder.find(map).cycles().get(0).isPositive());
    }

    @Test
    void cycleWithThreeNegativeEdgesIsNegative() {
        FuzzyCognitiveMap map = mapOf(3);
        map.addEdge("C1", "C2", -0.5);
        map.addEdge("C2", "C3", -0.5);
        map.addEdge("C3", "C1", -0.5);

        assertFalse(CycleFinder.find(map).cycles().get(0).isPositive());
    }

    // ---------- количество циклов ----------

    @Test
    void completeGraphCycleCountMatchesFormula() {
        // В полном орграфе на n вершинах циклов длины k ровно C(n, k) * (k - 1)!
        // (выбрать k вершин и обойти их по кругу, при этом начало фиксировано)
        assertEquals(5, CycleFinder.find(complete(3, false)).cycles().size());   // 3*1 + 1*2
        assertEquals(20, CycleFinder.find(complete(4, false)).cycles().size());  // 6*1 + 4*2 + 1*6
        assertEquals(24, CycleFinder.find(complete(4, true)).cycles().size());   // и ещё 4 петли
        assertEquals(84, CycleFinder.find(complete(5, false)).cycles().size());  // 10*1 + 10*2 + 5*6 + 24
    }

    @Test
    void noCycleIsFoundTwice() {
        List<Cycle> cycles = CycleFinder.find(complete(5, true)).cycles();

        Set<String> unique = new HashSet<>();
        cycles.forEach(c -> unique.add(c.pathText()));

        assertEquals(cycles.size(), unique.size());
    }

    @Test
    void resultMatchesBruteForceOnRandomGraphs() {
        Random random = new Random(42);
        for (int attempt = 0; attempt < 200; attempt++) {
            int n = 2 + random.nextInt(6);
            FuzzyCognitiveMap map = mapOf(n);
            boolean[][] edge = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (random.nextDouble() < 0.35) {
                        edge[i][j] = true;
                        map.addEdge("C" + (i + 1), "C" + (j + 1), random.nextBoolean() ? 0.5 : -0.5);
                    }
                }
            }

            Set<String> expected = bruteForce(edge);
            List<Cycle> found = CycleFinder.find(map).cycles();
            Set<String> actual = new HashSet<>();
            found.forEach(c -> actual.add(c.pathText()));

            assertEquals(expected, actual, "граф №" + attempt);
            assertEquals(expected.size(), found.size(), "дубликаты в графе №" + attempt);
        }
    }

    /** Независимый перебор: из каждой вершины s ходим только по вершинам с большими номерами. */
    private static Set<String> bruteForce(boolean[][] edge) {
        Set<String> result = new HashSet<>();
        for (int s = 0; s < edge.length; s++) {
            walk(edge, s, s, new ArrayList<>(List.of(s)), result);
        }
        return result;
    }

    private static void walk(boolean[][] edge, int s, int v, List<Integer> path, Set<String> result) {
        for (int u = s; u < edge.length; u++) {
            if (!edge[v][u]) {
                continue;
            }
            if (u == s) {
                StringBuilder text = new StringBuilder();
                for (int p : path) {
                    text.append("C").append(p + 1).append("->");
                }
                result.add(text.append("C").append(s + 1).toString());
            } else if (!path.contains(u)) {
                path.add(u);
                walk(edge, s, u, path, result);
                path.remove(path.size() - 1);
            }
        }
    }

    // ---------- лимит ----------

    @Test
    void limitStopsSearchAndMarksResultTruncated() {
        CycleFinder.Result result = CycleFinder.find(complete(4, false), 5); // всего 20

        assertEquals(5, result.cycles().size());
        assertTrue(result.truncated());
    }

    @Test
    void limitEqualToCycleCountIsNotTruncated() {
        CycleFinder.Result result = CycleFinder.find(complete(4, false), 20);

        assertEquals(20, result.cycles().size());
        assertFalse(result.truncated());
    }

    @Test
    void limitOneBelowCycleCountIsTruncated() {
        CycleFinder.Result result = CycleFinder.find(complete(4, false), 19);

        assertEquals(19, result.cycles().size());
        assertTrue(result.truncated());
    }

    @Test
    void limitAppliesToLoopsToo() {
        CycleFinder.Result result = CycleFinder.find(complete(3, true), 2); // 3 петли и 5 других

        assertEquals(2, result.cycles().size());
        assertTrue(result.truncated());
    }

    @Test
    void limitBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CycleFinder.find(mapOf(1), 0));
    }

    @Test
    void denseGraphWithManyVerticesStopsQuickly() {
        // Полный орграф на 25 вершинах: циклов астрономическое число, лимит должен спасти
        long started = System.nanoTime();

        CycleFinder.Result result = CycleFinder.find(complete(25, true), 10_000);

        assertEquals(10_000, result.cycles().size());
        assertTrue(result.truncated());
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000, "поиск слишком медленный");
    }
}

package ru.david.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ForceLayoutTest {

    private static final double W = 1000;
    private static final double H = 650;
    private static final double MARGIN = 76;
    private static final double MIN_DISTANCE = 72;

    private static double[][] layout(int n, List<int[]> edges) {
        return ForceLayout.compute(n, edges, W, H, MARGIN, MIN_DISTANCE);
    }

    private static double distance(double[][] pos, int a, int b) {
        return Math.hypot(pos[a][0] - pos[b][0], pos[a][1] - pos[b][1]);
    }

    @Test
    void emptyGraphGivesNoPositions() {
        assertEquals(0, layout(0, List.of()).length);
    }

    @Test
    void singleVertexIsCentered() {
        double[][] pos = layout(1, List.of());

        assertEquals(W / 2, pos[0][0], 1e-9);
        assertEquals(H / 2, pos[0][1], 1e-9);
    }

    @Test
    void positionsStayInsideMargins() {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            edges.add(new int[]{i, i + 1});
        }

        double[][] pos = layout(25, edges);

        for (double[] p : pos) {
            assertTrue(p[0] >= MARGIN - 1e-9 && p[0] <= W - MARGIN + 1e-9, "x вне области: " + p[0]);
            assertTrue(p[1] >= MARGIN - 1e-9 && p[1] <= H - MARGIN + 1e-9, "y вне области: " + p[1]);
        }
    }

    @Test
    void verticesDoNotOverlapEvenInDenseGraph() {
        int n = 25;
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                edges.add(new int[]{i, j}); // полный граф: притяжение стягивает всё в кучу
            }
        }

        double[][] pos = layout(n, edges);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                assertTrue(distance(pos, i, j) >= MIN_DISTANCE - 1e-6,
                        "вершины " + i + " и " + j + " слишком близко: " + distance(pos, i, j));
            }
        }
    }

    @Test
    void verticesWithoutEdgesDoNotOverlap() {
        double[][] pos = layout(20, List.of());

        for (int i = 0; i < 20; i++) {
            for (int j = i + 1; j < 20; j++) {
                assertTrue(distance(pos, i, j) >= MIN_DISTANCE - 1e-6);
            }
        }
    }

    @Test
    void connectedVerticesEndUpCloserThanUnconnected() {
        // Две отдельные пары: 0-1 и 2-3
        double[][] pos = layout(4, List.of(new int[]{0, 1}, new int[]{2, 3}));

        assertTrue(distance(pos, 0, 1) < distance(pos, 0, 2));
        assertTrue(distance(pos, 0, 1) < distance(pos, 1, 3));
        assertTrue(distance(pos, 2, 3) < distance(pos, 0, 3));
    }

    @Test
    void sameInputGivesSameResult() {
        List<int[]> edges = List.of(new int[]{0, 1}, new int[]{1, 2}, new int[]{2, 0}, new int[]{2, 3});

        assertArrayEquals(layout(4, edges), layout(4, edges));
    }

    @Test
    void randomGraphsAreAlwaysLaidOutWithoutNaN() {
        Random random = new Random(11);
        for (int attempt = 0; attempt < 50; attempt++) {
            int n = 1 + random.nextInt(30);
            List<int[]> edges = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (random.nextDouble() < 0.2) {
                        edges.add(new int[]{i, j});
                    }
                }
            }

            double[][] pos = layout(n, edges);

            for (double[] p : pos) {
                assertFalse(Double.isNaN(p[0]) || Double.isNaN(p[1]), "NaN в графе №" + attempt);
            }
        }
    }

    @Test
    void tinyAreaDoesNotBreak() {
        double[][] pos = ForceLayout.compute(5, List.of(new int[]{0, 1}), 50, 50, 76, 72);

        for (double[] p : pos) {
            assertFalse(Double.isNaN(p[0]) || Double.isNaN(p[1]));
        }
    }
}

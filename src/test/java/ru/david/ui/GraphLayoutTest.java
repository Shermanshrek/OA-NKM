package ru.david.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

class GraphLayoutTest {

    private FuzzyCognitiveMap map;
    private GraphLayout layout;

    @BeforeEach
    void setUp() {
        map = new FuzzyCognitiveMap();
        layout = new GraphLayout(map);
        addConcept("A", 100, 100);
        addConcept("B", 300, 100);
    }

    private void addConcept(String code, double x, double y) {
        map.addConcept(new Concept(code, ""));
        layout.setPosition(code, x, y);
    }

    @Test
    void conceptAtFindsNodeInsideRadiusOnly() {
        assertEquals("A", layout.conceptAt(new Point2D.Double(110, 100)).orElseThrow().code());
        assertTrue(layout.conceptAt(new Point2D.Double(200, 100)).isEmpty());
        assertTrue(layout.conceptAt(new Point2D.Double(100 + GraphLayout.NODE_RADIUS + 1, 100)).isEmpty());
    }

    @Test
    void conceptAtPrefersTopmostOfOverlappingNodes() {
        addConcept("C", 105, 100); // добавлена позже, значит нарисована сверху

        assertEquals("C", layout.conceptAt(new Point2D.Double(103, 100)).orElseThrow().code());
    }

    @Test
    void edgeAtFindsEdgeNearLineOnly() {
        map.addEdge("A", "B", 0.5);

        assertTrue(layout.edgeAt(new Point2D.Double(200, 103)).isPresent());
        assertTrue(layout.edgeAt(new Point2D.Double(200, 130)).isEmpty());
    }

    @Test
    void oppositeEdgesAreDrawnApartByDefault() {
        map.addEdge("A", "B", 0.5);
        map.addEdge("B", "A", -0.5);

        double ab = layout.shapeOf(map.findEdge("A", "B").orElseThrow()).labelPoint().getY();
        double ba = layout.shapeOf(map.findEdge("B", "A").orElseThrow()).labelPoint().getY();

        assertTrue(Math.abs(ab - ba) > 30, "встречные связи должны расходиться");
        assertTrue((ab - 100) * (ba - 100) < 0, "и лежать по разные стороны отрезка");
    }

    @Test
    void singleEdgeIsStraightByDefault() {
        map.addEdge("A", "B", 0.5);

        Point2D label = layout.shapeOf(map.findEdge("A", "B").orElseThrow()).labelPoint();

        assertEquals(100, label.getY(), 1e-6);
    }

    @Test
    void dragEdgeBendsCurveTowardsMouse() {
        map.addEdge("A", "B", 0.5);
        Edge edge = map.findEdge("A", "B").orElseThrow();

        layout.dragEdge(edge, new Point2D.Double(200, 160));

        Point2D label = layout.shapeOf(edge).labelPoint();
        assertEquals(200, label.getX(), 1e-3);
        assertEquals(160, label.getY(), 1e-3);
    }

    @Test
    void dragLoopTurnsItTowardsMouse() {
        map.addEdge("A", "A", 0.5);
        Edge loop = map.findEdge("A", "A").orElseThrow();

        layout.dragEdge(loop, new Point2D.Double(100, 200)); // мышь строго под вершиной

        assertTrue(layout.shapeOf(loop).labelPoint().getY() > 100 + GraphLayout.NODE_RADIUS);
    }

    @Test
    void edgeAtFindsLoop() {
        map.addEdge("A", "A", 0.5);
        Point2D onLoop = layout.shapeOf(map.findEdge("A", "A").orElseThrow()).labelPoint();

        assertTrue(layout.edgeAt(onLoop).isPresent());
    }

    @Test
    void forgetRemovedDropsStaleBendOfDeletedEdge() {
        map.addEdge("A", "B", 0.5);
        layout.dragEdge(map.findEdge("A", "B").orElseThrow(), new Point2D.Double(200, 200));

        map.removeConcept("B");
        layout.forgetRemoved();
        addConcept("B", 300, 100);
        map.addEdge("A", "B", 0.5);

        // Новая связь A->B не должна унаследовать изгиб старой
        Point2D label = layout.shapeOf(map.findEdge("A", "B").orElseThrow()).labelPoint();
        assertEquals(100, label.getY(), 1e-6);
    }

    @Test
    void exportAndRestoreKeepPositionsAndBends() {
        map.addEdge("A", "B", 0.5);
        map.addEdge("A", "A", 0.5);
        Edge ab = map.findEdge("A", "B").orElseThrow();
        Edge loop = map.findEdge("A", "A").orElseThrow();
        layout.dragEdge(ab, new Point2D.Double(200, 170));
        layout.dragEdge(loop, new Point2D.Double(100, 200));

        GraphLayout restored = new GraphLayout(map);
        restored.restore(layout.export());

        assertEquals(layout.position("A"), restored.position("A"));
        assertEquals(layout.position("B"), restored.position("B"));
        assertEquals(layout.shapeOf(ab).labelPoint(), restored.shapeOf(ab).labelPoint());
        assertEquals(layout.shapeOf(loop).labelPoint(), restored.shapeOf(loop).labelPoint());
    }

    @Test
    void exportSkipsDefaultShapes() {
        map.addEdge("A", "B", 0.5); // изгиб не трогали

        assertTrue(layout.export().bends().isEmpty());
        assertEquals(2, layout.export().nodes().size());
    }

    @Test
    void restoreIgnoresEntriesForUnknownConceptsAndEdges() {
        GraphLayout fresh = new GraphLayout(map);

        fresh.restore(new ru.david.io.LayoutData(
                java.util.List.of(new ru.david.io.LayoutData.NodePosition("Z", 1, 1),
                        new ru.david.io.LayoutData.NodePosition("A", 10, 20)),
                java.util.List.of(new ru.david.io.LayoutData.EdgeBend("A", "Z", 5))));

        assertNull(fresh.position("Z"));
        assertEquals(new Point2D.Double(10, 20), fresh.position("A"));
    }

    @Test
    void restoreIgnoresNonFiniteNumbers() {
        GraphLayout fresh = new GraphLayout(map);

        fresh.restore(new ru.david.io.LayoutData(
                java.util.List.of(new ru.david.io.LayoutData.NodePosition("A", Double.NaN, 5)),
                java.util.List.of()));

        assertNull(fresh.position("A"));
    }

    @Test
    void placeMissingPlacesOnlyUnplacedNodesInsideArea() {
        GraphLayout fresh = new GraphLayout(map);
        fresh.setPosition("A", 11, 22);

        fresh.placeMissing(800, 600);

        assertEquals(new Point2D.Double(11, 22), fresh.position("A")); // уже стояла, не тронута
        Point2D.Double b = fresh.position("B");
        assertNotNull(b);
        assertTrue(b.x >= GraphLayout.NODE_RADIUS && b.x <= 800 - GraphLayout.NODE_RADIUS);
        assertTrue(b.y >= GraphLayout.NODE_RADIUS && b.y <= 600 - GraphLayout.NODE_RADIUS);
    }

    @Test
    void placeMissingSpreadsNodesApart() {
        for (String code : new String[]{"C", "D", "E", "F"}) {
            map.addConcept(new Concept(code, ""));
        }
        GraphLayout fresh = new GraphLayout(map);

        fresh.placeMissing(800, 600);

        for (int i = 0; i < map.concepts().size(); i++) {
            for (int j = i + 1; j < map.concepts().size(); j++) {
                Point2D.Double p = fresh.position(map.concepts().get(i).code());
                Point2D.Double q = fresh.position(map.concepts().get(j).code());
                assertTrue(p.distance(q) > 2 * GraphLayout.NODE_RADIUS, "вершины наложились");
            }
        }
    }

    @Test
    void arrangePlacesAllVerticesAndResetsBends() {
        map.addEdge("A", "B", 0.5);
        layout.dragEdge(map.findEdge("A", "B").orElseThrow(), new Point2D.Double(200, 300));

        layout.arrange(1000, 650);

        for (Concept c : map.concepts()) {
            Point2D.Double p = layout.position(c.code());
            assertNotNull(p);
            assertTrue(p.x > 0 && p.x < 1000 && p.y > 0 && p.y < 650);
        }
        assertTrue(layout.export().bends().isEmpty());
        assertTrue(layout.position("A").distance(layout.position("B")) >= 2 * GraphLayout.NODE_RADIUS);
    }

    @Test
    void arrangeHandlesLoopsAndMutualEdgesWithoutFailing() {
        map.addEdge("A", "A", 0.5);
        map.addEdge("A", "B", 0.5);
        map.addEdge("B", "A", -0.5);

        layout.arrange(1000, 650);

        assertNotNull(layout.position("A"));
        assertNotNull(layout.position("B"));
    }

    @Test
    void edgeAtToleranceIsConfigurable() {
        map.addEdge("A", "B", 0.5);
        Point2D farFromLine = new Point2D.Double(200, 112); // в 12 единицах от линии

        assertTrue(layout.edgeAt(farFromLine).isEmpty());
        assertTrue(layout.edgeAt(farFromLine, 15).isPresent());
    }

    @Test
    void forgetRemovedDropsPositionOfDeletedNode() {
        map.removeConcept("A");
        layout.forgetRemoved();

        assertNull(layout.position("A"));
        assertNotNull(layout.position("B"));
    }
}

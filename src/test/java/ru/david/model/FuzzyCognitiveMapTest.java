package ru.david.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzyCognitiveMapTest {

    private FuzzyCognitiveMap map;

    @BeforeEach
    void setUp() {
        map = new FuzzyCognitiveMap();
        map.addConcept(new Concept("A", "Первая"));
        map.addConcept(new Concept("B", "Вторая"));
        map.addConcept(new Concept("C", "Третья"));
    }

    // ---------- вершины ----------

    @Test
    void duplicateConceptCodeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> map.addConcept(new Concept("A", "Другая")));
        assertEquals(3, map.concepts().size());
    }

    @Test
    void blankConceptCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Concept("  ", ""));
    }

    @Test
    void updateConceptKeepsPosition() {
        map.updateConcept(new Concept("B", "Новое имя"));

        assertEquals(1, map.indexOf("B"));
        assertEquals("Новое имя", map.findConcept("B").orElseThrow().name());
    }

    @Test
    void updateUnknownConceptIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> map.updateConcept(new Concept("Z", "")));
    }

    // ---------- автоматические коды ----------

    @Test
    void nextConceptCodeStartsFromC1() {
        assertEquals("C1", new FuzzyCognitiveMap().nextConceptCode());
    }

    @Test
    void nextConceptCodesFollowCreationOrder() {
        FuzzyCognitiveMap fresh = new FuzzyCognitiveMap();
        for (int i = 1; i <= 3; i++) {
            String code = fresh.nextConceptCode();
            assertEquals("C" + i, code);
            fresh.addConcept(new Concept(code, ""));
        }
    }

    @Test
    void nextConceptCodeDoesNotFillGapsAfterRemoval() {
        FuzzyCognitiveMap fresh = new FuzzyCognitiveMap();
        for (String code : new String[]{"C1", "C2", "C3"}) {
            fresh.addConcept(new Concept(code, ""));
        }

        fresh.removeConcept("C2");

        assertEquals("C4", fresh.nextConceptCode());
    }

    @Test
    void nextConceptCodeIgnoresForeignCodes() {
        // В setUp вершины A, B, C: «C» без номера и чужие коды не должны влиять
        map.addConcept(new Concept("C7", ""));
        map.addConcept(new Concept("X99", ""));
        map.addConcept(new Concept("Cabc", ""));

        assertEquals("C8", map.nextConceptCode());
    }

    // ---------- каскадное удаление ----------

    @Test
    void removeConceptDeletesIncomingAndOutgoingEdges() {
        map.addEdge("A", "B", 0.5);  // входящая в B
        map.addEdge("B", "C", -0.4); // исходящая из B
        map.addEdge("A", "C", 0.7);  // B не касается

        assertTrue(map.removeConcept("B"));

        assertEquals(2, map.concepts().size());
        assertEquals(1, map.edges().size());
        assertEquals(new Edge("A", "C", 0.7), map.edges().get(0));
    }

    @Test
    void removeConceptDeletesLoop() {
        map.addEdge("B", "B", 0.5);
        map.addEdge("A", "C", 0.1);

        map.removeConcept("B");

        assertEquals(1, map.edges().size());
        assertTrue(map.findEdge("B", "B").isEmpty());
    }

    @Test
    void removeConceptDeletesBothDirectionsOfBidirectionalEdge() {
        map.addEdge("A", "B", 0.5);
        map.addEdge("B", "A", -0.5);

        map.removeConcept("A");

        assertTrue(map.edges().isEmpty());
    }

    @Test
    void removeUnknownConceptChangesNothing() {
        map.addEdge("A", "B", 0.5);

        assertFalse(map.removeConcept("Z"));

        assertEquals(3, map.concepts().size());
        assertEquals(1, map.edges().size());
    }

    @Test
    void removedConceptCodeCanBeReused() {
        map.addEdge("A", "B", 0.5);
        map.removeConcept("B");

        map.addConcept(new Concept("B", "Новая"));

        // Старое ребро A->B не должно «воскреснуть»
        assertTrue(map.edges().isEmpty());
    }

    // ---------- связи ----------

    @Test
    void edgeToUnknownConceptIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "Z", 0.5));
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("Z", "A", 0.5));
        assertTrue(map.edges().isEmpty());
    }

    @Test
    void duplicateEdgeIsRejected() {
        map.addEdge("A", "B", 0.5);

        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "B", -0.2));
        assertEquals(1, map.edges().size());
    }

    @Test
    void reverseEdgeAndLoopAreAllowed() {
        map.addEdge("A", "B", 0.5);
        map.addEdge("B", "A", -0.5);
        map.addEdge("A", "A", 0.3);

        assertEquals(3, map.edges().size());
        assertTrue(map.findEdge("A", "A").orElseThrow().isLoop());
    }

    @Test
    void weightOutsideRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "B", 1.01));
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "B", -1.01));
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "B", Double.NaN));
    }

    @Test
    void weightOnBoundsIsAccepted() {
        map.addEdge("A", "B", 1);
        map.addEdge("B", "C", -1);

        assertEquals(2, map.edges().size());
    }

    @Test
    void zeroWeightIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.addEdge("A", "B", 0));
    }

    @Test
    void setWeightChangesExistingEdge() {
        map.addEdge("A", "B", 0.5);

        map.setWeight("A", "B", -0.8);

        assertEquals(-0.8, map.findEdge("A", "B").orElseThrow().weight());
        assertEquals(1, map.edges().size());
    }

    @Test
    void setWeightRejectsMissingEdgeAndBadWeight() {
        assertThrows(IllegalArgumentException.class, () -> map.setWeight("A", "B", 0.5));

        map.addEdge("A", "B", 0.5);
        assertThrows(IllegalArgumentException.class, () -> map.setWeight("A", "B", 2));
        assertEquals(0.5, map.findEdge("A", "B").orElseThrow().weight()); // не испортилось
    }

    @Test
    void removeEdgeRemovesOnlyThatDirection() {
        map.addEdge("A", "B", 0.5);
        map.addEdge("B", "A", 0.5);

        assertTrue(map.removeEdge("A", "B"));
        assertFalse(map.removeEdge("A", "B"));

        assertTrue(map.findEdge("B", "A").isPresent());
    }

    // ---------- матрица отношений ----------

    @Test
    void relationMatrixHasWeightOfEdgeFromIToJ() {
        map.addEdge("A", "B", 0.5);   // i=0, j=1
        map.addEdge("C", "A", -0.4);  // i=2, j=0
        map.addEdge("B", "B", 0.9);   // петля на диагонали

        double[][] w = map.relationMatrix();

        assertEquals(0.5, w[0][1]);
        assertEquals(0, w[1][0]); // обратной дуги нет
        assertEquals(-0.4, w[2][0]);
        assertEquals(0.9, w[1][1]);
        assertEquals(0, w[0][0]);
    }

    @Test
    void relationMatrixFollowsConceptOrderAfterRemoval() {
        map.addEdge("A", "C", 0.6);
        map.removeConcept("B"); // C сдвигается с индекса 2 на 1

        double[][] w = map.relationMatrix();

        assertEquals(2, w.length);
        assertEquals(0.6, w[0][1]);
    }

    @Test
    void emptyMapGivesEmptyMatrix() {
        assertEquals(0, new FuzzyCognitiveMap().relationMatrix().length);
    }

    // ---------- замена содержимого ----------

    @Test
    void replaceWithSwapsAllContent() {
        map.addEdge("A", "B", 0.5);
        List<Concept> newConcepts = List.of(new Concept("X", "Икс"), new Concept("Y", "Игрек"));
        List<Edge> newEdges = List.of(new Edge("X", "Y", -0.4), new Edge("Y", "Y", 0.2));

        map.replaceWith(newConcepts, newEdges);

        assertEquals(newConcepts, map.concepts());
        assertEquals(newEdges, map.edges());
        assertEquals(1, map.indexOf("Y"));
    }

    @Test
    void replaceWithAcceptsViewsOfTheSameMap() {
        map.addEdge("A", "B", 0.5);
        List<Concept> concepts = List.copyOf(map.concepts());
        List<Edge> edges = List.copyOf(map.edges());
        map.removeConcept("A");

        map.replaceWith(concepts, edges); // как при отмене удаления

        assertEquals(concepts, map.concepts());
        assertEquals(edges, map.edges());
    }

    @Test
    void replaceWithInvalidDataLeavesMapUntouched() {
        map.addEdge("A", "B", 0.5);

        assertThrows(IllegalArgumentException.class, () -> map.replaceWith(
                List.of(new Concept("X", "")), List.of(new Edge("X", "Z", 0.5)))); // связь на несуществующую вершину
        assertThrows(IllegalArgumentException.class, () -> map.replaceWith(
                List.of(new Concept("X", ""), new Concept("X", "")), List.of())); // дубликат кода

        assertEquals(3, map.concepts().size());
        assertEquals(1, map.edges().size());
    }

    // ---------- защита от изменения снаружи ----------

    @Test
    void returnedListsAreReadOnly() {
        assertThrows(UnsupportedOperationException.class,
                () -> map.concepts().add(new Concept("Z", "")));
        assertThrows(UnsupportedOperationException.class,
                () -> map.edges().add(new Edge("A", "B", 0.5)));
    }
}

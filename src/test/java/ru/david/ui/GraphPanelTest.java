package ru.david.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.david.io.LayoutData;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка поведения холста через синтетические события мыши. Диалоги в тестах недоступны,
 * поэтому проверяются режимы без диалогов: выбор, перетаскивание, удаление, а также отмена и масштаб.
 */
class GraphPanelTest {

    private GraphPanel panel;
    private FuzzyCognitiveMap map;

    @BeforeEach
    void setUp() {
        map = new FuzzyCognitiveMap();
        map.addConcept(new Concept("C1", "Первая"));
        map.addConcept(new Concept("C2", "Вторая"));
        map.addConcept(new Concept("C3", "Третья"));
        map.addEdge("C1", "C2", 0.5);
        map.addEdge("C2", "C3", -0.4);
        map.addEdge("C3", "C3", 0.2);

        panel = new GraphPanel();
        panel.setSize(1000, 650);
        panel.setModel(map, new LayoutData(java.util.List.of(
                new LayoutData.NodePosition("C1", 200, 200),
                new LayoutData.NodePosition("C2", 500, 200),
                new LayoutData.NodePosition("C3", 500, 450)), java.util.List.of()));
    }

    // ---------- помощники для событий ----------

    private void press(double x, double y, int button) {
        send(MouseEvent.MOUSE_PRESSED, x, y, button, 1);
    }

    private void drag(double x, double y, int button) {
        send(MouseEvent.MOUSE_DRAGGED, x, y, button, 0);
    }

    private void release(double x, double y, int button) {
        send(MouseEvent.MOUSE_RELEASED, x, y, button, 1);
    }

    private void send(int type, double x, double y, int button, int clicks) {
        int modifiers = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK
                : button == MouseEvent.BUTTON3 ? InputEvent.BUTTON3_DOWN_MASK : 0;
        panel.dispatchEvent(new MouseEvent(panel, type, System.currentTimeMillis(), modifiers,
                (int) Math.round(x), (int) Math.round(y), clicks, false, button));
    }

    private Point2D position(String code) {
        return panel.layoutData().nodes().stream().filter(n -> n.code().equals(code)).findFirst()
                .map(n -> (Point2D) new Point2D.Double(n.x(), n.y())).orElseThrow();
    }

    private void click(double x, double y) {
        press(x, y, MouseEvent.BUTTON1);
        release(x, y, MouseEvent.BUTTON1);
    }

    private void dragNode(double fromX, double fromY, double toX, double toY) {
        press(fromX, fromY, MouseEvent.BUTTON1);
        drag((fromX + toX) / 2, (fromY + toY) / 2, MouseEvent.BUTTON1);
        drag(toX, toY, MouseEvent.BUTTON1);
        release(toX, toY, MouseEvent.BUTTON1);
    }

    // ---------- перетаскивание ----------

    @Test
    void draggingNodeMovesItAndKeepsGrabOffset() {
        dragNode(210, 205, 310, 305); // схватили за точку правее и ниже центра вершины

        assertEquals(300, position("C1").getX(), 1e-6);
        assertEquals(300, position("C1").getY(), 1e-6);
    }

    @Test
    void draggingIsOneUndoStepAndUndoRestoresPosition() {
        dragNode(200, 200, 300, 300);
        assertTrue(panel.canUndo());

        panel.undo();

        assertEquals(200, position("C1").getX(), 1e-6);
        assertEquals(200, position("C1").getY(), 1e-6);
        assertFalse(panel.canUndo()); // шаг был один, а не по одному на каждое движение мыши
        assertTrue(panel.canRedo());
    }

    @Test
    void redoRepeatsTheDrag() {
        dragNode(200, 200, 300, 300);
        panel.undo();

        panel.redo();

        assertEquals(300, position("C1").getX(), 1e-6);
    }

    @Test
    void clickWithoutMovementIsNotAnUndoStep() {
        click(200, 200);

        assertFalse(panel.canUndo());
        assertFalse(panel.isModified());
    }

    @Test
    void draggingEdgeBendsItAndCanBeUndone() {
        // Линия C1->C2 идёт по y = 200 от x = 226 до x = 474; середина (350, 200)
        dragNode(350, 200, 350, 260);
        assertEquals(1, panel.layoutData().bends().size());
        assertTrue(panel.layoutData().bends().get(0).bend() > 50);

        panel.undo();

        assertTrue(panel.layoutData().bends().isEmpty());
    }

    // ---------- удаление ----------

    @Test
    void deleteModeRemovesVertexWithItsEdgesAndUndoBringsEverythingBack() {
        panel.setMode(EditMode.DELETE);

        click(500, 200); // C2: у неё входящая C1->C2 и исходящая C2->C3
        assertEquals(2, map.concepts().size());
        assertEquals(1, map.edges().size()); // осталась только петля C3
        assertTrue(panel.isModified());

        panel.undo();

        assertEquals(3, map.concepts().size());
        assertEquals(3, map.edges().size());
        assertEquals(500, position("C2").getX(), 1e-6);
        assertFalse(panel.isModified());
    }

    @Test
    void deleteModeRemovesEdgeByClickOnLine() {
        panel.setMode(EditMode.DELETE);

        click(350, 200);

        assertTrue(map.findEdge("C1", "C2").isEmpty());
        assertEquals(3, map.concepts().size());
    }

    @Test
    void deleteClickOnEmptySpaceChangesNothing() {
        panel.setMode(EditMode.DELETE);

        click(800, 600);

        assertFalse(panel.canUndo());
        assertFalse(panel.isModified());
    }

    @Test
    void undoKeepsTheSameModelObject() {
        panel.setMode(EditMode.DELETE);
        click(500, 200);

        panel.undo();

        assertSame(map, panel.model()); // другие вкладки держат ссылку на этот объект
    }

    // ---------- несохранённые изменения ----------

    @Test
    void freshlyLoadedModelIsNotModified() {
        assertFalse(panel.isModified());
        assertFalse(panel.canUndo());
    }

    @Test
    void editMakesModelModifiedAndMarkSavedClearsIt() {
        dragNode(200, 200, 300, 300);
        assertTrue(panel.isModified());

        panel.markSaved();
        assertFalse(panel.isModified());

        panel.undo();
        assertTrue(panel.isModified()); // ушли от сохранённого состояния
        panel.redo();
        assertFalse(panel.isModified()); // и вернулись
    }

    @Test
    void undoToSavedStateClearsModifiedFlag() {
        dragNode(200, 200, 300, 300);

        panel.undo();

        assertFalse(panel.isModified());
    }

    @Test
    void settingNewModelClearsHistory() {
        dragNode(200, 200, 300, 300);

        panel.setModel(new FuzzyCognitiveMap(), LayoutData.EMPTY);

        assertFalse(panel.canUndo());
        assertFalse(panel.canRedo());
        assertFalse(panel.isModified());
    }

    @Test
    void changeListenerIsCalledOnEdits() {
        int[] calls = {0};
        panel.addChangeListener(() -> calls[0]++);

        dragNode(200, 200, 300, 300);
        int afterDrag = calls[0];
        panel.undo();

        assertTrue(afterDrag > 0);
        assertTrue(calls[0] > afterDrag);
    }

    // ---------- масштаб и сдвиг ----------

    @Test
    void zoomChangesScaleWithinLimits() {
        for (int i = 0; i < 30; i++) {
            panel.zoomIn();
        }
        assertEquals(3.0, panel.scale(), 1e-9);

        for (int i = 0; i < 60; i++) {
            panel.zoomOut();
        }
        assertEquals(0.25, panel.scale(), 1e-9);

        panel.resetZoom();
        assertEquals(1.0, panel.scale(), 1e-9);
    }

    @Test
    void hitTestingWorksAtAnyZoom() {
        panel.zoomIn();
        panel.zoomIn();
        panel.setMode(EditMode.DELETE);

        Point2D onScreen = panel.toScreen(new Point2D.Double(500, 200)); // где теперь вершина C2
        click(onScreen.getX(), onScreen.getY());

        assertTrue(map.findConcept("C2").isEmpty());
    }

    @Test
    void draggingWhileZoomedMovesNodeByModelDistance() {
        panel.zoomIn(); // масштаб 1.25
        Point2D from = panel.toScreen(new Point2D.Double(200, 200));
        Point2D to = panel.toScreen(new Point2D.Double(260, 240));

        dragNode(from.getX(), from.getY(), to.getX(), to.getY());

        assertEquals(260, position("C1").getX(), 1.0); // допуск на округление экранных координат
        assertEquals(240, position("C1").getY(), 1.0);
    }

    @Test
    void draggingEmptySpaceInSelectModePansTheView() {
        Point2D before = panel.toScreen(new Point2D.Double(200, 200));

        dragNode(800, 600, 850, 630);

        Point2D after = panel.toScreen(new Point2D.Double(200, 200));
        assertEquals(50, after.getX() - before.getX(), 1.0);
        assertEquals(30, after.getY() - before.getY(), 1.0);
        assertFalse(panel.canUndo()); // сдвиг вида не правка модели
        assertFalse(panel.isModified());
    }

    @Test
    void rightButtonDragPansViewEvenOverNode() {
        Point2D before = panel.toScreen(new Point2D.Double(200, 200));

        press(200, 200, MouseEvent.BUTTON3);
        drag(240, 220, MouseEvent.BUTTON3);
        release(240, 220, MouseEvent.BUTTON3);

        Point2D after = panel.toScreen(new Point2D.Double(200, 200));
        assertEquals(40, after.getX() - before.getX(), 1.0);
        assertEquals(position("C1").getX(), 200, 1e-6); // вершина осталась на месте
    }

    @Test
    void fitToWindowShowsAllVertices() {
        panel.setModel(map, new LayoutData(java.util.List.of(
                new LayoutData.NodePosition("C1", -300, -200),
                new LayoutData.NodePosition("C2", 1800, 100),
                new LayoutData.NodePosition("C3", 700, 1500)), java.util.List.of()));

        panel.fitToWindow();

        assertTrue(panel.scale() < 1);
        for (String code : new String[]{"C1", "C2", "C3"}) {
            Point2D s = panel.toScreen(position(code));
            assertTrue(s.getX() > 0 && s.getX() < 1000 && s.getY() > 0 && s.getY() < 650,
                    code + " вне окна: " + s);
        }
    }

    @Test
    void autoArrangeOfLargerGraphSavesPreview() throws java.io.IOException {
        FuzzyCognitiveMap big = new FuzzyCognitiveMap();
        for (int i = 1; i <= 12; i++) {
            big.addConcept(new Concept("C" + i, "Фактор " + i));
        }
        int[][] arcs = {{1, 2}, {2, 3}, {3, 4}, {4, 1}, {2, 5}, {5, 6}, {6, 7}, {7, 5}, {3, 8}, {8, 9},
                {9, 10}, {10, 8}, {10, 11}, {11, 12}, {12, 1}, {6, 6}, {9, 3}, {4, 11}};
        double[] weights = {0.8, -0.6, 0.7, 0.5, -0.4, 0.9, 0.3, -0.7, 0.6, 0.5, -0.5, 0.4, 0.8, 0.6, -0.9, 0.3, 0.5, -0.3};
        for (int i = 0; i < arcs.length; i++) {
            big.addEdge("C" + arcs[i][0], "C" + arcs[i][1], weights[i]);
        }
        GraphPanel big1 = new GraphPanel();
        big1.setSize(1000, 650);
        big1.setModel(big, LayoutData.EMPTY); // позиции назначены по кругу

        big1.autoArrange();

        javax.imageio.ImageIO.write(PanelSnapshot.render(big1, 1000, 650), "png",
                new java.io.File("target/arranged-preview.png"));
        assertTrue(big1.canUndo());
    }

    @Test
    void autoArrangeIsUndoable() {
        Point2D before = position("C1");

        panel.autoArrange();
        assertNotEquals(before, position("C1"));
        assertTrue(panel.canUndo());

        panel.undo();
        assertEquals(before, position("C1"));
    }
}

package ru.david.ui;

import ru.david.io.LayoutData;
import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Холст редактора графа: показывает модель и превращает действия мыши в изменения модели.
 * Что именно делает щелчок, зависит от режима {@link EditMode}.
 * <p>
 * Координаты. Вершины и связи живут в координатах холста (модельных), а на экран попадают через
 * масштаб и сдвиг: {@code экран = модельные * scale + offset}. Все проверки попадания мышью
 * делаются в модельных координатах, поэтому масштаб на них не влияет.
 */
public class GraphPanel extends JPanel {

    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 3.0;
    private static final double BUTTON_ZOOM_STEP = 1.25;
    private static final double WHEEL_ZOOM_STEP = 1.1;
    /** Насколько близко к линии надо щёлкнуть, чтобы попасть по связи, в пикселях экрана. */
    private static final double EDGE_HIT_PIXELS = 6;

    private FuzzyCognitiveMap map;
    private GraphLayout layout;
    private EditMode mode = EditMode.SELECT;

    private final History history = new History();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private History.Snapshot savedState; // состояние на момент последнего сохранения или загрузки

    // Вид
    private double scale = 1;
    private double offsetX;
    private double offsetY;

    // Состояние мыши
    private String edgeSource;          // режим «Связь»: уже выбранная вершина-источник
    private Point2D mouse;              // последняя позиция мыши (модельные координаты), до неё тянется «резинка»
    private String draggedNode;         // перетаскиваемая вершина
    private Point2D grabOffset;         // от точки захвата до центра вершины: вершина не «прыгает» под курсором
    private Edge draggedEdge;           // перетаскиваемая связь
    private History.Snapshot dragStart; // состояние до начала перетаскивания (для отмены)
    private Point panAnchor;            // точка экрана, с которой начат сдвиг холста
    private double panStartX;
    private double panStartY;

    public GraphPanel() {
        setPreferredSize(new Dimension(1000, 650));
        setBackground(Color.WHITE);
        setModel(new FuzzyCognitiveMap(), LayoutData.EMPTY);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                onDrag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedNode = null;
                draggedEdge = null;
                dragStart = null;
                panAnchor = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = toModel(e.getPoint());
                if (edgeSource != null) {
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(e.getPoint(), Math.pow(WHEEL_ZOOM_STEP, -e.getPreciseWheelRotation()));
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    // ---------- модель и режимы ----------

    /**
     * Заменяет показываемую модель целиком (новая модель или загрузка из файла): очищает историю
     * отмены и считает это состояние сохранённым. Вершинам без сохранённой позиции позиция
     * назначается автоматически.
     */
    public void setModel(FuzzyCognitiveMap map, LayoutData layoutData) {
        this.map = map;
        this.layout = new GraphLayout(map);
        layout.restore(layoutData);
        layout.placeMissing(viewWidth(), viewHeight());
        history.clear();
        resetInteraction();
        resetView();
        savedState = snapshot();
        changed();
    }

    public FuzzyCognitiveMap model() {
        return map;
    }

    public LayoutData layoutData() {
        return layout.export();
    }

    public void setMode(EditMode mode) {
        this.mode = mode;
        edgeSource = null; // недоделанная связь при смене режима отменяется
        repaint();
    }

    // ---------- несохранённые изменения ----------

    /** Есть ли правки после последнего сохранения (отмена до сохранённого состояния снимает пометку). */
    public boolean isModified() {
        return !snapshot().equals(savedState);
    }

    public void markSaved() {
        savedState = snapshot();
    }

    /** Слушатель вызывается после любого изменения модели, расположения и истории. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    // ---------- отмена и повтор ----------

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        if (history.canUndo()) {
            restore(history.undo(snapshot()));
        }
    }

    public void redo() {
        if (history.canRedo()) {
            restore(history.redo(snapshot()));
        }
    }

    private History.Snapshot snapshot() {
        return new History.Snapshot(List.copyOf(map.concepts()), List.copyOf(map.edges()), layout.export());
    }

    private void restore(History.Snapshot snapshot) {
        // Объект модели остаётся тем же: на него ссылаются другие вкладки
        map.replaceWith(snapshot.concepts(), snapshot.edges());
        layout = new GraphLayout(map);
        layout.restore(snapshot.layout());
        resetInteraction();
        changed();
    }

    /** Завершает правку: если состояние изменилось, прежнее попадает в историю. */
    private void commit(History.Snapshot before) {
        if (!snapshot().equals(before)) {
            history.record(before);
        }
        changed();
    }

    private void commitDragStart() {
        if (dragStart != null) { // перетаскивание началось по-настоящему: запоминаем состояние до него
            history.record(dragStart);
            dragStart = null;
        }
    }

    private void changed() {
        repaint();
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private void resetInteraction() {
        edgeSource = null;
        draggedNode = null;
        draggedEdge = null;
        dragStart = null;
        panAnchor = null;
    }

    // ---------- расстановка и вид ----------

    /** Расставляет все вершины заново (отменяемо) и показывает холст целиком без масштаба. */
    public void autoArrange() {
        History.Snapshot before = snapshot();
        resetView();
        layout.arrange(viewWidth(), viewHeight());
        commit(before);
    }

    public void zoomIn() {
        zoomAt(new Point2D.Double(viewWidth() / 2.0, viewHeight() / 2.0), BUTTON_ZOOM_STEP);
    }

    public void zoomOut() {
        zoomAt(new Point2D.Double(viewWidth() / 2.0, viewHeight() / 2.0), 1 / BUTTON_ZOOM_STEP);
    }

    public void resetZoom() {
        resetView();
        repaint();
    }

    /** Подбирает масштаб и сдвиг так, чтобы весь граф был виден (но не увеличивает сверх 100%). */
    public void fitToWindow() {
        List<Concept> concepts = map.concepts();
        if (concepts.isEmpty()) {
            resetZoom();
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Concept c : concepts) {
            Point2D.Double p = layout.position(c.code());
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        double pad = GraphLayout.NODE_RADIUS + 50; // запас под подписи и петли
        double boxWidth = maxX - minX + 2 * pad;
        double boxHeight = maxY - minY + 2 * pad;
        scale = Math.max(MIN_SCALE, Math.min(1.0, Math.min(viewWidth() / boxWidth, viewHeight() / boxHeight)));
        // Центр графа переходит в центр окна
        offsetX = viewWidth() / 2.0 - (minX + maxX) / 2 * scale;
        offsetY = viewHeight() / 2.0 - (minY + maxY) / 2 * scale;
        repaint();
    }

    double scale() {
        return scale;
    }

    /** Перевод точки холста в точку экрана. */
    Point2D toScreen(Point2D model) {
        return new Point2D.Double(model.getX() * scale + offsetX, model.getY() * scale + offsetY);
    }

    /** Перевод точки экрана в точку холста. */
    private Point2D toModel(Point2D screen) {
        return new Point2D.Double((screen.getX() - offsetX) / scale, (screen.getY() - offsetY) / scale);
    }

    /** Меняет масштаб так, что точка холста под {@code screenPoint} остаётся на месте (зум «к курсору»). */
    private void zoomAt(Point2D screenPoint, double factor) {
        double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        Point2D anchor = toModel(screenPoint);
        scale = newScale;
        offsetX = screenPoint.getX() - anchor.getX() * scale;
        offsetY = screenPoint.getY() - anchor.getY() * scale;
        repaint();
    }

    private void resetView() {
        scale = 1;
        offsetX = 0;
        offsetY = 0;
    }

    private int viewWidth() {
        return getWidth() > 0 ? getWidth() : getPreferredSize().width;
    }

    private int viewHeight() {
        return getHeight() > 0 ? getHeight() : getPreferredSize().height;
    }

    // ---------- рисование ----------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(offsetX, offsetY);
            g2.scale(scale, scale);
            GraphPainter.paint(g2, map, layout, edgeSource, mouse);
        } finally {
            g2.dispose();
        }
        drawScaleLabel(g);
    }

    /** Показывает масштаб в углу, когда он не 100%: так видно, что холст увеличен или уменьшен. */
    private void drawScaleLabel(Graphics g) {
        if (Math.abs(scale - 1) < 0.005) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g2.setColor(Color.GRAY);
            g2.drawString(Math.round(scale * 100) + "%", getWidth() - 46, getHeight() - 8);
        } finally {
            g2.dispose();
        }
    }

    // ---------- мышь ----------

    // Всё делаем по нажатию, а не по щелчку: щелчок теряется, если мышь чуть сдвинулась
    private void onPress(MouseEvent e) {
        // Правая и средняя кнопки двигают холст в любом режиме
        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
            startPan(e);
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        Point2D p = toModel(e.getPoint());
        switch (mode) {
            case SELECT -> pressInSelectMode(e, p);
            case ADD_NODE -> pressInAddNodeMode(p);
            case ADD_EDGE -> pressInAddEdgeMode(p);
            case DELETE -> pressInDeleteMode(p);
        }
        repaint();
    }

    private void onDrag(MouseEvent e) {
        if (panAnchor != null) {
            offsetX = panStartX + (e.getX() - panAnchor.x);
            offsetY = panStartY + (e.getY() - panAnchor.y);
            repaint();
            return;
        }
        Point2D p = toModel(e.getPoint());
        if (draggedNode != null) {
            commitDragStart();
            layout.setPosition(draggedNode,
                    clampX(p.getX() + grabOffset.getX()), clampY(p.getY() + grabOffset.getY()));
            changed();
        } else if (draggedEdge != null) {
            commitDragStart();
            layout.dragEdge(draggedEdge, p);
            changed();
        }
        mouse = p;
    }

    private void startPan(MouseEvent e) {
        panAnchor = e.getPoint();
        panStartX = offsetX;
        panStartY = offsetY;
    }

    private void pressInSelectMode(MouseEvent e, Point2D p) {
        if (e.getClickCount() == 2) {
            editAt(p);
            return;
        }
        // Вершина важнее связи: у вершины под курсором связи могут проходить рядом
        Optional<Concept> concept = layout.conceptAt(p);
        if (concept.isPresent()) {
            draggedNode = concept.get().code();
            Point2D.Double center = layout.position(draggedNode);
            grabOffset = new Point2D.Double(center.x - p.getX(), center.y - p.getY());
            dragStart = snapshot();
            return;
        }
        draggedEdge = layout.edgeAt(p, EDGE_HIT_PIXELS / scale).orElse(null);
        if (draggedEdge != null) {
            dragStart = snapshot();
        } else {
            startPan(e); // перетаскивание пустого места двигает весь холст
        }
    }

    private void pressInAddNodeMode(Point2D p) {
        if (layout.conceptAt(p).isPresent()) {
            return; // поверх существующей вершины новую не ставим
        }
        History.Snapshot before = snapshot();
        Dialogs.conceptForm(this, "Новая вершина", map.nextConceptCode(), "", c -> {
            map.addConcept(c); // бросит исключение при дубликате кода, тогда позиция не запомнится
            layout.setPosition(c.code(), clampX(p.getX()), clampY(p.getY()));
        });
        commit(before);
    }

    private void pressInAddEdgeMode(Point2D p) {
        Optional<Concept> target = layout.conceptAt(p);
        if (target.isEmpty()) {
            edgeSource = null;
            return;
        }
        String code = target.get().code();
        if (edgeSource == null) {
            edgeSource = code;
            return;
        }
        createEdge(edgeSource, code);
        edgeSource = null;
    }

    private void pressInDeleteMode(Point2D p) {
        History.Snapshot before = snapshot();
        Optional<Concept> concept = layout.conceptAt(p);
        if (concept.isPresent()) {
            map.removeConcept(concept.get().code()); // модель сама удалит все связи вершины
        } else {
            layout.edgeAt(p, EDGE_HIT_PIXELS / scale).ifPresent(edge -> map.removeEdge(edge.from(), edge.to()));
        }
        layout.forgetRemoved();
        commit(before);
    }

    // ---------- действия ----------

    private void createEdge(String from, String to) {
        if (map.findEdge(from, to).isPresent()) {
            Dialogs.error(this, "Связь " + from + "->" + to + " уже есть. "
                    + "Чтобы изменить вес, дважды щёлкните по ней в режиме «Выбор».");
            return;
        }
        History.Snapshot before = snapshot();
        Dialogs.weightForm(this, "Новая связь " + from + "->" + to, "0.5",
                weight -> map.addEdge(from, to, weight));
        commit(before);
    }

    /** Двойной щелчок: правка вершины или веса связи под курсором. */
    private void editAt(Point2D p) {
        History.Snapshot before = snapshot();
        Optional<Concept> concept = layout.conceptAt(p);
        if (concept.isPresent()) {
            Concept c = concept.get();
            Dialogs.conceptForm(this, "Вершина " + c.code(), c.code(), c.name(), map::updateConcept);
        } else {
            layout.edgeAt(p, EDGE_HIT_PIXELS / scale).ifPresent(edge -> Dialogs.weightForm(
                    this, "Связь " + edge.from() + "->" + edge.to(), String.valueOf(edge.weight()),
                    weight -> map.setWeight(edge.from(), edge.to(), weight)));
        }
        commit(before);
    }

    // Вершину нельзя вытащить за край видимой области: иначе её легко потерять
    private double clampX(double x) {
        double low = toModel(new Point2D.Double(0, 0)).getX() + GraphLayout.NODE_RADIUS;
        double high = toModel(new Point2D.Double(viewWidth(), 0)).getX() - GraphLayout.NODE_RADIUS;
        return Math.max(low, Math.min(high, x));
    }

    private double clampY(double y) {
        double low = toModel(new Point2D.Double(0, 0)).getY() + GraphLayout.NODE_RADIUS;
        double high = toModel(new Point2D.Double(0, viewHeight())).getY() - GraphLayout.NODE_RADIUS;
        return Math.max(low, Math.min(high, y));
    }
}

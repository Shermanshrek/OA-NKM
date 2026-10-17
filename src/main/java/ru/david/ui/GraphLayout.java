package ru.david.ui;

import ru.david.io.LayoutData;
import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Раскладка модели на холсте: где стоят вершины, как изогнуты связи и куда смотрят петли.
 * Это данные отображения, поэтому в {@link FuzzyCognitiveMap} они не хранятся.
 */
final class GraphLayout {

    static final int NODE_RADIUS = 26;

    private static final double EDGE_HIT_TOLERANCE = 6;
    /** Изгиб встречных связей (A->B и B->A) по умолчанию, чтобы они не лежали друг на друге. */
    private static final double DEFAULT_BEND = 40;
    private static final double DEFAULT_LOOP_ANGLE = -Math.PI / 2; // петля сверху

    private record EdgeKey(String from, String to) {
    }

    private final FuzzyCognitiveMap map;
    private final Map<String, Point2D.Double> positions = new HashMap<>();
    /**
     * Форма связей, заданная пользователем. Для обычной связи это изгиб в пикселях,
     * для петли (from == to) угол направления в радианах. Нет записи значит форма по умолчанию.
     */
    private final Map<EdgeKey, Double> bends = new HashMap<>();

    GraphLayout(FuzzyCognitiveMap map) {
        this.map = map;
    }

    // ---------- вершины ----------

    Point2D.Double position(String code) {
        return positions.get(code);
    }

    void setPosition(String code, double x, double y) {
        positions.put(code, new Point2D.Double(x, y));
    }

    /** Вершина под точкой. Если вершины перекрываются, берётся нарисованная позже (верхняя). */
    Optional<Concept> conceptAt(Point2D p) {
        List<Concept> concepts = map.concepts();
        for (int i = concepts.size() - 1; i >= 0; i--) {
            Concept c = concepts.get(i);
            Point2D.Double pos = positions.get(c.code());
            if (pos != null && pos.distance(p) <= NODE_RADIUS) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    // ---------- связи ----------

    EdgeShape shapeOf(Edge e) {
        Point2D.Double a = positions.get(e.from());
        EdgeKey key = new EdgeKey(e.from(), e.to());
        if (e.isLoop()) {
            return new EdgeShape.Loop(a, bends.getOrDefault(key, DEFAULT_LOOP_ANGLE), NODE_RADIUS);
        }
        boolean hasReverse = map.findEdge(e.to(), e.from()).isPresent();
        double bend = bends.getOrDefault(key, hasReverse ? DEFAULT_BEND : 0.0);
        return EdgeShape.Curve.between(a, positions.get(e.to()), bend, NODE_RADIUS);
    }

    Optional<Edge> edgeAt(Point2D p) {
        return edgeAt(p, EDGE_HIT_TOLERANCE);
    }

    /** Связь, ближайшая к точке (не дальше допуска), или пусто. Допуск в единицах холста. */
    Optional<Edge> edgeAt(Point2D p, double tolerance) {
        Edge best = null;
        double bestDistance = tolerance;
        for (Edge e : map.edges()) {
            double distance = shapeOf(e).distanceTo(p);
            if (distance <= bestDistance) {
                best = e;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Двигает связь за мышью: у кривой меняется изгиб, у петли направление. */
    void dragEdge(Edge e, Point2D mouse) {
        Point2D.Double a = positions.get(e.from());
        EdgeKey key = new EdgeKey(e.from(), e.to());
        if (e.isLoop()) {
            bends.put(key, Math.atan2(mouse.getY() - a.y, mouse.getX() - a.x));
        } else {
            bends.put(key, EdgeShape.Curve.bendFor(a, positions.get(e.to()), mouse));
        }
    }

    // ---------- синхронизация с моделью ----------

    /**
     * Забывает раскладку удалённых вершин и связей. Вызывать после удаления из модели:
     * иначе новая вершина или связь с тем же кодом унаследовала бы чужой изгиб.
     */
    void forgetRemoved() {
        Set<String> codes = map.concepts().stream().map(Concept::code).collect(Collectors.toSet());
        positions.keySet().retainAll(codes);
        bends.keySet().removeIf(k -> map.findEdge(k.from(), k.to()).isEmpty());
    }

    // ---------- сохранение и загрузка ----------

    /** Раскладка в виде, пригодном для записи в файл. Форма по умолчанию не пишется. */
    LayoutData export() {
        List<LayoutData.NodePosition> nodes = new ArrayList<>();
        for (Concept c : map.concepts()) {
            Point2D.Double p = positions.get(c.code());
            if (p != null) {
                nodes.add(new LayoutData.NodePosition(c.code(), p.x, p.y));
            }
        }
        List<LayoutData.EdgeBend> edgeBends = new ArrayList<>();
        for (Edge e : map.edges()) {
            Double bend = bends.get(new EdgeKey(e.from(), e.to()));
            if (bend != null) {
                edgeBends.add(new LayoutData.EdgeBend(e.from(), e.to(), bend));
            }
        }
        return new LayoutData(nodes, edgeBends);
    }

    /** Применяет раскладку из файла. Записи про несуществующие вершины и связи пропускаются. */
    void restore(LayoutData data) {
        for (LayoutData.NodePosition n : data.nodes()) {
            if (n != null && map.indexOf(n.code()) >= 0 && isFinite(n.x()) && isFinite(n.y())) {
                setPosition(n.code(), n.x(), n.y());
            }
        }
        for (LayoutData.EdgeBend b : data.bends()) {
            if (b != null && map.findEdge(b.from(), b.to()).isPresent() && isFinite(b.bend())) {
                bends.put(new EdgeKey(b.from(), b.to()), b.bend());
            }
        }
    }

    /**
     * Ставит вершины, у которых нет позиции (файл без раскладки или написанный вручную),
     * по окружности в центре области width x height. Вершины с позицией не трогает.
     */
    void placeMissing(double width, double height) {
        List<Concept> concepts = map.concepts();
        double radius = Math.max(0, Math.min(width, height) / 2 - NODE_RADIUS - 30);
        for (int i = 0; i < concepts.size(); i++) {
            String code = concepts.get(i).code();
            if (!positions.containsKey(code)) {
                double angle = 2 * Math.PI * i / concepts.size() - Math.PI / 2; // первая вершина сверху
                setPosition(code, width / 2 + radius * Math.cos(angle), height / 2 + radius * Math.sin(angle));
            }
        }
    }

    /**
     * Расставляет все вершины заново силовым методом ({@link ForceLayout}) в области width x height
     * и сбрасывает изгибы связей и направления петель к значениям по умолчанию: они были подобраны
     * под прежнее расположение и после перестановки только мешали бы.
     */
    void arrange(double width, double height) {
        List<Concept> concepts = map.concepts();
        int n = concepts.size();
        List<int[]> pairs = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Edge e : map.edges()) {
            int a = map.indexOf(e.from());
            int b = map.indexOf(e.to());
            // Петля вершины не двигает, а встречные связи A->B и B->A стягивают пару один раз
            if (a != b && seen.add(Math.min(a, b) * n + Math.max(a, b))) {
                pairs.add(new int[]{Math.min(a, b), Math.max(a, b)});
            }
        }
        // Отступ от края с запасом под подпись вершины и петлю, которая торчит над ней
        double[][] xy = ForceLayout.compute(n, pairs, width, height, NODE_RADIUS + 50, 2 * NODE_RADIUS + 20);
        positions.clear();
        bends.clear();
        for (int i = 0; i < n; i++) {
            setPosition(concepts.get(i).code(), xy[i][0], xy[i][1]);
        }
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}

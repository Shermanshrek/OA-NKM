package ru.david.analysis;

import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Поиск всех элементарных циклов алгоритмом Джонсона (D. B. Johnson, 1975).
 * <p>
 * Идея. Вершины нумеруются 0..n-1. Для каждой стартовой вершины s ищутся циклы, проходящие
 * через s и не заходящие в вершины с меньшими номерами: так каждый цикл находится ровно один раз,
 * начиная со своей наименьшей вершины. Поиск в глубину из s ускоряется двумя приёмами:
 * <ul>
 *   <li>работаем только внутри компоненты сильной связности s (из вершины можно вернуться в s);</li>
 *   <li>блокировка вершин: если из вершины уже не удалось вернуться в s, повторно в неё не заходим,
 *       пока в пути не появится новая вершина, дающая шанс на возврат (метод unblock).</li>
 * </ul>
 * Петли в общий поиск не входят: каждая петля сразу даёт цикл из одной дуги.
 * Число циклов в графе может быть огромным, поэтому есть лимит; при его превышении поиск
 * останавливается и результат помечается как усечённый.
 */
public final class CycleFinder {

    public static final int DEFAULT_LIMIT = 10_000;

    /**
     * @param truncated true, если циклов больше лимита и показаны не все
     */
    public record Result(List<Cycle> cycles, boolean truncated) {
    }

    private final int limit;
    private final List<String> codes;
    private final double[][] weights;
    private final boolean[] hasLoop;
    private final List<List<Integer>> out = new ArrayList<>(); // out.get(v): куда ведут дуги из v (без петель)
    private final List<List<Integer>> in = new ArrayList<>();  // in.get(v): откуда ведут дуги в v (без петель)

    private final List<Cycle> cycles = new ArrayList<>();
    private boolean truncated;

    // Состояние поиска для текущей стартовой вершины
    private int start;
    private boolean[] inComponent;
    private boolean[] blocked;
    private List<Set<Integer>> blockedBy; // blockedBy.get(u): вершины, которые разблокируются вместе с u
    private final List<Integer> path = new ArrayList<>();

    private CycleFinder(FuzzyCognitiveMap map, int limit) {
        this.limit = limit;
        codes = map.concepts().stream().map(Concept::code).toList();
        weights = map.relationMatrix();
        int n = codes.size();
        hasLoop = new boolean[n];
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
            in.add(new ArrayList<>());
        }
        for (Edge e : map.edges()) {
            int from = map.indexOf(e.from());
            int to = map.indexOf(e.to());
            if (from == to) {
                hasLoop[from] = true;
            } else {
                out.get(from).add(to);
                in.get(to).add(from);
            }
        }
        // Сортировка нужна только для воспроизводимого порядка циклов в таблице
        out.forEach(Collections::sort);
    }

    /**
     * @param limit наибольшее число возвращаемых циклов (не меньше 1)
     */
    public static Result find(FuzzyCognitiveMap map, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Лимит циклов должен быть не меньше 1");
        }
        return new CycleFinder(map, limit).run();
    }

    public static Result find(FuzzyCognitiveMap map) {
        return find(map, DEFAULT_LIMIT);
    }

    private Result run() {
        for (int s = 0; s < codes.size() && !truncated; s++) {
            start = s;
            if (hasLoop[s]) {
                report(List.of(s));
            }
            // Цикл длиннее одной дуги возможен, только если компонента больше одной вершины
            if (!truncated && prepareComponent() > 1) {
                circuit(s);
            }
        }
        return new Result(List.copyOf(cycles), truncated);
    }

    /**
     * Отмечает компоненту сильной связности стартовой вершины в подграфе вершин {@code >= start}
     * (вершины, достижимые из start и из которых достижима start) и сбрасывает блокировки.
     *
     * @return число вершин в компоненте
     */
    private int prepareComponent() {
        int n = codes.size();
        boolean[] forward = reachable(out);
        boolean[] backward = reachable(in);
        inComponent = new boolean[n];
        int size = 0;
        for (int v = 0; v < n; v++) {
            inComponent[v] = forward[v] && backward[v];
            if (inComponent[v]) {
                size++;
            }
        }
        blocked = new boolean[n];
        blockedBy = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            blockedBy.add(new HashSet<>());
        }
        return size;
    }

    /** Вершины, достижимые из start по заданным дугам, среди вершин с номерами не меньше start. */
    private boolean[] reachable(List<List<Integer>> edges) {
        boolean[] seen = new boolean[codes.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        seen[start] = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int u : edges.get(v)) {
                if (u >= start && !seen[u]) {
                    seen[u] = true;
                    stack.push(u);
                }
            }
        }
        return seen;
    }

    /**
     * Поиск в глубину из v по текущему пути.
     *
     * @return true, если из v удалось дойти до start (то есть найден хотя бы один цикл)
     */
    private boolean circuit(int v) {
        boolean found = false;
        path.add(v);
        blocked[v] = true;
        for (int u : out.get(v)) {
            if (!inComponent[u]) {
                continue;
            }
            if (u == start) {
                report(path);
                found = true;
            } else if (!blocked[u] && circuit(u)) {
                found = true;
            }
            if (truncated) {
                break; // лимит исчерпан, дальше искать незачем
            }
        }
        if (found) {
            unblock(v);
        } else {
            // Из v вернуться в start не получилось, v остаётся заблокированной. Запоминаем: как только
            // разблокируется любой из её соседей u, у v снова появится шанс, и её надо разблокировать тоже
            for (int u : out.get(v)) {
                if (inComponent[u]) {
                    blockedBy.get(u).add(v);
                }
            }
        }
        path.removeLast();
        return found;
    }

    /** Снимает блокировку с u и, по цепочке, со всех вершин, ожидавших её разблокировки. */
    private void unblock(int u) {
        blocked[u] = false;
        List<Integer> waiting = new ArrayList<>(blockedBy.get(u));
        blockedBy.get(u).clear();
        for (int v : waiting) {
            if (blocked[v]) {
                unblock(v);
            }
        }
    }

    /** Запоминает цикл по вершинам {@code vertices} (замыкающая дуга ведёт из последней в первую). */
    private void report(List<Integer> vertices) {
        if (cycles.size() >= limit) {
            truncated = true; // нашли цикл сверх лимита: значит, показаны не все
            return;
        }
        double product = 1;
        int negative = 0;
        for (int i = 0; i < vertices.size(); i++) {
            int from = vertices.get(i);
            int to = vertices.get((i + 1) % vertices.size());
            double weight = weights[from][to];
            product *= weight;
            if (weight < 0) {
                negative++;
            }
        }
        List<String> names = vertices.stream().map(codes::get).toList();
        cycles.add(new Cycle(names, product, negative));
    }
}

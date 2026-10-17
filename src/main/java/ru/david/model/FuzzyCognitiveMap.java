package ru.david.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нечёткая когнитивная карта: набор вершин и направленных взвешенных связей между ними.
 * Все правила целостности (уникальные коды, рёбра только между существующими вершинами,
 * каскадное удаление) держатся здесь, а не в интерфейсе, поэтому работают всегда.
 */
public class FuzzyCognitiveMap {

    private static final String CODE_PREFIX = "C";
    // До 9 цифр, чтобы номер заведомо помещался в int
    private static final Pattern AUTO_CODE = Pattern.compile(CODE_PREFIX + "(\\d{1,9})");

    // Порядок вершин важен: он задаёт номера строк и столбцов матрицы отношений
    private final List<Concept> concepts = new ArrayList<>();
    // Моделей десятки вершин, поэтому обычного списка и линейного поиска достаточно
    private final List<Edge> edges = new ArrayList<>();

    // ---------- вершины ----------

    public void addConcept(Concept concept) {
        if (findConcept(concept.code()).isPresent()) {
            throw new IllegalArgumentException("Вершина с кодом " + concept.code() + " уже есть");
        }
        concepts.add(concept);
    }

    /**
     * Удаляет вершину вместе со ВСЕМИ входящими и исходящими рёбрами (включая петлю),
     * иначе остались бы рёбра, указывающие в пустоту.
     *
     * @return true, если вершина была и удалена
     */
    public boolean removeConcept(String code) {
        boolean removed = concepts.removeIf(c -> c.code().equals(code));
        if (removed) {
            edges.removeIf(e -> e.from().equals(code) || e.to().equals(code));
        }
        return removed;
    }

    /**
     * Меняет название вершины. Код менять нельзя: на него ссылаются рёбра.
     * Вершина остаётся на прежней позиции, поэтому матрица отношений не «перемешивается».
     */
    public void updateConcept(Concept updated) {
        int index = indexOf(updated.code());
        if (index < 0) {
            throw new IllegalArgumentException("Нет вершины с кодом " + updated.code());
        }
        concepts.set(index, updated);
    }

    /**
     * Код для новой вершины: C1, C2, C3... Номер на единицу больше наибольшего из уже
     * существующих. Так коды идут по порядку создания, не повторяются и не заполняют
     * «дыры» после удаления (после удаления C2 из C1, C2, C3 следующей будет C4).
     * Вершины с другими кодами (например, из файла) на нумерацию не влияют.
     */
    public String nextConceptCode() {
        int max = 0;
        for (Concept c : concepts) {
            Matcher m = AUTO_CODE.matcher(c.code());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return CODE_PREFIX + (max + 1);
    }

    public Optional<Concept> findConcept(String code) {
        return concepts.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    /** Номер вершины в матрице отношений или -1, если такой вершины нет. */
    public int indexOf(String code) {
        for (int i = 0; i < concepts.size(); i++) {
            if (concepts.get(i).code().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    public List<Concept> concepts() {
        return Collections.unmodifiableList(concepts);
    }

    // ---------- связи ----------

    /**
     * Добавляет связь from -> to. Допускаются петли (from == to) и встречные связи
     * (A->B вместе с B->A: это два разных ребра). Дубликат того же направления запрещён.
     */
    public void addEdge(String from, String to, double weight) {
        Edge edge = new Edge(from, to, weight); // проверяет вес
        requireConcept(from);
        requireConcept(to);
        if (findEdge(from, to).isPresent()) {
            throw new IllegalArgumentException("Связь " + from + "->" + to + " уже есть");
        }
        edges.add(edge);
    }

    /** Меняет вес существующей связи. */
    public void setWeight(String from, String to, double weight) {
        Edge updated = new Edge(from, to, weight); // проверяет вес
        int index = edgeIndex(from, to);
        if (index < 0) {
            throw new IllegalArgumentException("Нет связи " + from + "->" + to);
        }
        edges.set(index, updated);
    }

    /** @return true, если связь была и удалена */
    public boolean removeEdge(String from, String to) {
        int index = edgeIndex(from, to);
        if (index < 0) {
            return false;
        }
        edges.remove(index);
        return true;
    }

    public Optional<Edge> findEdge(String from, String to) {
        int index = edgeIndex(from, to);
        return index < 0 ? Optional.empty() : Optional.of(edges.get(index));
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    // ---------- целиком ----------

    /**
     * Заменяет всё содержимое карты переданными вершинами и связями (так работает отмена действий).
     * Объект карты остаётся тем же, поэтому остальные части приложения, которые на него ссылаются,
     * ничего не замечают. Данные сначала проверяются на временной карте: если они некорректны,
     * бросается исключение, а эта карта не меняется.
     */
    public void replaceWith(List<Concept> newConcepts, List<Edge> newEdges) {
        FuzzyCognitiveMap check = new FuzzyCognitiveMap();
        newConcepts.forEach(check::addConcept);
        newEdges.forEach(e -> check.addEdge(e.from(), e.to(), e.weight()));
        concepts.clear();
        concepts.addAll(check.concepts);
        edges.clear();
        edges.addAll(check.edges);
    }

    // ---------- матрица ----------

    /**
     * Матрица отношений W: {@code W[i][j]} — вес дуги из вершины i в вершину j,
     * нумерация по порядку {@link #concepts()}. Нет дуги — 0.
     */
    public double[][] relationMatrix() {
        int n = concepts.size();
        double[][] w = new double[n][n];
        for (Edge e : edges) {
            w[indexOf(e.from())][indexOf(e.to())] = e.weight();
        }
        return w;
    }

    // ---------- служебное ----------

    private int edgeIndex(String from, String to) {
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            if (e.from().equals(from) && e.to().equals(to)) {
                return i;
            }
        }
        return -1;
    }

    private void requireConcept(String code) {
        if (indexOf(code) < 0) {
            throw new IllegalArgumentException("Нет вершины с кодом " + code);
        }
    }
}

package ru.david.ui;

import java.util.List;

/**
 * Автоматическая расстановка вершин силовым методом Фрюхтермана и Рейнгольда.
 * <p>
 * Идея: вершины считаются заряженными частицами, которые отталкиваются друг от друга, а связи
 * работают как пружины и стягивают соединённые вершины. Силы пересчитываются много раз, вершины
 * понемногу сдвигаются (максимальный сдвиг, «температура», постепенно убывает), и граф
 * приходит в равновесие: связанные вершины рядом, несвязанные дальше друг от друга.
 * Алгоритм детерминированный: начальные позиции задаются по кругу, а не случайно, поэтому
 * один и тот же граф всегда расставляется одинаково.
 */
final class ForceLayout {

    private static final int ITERATIONS = 300;
    private static final int SEPARATION_PASSES = 100;
    /** Сила притяжения к центру на единицу расстояния; подобрана по виду графов на 10-25 вершин. */
    private static final double GRAVITY = 0.4;

    private ForceLayout() {
    }

    /**
     * @param n           число вершин
     * @param edges       связи как пары номеров вершин (без петель и повторов)
     * @param margin      отступ от краёв области: за него вершины не выходят
     * @param minDistance наименьшее расстояние между центрами вершин
     * @return {@code result[i]} = {x, y} вершины i в области width x height
     */
    static double[][] compute(int n, List<int[]> edges, double width, double height,
                              double margin, double minDistance) {
        double[][] pos = new double[n][2];
        if (n == 0) {
            return pos;
        }
        double left = margin;
        double right = Math.max(margin, width - margin);
        double top = margin;
        double bottom = Math.max(margin, height - margin);
        if (n == 1) {
            pos[0][0] = (left + right) / 2;
            pos[0][1] = (top + bottom) / 2;
            return pos;
        }

        // Старт: равномерно по эллипсу, все точки разные
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            pos[i][0] = (left + right) / 2 + (right - left) / 2 * Math.cos(angle);
            pos[i][1] = (top + bottom) / 2 + (bottom - top) / 2 * Math.sin(angle);
        }

        // k: расстояние, на котором отталкивание и притяжение уравновешены (площадь делим на число вершин)
        double k = Math.sqrt(Math.max(1, (right - left) * (bottom - top)) / n);
        double startTemperature = Math.max(right - left, bottom - top) / 10;

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[][] shift = new double[n][2];

            // Отталкивание всех пар: сила k^2 / d, чем ближе, тем сильнее
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = pos[i][0] - pos[j][0];
                    double dy = pos[i][1] - pos[j][1];
                    double d = Math.max(Math.hypot(dx, dy), 0.01);
                    double force = k * k / d;
                    shift[i][0] += dx / d * force;
                    shift[i][1] += dy / d * force;
                    shift[j][0] -= dx / d * force;
                    shift[j][1] -= dy / d * force;
                }
            }
            // Притяжение вдоль связей: сила d^2 / k, чем дальше, тем сильнее
            for (int[] edge : edges) {
                double dx = pos[edge[0]][0] - pos[edge[1]][0];
                double dy = pos[edge[0]][1] - pos[edge[1]][1];
                double d = Math.max(Math.hypot(dx, dy), 0.01);
                double force = d * d / k;
                shift[edge[0]][0] -= dx / d * force;
                shift[edge[0]][1] -= dy / d * force;
                shift[edge[1]][0] += dx / d * force;
                shift[edge[1]][1] += dy / d * force;
            }

            // Слабое притяжение к центру: без него отталкивание выдавливает слабо связанные кластеры
            // к границе области, где они сплющиваются в линию вдоль края
            double centerX = (left + right) / 2;
            double centerY = (top + bottom) / 2;
            for (int i = 0; i < n; i++) {
                shift[i][0] += (centerX - pos[i][0]) * GRAVITY;
                shift[i][1] += (centerY - pos[i][1]) * GRAVITY;
            }

            // Остывание: сдвиг каждой вершины ограничен температурой, которая линейно падает до нуля
            double temperature = startTemperature * (1 - (double) (iteration + 1) / ITERATIONS);
            for (int i = 0; i < n; i++) {
                double length = Math.hypot(shift[i][0], shift[i][1]);
                if (length > 0) {
                    double step = Math.min(length, temperature);
                    pos[i][0] = clamp(pos[i][0] + shift[i][0] / length * step, left, right);
                    pos[i][1] = clamp(pos[i][1] + shift[i][1] / length * step, top, bottom);
                }
            }
        }

        separate(pos, minDistance, left, right, top, bottom);
        return pos;
    }

    /** Раздвигает вершины, которые оказались ближе minDistance, чтобы круги не налезали друг на друга. */
    private static void separate(double[][] pos, double minDistance,
                                 double left, double right, double top, double bottom) {
        int n = pos.length;
        for (int pass = 0; pass < SEPARATION_PASSES; pass++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = pos[j][0] - pos[i][0];
                    double dy = pos[j][1] - pos[i][1];
                    double d = Math.hypot(dx, dy);
                    if (d >= minDistance) {
                        continue;
                    }
                    if (d < 1e-9) { // совпали: направление берём произвольное, но всегда одно и то же
                        dx = Math.cos(i + j);
                        dy = Math.sin(i + j);
                        d = 1;
                    }
                    double push = (minDistance - d) / 2;
                    pos[i][0] = clamp(pos[i][0] - dx / d * push, left, right);
                    pos[i][1] = clamp(pos[i][1] - dy / d * push, top, bottom);
                    pos[j][0] = clamp(pos[j][0] + dx / d * push, left, right);
                    pos[j][1] = clamp(pos[j][1] + dy / d * push, top, bottom);
                    moved = true;
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

package ru.david.ui;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;

/**
 * Геометрия одной связи на холсте: линия, остриё стрелки, место подписи веса.
 * Бывает двух видов: изогнутая линия между двумя вершинами и петля на одной вершине.
 */
interface EdgeShape {

    /** Линия связи (без стрелки). */
    Shape line();

    /** Остриё стрелки: точка на границе вершины-приёмника. */
    Point2D tip();

    /** Куда направлена стрелка, в радианах. */
    double tipAngle();

    /** Точка на линии, в которой рисуется подпись веса. */
    Point2D labelPoint();

    /** Расстояние от точки до линии, нужно для попадания мышью по связи. */
    double distanceTo(Point2D p);

    /**
     * Связь между двумя разными вершинами: квадратичная кривая Безье.
     * Опорная точка лежит на серединном перпендикуляре к отрезку между центрами вершин
     * на расстоянии {@code bend}. При bend = 0 получается прямая. Встречные связи A->B и B->A
     * получают изгиб одного знака, но нормаль у них противоположная, поэтому они расходятся.
     */
    record Curve(Point2D start, Point2D control, Point2D end) implements EdgeShape {

        private static final int SAMPLES = 24;

        /**
         * Строит кривую между центрами a и b и обрезает её по границам вершин радиуса radius.
         * Обрезка делается по параметру кривой (а не просто «в сторону опорной точки»),
         * поэтому середина кривой лежит ровно на bend/2 от отрезка a-b и за мышью
         * при перетаскивании можно следить простой формулой {@link #bendFor}.
         */
        static Curve between(Point2D a, Point2D b, double bend, double radius) {
            double[] n = normal(a, b);
            Point2D c = new Point2D.Double(
                    (a.getX() + b.getX()) / 2 + n[0] * bend,
                    (a.getY() + b.getY()) / 2 + n[1] * bend);
            // Обе вершины одного радиуса, кривая симметрична: выход из b при t1 = 1 - t0
            double t0 = exitParameter(a, c, b, radius);
            double t1 = 1 - t0;
            // Опорная точка отрезка [t0; t1] исходной кривой (формула разбиения Безье)
            double w0 = (1 - t0) * (1 - t1);
            double w1 = t0 + t1 - 2 * t0 * t1;
            double w2 = t0 * t1;
            Point2D control = new Point2D.Double(
                    w0 * a.getX() + w1 * c.getX() + w2 * b.getX(),
                    w0 * a.getY() + w1 * c.getY() + w2 * b.getY());
            return new Curve(pointAt(a, c, b, t0), control, pointAt(a, c, b, t1));
        }

        /** Изгиб, при котором середина кривой окажется под мышью (по перпендикуляру к a-b). */
        static double bendFor(Point2D a, Point2D b, Point2D mouse) {
            double[] n = normal(a, b);
            double mx = (a.getX() + b.getX()) / 2;
            double my = (a.getY() + b.getY()) / 2;
            // Середина кривой отстоит от отрезка a-b ровно на bend/2
            return 2 * ((mouse.getX() - mx) * n[0] + (mouse.getY() - my) * n[1]);
        }

        @Override
        public Shape line() {
            return new QuadCurve2D.Double(start.getX(), start.getY(),
                    control.getX(), control.getY(), end.getX(), end.getY());
        }

        @Override
        public Point2D tip() {
            return end;
        }

        @Override
        public double tipAngle() {
            return Math.atan2(end.getY() - control.getY(), end.getX() - control.getX());
        }

        @Override
        public Point2D labelPoint() {
            return pointAt(start, control, end, 0.5);
        }

        @Override
        public double distanceTo(Point2D p) {
            double best = Double.MAX_VALUE;
            Point2D prev = start;
            for (int i = 1; i <= SAMPLES; i++) {
                Point2D next = pointAt(start, control, end, (double) i / SAMPLES);
                best = Math.min(best, Line2D.ptSegDist(
                        prev.getX(), prev.getY(), next.getX(), next.getY(), p.getX(), p.getY()));
                prev = next;
            }
            return best;
        }

        /** Точка квадратичной кривой Безье: B(t) = (1-t)^2 P0 + 2t(1-t) P1 + t^2 P2. */
        private static Point2D pointAt(Point2D p0, Point2D p1, Point2D p2, double t) {
            double u = 1 - t;
            return new Point2D.Double(
                    u * u * p0.getX() + 2 * t * u * p1.getX() + t * t * p2.getX(),
                    u * u * p0.getY() + 2 * t * u * p1.getY() + t * t * p2.getY());
        }

        /** Параметр t, при котором кривая выходит из круга радиуса r вокруг a (метод деления пополам). */
        private static double exitParameter(Point2D a, Point2D c, Point2D b, double r) {
            // Вершины перекрываются: рисовать нечего, схлопываем кривую в точку
            if (pointAt(a, c, b, 0.5).distance(a) <= r) {
                return 0.5;
            }
            double lo = 0;
            double hi = 0.5;
            for (int i = 0; i < 30; i++) {
                double mid = (lo + hi) / 2;
                if (pointAt(a, c, b, mid).distance(a) < r) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return hi;
        }

        /** Единичная нормаль к отрезку a->b (поворот на 90 градусов). */
        private static double[] normal(Point2D a, Point2D b) {
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double len = Math.hypot(dx, dy);
            if (len < 1e-9) { // вершины в одной точке, направление не определено
                return new double[]{0, -1};
            }
            return new double[]{-dy / len, dx / len};
        }
    }

    /**
     * Петля: окружность, торчащая из вершины в направлении {@code angle}.
     * Центр петли отстоит от центра вершины на (радиус вершины + GAP), поэтому окружности
     * пересекаются; часть петли внутри вершины скрыта, так как вершины рисуются поверх связей.
     */
    record Loop(Point2D nodeCenter, double angle, double nodeRadius) implements EdgeShape {

        private static final double RADIUS = 17;
        private static final double GAP = 10;

        @Override
        public Shape line() {
            Point2D c = loopCenter();
            return new Ellipse2D.Double(c.getX() - RADIUS, c.getY() - RADIUS, 2 * RADIUS, 2 * RADIUS);
        }

        /** Остриё стрелки: одна из двух точек пересечения окружности петли и окружности вершины. */
        @Override
        public Point2D tip() {
            double d = nodeRadius + GAP; // расстояние между центрами двух окружностей
            // a: расстояние от центра вершины до хорды пересечения вдоль оси центров;
            // h: половина длины хорды (теорема Пифагора)
            double a = (nodeRadius * nodeRadius - RADIUS * RADIUS + d * d) / (2 * d);
            double h = Math.sqrt(nodeRadius * nodeRadius - a * a);
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            return new Point2D.Double(
                    nodeCenter.getX() + dx * a - dy * h,
                    nodeCenter.getY() + dy * a + dx * h);
        }

        @Override
        public double tipAngle() {
            Point2D tip = tip();
            Point2D c = loopCenter();
            // Касательная к окружности петли перпендикулярна радиусу (tip - c)
            double tx = -(tip.getY() - c.getY());
            double ty = tip.getX() - c.getX();
            // Из двух направлений касательной берём то, что ведёт внутрь вершины
            if (tx * (nodeCenter.getX() - tip.getX()) + ty * (nodeCenter.getY() - tip.getY()) < 0) {
                tx = -tx;
                ty = -ty;
            }
            return Math.atan2(ty, tx);
        }

        /** Самая дальняя от вершины точка петли. */
        @Override
        public Point2D labelPoint() {
            Point2D c = loopCenter();
            return new Point2D.Double(
                    c.getX() + Math.cos(angle) * RADIUS,
                    c.getY() + Math.sin(angle) * RADIUS);
        }

        @Override
        public double distanceTo(Point2D p) {
            return Math.abs(loopCenter().distance(p) - RADIUS);
        }

        private Point2D loopCenter() {
            double d = nodeRadius + GAP;
            return new Point2D.Double(
                    nodeCenter.getX() + Math.cos(angle) * d,
                    nodeCenter.getY() + Math.sin(angle) * d);
        }
    }
}

package ru.david.ui;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeShapeTest {

    private static final double R = 26;
    private static final double EPS = 1e-6;
    private final Point2D a = new Point2D.Double(0, 0);
    private final Point2D b = new Point2D.Double(200, 0);

    @Test
    void curveStartsAndEndsOnNodeBoundaries() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, b, 40, R);

        assertEquals(R, curve.start().distance(a), 1e-3);
        assertEquals(R, curve.end().distance(b), 1e-3);
    }

    @Test
    void straightCurveLiesOnTheChord() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, b, 0, R);

        assertEquals(R, curve.start().getX(), 1e-3);
        assertEquals(0, curve.start().getY(), EPS);
        assertEquals(100, curve.labelPoint().getX(), 1e-3);
        assertEquals(0, curve.labelPoint().getY(), EPS);
        assertEquals(0, curve.tipAngle(), EPS); // стрелка смотрит вправо, на b
    }

    @Test
    void curveMiddleIsHalfBendAwayFromChord() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, b, 40, R);

        assertEquals(100, curve.labelPoint().getX(), 1e-3);
        // Нормаль к отрезку a->b вдоль оси X направлена в +Y
        assertEquals(20, curve.labelPoint().getY(), 1e-3);
    }

    @Test
    void bendForMakesCurveMiddleFollowMouse() {
        Point2D mouse = new Point2D.Double(100, -35);

        double bend = EdgeShape.Curve.bendFor(a, b, mouse);
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, b, bend, R);

        assertEquals(mouse.getX(), curve.labelPoint().getX(), 1e-3);
        assertEquals(mouse.getY(), curve.labelPoint().getY(), 1e-3);
    }

    @Test
    void oppositeEdgesWithSameBendDiverge() {
        double ab = EdgeShape.Curve.between(a, b, 40, R).labelPoint().getY();
        double ba = EdgeShape.Curve.between(b, a, 40, R).labelPoint().getY();

        assertTrue(ab > 0 && ba < 0, "встречные связи должны лежать по разные стороны отрезка");
    }

    @Test
    void curveDistanceIsZeroOnLineAndGrowsAway() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, b, 0, R);

        assertEquals(0, curve.distanceTo(new Point2D.Double(100, 0)), 1e-6);
        assertEquals(10, curve.distanceTo(new Point2D.Double(100, 10)), 1e-6);
    }

    @Test
    void overlappingNodesGiveDegenerateCurveWithoutNaN() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, new Point2D.Double(10, 0), 0, R);

        assertFalse(Double.isNaN(curve.tipAngle()));
        assertFalse(Double.isNaN(curve.labelPoint().getX()));
    }

    @Test
    void coincidentNodesDoNotProduceNaN() {
        EdgeShape.Curve curve = EdgeShape.Curve.between(a, new Point2D.Double(0, 0), 20, R);

        assertFalse(Double.isNaN(curve.labelPoint().getX()));
        assertFalse(Double.isNaN(curve.labelPoint().getY()));
    }

    @Test
    void loopTipLiesOnNodeBoundary() {
        for (double angle : new double[]{0, Math.PI / 3, -Math.PI / 2, 2.5}) {
            EdgeShape.Loop loop = new EdgeShape.Loop(a, angle, R);

            assertEquals(R, loop.tip().distance(a), 1e-6, "угол " + angle);
        }
    }

    @Test
    void loopTipLiesOnLoopCircle() {
        EdgeShape.Loop loop = new EdgeShape.Loop(a, -Math.PI / 2, R);

        // Петля сверху: её дальняя точка (подпись) выше вершины, а остриё рядом с границей вершины
        assertTrue(loop.labelPoint().getY() < -R);
        assertEquals(0, loop.distanceTo(loop.tip()), 1e-6);
        assertEquals(0, loop.distanceTo(loop.labelPoint()), 1e-6);
    }

    @Test
    void loopArrowPointsIntoNode() {
        EdgeShape.Loop loop = new EdgeShape.Loop(a, -Math.PI / 2, R);
        Point2D tip = loop.tip();

        // Шаг вдоль стрелки от острия должен приблизить к центру вершины
        double step = 1;
        Point2D next = new Point2D.Double(
                tip.getX() + step * Math.cos(loop.tipAngle()),
                tip.getY() + step * Math.sin(loop.tipAngle()));
        assertTrue(next.distance(a) < tip.distance(a));
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}

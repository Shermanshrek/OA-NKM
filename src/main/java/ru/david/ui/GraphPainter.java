package ru.david.ui;

import ru.david.model.Concept;
import ru.david.model.Edge;
import ru.david.model.FuzzyCognitiveMap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/** Рисует модель на холсте. Только отрисовка: состояния не хранит и модель не меняет. */
final class GraphPainter {

    // Положительная связь (усиливает) зелёная сплошная, отрицательная (ослабляет) красная пунктирная
    static final Color POSITIVE = new Color(0x2E9E4F);
    static final Color NEGATIVE = new Color(0xD32F2F);

    private static final Color NODE_FILL = new Color(0xDCE9FF);
    private static final Color NODE_BORDER = new Color(0x2F4B7C);
    private static final Color SOURCE_BORDER = new Color(0xF28C00);
    private static final Color LABEL_BACKGROUND = new Color(255, 255, 255, 230);

    private static final Stroke SOLID = new BasicStroke(2f);
    private static final Stroke DASHED = new BasicStroke(
            2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f);

    private static final Font CODE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font WEIGHT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private GraphPainter() {
    }

    /**
     * @param edgeSource вершина, выбранная источником новой связи, или null
     * @param mouse      положение мыши, до него тянется «резинка» от источника; может быть null
     */
    static void paint(Graphics2D g, FuzzyCognitiveMap map, GraphLayout layout,
                      String edgeSource, Point2D mouse) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Порядок слоёв: линии, подписи весов, «резинка», вершины. Вершины сверху скрывают
        // концы линий и часть петли внутри вершины.
        for (Edge e : map.edges()) {
            drawLine(g, e, layout.shapeOf(e));
        }
        for (Edge e : map.edges()) {
            drawWeight(g, e, layout.shapeOf(e));
        }
        if (edgeSource != null && mouse != null) {
            drawRubberBand(g, layout.position(edgeSource), mouse);
        }
        for (Concept c : map.concepts()) {
            drawNode(g, c, layout.position(c.code()), c.code().equals(edgeSource));
        }
    }

    private static Color colorOf(Edge e) {
        return e.isNegative() ? NEGATIVE : POSITIVE;
    }

    private static void drawLine(Graphics2D g, Edge e, EdgeShape shape) {
        g.setColor(colorOf(e));
        g.setStroke(e.isNegative() ? DASHED : SOLID);
        g.draw(shape.line());

        // Наконечник всегда сплошной, даже у пунктирной связи
        g.setStroke(SOLID);
        Point2D tip = shape.tip();
        double angle = shape.tipAngle();
        double length = 12;
        double spread = Math.toRadians(25);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(tip.getX(), tip.getY());
        arrow.lineTo(tip.getX() - length * Math.cos(angle - spread),
                tip.getY() - length * Math.sin(angle - spread));
        arrow.lineTo(tip.getX() - length * Math.cos(angle + spread),
                tip.getY() - length * Math.sin(angle + spread));
        arrow.closePath();
        g.fill(arrow);
    }

    private static void drawWeight(Graphics2D g, Edge e, EdgeShape shape) {
        String text = String.format("%.2f", e.weight());
        g.setFont(WEIGHT_FONT);
        FontMetrics fm = g.getFontMetrics();
        Point2D p = shape.labelPoint();
        double width = fm.stringWidth(text);

        // Белая подложка, чтобы линия не пересекала цифры
        g.setColor(LABEL_BACKGROUND);
        g.fill(new Ellipse2D.Double(p.getX() - width / 2 - 3, p.getY() - fm.getHeight() / 2.0,
                width + 6, fm.getHeight()));
        g.setColor(colorOf(e));
        drawCentered(g, text, p.getX(), p.getY());
    }

    private static void drawRubberBand(Graphics2D g, Point2D from, Point2D to) {
        g.setColor(Color.GRAY);
        g.setStroke(DASHED);
        g.draw(new Line2D.Double(from, to));
    }

    private static void drawNode(Graphics2D g, Concept c, Point2D center, boolean isSource) {
        double r = GraphLayout.NODE_RADIUS;
        Ellipse2D circle = new Ellipse2D.Double(center.getX() - r, center.getY() - r, 2 * r, 2 * r);
        g.setColor(NODE_FILL);
        g.fill(circle);
        g.setColor(isSource ? SOURCE_BORDER : NODE_BORDER);
        g.setStroke(isSource ? new BasicStroke(3.5f) : SOLID);
        g.draw(circle);

        g.setColor(Color.BLACK);
        g.setFont(CODE_FONT);
        drawCentered(g, c.code(), center.getX(), center.getY());
        g.setFont(NAME_FONT);
        g.setColor(Color.BLACK);
        drawCentered(g, c.name(), center.getX(), center.getY() + r + 12);
    }

    /** Рисует строку так, чтобы точка (cx, cy) была её центром. */
    private static void drawCentered(Graphics2D g, String text, double cx, double cy) {
        FontMetrics fm = g.getFontMetrics();
        float x = (float) (cx - fm.stringWidth(text) / 2.0);
        float y = (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0);
        g.drawString(text, x, y);
    }
}

package ru.david.ui;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверка, что отрисовка работает и цвета связей правильные. Картинка пишется для просмотра глазами. */
class GraphPainterTest {

    @Test
    void paintsPositiveGreenAndNegativeRedAndSavesPreview() throws IOException {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        GraphLayout layout = new GraphLayout(map);
        add(map, layout, "A", "Спрос", 120, 120);
        add(map, layout, "B", "Цена", 360, 120);
        add(map, layout, "C", "Прибыль", 240, 300);
        map.addEdge("A", "B", 0.8);   // встречные связи
        map.addEdge("B", "A", -0.6);
        map.addEdge("B", "C", -0.5);
        map.addEdge("C", "C", 0.4);   // петля
        map.addEdge("A", "C", 0.7);
        map.addEdge("C", "A", -0.2);  // ещё одна пара встречных

        BufferedImage image = new BufferedImage(520, 420, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        GraphPainter.paint(g, map, layout, "A", new Point2D.Double(200, 200));
        g.dispose();

        assertTrue(hasPixelCloseTo(image, GraphPainter.POSITIVE), "нет зелёных пикселей");
        assertTrue(hasPixelCloseTo(image, GraphPainter.NEGATIVE), "нет красных пикселей");

        File preview = new File("target/graph-preview.png");
        ImageIO.write(image, "png", preview);
    }

    private static void add(FuzzyCognitiveMap map, GraphLayout layout,
                            String code, String name, double x, double y) {
        map.addConcept(new Concept(code, name));
        layout.setPosition(code, x, y);
    }

    private static boolean hasPixelCloseTo(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) < 12
                        && Math.abs(c.getGreen() - color.getGreen()) < 12
                        && Math.abs(c.getBlue() - color.getBlue()) < 12) {
                    return true;
                }
            }
        }
        return false;
    }
}

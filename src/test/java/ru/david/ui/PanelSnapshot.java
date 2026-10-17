package ru.david.ui;

import javax.swing.JTable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Помощник для тестов панелей: рисует панель без окна в картинку и достаёт из неё таблицы. */
final class PanelSnapshot {

    private PanelSnapshot() {
    }

    static BufferedImage render(Container panel, int width, int height) {
        panel.setSize(width, height);
        panel.addNotify(); // без этого у компонентов нет «пира» и раскладка не выполняется
        layoutRecursively(panel);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.printAll(g);
        g.dispose();
        return image;
    }

    static List<JTable> tablesOf(Container container) {
        List<JTable> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JTable table) {
                result.add(table);
            } else if (child instanceof Container c) {
                result.addAll(tablesOf(c));
            }
        }
        return result;
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container c) {
                layoutRecursively(c);
            }
        }
    }
}

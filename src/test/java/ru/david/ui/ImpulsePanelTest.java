package ru.david.ui;

import org.jfree.chart.ChartPanel;
import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import javax.imageio.ImageIO;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверка вкладки импульсов: 6 графиков, таблица плана. Картинка пишется для просмотра глазами. */
class ImpulsePanelTest {

    private static FuzzyCognitiveMap sampleMap() {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        String[] names = {"Спрос", "Цена", "Прибыль", "Затраты", "Качество", "Репутация"};
        for (int i = 0; i < names.length; i++) {
            map.addConcept(new Concept("C" + (i + 1), names[i]));
        }
        map.addEdge("C1", "C2", 0.8);
        map.addEdge("C2", "C1", -0.6);
        map.addEdge("C2", "C3", 0.7);
        map.addEdge("C4", "C3", -0.5);
        map.addEdge("C5", "C1", 0.4);
        map.addEdge("C5", "C4", 0.6);
        map.addEdge("C3", "C3", 0.3);
        map.addEdge("C3", "C6", 0.9);
        map.addEdge("C6", "C1", 0.5);
        return map;
    }

    private static List<ChartPanel> chartsOf(Container container) {
        List<ChartPanel> result = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof ChartPanel chart) {
                result.add(chart);
            } else if (child instanceof Container c) {
                result.addAll(chartsOf(c));
            }
        }
        return result;
    }

    @Test
    void showsSixChartsAndPlanTableAndSavesPreview() throws IOException {
        ImpulsePanel panel = new ImpulsePanel(new Random(3));

        panel.showFor(sampleMap());

        assertEquals(6, chartsOf(panel).size());
        JTable plan = PanelSnapshot.tablesOf(panel).get(0);
        assertEquals(6, plan.getRowCount());
        assertEquals(2 + 6, plan.getColumnCount()); // «Сценарий», «Возмущение» и по столбцу на вершину
        ImageIO.write(PanelSnapshot.render(panel, 1100, 760), "png", new File("target/impulse-preview.png"));
    }

    @Test
    void everyChartHasAtMostFourLines() {
        ImpulsePanel panel = new ImpulsePanel(new Random(3));

        panel.showFor(sampleMap());

        for (ChartPanel chart : chartsOf(panel)) {
            int lines = chart.getChart().getXYPlot().getDataset().getSeriesCount();
            assertTrue(lines >= 1 && lines <= ImpulseChartFactory.MAX_VERTICES, "линий: " + lines);
        }
    }

    @Test
    void planStaysTheSameForTheSameModel() {
        ImpulsePanel panel = new ImpulsePanel(new Random(3));
        FuzzyCognitiveMap map = sampleMap();

        panel.showFor(map);
        String first = PanelSnapshot.tablesOf(panel).get(0).getValueAt(0, 1).toString();
        panel.showFor(map);
        String second = PanelSnapshot.tablesOf(panel).get(0).getValueAt(0, 1).toString();

        assertEquals(first, second);
    }

    @Test
    void planIsRebuiltWhenItsVertexWasRemoved() {
        ImpulsePanel panel = new ImpulsePanel(new Random(3));
        FuzzyCognitiveMap map = sampleMap();
        panel.showFor(map);
        String vertex = PanelSnapshot.tablesOf(panel).get(0).getValueAt(0, 1).toString()
                .replace("q(", "").replace(") = +1", "");

        map.removeConcept(vertex);
        panel.showFor(map);

        assertEquals(6, chartsOf(panel).size()); // 5 вершин осталось, план снова из трёх вершин
        for (int row = 0; row < 6; row++) {
            assertTrue(!PanelSnapshot.tablesOf(panel).get(0).getValueAt(row, 1).toString().contains("(" + vertex + ")"));
        }
    }

    @Test
    void emptyModelShowsNoCharts() {
        ImpulsePanel panel = new ImpulsePanel(new Random(3));

        panel.showFor(new FuzzyCognitiveMap());

        assertEquals(0, chartsOf(panel).size());
    }

    @Test
    void twoVerticesGiveFourCharts() {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        map.addConcept(new Concept("C1", ""));
        map.addConcept(new Concept("C2", ""));
        map.addEdge("C1", "C2", 0.5);
        ImpulsePanel panel = new ImpulsePanel(new Random(3));

        panel.showFor(map);

        assertEquals(4, chartsOf(panel).size());
    }
}

package ru.david.ui;

import org.junit.jupiter.api.Test;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import javax.imageio.ImageIO;
import javax.swing.JTable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Проверка, что вкладка анализа заполняется и отрисовывается. Картинка пишется для просмотра глазами. */
class AnalysisPanelTest {

    private static FuzzyCognitiveMap sampleMap() {
        FuzzyCognitiveMap map = new FuzzyCognitiveMap();
        for (String code : new String[]{"C1", "C2", "C3", "C4"}) {
            map.addConcept(new Concept(code, ""));
        }
        map.addEdge("C1", "C2", 0.8);
        map.addEdge("C2", "C1", -0.6);
        map.addEdge("C2", "C3", 0.7);
        map.addEdge("C3", "C4", -0.5);
        map.addEdge("C4", "C2", 0.4);
        map.addEdge("C3", "C3", 0.3);
        return map;
    }

    @Test
    void fillsBothTablesAndSavesPreview() throws IOException {
        AnalysisPanel panel = new AnalysisPanel();

        panel.showAnalysisOf(sampleMap());

        List<JTable> tables = PanelSnapshot.tablesOf(panel);
        assertEquals(2, tables.size());
        assertEquals(4, tables.get(0).getColumnCount());
        assertEquals(4, tables.get(1).getRowCount()); // 4 вершины: 4 собственных числа
        ImageIO.write(PanelSnapshot.render(panel, 1000, 450), "png", new File("target/analysis-preview.png"));
    }

    @Test
    void emptyModelShowsMessageAndEmptyTables() throws IOException {
        AnalysisPanel panel = new AnalysisPanel();

        panel.showAnalysisOf(new FuzzyCognitiveMap());

        for (JTable table : PanelSnapshot.tablesOf(panel)) {
            assertEquals(0, table.getRowCount());
        }
        ImageIO.write(PanelSnapshot.render(panel, 1000, 450), "png", new File("target/analysis-empty-preview.png"));
    }
}

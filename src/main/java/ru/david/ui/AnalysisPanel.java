package ru.david.ui;

import ru.david.analysis.Cycle;
import ru.david.analysis.EigenValue;
import ru.david.analysis.StabilityAnalyzer;
import ru.david.analysis.StabilityReport;
import ru.david.analysis.StabilityStatus;
import ru.david.model.FuzzyCognitiveMap;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/**
 * Вкладка с результатами анализа: итоговые выводы сверху, под ними таблица циклов
 * и таблица собственных чисел (как на слайде 11). Результат пересчитывается при каждом показе.
 */
final class AnalysisPanel extends JPanel {

    private static final String GREEN = "#2E9E4F";
    private static final String RED = "#D32F2F";
    private static final String ORANGE = "#B26A00";

    private final JLabel summary = new JLabel();
    private final JTable cycleTable = new JTable();
    private final JTable eigenTable = new JTable();
    private final JLabel cycleCaption = new JLabel();
    private final JLabel eigenCaption = new JLabel();

    AnalysisPanel() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        summary.setVerticalAlignment(SwingConstants.TOP);

        for (JTable table : List.of(cycleTable, eigenTable)) {
            table.setDefaultRenderer(Double.class, new NumberRenderer());
            table.setFillsViewportHeight(true);
        }

        JSplitPane tables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tablePanel(cycleTable, cycleCaption), tablePanel(eigenTable, eigenCaption));
        tables.setResizeWeight(0.6);
        add(summary, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1000, 650));
    }

    /** Считает модель и показывает результат; если считать нечего или не получилось, показывает причину. */
    void showAnalysisOf(FuzzyCognitiveMap map) {
        try {
            display(StabilityAnalyzer.analyze(map));
        } catch (IllegalArgumentException | IllegalStateException e) {
            showProblem(e.getMessage());
        }
    }

    // ---------- вывод ----------

    private void display(StabilityReport report) {
        showSummary(report);
        showCycles(report);
        showEigenvalues(report);
    }

    private void showSummary(StabilityReport report) {
        StringBuilder html = new StringBuilder("<html>");

        boolean structural = report.structurallyStable();
        html.append("<b>Структурная устойчивость:</b> ")
                .append(colored(structural ? "устойчива" : "не устойчива", structural ? GREEN : RED))
                .append(" (циклов с отрицательной обратной связью: ").append(report.negativeCycleCount())
                .append(")<br>");

        StabilityStatus status = report.status();
        html.append("<b>Устойчивость по возмущению и по начальному значению:</b> ")
                .append(colored(status.title(), statusColor(status)))
                .append(" (|M| = ").append(format(report.maxEigenvalue().modulus()))
                .append(" ").append(escape(status.relationToOne())).append(" 1)"); // «<» иначе съест HTML

        if (report.cyclesTruncated()) {
            html.append("<br>").append(colored("Внимание: найдено больше циклов, чем позволяет лимит ("
                    + report.cycles().size() + "). Показаны не все, поэтому вывод о структурной "
                    + "устойчивости может быть неточным.", ORANGE));
        }
        summary.setText(html.append("</html>").toString());
    }

    private void showCycles(StabilityReport report) {
        List<Cycle> cycles = report.cycles();
        Object[][] rows = new Object[cycles.size()][];
        for (int i = 0; i < rows.length; i++) {
            Cycle c = cycles.get(i);
            rows[i] = new Object[]{i + 1, c.pathText(), c.isPositive() ? "+" : "-", c.weightProduct()};
        }
        setModel(cycleTable,
                new ReadOnlyTableModel(new String[]{"№", "Цикл", "Знак цикла", "Показатель"},
                        new Class<?>[]{Integer.class, String.class, String.class, Double.class}, rows),
                50, 280, 90, 90);
        cycleCaption.setText("Всего циклов: " + cycles.size() + ", с положительной ОС: "
                + report.positiveCycleCount() + (report.cyclesTruncated() ? " (лимит достигнут)" : ""));
    }

    private void showEigenvalues(StabilityReport report) {
        List<EigenValue> values = report.eigenvalues();
        Object[][] rows = new Object[values.size()][];
        for (int i = 0; i < rows.length; i++) {
            EigenValue v = values.get(i);
            rows[i] = new Object[]{i + 1, v.re(), v.im(), v.modulus()};
        }
        setModel(eigenTable,
                new ReadOnlyTableModel(new String[]{"N", "Действ. часть", "Мнимая часть", "Модуль"},
                        new Class<?>[]{Integer.class, Double.class, Double.class, Double.class}, rows),
                40, 90, 90, 90);
        EigenValue max = report.maxEigenvalue();
        String sign = max.im() < 0 ? " - " : " + ";
        eigenCaption.setText("Максимальное по модулю: " + format(max.re()) + sign + format(Math.abs(max.im()))
                + "i, модуль " + format(max.modulus()));
    }

    private void showProblem(String message) {
        summary.setText("<html>" + colored(escape(message), RED) + "</html>");
        cycleTable.setModel(new DefaultTableModel());
        eigenTable.setModel(new DefaultTableModel());
        cycleCaption.setText(" ");
        eigenCaption.setText(" ");
    }

    // ---------- таблицы ----------

    private static JPanel tablePanel(JTable table, JLabel caption) {
        caption.setBorder(new EmptyBorder(4, 2, 0, 2));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(caption, BorderLayout.SOUTH);
        return panel;
    }

    private static void setModel(JTable table, ReadOnlyTableModel model, int... widths) {
        table.setModel(model);
        table.setAutoCreateRowSorter(true); // по щелчку на заголовок строки сортируются
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static final class NumberRenderer extends DefaultTableCellRenderer {
        NumberRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value instanceof Double d ? format(d) : String.valueOf(value));
        }
    }

    // ---------- оформление ----------

    /** Число с четырьмя знаками; «-0,0000» от крошечных отрицательных погрешностей показывается как «0,0000». */
    private static String format(double value) {
        double rounded = Math.round(value * 10_000) / 10_000.0;
        if (rounded == 0) {
            rounded = 0; // отбрасывает знак у отрицательного нуля
        }
        return String.format("%.4f", rounded);
    }

    private static String statusColor(StabilityStatus status) {
        return switch (status) {
            case STABLE -> GREEN;
            case UNSTABLE -> RED;
            case BOUNDARY -> ORANGE;
        };
    }

    private static String colored(String text, String color) {
        return "<font color='" + color + "'>" + text + "</font>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

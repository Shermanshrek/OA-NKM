package ru.david.ui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import ru.david.analysis.ImpulseResult;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;

/** Строит график импульсного процесса: по оси X шаги t, по оси Y изменение значений вершин. */
final class ImpulseChartFactory {

    /** Вершин на одном графике (слайд 12: четыре, для лучшего восприятия). */
    static final int MAX_VERTICES = 4;

    // Цвет и штрих вместе, чтобы линии различались и на чёрно-белой печати (на слайде 17 только штрихи)
    private static final Color[] COLORS = {
            new Color(0x1F77B4), new Color(0xD95F02), new Color(0x2CA02C), new Color(0x7B3294)};
    private static final float[][] DASHES = {null, {9f, 4f}, {2f, 3f}, {9f, 3f, 2f, 3f}};

    private ImpulseChartFactory() {
    }

    /**
     * @param percent true: единичный импульс = 100%, значения умножаются на 100;
     *                false: значения в долях единицы
     */
    static JFreeChart create(ImpulseResult result, FuzzyCognitiveMap map, boolean percent) {
        double scale = percent ? 100 : 1;
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (int vertex : result.chartVertices(MAX_VERTICES)) {
            XYSeries series = new XYSeries(label(map, result.codes().get(vertex)));
            for (int t = 0; t <= result.steps(); t++) {
                series.add(t, result.value(t, vertex) * scale);
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                result.scenario().title() + ": " + result.scenario().perturbationText(),
                "Шаг t", percent ? "Изменение значений, %" : "Изменение значений",
                dataset, PlotOrientation.VERTICAL, true, false, false);
        style(chart, dataset.getSeriesCount());
        return chart;
    }

    private static void style(JFreeChart chart, int seriesCount) {
        chart.getTitle().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chart.getLegend().setItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        // Шаги целые, поэтому и деления на оси X целые
        ((NumberAxis) plot.getDomainAxis()).setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < seriesCount; i++) {
            float[] dash = DASHES[i % DASHES.length];
            renderer.setSeriesPaint(i, COLORS[i % COLORS.length]);
            renderer.setSeriesStroke(i, dash == null
                    ? new BasicStroke(2f)
                    : new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        }
        plot.setRenderer(renderer);
    }

    /** Подпись линии в легенде: «C3 (Спрос)», а если названия нет, то просто код. */
    private static String label(FuzzyCognitiveMap map, String code) {
        String name = map.findConcept(code).map(Concept::name).orElse("");
        return name.isBlank() ? code : code + " (" + name + ")";
    }
}

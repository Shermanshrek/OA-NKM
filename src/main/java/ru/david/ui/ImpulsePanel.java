package ru.david.ui;

import org.jfree.chart.ChartPanel;
import ru.david.analysis.ImpulseResult;
import ru.david.analysis.ImpulseSimulator;
import ru.david.analysis.Scenario;
import ru.david.analysis.ScenarioPlanner;
import ru.david.model.Concept;
import ru.david.model.FuzzyCognitiveMap;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Вкладка импульсного моделирования: план эксперимента (таблица как на слайде 12)
 * и графики процессов. Вершины для импульса выбираются случайно (3 вершины, по +1 и -1 на каждую,
 * итого 6 графиков); кнопка выбирает другие. Остальное пересчитывается сразу при смене параметров.
 */
final class ImpulsePanel extends JPanel {

    private static final int DEFAULT_STEPS = 20;
    private static final int MAX_STEPS = 200;

    private final JSpinner stepsSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_STEPS, 1, MAX_STEPS, 1));
    private final JCheckBox percentBox = new JCheckBox("В процентах (единичный импульс = 100%)", true);
    private final JLabel message = new JLabel(" ");
    private final JTable planTable = new JTable();
    private final JPanel charts = new JPanel();
    private final Random random;

    private FuzzyCognitiveMap map;      // модель, для которой составлен план
    private List<Scenario> plan = List.of();

    ImpulsePanel() {
        this(new Random());
    }

    /** Генератор задаётся снаружи, чтобы в тестах выбор вершин был воспроизводимым. */
    ImpulsePanel(Random random) {
        super(new BorderLayout(0, 6));
        this.random = random;
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JButton rerollButton = new JButton("Выбрать вершины случайно");
        rerollButton.addActionListener(e -> {
            plan = ScenarioPlanner.randomPlan(map, random);
            refresh();
        });
        stepsSpinner.addChangeListener(e -> refresh());
        percentBox.addActionListener(e -> refresh());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Число шагов t:"));
        controls.add(stepsSpinner);
        controls.add(percentBox);
        controls.add(rerollButton);

        planTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // вершин может быть много: таблица прокручивается
        JScrollPane planScroll = new JScrollPane(planTable);
        planScroll.setPreferredSize(new Dimension(100, 165));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(controls, BorderLayout.NORTH);
        top.add(message, BorderLayout.CENTER);
        top.add(planScroll, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(charts, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1000, 650));
    }

    /**
     * Показывает вкладку для модели. План (какие вершины возмущаются) остаётся прежним, пока модель
     * та же и вершины плана в ней есть; иначе составляется новый. Расчёт делается заново всегда,
     * так как веса связей могли измениться.
     */
    void showFor(FuzzyCognitiveMap model) {
        boolean sameModel = model == map;
        map = model;
        if (!sameModel || !planFitsModel()) {
            plan = ScenarioPlanner.randomPlan(map, random);
        }
        refresh();
    }

    private boolean planFitsModel() {
        return !plan.isEmpty() && plan.stream().flatMap(s -> s.impulses().stream())
                .allMatch(i -> map.indexOf(i.vertex()) >= 0);
    }

    // ---------- вывод ----------

    private void refresh() {
        charts.removeAll();
        if (map == null || plan.isEmpty()) {
            message.setText("Модель пуста: добавьте вершины и связи");
            planTable.setModel(new DefaultTableModel());
        } else {
            showPlan();
            showCharts();
        }
        charts.revalidate();
        charts.repaint();
    }

    private void showPlan() {
        List<String> codes = map.concepts().stream().map(Concept::code).toList();

        String[] columns = new String[2 + codes.size()];
        Class<?>[] types = new Class<?>[columns.length];
        columns[0] = "Сценарий";
        columns[1] = "Возмущение";
        for (int i = 0; i < codes.size(); i++) {
            columns[2 + i] = codes.get(i);
        }
        Arrays.fill(types, String.class);

        // Строка плана: номер, возмущение и в столбце каждой возмущаемой вершины её знак (как на слайде 12)
        Object[][] rows = new Object[plan.size()][];
        for (int r = 0; r < rows.length; r++) {
            Scenario scenario = plan.get(r);
            Object[] row = new Object[columns.length];
            row[0] = "№ " + scenario.number();
            row[1] = scenario.perturbationText();
            for (int i = 0; i < codes.size(); i++) {
                row[2 + i] = "";
            }
            for (Scenario.Impulse impulse : scenario.impulses()) {
                row[2 + codes.indexOf(impulse.vertex())] = String.valueOf(impulse.sign());
            }
            rows[r] = row;
        }

        planTable.setModel(new ReadOnlyTableModel(columns, types, rows));
        planTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        planTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        for (int i = 0; i < codes.size(); i++) {
            planTable.getColumnModel().getColumn(2 + i).setPreferredWidth(45);
        }

        List<String> chosen = new ArrayList<>();
        for (Scenario s : plan) {
            for (Scenario.Impulse impulse : s.impulses()) {
                if (!chosen.contains(impulse.vertex())) {
                    chosen.add(impulse.vertex());
                }
            }
        }
        message.setText("Импульс подаётся в случайные вершины: " + String.join(", ", chosen)
                + ". Начальные значения вершин нулевые, импульс разовый (шаг t = 0).");
    }

    private void showCharts() {
        int steps = (Integer) stepsSpinner.getValue();
        boolean percent = percentBox.isSelected();

        // Положительные сценарии идут первыми, отрицательные следом: два ряда, по столбцу на вершину
        charts.setLayout(new GridLayout(2, plan.size() / 2, 6, 6));
        for (Scenario scenario : plan) {
            ImpulseResult result = ImpulseSimulator.simulate(map, scenario, steps);
            charts.add(new ChartPanel(ImpulseChartFactory.create(result, map, percent)));
        }
    }
}

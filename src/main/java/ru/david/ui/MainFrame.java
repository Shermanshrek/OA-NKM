package ru.david.ui;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

/** Главное окно: меню и вкладки «Редактор» (режимы, холст графа, подсказка), «Анализ» и «Импульсы». */
public class MainFrame extends JFrame {

    private static final String TITLE = "Нечёткая когнитивная модель";
    private static final int CTRL = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final GraphPanel graphPanel = new GraphPanel();
    private final AnalysisPanel analysisPanel = new AnalysisPanel();
    private final ImpulsePanel impulsePanel = new ImpulsePanel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final FileController files = new FileController(this, graphPanel, this::onModelChanged);

    // Одни и те же действия стоят и в меню, и на панели инструментов
    private final Action undoAction = action("Отменить", key(KeyEvent.VK_Z, 0), graphPanel::undo);
    private final Action redoAction = action("Повторить", key(KeyEvent.VK_Y, 0), graphPanel::redo);
    private final Action arrangeAction = action("Расставить", null, graphPanel::autoArrange);
    private final Action fitAction = action("Вписать", key(KeyEvent.VK_9, 0), graphPanel::fitToWindow);

    public MainFrame() {
        super(TITLE);
        // Закрытие окна сначала спрашивает о несохранённых правках
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (files.confirmDiscard()) {
                    System.exit(0);
                }
            }
        });

        JLabel hint = new JLabel();
        hint.setBorder(new EmptyBorder(4, 8, 4, 8));
        JPanel editor = new JPanel(new BorderLayout());
        editor.add(createToolBar(hint), BorderLayout.NORTH);
        editor.add(graphPanel, BorderLayout.CENTER);
        editor.add(hint, BorderLayout.SOUTH);

        tabs.addTab("Редактор", editor);
        tabs.addTab("Анализ", analysisPanel);
        tabs.addTab("Импульсы", impulsePanel);
        // Результаты считаются заново при каждом переходе на вкладку: так они не устаревают после правок графа
        tabs.addChangeListener(e -> refreshSelectedTab());

        graphPanel.addChangeListener(this::onModelChanged);
        setJMenuBar(createMenuBar());
        add(tabs, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        onModelChanged();
    }

    // ---------- сборка окна ----------

    private JToolBar createToolBar(JLabel hint) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        ButtonGroup group = new ButtonGroup(); // в группе активна ровно одна кнопка
        for (EditMode mode : EditMode.values()) {
            JToggleButton button = new JToggleButton(mode.title());
            button.addActionListener(e -> {
                graphPanel.setMode(mode);
                hint.setText(mode.hint());
            });
            group.add(button);
            toolBar.add(button);
        }
        // Стартовый режим выбирается так же, как если бы пользователь нажал кнопку
        ((JToggleButton) toolBar.getComponent(EditMode.SELECT.ordinal())).doClick();

        toolBar.addSeparator();
        for (Action action : new Action[]{undoAction, redoAction, arrangeAction, fitAction}) {
            toolBar.add(new JButton(action));
        }
        return toolBar;
    }

    private JMenuBar createMenuBar() {
        JMenu file = new JMenu("Файл");
        file.add(new JMenuItem(action("Новая модель", key(KeyEvent.VK_N, 0), files::newModel)));
        file.add(new JMenuItem(action("Открыть...", key(KeyEvent.VK_O, 0), files::open)));
        file.addSeparator();
        file.add(new JMenuItem(action("Сохранить", key(KeyEvent.VK_S, 0), files::save)));
        file.add(new JMenuItem(action("Сохранить как...", key(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK), files::saveAs)));

        JMenu edit = new JMenu("Правка");
        edit.add(new JMenuItem(undoAction));
        edit.add(new JMenuItem(redoAction));
        edit.addSeparator();
        edit.add(new JMenuItem(arrangeAction));

        JMenu view = new JMenu("Вид");
        view.add(new JMenuItem(action("Увеличить", key(KeyEvent.VK_EQUALS, 0), graphPanel::zoomIn)));
        view.add(new JMenuItem(action("Уменьшить", key(KeyEvent.VK_MINUS, 0), graphPanel::zoomOut)));
        view.add(new JMenuItem(action("Масштаб 100%", key(KeyEvent.VK_0, 0), graphPanel::resetZoom)));
        view.add(new JMenuItem(fitAction));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(edit);
        bar.add(view);
        return bar;
    }

    /** Действие с названием и необязательным сочетанием клавиш; кнопке сочетание не показывается, пункту меню показывается. */
    private static Action action(String title, KeyStroke shortcut, Runnable run) {
        AbstractAction action = new AbstractAction(title) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (shortcut != null) {
            action.putValue(Action.ACCELERATOR_KEY, shortcut);
        }
        return action;
    }

    /** Ctrl + клавиша (на Mac Cmd), плюс дополнительные модификаторы. */
    private static KeyStroke key(int keyCode, int extraModifiers) {
        return KeyStroke.getKeyStroke(keyCode, CTRL | extraModifiers);
    }

    // ---------- реакция на изменения ----------

    /** Вызывается после любого изменения модели или файла: обновляет заголовок, кнопки и открытую вкладку. */
    private void onModelChanged() {
        undoAction.setEnabled(graphPanel.canUndo());
        redoAction.setEnabled(graphPanel.canRedo());
        updateTitle();
        refreshSelectedTab();
    }

    /** Заголовок: имя файла и звёздочка, если есть несохранённые правки. */
    private void updateTitle() {
        Path file = files.currentFile();
        boolean modified = graphPanel.isModified();
        String name = file != null ? file.getFileName().toString() : (modified ? "новая модель" : null);
        setTitle(name == null ? TITLE : TITLE + " — " + name + (modified ? " *" : ""));
    }

    /** Пересчитывает открытую вкладку результатов (модель могли изменить отменой или заменить из меню). */
    private void refreshSelectedTab() {
        Component selected = tabs.getSelectedComponent();
        if (selected == analysisPanel) {
            analysisPanel.showAnalysisOf(graphPanel.model());
        } else if (selected == impulsePanel) {
            impulsePanel.showFor(graphPanel.model());
        }
    }
}

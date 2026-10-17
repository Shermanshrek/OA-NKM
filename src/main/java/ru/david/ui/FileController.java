package ru.david.ui;

import ru.david.io.LayoutData;
import ru.david.io.ModelFileException;
import ru.david.io.ModelStorage;
import ru.david.model.FuzzyCognitiveMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Файловые действия окна: новая модель, открытие, сохранение и вопрос о несохранённых изменениях.
 * Знает, из какого файла загружена модель, и сообщает окну через {@code onChange}, что это изменилось.
 */
final class FileController {

    private final Component parent;
    private final GraphPanel graph;
    private final Runnable onChange;
    // Один диалог на всё время работы: он помнит последнюю открытую папку. Первый раз открывается
    // в текущей папке запуска (при запуске из IDE это папка проекта), а не в «Документах».
    private final JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
    private Path currentFile; // файл, из которого модель загружена или в который сохранена

    FileController(Component parent, GraphPanel graph, Runnable onChange) {
        this.parent = parent;
        this.graph = graph;
        this.onChange = onChange;
        chooser.setFileFilter(new FileNameExtensionFilter("Модель НКМ (*.json)", "json"));
    }

    Path currentFile() {
        return currentFile;
    }

    /**
     * Если есть несохранённые правки, спрашивает, что с ними делать.
     *
     * @return true, если можно продолжать (правок нет, сохранены или пользователь от них отказался);
     * false, если пользователь отменил действие или сохранить не получилось
     */
    boolean confirmDiscard() {
        if (!graph.isModified()) {
            return true;
        }
        String[] options = {"Сохранить", "Не сохранять", "Отмена"};
        int answer = JOptionPane.showOptionDialog(parent, "Сохранить изменения в модели?",
                "Несохранённые изменения", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        return switch (answer) {
            case 0 -> save();
            case 1 -> true;
            default -> false; // «Отмена» или закрытие диалога
        };
    }

    void newModel() {
        if (!confirmDiscard()) {
            return;
        }
        graph.setModel(new FuzzyCognitiveMap(), LayoutData.EMPTY);
        currentFile = null;
        onChange.run();
    }

    void open() {
        if (!confirmDiscard() || chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        try {
            ModelStorage.LoadedModel loaded = ModelStorage.load(file);
            graph.setModel(loaded.map(), loaded.layout());
            currentFile = file;
            onChange.run();
        } catch (ModelFileException e) {
            showError("Не удалось открыть " + file.getFileName(), e);
        }
    }

    /** @return true, если модель сохранена */
    boolean save() {
        return currentFile == null ? saveAs() : writeTo(currentFile);
    }

    /** @return true, если модель сохранена */
    boolean saveAs() {
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".json")) {
            file = file.resolveSibling(file.getFileName() + ".json");
        }
        // JFileChooser сам о перезаписи не спрашивает
        if (Files.exists(file) && JOptionPane.showConfirmDialog(parent,
                "Файл " + file.getFileName() + " уже существует. Заменить?",
                "Сохранение", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return false;
        }
        return writeTo(file);
    }

    private boolean writeTo(Path file) {
        try {
            ModelStorage.save(file, graph.model(), graph.layoutData());
            graph.markSaved();
            currentFile = file;
            onChange.run();
            return true;
        } catch (ModelFileException e) {
            showError("Не удалось сохранить модель", e);
            return false;
        }
    }

    private void showError(String title, ModelFileException e) {
        JOptionPane.showMessageDialog(parent, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}

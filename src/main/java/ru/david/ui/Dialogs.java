package ru.david.ui;

import ru.david.model.Concept;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Диалоги ввода вершины и веса связи.
 * Введённое сразу применяется переданным действием. Если модель отвергла данные
 * (дубликат кода, вес вне [-1; 1] и т. п.), показывается её сообщение, а диалог открывается
 * снова с тем, что ввёл пользователь, поэтому ввод не пропадает.
 */
final class Dialogs {

    private Dialogs() {
    }

    /**
     * Код вершины только показывается: он назначается автоматически (C1, C2...), а менять его
     * нельзя, потому что на код ссылаются связи.
     *
     * @return true, если вершина введена и применена; false, если пользователь отменил
     */
    static boolean conceptForm(Component parent, String title, String code,
                               String name, Consumer<Concept> apply) {
        JTextField codeField = new JTextField(code, 14);
        codeField.setEditable(false);
        JTextField nameField = new JTextField(name, 14);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Код:"));
        form.add(codeField);
        form.add(new JLabel("Название:"));
        form.add(nameField);

        return show(parent, title, form, () -> apply.accept(
                new Concept(codeField.getText(), nameField.getText())));
    }

    /** @return true, если вес введён и применён; false, если пользователь отменил */
    static boolean weightForm(Component parent, String title, String initial, DoubleConsumer apply) {
        JTextField weightField = new JTextField(initial, 10);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Вес связи [-1; 1]:"));
        form.add(weightField);

        return show(parent, title, form, () -> apply.accept(parseNumber(weightField.getText())));
    }

    static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
    }

    private static boolean show(Component parent, String title, JPanel form, Runnable apply) {
        while (true) {
            int answer = JOptionPane.showConfirmDialog(
                    parent, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return false;
            }
            try {
                apply.run();
                return true;
            } catch (IllegalArgumentException ex) {
                error(parent, ex.getMessage());
            }
        }
    }

    /** Принимает и точку, и запятую: в русской раскладке дробную часть часто вводят через запятую. */
    private static double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Введите число, например 0.5 (получено: «" + text + "»)");
        }
    }
}

package ru.david;

import ru.david.ui.MainFrame;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Всё взаимодействие со Swing выполняется в потоке обработки событий (EDT)
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}

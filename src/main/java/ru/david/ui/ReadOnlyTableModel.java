package ru.david.ui;

import javax.swing.table.DefaultTableModel;

/** Таблица только для чтения; тип столбца нужен, чтобы числа сортировались как числа, а не как строки. */
final class ReadOnlyTableModel extends DefaultTableModel {

    private final Class<?>[] types;

    ReadOnlyTableModel(String[] columns, Class<?>[] types, Object[][] rows) {
        super(rows, columns);
        this.types = types;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return types[column];
    }
}

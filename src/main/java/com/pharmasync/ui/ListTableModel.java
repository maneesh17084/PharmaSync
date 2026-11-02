package com.pharmasync.ui;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Table model backed by a list of records, with columns described by getter functions. */
public class ListTableModel<T> extends AbstractTableModel {

    public record Column<T>(String name, Class<?> type, Function<T, ?> getter) {
    }

    public static <T> Column<T> col(String name, Class<?> type, Function<T, ?> getter) {
        return new Column<>(name, type, getter);
    }

    private final List<Column<T>> columns;
    private List<T> rows = new ArrayList<>();

    @SafeVarargs
    public ListTableModel(Column<T>... columns) {
        this.columns = List.of(columns);
    }

    public void setRows(List<T> newRows) {
        rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    public List<T> rows() {
        return rows;
    }

    public T row(int modelIndex) {
        return rows.get(modelIndex);
    }

    public void add(T item) {
        rows.add(item);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    public void remove(int modelIndex) {
        rows.remove(modelIndex);
        fireTableRowsDeleted(modelIndex, modelIndex);
    }

    /** The record under the table's selected row, accounting for sorting. */
    public Optional<T> selected(JTable table) {
        int view = table.getSelectedRow();
        if (view < 0) {
            return Optional.empty();
        }
        return Optional.of(rows.get(table.convertRowIndexToModel(view)));
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).name();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type();
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).getter().apply(rows.get(row));
    }
}

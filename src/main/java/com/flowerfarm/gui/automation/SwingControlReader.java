package com.flowerfarm.gui.automation;

import javax.swing.AbstractButton;
import javax.swing.BoundedRangeModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, resumable reads of the values displayed by Swing controls. */
final class SwingControlReader {
    private static final int MAX_COLLECTION_ITEMS = 200;
    private static final int MAX_COLUMNS = 50;
    private static final int MAX_TABLE_CELLS = 2_000;
    private static final int MAX_SELECTION_SCAN = 10_000;
    private static final int MAX_ITEM_CHARS = 500;
    private static final int MAX_TEXT_CHARS = 8_000;

    private SwingControlReader() { }

    static Map<String, Object> read(JComponent component, String selector,
                                    Integer offset, Integer limit,
                                    Integer columnOffset, Integer columnLimit) {
        requireReadable(component, selector);
        if (!(component instanceof JTable) && (columnOffset != null || columnLimit != null)) {
            throw new IllegalArgumentException("Column paging applies only to tables");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        if (component instanceof JTextComponent text) {
            textValue(text, offset, limit, value);
        } else if (component instanceof JLabel label) {
            value.put("kind", "label");
            textValue(label.getText(), offset, limit, value);
        } else if (component instanceof JComboBox<?> combo) {
            comboValue(combo, offset, limit, value);
        } else if (component instanceof JTable table) {
            tableValue(table, offset, limit, columnOffset, columnLimit, value);
        } else if (component instanceof JList<?> list) {
            listValue(list, offset, limit, value);
        } else if (component instanceof JTabbedPane tabs) {
            tabValue(tabs, offset, limit, value);
        } else {
            if (offset != null || limit != null) {
                throw new IllegalArgumentException("Paging applies only to text, labels, and collections");
            }
            scalarValue(component, selector, value);
        }
        value.put("enabled", component.isEnabled());
        return Map.copyOf(value);
    }

    static Map<String, Object> readItem(JComponent component, String selector,
                                        int index, Integer column, Integer offset, Integer limit) {
        requireReadable(component, selector);
        Map<String, Object> value = new LinkedHashMap<>();
        String text;
        if (component instanceof JTable table) {
            requireIndex(index, table.getRowCount(), "Row");
            if (column == null) {
                throw new IllegalArgumentException("A table item read requires its current-view column index");
            }
            requireIndex(column, table.getColumnCount(), "Column");
            value.put("kind", "table-cell");
            value.put("row", index);
            value.put("column", column);
            value.put("selected", table.isRowSelected(index));
            text = tableCellText(table, index, column);
        } else {
            if (column != null) {
                throw new IllegalArgumentException("An item column applies only to tables");
            }
            if (component instanceof JList<?> list) {
                requireIndex(index, list.getModel().getSize(), "Item");
                value.put("kind", "list-item");
                value.put("selected", list.isSelectedIndex(index));
                text = listItemText(list, index);
            } else if (component instanceof JComboBox<?> combo) {
                requireIndex(index, combo.getItemCount(), "Option");
                value.put("kind", "option");
                value.put("selected", combo.getSelectedIndex() == index);
                text = comboItemText(combo, index);
            } else {
                throw new IllegalArgumentException("Item reads require a table, list, or combo box");
            }
        }
        value.put("index", index);
        textValue(text, offset, limit, value);
        value.put("enabled", component.isEnabled());
        return Map.copyOf(value);
    }

    private static void requireReadable(JComponent component, String selector) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing control reads require the event-dispatch thread");
        }
        if (!component.isVisible()) {
            throw new IllegalArgumentException("Control '" + selector + "' is not visible");
        }
        if (component instanceof JPasswordField) {
            throw new IllegalArgumentException(
                    "Password fields are never readable through the automation harness");
        }
        if (component instanceof JComboBox<?> combo) {
            SwingPasswordGuard.requireNoPasswordEditor(combo);
        }
    }

    private static void textValue(JTextComponent text, Integer offset, Integer limit,
                                  Map<String, Object> value) {
        value.put("kind", "text");
        value.put("editable", text.isEditable());
        Document document = text.getDocument();
        Page page = page(offset, limit, document.getLength(), MAX_TEXT_CHARS, "Text");
        value.put("value", documentText(document, page.offset(), page.count()));
        value.put("textLength", document.getLength());
        pagination(value, page);
    }

    private static void textValue(String text, Integer offset, Integer limit,
                                  Map<String, Object> value) {
        String safe = text == null ? "" : text;
        Page page = page(offset, limit, safe.length(), MAX_TEXT_CHARS, "Text");
        value.put("value", safe.substring(page.offset(), page.end()));
        value.put("textLength", safe.length());
        pagination(value, page);
    }

    private static void comboValue(JComboBox<?> combo, Integer offset, Integer limit,
                                   Map<String, Object> value) {
        value.put("kind", "selector");
        value.put("editable", combo.isEditable());
        value.put("selectedIndex", combo.getSelectedIndex());
        Object editorItem = combo.isEditable() ? combo.getEditor().getItem() : null;
        putText(value, "selected", combo.isEditable()
                ? editorItem == null ? "" : String.valueOf(editorItem)
                : combo.getSelectedIndex() < 0 ? ""
                : comboItemText(combo, combo.getSelectedIndex()), MAX_ITEM_CHARS);
        Page page = page(offset, limit, combo.getItemCount(), MAX_COLLECTION_ITEMS, "Option");
        List<String> options = new ArrayList<>(page.count());
        for (int index = page.offset(); index < page.end(); index++) {
            options.add(boundedItem(value, comboItemText(combo, index)));
        }
        value.put("options", List.copyOf(options));
        value.put("optionIndexes", indexes(page));
        value.put("optionCount", combo.getItemCount());
        pagination(value, page);
    }

    private static void tableValue(JTable table, Integer offset, Integer limit,
                                   Integer columnOffset, Integer columnLimit,
                                   Map<String, Object> value) {
        value.put("kind", "table");
        Page columns = page(columnOffset, columnLimit, table.getColumnCount(), MAX_COLUMNS, "Column");
        Page requestedRows = page(offset, limit, table.getRowCount(), MAX_COLLECTION_ITEMS, "Row");
        int cellBoundRows = columns.count() == 0
                ? MAX_COLLECTION_ITEMS : MAX_TABLE_CELLS / columns.count();
        Page rows = new Page(requestedRows.offset(), requestedRows.limit(),
                Math.min(requestedRows.count(), cellBoundRows), requestedRows.total());
        List<String> titles = new ArrayList<>(columns.count());
        for (int column = columns.offset(); column < columns.end(); column++) {
            titles.add(boundedItem(value, tableColumnTitle(table, column)));
        }
        List<List<String>> contents = new ArrayList<>(rows.count());
        for (int row = rows.offset(); row < rows.end(); row++) {
            List<String> cells = new ArrayList<>(columns.count());
            for (int column = columns.offset(); column < columns.end(); column++) {
                cells.add(boundedItem(value, tableCellText(table, row, column)));
            }
            contents.add(List.copyOf(cells));
        }
        value.put("columns", List.copyOf(titles));
        value.put("rows", List.copyOf(contents));
        value.put("rowCount", table.getRowCount());
        value.put("columnCount", table.getColumnCount());
        value.put("rowIndexes", indexes(rows));
        value.put("columnIndexes", indexes(columns));
        value.put("selectedRows", selectedRows(table.getSelectionModel(), table.getRowCount(), value));
        pagination(value, rows);
        value.put("columnOffset", columns.offset());
        value.put("columnLimit", columns.limit());
        value.put("returnedColumnCount", columns.count());
        value.put("hasMoreColumns", columns.hasMore());
        if (columns.hasMore()) {
            value.put("nextColumnOffset", columns.end());
        }
        markTruncated(value, columns.offset() > 0 || columns.hasMore());
    }

    private static void listValue(JList<?> list, Integer offset, Integer limit,
                                  Map<String, Object> value) {
        value.put("kind", "list");
        Page page = page(offset, limit, list.getModel().getSize(), MAX_COLLECTION_ITEMS, "Item");
        List<String> items = new ArrayList<>(page.count());
        for (int index = page.offset(); index < page.end(); index++) {
            items.add(boundedItem(value, listItemText(list, index)));
        }
        value.put("items", List.copyOf(items));
        value.put("itemIndexes", indexes(page));
        value.put("itemCount", list.getModel().getSize());
        value.put("selectedRows", selectedRows(list.getSelectionModel(), list.getModel().getSize(), value));
        pagination(value, page);
    }

    private static void tabValue(JTabbedPane tabs, Integer offset, Integer limit,
                                 Map<String, Object> value) {
        value.put("kind", "tabs");
        value.put("selectedIndex", tabs.getSelectedIndex());
        putText(value, "selected", tabs.getSelectedIndex() < 0
                ? "" : tabs.getTitleAt(tabs.getSelectedIndex()), MAX_ITEM_CHARS);
        Page page = page(offset, limit, tabs.getTabCount(), MAX_COLLECTION_ITEMS, "Tab");
        List<String> titles = new ArrayList<>(page.count());
        for (int index = page.offset(); index < page.end(); index++) {
            titles.add(boundedItem(value, tabs.getTitleAt(index)));
        }
        value.put("tabs", List.copyOf(titles));
        value.put("tabIndexes", indexes(page));
        value.put("tabCount", tabs.getTabCount());
        pagination(value, page);
    }

    private static void scalarValue(JComponent component, String selector, Map<String, Object> value) {
        if (component instanceof JFileChooser chooser) {
            value.put("kind", "file-chooser");
            value.put("dialogType", chooser.getDialogType() == JFileChooser.SAVE_DIALOG ? "SAVE" : "OPEN");
            putText(value, "title", chooser.getDialogTitle(), MAX_ITEM_CHARS);
            putText(value, "selectedPath", chooser.getSelectedFile() == null ? ""
                    : chooser.getSelectedFile().getAbsolutePath(), MAX_TEXT_CHARS);
            putText(value, "directory", chooser.getCurrentDirectory() == null ? ""
                    : chooser.getCurrentDirectory().getAbsolutePath(), MAX_TEXT_CHARS);
        } else if (component instanceof JSpinner spinner) {
            value.put("kind", "spinner");
            putText(value, "value", String.valueOf(spinner.getValue()), MAX_ITEM_CHARS);
            if (spinner.getModel() instanceof SpinnerNumberModel model) {
                value.put("modelType", "number");
                putNumberBound(value, "minimum", model.getMinimum());
                putNumberBound(value, "maximum", model.getMaximum());
                value.put("stepSize", model.getStepSize());
            } else if (spinner.getModel() instanceof SpinnerDateModel model) {
                value.put("modelType", "date");
                value.put("calendarField", model.getCalendarField());
            }
            if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
                Document document = editor.getTextField().getDocument();
                value.put("editorText", documentText(document, 0,
                        Math.min(document.getLength(), MAX_ITEM_CHARS)));
                markTruncated(value, document.getLength() > MAX_ITEM_CHARS);
            }
        } else if (component instanceof JSlider slider) {
            value.put("kind", "slider");
            value.put("value", slider.getValue());
            value.put("minimum", slider.getMinimum());
            value.put("maximum", slider.getMaximum());
        } else if (component instanceof JScrollPane scroll) {
            value.put("kind", "scroll");
            value.put("horizontal", scrollAxis(scroll, scroll.getHorizontalScrollBar(),
                    scroll.getHorizontalScrollBarPolicy(), JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
            value.put("vertical", scrollAxis(scroll, scroll.getVerticalScrollBar(),
                    scroll.getVerticalScrollBarPolicy(), JScrollPane.VERTICAL_SCROLLBAR_NEVER));
        } else if (component instanceof JScrollBar scroll) {
            value.put("kind", "scrollbar");
            value.putAll(scrollRange(scroll.getModel()));
            value.put("available", scroll.isVisible());
            value.put("orientation", scroll.getOrientation() == JScrollBar.HORIZONTAL
                    ? "HORIZONTAL" : "VERTICAL");
        } else if (component instanceof JSplitPane split) {
            value.put("kind", "split");
            value.put("value", split.getDividerLocation());
            value.put("minimum", split.getMinimumDividerLocation());
            value.put("maximum", split.getMaximumDividerLocation());
            value.put("dividerSize", split.getDividerSize());
            value.put("orientation", split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                    ? "HORIZONTAL" : "VERTICAL");
        } else if (component instanceof AbstractButton button) {
            value.put("kind", "button");
            value.put("selected", button.isSelected());
            putText(value, "label", button.getText(), MAX_ITEM_CHARS);
        } else {
            throw new IllegalArgumentException("Control '" + selector + "' has no supported readable value");
        }
    }

    private static Map<String, Object> scrollRange(BoundedRangeModel model) {
        return Map.of("value", model.getValue(), "minimum", model.getMinimum(),
                "maximum", model.getMaximum(), "extent", model.getExtent(),
                "maximumValue", (long) model.getMaximum() - model.getExtent());
    }

    private static Map<String, Object> scrollAxis(
            JScrollPane pane, JScrollBar bar, int policy, int neverPolicy) {
        Map<String, Object> value = new LinkedHashMap<>();
        boolean visible = bar != null && bar.isVisible();
        boolean available = pane.isVisible() && visible && policy != neverPolicy;
        value.put("available", available);
        value.put("visible", visible);
        value.put("enabled", available && pane.isEnabled() && bar.isEnabled());
        value.put("policy", policy);
        if (bar != null) {
            value.putAll(scrollRange(bar.getModel()));
        }
        return Map.copyOf(value);
    }

    private static void putNumberBound(Map<String, Object> value, String key, Object bound) {
        if (bound instanceof Number number) {
            value.put(key, number);
        }
    }

    private static String documentText(Document document, int offset, int count) {
        try {
            return document.getText(offset, count);
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Text changed while reading its requested page", exception);
        }
    }

    private static Page page(Integer offset, Integer limit, int total, int maximum, String kind) {
        int start = offset == null ? 0 : offset;
        int size = limit == null ? maximum : limit;
        if (start < 0 || start > total) {
            throw new IllegalArgumentException(kind + " offset must be between 0 and " + total);
        }
        if (size < 1 || size > maximum) {
            throw new IllegalArgumentException(kind + " limit must be between 1 and " + maximum);
        }
        return new Page(start, size, Math.min(size, total - start), total);
    }

    private static void requireIndex(int index, int total, String kind) {
        if (index < 0 || index >= total) {
            throw new IllegalArgumentException(kind + " index is outside the current " + total + " item(s)");
        }
    }

    private static void pagination(Map<String, Object> value, Page page) {
        value.put("offset", page.offset());
        value.put("limit", page.limit());
        value.put("returnedCount", page.count());
        value.put("hasMore", page.hasMore());
        if (page.hasMore()) {
            value.put("nextOffset", page.end());
        }
        markTruncated(value, page.offset() > 0 || page.hasMore());
    }

    private static List<Integer> indexes(Page page) {
        List<Integer> indexes = new ArrayList<>(page.count());
        for (int index = page.offset(); index < page.end(); index++) {
            indexes.add(index);
        }
        return List.copyOf(indexes);
    }

    private static List<Integer> selectedRows(
            ListSelectionModel selection, int itemCount, Map<String, Object> value) {
        int first = selection.getMinSelectionIndex();
        int last = Math.min(selection.getMaxSelectionIndex(), itemCount - 1);
        if (first < 0 || first > last) {
            return List.of();
        }
        List<Integer> rows = new ArrayList<>();
        int scanned = 0;
        for (int index = first; index <= last && scanned < MAX_SELECTION_SCAN; index++, scanned++) {
            if (selection.isSelectedIndex(index)) {
                if (rows.size() == MAX_COLLECTION_ITEMS) {
                    markTruncated(value, true);
                    return List.copyOf(rows);
                }
                rows.add(index);
            }
        }
        markTruncated(value, (long) last - first + 1 > scanned);
        return List.copyOf(rows);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String comboItemText(JComboBox<?> combo, int index) {
        Object item = combo.getItemAt(index);
        javax.swing.ListCellRenderer renderer = combo.getRenderer();
        if (renderer != null) {
            Component rendered = renderer.getListCellRendererComponent(new JList<>(), item, index, false, false);
            SwingPasswordGuard.requireNoPassword(rendered);
            if (rendered instanceof JLabel label && label.getText() != null) {
                return label.getText();
            }
        }
        return String.valueOf(item);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String listItemText(JList<?> list, int index) {
        Object item = list.getModel().getElementAt(index);
        javax.swing.ListCellRenderer renderer = list.getCellRenderer();
        Component rendered = renderer == null ? null
                : renderer.getListCellRendererComponent(list, item, index, false, false);
        SwingPasswordGuard.requireNoPassword(rendered);
        if (rendered instanceof JLabel label && label.getText() != null) {
            return label.getText();
        }
        return String.valueOf(item);
    }

    static String tableColumnTitle(JTable table, int column) {
        Object title = table.getColumnModel().getColumn(column).getHeaderValue();
        return title == null ? "" : String.valueOf(title);
    }

    private static String tableCellText(JTable table, int row, int column) {
        Component rendered = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        SwingPasswordGuard.requireNoPassword(rendered);
        if (rendered instanceof JLabel label && label.getText() != null) {
            return label.getText();
        }
        return String.valueOf(table.getValueAt(row, column));
    }

    private static String boundedItem(Map<String, Object> value, String text) {
        markTruncated(value, text != null && text.length() > MAX_ITEM_CHARS);
        return truncate(text, MAX_ITEM_CHARS);
    }

    private static void putText(Map<String, Object> value, String key, String text, int maximum) {
        String safe = text == null ? "" : text;
        value.put(key, truncate(safe, maximum));
        markTruncated(value, safe.length() > maximum);
    }

    private static void markTruncated(Map<String, Object> value, boolean clipped) {
        value.put("truncated", clipped || Boolean.TRUE.equals(value.get("truncated")));
    }

    private static String truncate(String text, int maximum) {
        return text == null ? "" : text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private record Page(int offset, int limit, int count, int total) {
        int end() { return offset + count; }
        boolean hasMore() { return end() < total; }
    }
}

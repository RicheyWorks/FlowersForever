package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingControlReaderTest {

    @Test
    void readsEveryTextCharacterUsingOnlyBoundedDocumentSlices() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean reading = new AtomicBoolean();
            List<Integer> documentReads = new ArrayList<>();
            PlainDocument document = new PlainDocument() {
                @Override
                public String getText(int offset, int length) throws BadLocationException {
                    if (reading.get()) {
                        assertThat(length).isLessThanOrEqualTo(8_000);
                        documentReads.add(length);
                    }
                    return super.getText(offset, length);
                }
            };
            String original = "First page ".repeat(2_000) + "Final evidence";
            try {
                document.insertString(0, original, null);
            } catch (BadLocationException exception) {
                throw new AssertionError(exception);
            }
            JTextArea text = new JTextArea(document);
            text.setEditable(false);
            reading.set(true);
            StringBuilder recovered = new StringBuilder();
            int offset = 0;
            while (true) {
                Map<String, Object> page = read(text, offset, null, null, null);
                recovered.append(page.get("value"));
                assertThat(page).containsEntry("editable", false)
                        .containsEntry("textLength", original.length());
                if (!Boolean.TRUE.equals(page.get("hasMore"))) {
                    assertThat(page).doesNotContainKey("nextOffset");
                    break;
                }
                int next = (Integer) page.get("nextOffset");
                assertThat(next).isGreaterThan(offset);
                offset = next;
            }
            assertThat(recovered.toString()).isEqualTo(original);
            assertThat(documentReads).hasSize(3).contains(8_000);
        });
    }

    @Test
    void listPagesReadOnlyRequestedItemsAndKeepAbsoluteSelectionIndexes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Integer> visited = new ArrayList<>();
            JList<String> list = new JList<>(new AbstractListModel<>() {
                @Override public int getSize() { return 451; }
                @Override public String getElementAt(int index) {
                    visited.add(index);
                    return "Harvest " + index;
                }
            });
            list.setSelectedIndex(420);
            visited.clear();
            Map<String, Object> middle = read(list, 200, 200, null, null);
            assertThat(visited).containsExactlyElementsOf(IntStream.range(200, 400).boxed().toList());
            assertThat(middle).containsEntry("nextOffset", 400).containsEntry("hasMore", true)
                    .containsEntry("itemCount", 451).containsEntry("selectedRows", List.of(420));
            assertThat(middle.get("itemIndexes")).isEqualTo(IntStream.range(200, 400).boxed().toList());
            Map<String, Object> last = read(list, 400, 200, null, null);
            assertThat(last).containsEntry("returnedCount", 51).containsEntry("hasMore", false)
                    .doesNotContainKey("nextOffset");
            assertThat(last.get("items")).asList().contains("Harvest 420").endsWith("Harvest 450");
        });
    }

    @Test
    void pagesComboOptionsAndTabsWithoutLosingTheirCurrentChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(IntStream.range(0, 401)
                    .mapToObj(index -> "customer-" + index).toArray(String[]::new));
            combo.setRenderer((list, item, index, selected, focus) -> new JLabel("Display " + item));
            combo.setSelectedIndex(350);
            Map<String, Object> options = read(combo, 300, 100, null, null);
            assertThat(options).containsEntry("selectedIndex", 350)
                    .containsEntry("selected", "Display customer-350")
                    .containsEntry("nextOffset", 400).containsEntry("optionCount", 401);
            assertThat(options.get("options")).asList().startsWith("Display customer-300")
                    .endsWith("Display customer-399");
            assertThat(options.get("optionIndexes")).isEqualTo(IntStream.range(300, 400).boxed().toList());

            JTabbedPane tabs = new JTabbedPane();
            for (int index = 0; index < 205; index++) {
                tabs.addTab("Tab " + index, new JPanel());
            }
            tabs.setSelectedIndex(203);
            Map<String, Object> page = read(tabs, 200, 5, null, null);
            assertThat(page).containsEntry("tabs", List.of("Tab 200", "Tab 201", "Tab 202", "Tab 203", "Tab 204"))
                    .containsEntry("selected", "Tab 203").containsEntry("hasMore", false)
                    .containsEntry("tabIndexes", List.of(200, 201, 202, 203, 204));
        });
    }

    @Test
    void tablePagingUsesCurrentSortedRowsAndReorderedColumns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new DefaultTableModel(
                    new Object[][]{{"C", 30, "third"}, {"A", 10, "first"}, {"B", 20, "second"}},
                    new Object[]{"Name", "Count", "Position"}));
            table.setAutoCreateRowSorter(true);
            table.getRowSorter().toggleSortOrder(0);
            table.moveColumn(2, 0);
            table.setRowSelectionInterval(2, 2);
            Map<String, Object> page = read(table, 1, 2, 1, 2);
            assertThat(page).containsEntry("columns", List.of("Name", "Count"))
                    .containsEntry("rows", List.of(List.of("B", "20"), List.of("C", "30")))
                    .containsEntry("rowIndexes", List.of(1, 2))
                    .containsEntry("columnIndexes", List.of(1, 2))
                    .containsEntry("selectedRows", List.of(2))
                    .containsEntry("hasMore", false).containsEntry("hasMoreColumns", false);
        });
    }

    @Test
    void tableCellBudgetStillMakesProgressAcrossBothPagingDimensions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger cells = new AtomicInteger();
            JTable table = new JTable(new AbstractTableModel() {
                @Override public int getRowCount() { return 500; }
                @Override public int getColumnCount() { return 70; }
                @Override public Object getValueAt(int row, int column) {
                    cells.incrementAndGet();
                    return row + ":" + column;
                }
            });
            Map<String, Object> first = read(table, 0, 200, 0, 50);
            assertThat(first).containsEntry("returnedCount", 40).containsEntry("nextOffset", 40)
                    .containsEntry("returnedColumnCount", 50).containsEntry("nextColumnOffset", 50);
            assertThat(cells).hasValue(2_000);
            cells.set(0);
            Map<String, Object> following = read(table, 40, 200, 50, 50);
            assertThat(following).containsEntry("returnedCount", 100).containsEntry("nextOffset", 140)
                    .containsEntry("returnedColumnCount", 20).containsEntry("hasMoreColumns", false);
            assertThat(following.get("rows")).asList().first().asList().startsWith("40:50");
            assertThat(cells).hasValue(2_000);
        });
    }

    @Test
    void preservesItemClippingAndSelectionBoundsOnPagedCollections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String large = "x".repeat(501);
            JList<String> list = new JList<>(IntStream.range(0, 300)
                    .mapToObj(index -> large).toArray(String[]::new));
            list.setSelectionInterval(0, 299);
            Map<String, Object> page = read(list, 299, 1, null, null);
            assertThat(page).containsEntry("items", List.of("x".repeat(500)))
                    .containsEntry("truncated", true).containsEntry("hasMore", false);
            assertThat(page.get("selectedRows")).asList().hasSize(200).endsWith(199);
        });
    }

    @Test
    void readsTheFullRenderedItemBeyondTheCollectionPreviewLimit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String longText = "c".repeat(17_000) + "TAIL";
            JComboBox<String> combo = new JComboBox<>(new String[]{longText});
            combo.setRenderer((list, item, index, selected, focus) -> new JLabel("Display " + item));
            Map<String, Object> preview = read(combo, null, null, null, null);
            assertThat(preview.get("options")).asList().singleElement().asString().hasSize(500);
            StringBuilder complete = new StringBuilder();
            int offset = 0;
            while (true) {
                Map<String, Object> page = SwingControlReader.readItem(combo, "Options", 0, null, offset, null);
                complete.append(page.get("value"));
                assertThat(page).containsEntry("index", 0).containsEntry("selected", true);
                if (!Boolean.TRUE.equals(page.get("hasMore"))) {
                    break;
                }
                offset = (Integer) page.get("nextOffset");
            }
            assertThat(complete.toString()).isEqualTo("Display " + longText);
            JList<String> list = new JList<>(new String[]{longText});
            assertThat(SwingControlReader.readItem(list, "Items", 0, null, 17_000, 4))
                    .containsEntry("value", "TAIL").containsEntry("hasMore", false);

            JTable table = new JTable(new DefaultTableModel(
                    new Object[][]{{"C", longText}, {"A", "Another row"}}, new Object[]{"Name", "Notes"}));
            table.setAutoCreateRowSorter(true);
            table.getRowSorter().toggleSortOrder(0);
            table.moveColumn(1, 0);
            Map<String, Object> cell = SwingControlReader.readItem(table, "Evidence", 1, 0, 17_000, 4);
            assertThat(cell).containsEntry("row", 1).containsEntry("column", 0)
                    .containsEntry("value", "TAIL").containsEntry("textLength", 17_004);
            assertThatThrownBy(() -> SwingControlReader.readItem(table, "Evidence", 0, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SwingControlReader.readItem(list, "Items", 0, 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SwingControlReader.readItem(combo, "Options", 1, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void rejectsInvalidBoundsAndUnsupportedPagingWithoutOverflow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea text = new JTextArea("Three");
            assertThatThrownBy(() -> read(text, -1, 1, null, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> read(text, 6, 1, null, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> read(text, 0, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> read(text, 0, Integer.MAX_VALUE, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> read(text, 0, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> read(new JSpinner(), 0, 1, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(read(text, 5, 1, null, null)).containsEntry("value", "")
                    .containsEntry("returnedCount", 0).containsEntry("hasMore", false);

            JTable enormous = new JTable(new AbstractTableModel() {
                @Override public int getRowCount() { return Integer.MAX_VALUE; }
                @Override public int getColumnCount() { return 1; }
                @Override public Object getValueAt(int row, int column) { return row; }
            });
            Map<String, Object> last = read(enormous, Integer.MAX_VALUE - 1, 200, 0, 1);
            assertThat(last).containsEntry("returnedCount", 1).containsEntry("hasMore", false)
                    .containsEntry("rowIndexes", List.of(Integer.MAX_VALUE - 1))
                    .doesNotContainKey("nextOffset");
        });
    }

    @Test
    void exposesLabelsSpinnerConstraintsAndLayoutBounds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(read(new JLabel("Saving complete"), 7, 8, null, null))
                    .containsEntry("kind", "label").containsEntry("value", "complete")
                    .containsEntry("textLength", 15);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(4, 0, 10, 2));
            assertThat(read(spinner, null, null, null, null)).containsEntry("value", "4")
                    .containsEntry("minimum", 0).containsEntry("maximum", 10)
                    .containsEntry("stepSize", 2).containsEntry("modelType", "number");
            JSpinner date = new JSpinner(new SpinnerDateModel());
            date.setEditor(new JSpinner.DateEditor(date, "yyyy-MM-dd HH:mm"));
            String displayed = ((JSpinner.DateEditor) date.getEditor()).getTextField().getText();
            assertThat(read(date, null, null, null, null)).containsEntry("editorText", displayed)
                    .containsEntry("modelType", "date");

            JScrollPane scroll = new JScrollPane(new JTextArea("Long result\n".repeat(100)));
            scroll.setSize(300, 120);
            scroll.doLayout();
            Map<String, Object> bounds = read(scroll, null, null, null, null);
            assertThat(bounds).containsEntry("kind", "scroll");
            assertThat(bounds.get("vertical")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("value", scroll.getVerticalScrollBar().getValue())
                    .containsEntry("minimum", scroll.getVerticalScrollBar().getMinimum())
                    .containsEntry("maximum", scroll.getVerticalScrollBar().getMaximum())
                    .containsEntry("extent", scroll.getVerticalScrollBar().getVisibleAmount());

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
            split.setSize(600, 200);
            split.setDividerLocation(240);
            split.doLayout();
            assertThat(read(split, null, null, null, null)).containsEntry("kind", "split")
                    .containsEntry("value", split.getDividerLocation())
                    .containsEntry("minimum", split.getMinimumDividerLocation())
                    .containsEntry("maximum", split.getMaximumDividerLocation());
        });
    }

    @Test
    void reportsUnavailableHiddenAndDisabledScrollAxesWithoutInventingBounds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            class OptionalAxisPane extends JScrollPane {
                private boolean verticalAbsent;
                @Override public JScrollBar getVerticalScrollBar() {
                    return verticalAbsent ? null : super.getVerticalScrollBar();
                }
            }
            OptionalAxisPane scroll = new OptionalAxisPane();
            scroll.setViewportView(new JTextArea());
            scroll.verticalAbsent = true;
            assertThat(read(scroll, null, null, null, null).get("vertical"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("available", false).containsEntry("enabled", false)
                    .doesNotContainKey("value");
            scroll.verticalAbsent = false;
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(read(scroll, null, null, null, null).get("horizontal"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("available", false).containsEntry("enabled", false);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            scroll.getHorizontalScrollBar().setVisible(false);
            assertThat(read(scroll, null, null, null, null).get("horizontal"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("available", false).containsEntry("visible", false);
            scroll.getHorizontalScrollBar().setVisible(true);
            scroll.getHorizontalScrollBar().setEnabled(false);
            assertThat(read(scroll, null, null, null, null).get("horizontal"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("available", true).containsEntry("enabled", false);
            scroll.getHorizontalScrollBar().setEnabled(true);
            scroll.setEnabled(false);
            assertThat(read(scroll, null, null, null, null).get("horizontal"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("available", true).containsEntry("enabled", false);
        });
    }

    @Test
    void stillRefusesPasswordsAndHiddenComponents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThatThrownBy(() -> read(new JPasswordField("secret"), null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never readable");
            JLabel hidden = new JLabel("Hidden information");
            hidden.setVisible(false);
            assertThatThrownBy(() -> read(hidden, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not visible");
        });
    }

    private static Map<String, Object> read(JComponent component, Integer offset, Integer limit,
                                            Integer columnOffset, Integer columnLimit) {
        return SwingControlReader.read(component, "Fixture", offset, limit, columnOffset, columnLimit);
    }
}

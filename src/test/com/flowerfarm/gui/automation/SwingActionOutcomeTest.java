package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingActionOutcomeTest {
    @Test
    void rejectsAComboModelReplacedDuringLabelMatchingBeforeSelectingAnotherRecord() throws Exception {
        assertRejectsRendererRefresh(true, false);
    }

    @Test
    void rejectsComboContentsChangedDuringLabelMatchingAndAllowsStableRecovery() throws Exception {
        assertRejectsRendererRefresh(false, false);
    }

    @Test
    void rejectsAComboRefreshBeforeWritingAnUnmatchedCustomOption() throws Exception {
        assertRejectsRendererRefresh(false, true);
    }

    private static void assertRejectsRendererRefresh(boolean replaceModel, boolean customText) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            class RefreshingModel extends DefaultComboBoxModel<Choice> {
                private Choice first = new Choice(1, "Tulips");

                RefreshingModel() {
                    super(new Choice[]{new Choice(1, "Tulips"), new Choice(2, "Roses")});
                }

                @Override public Choice getElementAt(int index) {
                    return index == 0 ? first : super.getElementAt(index);
                }

                void refresh() {
                    first = new Choice(99, "Dahlias");
                    fireContentsChanged(this, 0, 0);
                }
            }
            RefreshingModel initialModel = new RefreshingModel();
            JComboBox<Choice> combo = new JComboBox<>(initialModel);
            combo.setName("products");
            combo.setSelectedIndex(1);
            combo.setEditable(customText);
            AtomicBoolean refresh = new AtomicBoolean();
            AtomicInteger selections = new AtomicInteger();
            combo.setRenderer((list, item, index, selected, focus) -> {
                if (refresh.getAndSet(false)) {
                    if (replaceModel) {
                        DefaultComboBoxModel<Choice> replacement = new DefaultComboBoxModel<>(new Choice[]{
                                new Choice(99, "Dahlias"), new Choice(2, "Roses")});
                        replacement.setSelectedItem(replacement.getElementAt(1));
                        combo.setModel(replacement);
                    } else {
                        initialModel.refresh();
                    }
                }
                return new JLabel(String.valueOf(item));
            });
            combo.addActionListener(event -> selections.incrementAndGet());
            exercise(combo, bridge -> {
                refresh.set(true);
                assertThatThrownBy(() -> bridge.chooseOption("products", customText ? "Custom flower" : "Tulips"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed");
                assertThat(combo.getSelectedItem()).isEqualTo(new Choice(2, "Roses"));
                assertThat(selections).hasValue(0);
                if (customText) {
                    assertThat(((JTextComponent) combo.getEditor().getEditorComponent()).getText()).isEqualTo("Roses");
                }

                bridge.chooseOption("products", customText ? "Custom flower" : "Dahlias");
                assertThat(combo.getSelectedItem()).isEqualTo(customText
                        ? "Custom flower" : new Choice(99, "Dahlias"));
                assertThat(selections.get()).isGreaterThan(0);
            });
        });
    }

    @Test
    void reportsARevertedComboChoiceAsAFailedReceiptAndAllowsAFreshRecovery() throws Exception {
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger requestedSelections = new AtomicInteger();
        JComboBox<String> combo = onEdt(() -> {
            JComboBox<String> result = new JComboBox<>(new String[]{"Pickup", "Delivery"});
            result.setName("handoff");
            result.addActionListener(event -> {
                if ("Delivery".equals(result.getSelectedItem())) {
                    requestedSelections.incrementAndGet();
                    if (reject.get()) {
                        result.setSelectedItem("Pickup");
                    }
                }
            });
            return result;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(combo, title -> false);
        try {
            SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
            HarnessCommand refused = new HarnessCommand("refused-handoff", "choose-option",
                    Map.of("selector", "handoff", "value", "Delivery"));
            var receipt = plugin.execute(refused);
            onEdt(() -> null);
            assertThat(bridge.actionStatus(refused.requestId()).output())
                    .containsEntry("status", "FAILED")
                    .containsEntry("failureType", "IllegalStateException");
            assertThat(onEdt(combo::getSelectedItem)).isEqualTo("Pickup");
            reject.set(false);
            assertThat(plugin.execute(refused)).isEqualTo(receipt);
            assertThat(requestedSelections).hasValue(1);

            plugin.execute(new HarnessCommand("recovered-handoff", "choose-option",
                    Map.of("selector", "handoff", "value", "Delivery")));
            onEdt(() -> null);
            assertThat(bridge.actionStatus("recovered-handoff").output())
                    .containsEntry("status", "COMPLETED");
            assertThat(onEdt(combo::getSelectedItem)).isEqualTo("Delivery");
            assertThat(requestedSelections).hasValue(2);
        } finally {
            onEdt(() -> {
                bridge.detach();
                return null;
            });
        }
    }

    @Test
    void rejectsAnotherRecordSubstitutedAtTheRequestedComboIndex() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<Choice> combo = choices();
            AtomicBoolean substitute = new AtomicBoolean(true);
            AtomicBoolean replacing = new AtomicBoolean();
            combo.addActionListener(event -> {
                if (substitute.get() && !replacing.get()
                        && combo.getSelectedItem() instanceof Choice choice && choice.id() == 2) {
                    replacing.set(true);
                    try {
                        combo.setModel(new DefaultComboBoxModel<>(new Choice[]{
                                new Choice(1, "Tulips"), new Choice(99, "Roses")}));
                        combo.setSelectedIndex(1);
                    } finally {
                        replacing.set(false);
                    }
                }
            });
            exercise(combo, bridge -> {
                for (boolean byIndex : List.of(false, true)) {
                    substitute.set(false);
                    combo.setModel(choiceModel());
                    substitute.set(true);
                    assertThatThrownBy(() -> chooseRoses(bridge, byIndex))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
                    assertThat(combo.getSelectedIndex()).isEqualTo(1);
                    assertThat(combo.getSelectedItem()).isEqualTo(new Choice(99, "Roses"));

                    substitute.set(false);
                    combo.setModel(choiceModel());
                    chooseRoses(bridge, byIndex);
                    assertThat(combo.getSelectedItem()).isEqualTo(new Choice(2, "Roses"));
                }
            });
        });
    }

    @Test
    void acceptsCrmStyleModelRebuildsThatRetainFreshEqualChoiceRecords() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<Choice> combo = choices();
            AtomicBoolean rebuilding = new AtomicBoolean();
            AtomicInteger rebuilds = new AtomicInteger();
            combo.addActionListener(event -> {
                if (!rebuilding.get() && combo.getSelectedItem() instanceof Choice selected) {
                    rebuilding.set(true);
                    try {
                        combo.setModel(choiceModel());
                        combo.setSelectedIndex(selected.id() - 1);
                        rebuilds.incrementAndGet();
                    } finally {
                        rebuilding.set(false);
                    }
                }
            });
            exercise(combo, bridge -> {
                for (boolean byIndex : List.of(false, true)) {
                    combo.setSelectedIndex(0);
                    var originalModel = combo.getModel();
                    Choice originalChoice = combo.getItemAt(1);
                    chooseRoses(bridge, byIndex);
                    assertThat(combo.getModel()).isNotSameAs(originalModel);
                    assertThat(combo.getSelectedItem()).isEqualTo(originalChoice)
                            .isNotSameAs(originalChoice);
                    assertThat(combo.getSelectedIndex()).isEqualTo(1);
                }
                assertThat(rebuilds.get()).isGreaterThanOrEqualTo(2);
            });
        });
    }

    @Test
    void rejectsCustomOptionTextRefusedByTheEditorOrSelectionListener() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(new String[]{"Known category"});
            combo.setName("category");
            combo.setEditable(true);
            JTextComponent editor = (JTextComponent) combo.getEditor().getEditorComponent();
            AbstractDocument document = (AbstractDocument) editor.getDocument();
            document.setDocumentFilter(new DocumentFilter() {
                @Override
                public void replace(FilterBypass bypass, int offset, int length,
                                    String text, AttributeSet attributes) {
                    // A field validator can decline a replacement without throwing.
                }
            });
            AtomicBoolean rejectSelection = new AtomicBoolean(true);
            AtomicInteger customSelections = new AtomicInteger();
            combo.addActionListener(event -> {
                if ("Custom category".equals(combo.getSelectedItem())) {
                    customSelections.incrementAndGet();
                    if (rejectSelection.get()) {
                        combo.setSelectedItem("Known category");
                    }
                }
            });
            exercise(combo, bridge -> {
                assertThatThrownBy(() -> bridge.chooseOption("category", "Custom category"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("editor");
                assertThat(combo.getSelectedItem()).isEqualTo("Known category");
                assertThat(customSelections).hasValue(0);

                document.setDocumentFilter(null);
                assertThatThrownBy(() -> bridge.chooseOption("category", "Custom category"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("custom option");
                assertThat(combo.getSelectedItem()).isEqualTo("Known category");
                rejectSelection.set(false);
                bridge.chooseOption("category", "Custom category");
                assertThat(combo.getSelectedItem()).isEqualTo("Custom category");
                assertThat(editor.getText()).isEqualTo("Custom category");
                assertThat(customSelections).hasValue(2);
            });
        });
    }

    @Test
    void rejectsTabReversionAndReplacementEvenWhenTheTitleAndIndexMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabs = new JTabbedPane();
            tabs.setName("workspace");
            tabs.addTab("Overview", new JPanel());
            JPanel requested = new JPanel();
            tabs.addTab("Orders", requested);
            AtomicInteger mode = new AtomicInteger(1);
            AtomicBoolean handling = new AtomicBoolean();
            tabs.addChangeListener(event -> {
                if (tabs.getSelectedIndex() == 1 && !handling.get()) {
                    handling.set(true);
                    try {
                        if (mode.get() == 1) {
                            tabs.setSelectedIndex(0);
                        } else if (mode.get() == 2) {
                            tabs.setComponentAt(1, new JPanel());
                        }
                    } finally {
                        handling.set(false);
                    }
                }
            });
            exercise(tabs, bridge -> {
                assertThatThrownBy(() -> bridge.selectTab("workspace", "Orders"))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(tabs.getSelectedIndex()).isZero();
                mode.set(2);
                assertThatThrownBy(() -> bridge.selectTab("workspace", "Orders"))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(tabs.getSelectedIndex()).isEqualTo(1);
                assertThat(tabs.getTitleAt(1)).isEqualTo("Orders");
                assertThat(tabs.getSelectedComponent()).isNotSameAs(requested);
                mode.set(0);
                tabs.setSelectedIndex(0);
                bridge.selectTab("workspace", "Orders");
                assertThat(tabs.getSelectedComponent()).isSameAs(tabs.getComponentAt(1));
            });
        });
    }

    @Test
    void rejectsListenersThatRedirectTableRowsOrAddUnrequestedListRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new Object[][]{{"Tulips"}, {"Roses"}, {"Dahlias"}},
                    new String[]{"Product"});
            table.setName("products");
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            AtomicBoolean rejectTable = new AtomicBoolean(true);
            table.getSelectionModel().addListSelectionListener(event -> {
                if (rejectTable.get() && !event.getValueIsAdjusting()
                        && !table.getSelectionModel().isSelectionEmpty()
                        && (table.getSelectedRow() != 2 || table.getSelectedRowCount() != 1)) {
                    table.setRowSelectionInterval(2, 2);
                }
            });
            JList<String> list = new JList<>(new String[]{"Pickup", "Delivery"});
            list.setName("handoffs");
            AtomicBoolean rejectList = new AtomicBoolean(true);
            list.addListSelectionListener(event -> {
                if (rejectList.get() && list.isSelectedIndex(0) && !list.isSelectedIndex(1)) {
                    list.addSelectionInterval(1, 1);
                }
            });
            JPanel root = new JPanel();
            root.add(table);
            root.add(list);
            exercise(root, bridge -> {
                assertThatThrownBy(() -> bridge.selectRow("products", 0))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(table.getSelectedRows()).containsExactly(2);
                rejectTable.set(false);
                bridge.selectRow("products", 0);
                assertThat(table.getSelectedRows()).containsExactly(0);

                rejectTable.set(true);
                assertThatThrownBy(() -> bridge.selectRows("products", List.of(0, 1)))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(table.getSelectedRows()).containsExactly(2);
                assertThat(table.getSelectionModel().getValueIsAdjusting()).isFalse();
                rejectTable.set(false);
                bridge.selectRows("products", List.of(1, 0, 1));
                assertThat(table.getSelectedRows()).containsExactly(0, 1);

                assertThatThrownBy(() -> bridge.selectListRow("handoffs", 0))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(list.getSelectedIndices()).containsExactly(0, 1);
                rejectList.set(false);
                bridge.selectListRow("handoffs", 0);
                assertThat(list.getSelectedIndices()).containsExactly(0);
            });
        });
    }

    @Test
    void rejectsASliderListenerThatRestoresThePreviousValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSlider slider = new JSlider(0, 100, 25);
            slider.setName("harvest-window");
            AtomicBoolean reject = new AtomicBoolean(true);
            slider.addChangeListener(event -> {
                if (reject.get() && slider.getValue() != 25) {
                    slider.setValue(25);
                }
            });
            exercise(slider, bridge -> {
                assertThatThrownBy(() -> bridge.setSlider("harvest-window", 75))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
                assertThat(slider.getValue()).isEqualTo(25);
                reject.set(false);
                bridge.setSlider("harvest-window", 75);
                assertThat(slider.getValue()).isEqualTo(75);
            });
        });
    }

    @Test
    void rejectsSortRollbackOrSorterReplacementAndThenSortsTheCurrentModel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(
                    new Object[][]{{"Roses"}, {"Dahlias"}}, new String[]{"Product"});
            JTable table = new JTable(model);
            table.setName("products");
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);
            AtomicInteger mode = new AtomicInteger(1);
            AtomicBoolean handling = new AtomicBoolean();
            sorter.addRowSorterListener(event -> {
                if (!sorter.getSortKeys().isEmpty() && !handling.get()) {
                    handling.set(true);
                    try {
                        if (mode.get() == 1) {
                            sorter.setSortKeys(List.of());
                        } else if (mode.get() == 2) {
                            TableRowSorter<DefaultTableModel> replacement = new TableRowSorter<>(model);
                            replacement.setSortKeys(sorter.getSortKeys());
                            table.setRowSorter(replacement);
                        }
                    } finally {
                        handling.set(false);
                    }
                }
            });
            exercise(table, bridge -> {
                assertThatThrownBy(() -> bridge.sortTable("products", "Product", "ASCENDING"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
                assertThat(sorter.getSortKeys()).isEmpty();
                mode.set(2);
                assertThatThrownBy(() -> bridge.sortTable("products", "Product", "ASCENDING"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
                assertThat(table.getRowSorter()).isNotSameAs(sorter);
                assertThat(table.getRowSorter().getSortKeys())
                        .isEqualTo(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));

                mode.set(0);
                bridge.sortTable("products", "Product", "DESCENDING");
                assertThat(table.getValueAt(0, 0)).isEqualTo("Roses");
                assertThat(table.getValueAt(1, 0)).isEqualTo("Dahlias");
                assertThat(table.getRowSorter().getSortKeys())
                        .isEqualTo(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
            });
        });
    }

    private static JComboBox<Choice> choices() {
        JComboBox<Choice> combo = new JComboBox<>(choiceModel());
        combo.setName("order-lines");
        return combo;
    }

    private static DefaultComboBoxModel<Choice> choiceModel() {
        return new DefaultComboBoxModel<>(new Choice[]{
                new Choice(1, "Tulips"), new Choice(2, "Roses")});
    }

    private static void chooseRoses(SwingHarnessBridge bridge, boolean byIndex) {
        if (byIndex) {
            bridge.chooseOptionIndex("order-lines", 1);
        } else {
            bridge.chooseOption("order-lines", "Roses");
        }
    }

    private static void exercise(JComponent root, Consumer<SwingHarnessBridge> assertions) {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, title -> false);
        try {
            assertions.accept(bridge);
        } finally {
            bridge.detach();
        }
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> result = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(result);
        return result.get();
    }

    private record Choice(int id, String label) {
        @Override public String toString() { return label; }
    }
}

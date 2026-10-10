package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.PlainDocument;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingObservationContextTest {

    @Test
    void reusesAnUnchangedTokenAndDoesNotRevokeItForAnUnknownSuppliedToken() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JPanel root = new JPanel();
            root.add(new JButton("Save"));
            try {
                String token = context.observe(root);
                assertThat(token).matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
                assertThat(context.observe(root)).isEqualTo(token);
                assertThat(context.matches("unknown", root)).isFalse();
                assertThat(context.matches(token, root)).isTrue();
                context.clear();
                assertThat(context.matches(token, root)).isFalse();
                assertThat(context.observe(root)).isNotEqualTo(token);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void neverRevivesARejectedTokenWhenTheOldRootReturns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JPanel original = new JPanel();
            original.add(new JButton("Save"));
            JPanel modal = new JPanel();
            modal.add(new JButton("Save"));
            try {
                String token = context.observe(original);
                assertThat(context.matches(token, modal)).isFalse();
                assertThat(context.matches(token, original)).isFalse();
                assertThat(context.observe(original)).isNotEqualTo(token);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void bindsComponentIdentityPropertiesAndProductionListeners() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JPanel root = new JPanel();
            JButton button = new JButton("Save");
            root.add(button);
            try {
                rejectsChange(context, root, () -> button.setEnabled(false));
                rejectsChange(context, root, () -> button.setEnabled(true));
                rejectsChange(context, root, () -> button.setName("save.order"));
                rejectsChange(context, root,
                        () -> button.getAccessibleContext().setAccessibleName("Save order"));
                rejectsChange(context, root, () -> button.addActionListener(event -> { }));
                rejectsChange(context, root, () -> button.setVisible(false));
                rejectsChange(context, root, () -> button.setVisible(true));
                rejectsChange(context, root, () -> {
                    root.remove(button);
                    root.add(new JButton("Save"));
                });
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void documentRevisionsInvalidateEvenWhenPrivateTextIsRestored() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JTextField text = new JTextField("private customer note");
            try {
                String token = context.observe(text);
                text.setText("edited private note");
                text.setText("private customer note");
                assertThat(context.matches(token, text)).isFalse();
                assertThat(token).doesNotContain("private", "customer", "note");
                rejectsChange(context, text, () -> text.setDocument(new PlainDocument()));
                rejectsChange(context, text, () -> text.addActionListener(event -> { }));
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void tableContextsTrackModelAndSelectionEventsWithoutReadingRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            AtomicInteger cellReads = new AtomicInteger();
            AbstractTableModel model = new AbstractTableModel() {
                @Override public int getRowCount() { return 1_000_000; }
                @Override public int getColumnCount() { return 1; }
                @Override
                public Object getValueAt(int row, int column) {
                    cellReads.incrementAndGet();
                    throw new AssertionError("Observation must not inspect table rows");
                }
            };
            JTable table = new JTable(model);
            try {
                rejectsChange(context, table, () -> model.fireTableRowsUpdated(42, 42));
                rejectsChange(context, table, () -> table.setRowSelectionInterval(8, 8));
                rejectsChange(context, table,
                        () -> table.setSelectionModel(new DefaultListSelectionModel()));
                rejectsChange(context, table,
                        () -> table.getColumnModel().getColumn(0).setHeaderValue("Changed column"));
                assertThat(cellReads).hasValue(0);
                rejectsChange(context, table, () -> table.setModel(new DefaultTableModel(0, 1)));
                TableRowSorter<DefaultTableModel> sorter =
                        new TableRowSorter<>((DefaultTableModel) table.getModel());
                rejectsChange(context, table, () -> table.setRowSorter(sorter));
                rejectsChange(context, table, () -> sorter.toggleSortOrder(0));
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void firstObservationOfAFreshTableIsStableWithoutReadingCells() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            AtomicInteger cellReads = new AtomicInteger();
            JTable table = tableWithoutCellReads(cellReads, Object.class);
            try {
                String token = context.observe(table);
                assertThat(context.matches(token, table)).isTrue();
                assertThat(context.observe(table)).isEqualTo(token);
                assertThat(cellReads).hasValue(0);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void bindsDefaultTableRenderersByColumnClassWithoutReadingCells() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            AtomicInteger cellReads = new AtomicInteger();
            JTable table = tableWithoutCellReads(cellReads, Object.class, Integer.class);
            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
            table.setDefaultRenderer(Number.class, new DefaultTableCellRenderer());
            try {
                context.observe(table);
                String token = context.observe(table);
                assertThat(context.observe(table)).isEqualTo(token);
                assertThat(context.matches(token, table)).isTrue();
                rejectsChange(context, table,
                        () -> table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()));
                // Integer columns inherit the Number renderer from the default registry.
                rejectsChange(context, table,
                        () -> table.setDefaultRenderer(Number.class, new DefaultTableCellRenderer()));
                assertThat(cellReads).hasValue(0);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void ignoresUnusedDefaultRenderersUntilAColumnOverrideIsRemoved() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            AtomicInteger cellReads = new AtomicInteger();
            JTable table = tableWithoutCellReads(cellReads, Object.class);
            var column = table.getColumnModel().getColumn(0);
            column.setCellRenderer(new DefaultTableCellRenderer());
            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
            try {
                context.observe(table);
                String token = context.observe(table);
                table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
                assertThat(context.matches(token, table)).isTrue();
                assertThat(context.observe(table)).isEqualTo(token);
                rejectsChange(context, table,
                        () -> column.setCellRenderer(new DefaultTableCellRenderer()));
                rejectsChange(context, table, () -> column.setCellRenderer(null));
                rejectsChange(context, table,
                        () -> table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()));
                assertThat(cellReads).hasValue(0);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void bindsListComboSpinnerSliderAndButtonModels() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            DefaultListModel<String> rows = new DefaultListModel<>();
            rows.addElement("Rose");
            JList<String> list = new JList<>(rows);
            DefaultComboBoxModel<String> options = new DefaultComboBoxModel<>(new String[]{"stems"});
            JComboBox<String> combo = new JComboBox<>(options);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
            JSlider slider = new JSlider();
            JButton button = new JButton("Save");
            JPanel root = new JPanel();
            root.add(list);
            root.add(combo);
            root.add(spinner);
            root.add(slider);
            root.add(button);
            try {
                rejectsChange(context, root, () -> rows.setElementAt("Dahlia", 0));
                rejectsChange(context, root, () -> list.setSelectedIndex(0));
                rejectsChange(context, root, () -> list.setModel(new DefaultListModel<>()));
                rejectsChange(context, root, () -> options.addElement("bunches"));
                rejectsChange(context, root, () -> combo.setSelectedIndex(1));
                rejectsChange(context, root,
                        () -> combo.setModel(new DefaultComboBoxModel<>(new String[]{"bunches"})));
                rejectsChange(context, root, () -> spinner.setValue(2));
                rejectsChange(context, root, () -> slider.setValue(20));
                rejectsChange(context, root, () -> slider.setModel(new DefaultBoundedRangeModel()));
                rejectsChange(context, root, () -> button.getModel().setSelected(true));
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void watchesSelectedTabsAndLogicalMenuCommands() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JTabbedPane tabs = new JTabbedPane();
            JTextField hiddenField = new JTextField("Unselected content");
            tabs.addTab("Current", new JPanel());
            tabs.addTab("Other", hiddenField);
            JMenuItem command = new JMenuItem("Export");
            JMenu menu = new JMenu("Farm");
            menu.add(command);
            JMenuBar bar = new JMenuBar();
            bar.add(menu);
            JPanel root = new JPanel();
            root.add(tabs);
            root.add(bar);
            try {
                String token = context.observe(root);
                hiddenField.setText("Unobserved edit");
                assertThat(context.matches(token, root)).isTrue();
                rejectsChange(context, root, () -> tabs.setSelectedIndex(1));
                rejectsChange(context, root, () -> tabs.setEnabledAt(0, false));
                rejectsChange(context, root, () -> command.addActionListener(event -> { }));
                rejectsChange(context, root, () -> command.setEnabled(false));
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void scrollbarModelsInvalidateEvenWhenTheScrollPositionIsRestored() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JScrollBar scrollbar = new JScrollBar(JScrollBar.VERTICAL, 0, 10, 0, 100);
            scrollbar.getAccessibleContext();
            DefaultBoundedRangeModel original = (DefaultBoundedRangeModel) scrollbar.getModel();
            int listeners = original.getChangeListeners().length;
            try {
                String token = context.observe(scrollbar);
                assertThat(original.getChangeListeners()).hasSize(listeners + 1);
                scrollbar.setValue(40);
                scrollbar.setValue(0);
                assertThat(context.matches(token, scrollbar)).isFalse();
                token = context.observe(scrollbar);
                scrollbar.setModel(new DefaultBoundedRangeModel(0, 10, 0, 100));
                int oldListenersBeforeCleanup = original.getChangeListeners().length;
                assertThat(context.matches(token, scrollbar)).isFalse();
                assertThat(original.getChangeListeners()).hasSize(oldListenersBeforeCleanup - 1);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void viewportChangesInvalidateAndClearReleasesTheChangeListener() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JViewport viewport = new JViewport();
            viewport.setView(new JPanel());
            viewport.setViewSize(new Dimension(400, 800));
            viewport.setExtentSize(new Dimension(100, 100));
            viewport.getAccessibleContext();
            int listeners = viewport.getChangeListeners().length;
            try {
                String token = context.observe(viewport);
                assertThat(viewport.getChangeListeners()).hasSize(listeners + 1);
                viewport.setViewPosition(new Point(0, 100));
                viewport.setViewPosition(new Point(0, 0));
                assertThat(context.matches(token, viewport)).isFalse();
                token = context.observe(viewport);
                viewport.setExtentSize(new Dimension(90, 90));
                viewport.setExtentSize(new Dimension(100, 100));
                assertThat(context.matches(token, viewport)).isFalse();
                context.clear();
                assertThat(viewport.getChangeListeners()).hasSize(listeners);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void bindsShortcutMapsAndMappedActionsWithoutComponentPropertyNotifications() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            JPanel root = new JPanel();
            KeyStroke shortcut = KeyStroke.getKeyStroke("F5");
            AbstractAction first = action();
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, "refresh");
            root.getActionMap().put("refresh", first);
            try {
                rejectsChange(context, root, () -> first.setEnabled(false));
                rejectsChange(context, root, () -> root.getActionMap().put("refresh", action()));
                rejectsChange(context, root, () -> root.getInputMap(
                        JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, "replacement"));
                rejectsChange(context, root, () -> root.setActionMap(new ActionMap()));
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void detachesRemovedModelsAndClearReleasesAllOwnedListeners() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingObservationContext context = new SwingObservationContext();
            DefaultListModel<String> rows = new DefaultListModel<>();
            JList<String> list = new JList<>(rows);
            list.getAccessibleContext();
            JTextField field = new JTextField();
            field.getAccessibleContext();
            AbstractDocument document = (AbstractDocument) field.getDocument();
            JPanel root = new JPanel();
            root.add(list);
            root.add(field);
            int listListeners = rows.getListDataListeners().length;
            int documentListeners = document.getDocumentListeners().length;
            int rootListeners = root.getPropertyChangeListeners().length;
            try {
                String token = context.observe(root);
                assertThat(rows.getListDataListeners()).hasSize(listListeners + 1);
                assertThat(document.getDocumentListeners()).hasSize(documentListeners + 1);
                root.remove(list);
                assertThat(context.matches(token, root)).isFalse();
                assertThat(rows.getListDataListeners()).hasSize(listListeners);
                context.clear();
                assertThat(document.getDocumentListeners()).hasSize(documentListeners);
                assertThat(root.getPropertyChangeListeners()).hasSize(rootListeners);
            } finally {
                context.clear();
            }
        });
    }

    @Test
    void requiresTheEventDispatchThread() {
        SwingObservationContext context = new SwingObservationContext();
        assertThatThrownBy(() -> context.observe(new JPanel())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> context.matches("unknown", new JPanel()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(context::clear).isInstanceOf(IllegalStateException.class);
    }

    private static JTable tableWithoutCellReads(AtomicInteger cellReads, Class<?>... columnClasses) {
        return new JTable(new AbstractTableModel() {
            @Override public int getRowCount() { return 1_000_000; }
            @Override public int getColumnCount() { return columnClasses.length; }
            @Override public Class<?> getColumnClass(int column) { return columnClasses[column]; }
            @Override
            public Object getValueAt(int row, int column) {
                cellReads.incrementAndGet();
                throw new AssertionError("Observation must not inspect table rows");
            }
        });
    }

    private static AbstractAction action() {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { }
        };
    }

    private static void rejectsChange(
            SwingObservationContext context, JComponent root, Runnable mutation) {
        String token = context.observe(root);
        mutation.run();
        assertThat(context.matches(token, root)).isFalse();
        assertThat(context.observe(root)).isNotEqualTo(token);
    }
}

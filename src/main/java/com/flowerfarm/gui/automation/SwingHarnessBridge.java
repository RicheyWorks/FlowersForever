package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessControl;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessSnapshot;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultRowSorter;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RootPaneContainer;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;

/** Live, non-static attachment point between the desktop window and automation plugins. */
@org.springframework.stereotype.Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class SwingHarnessBridge {

    private static final int MAX_TEXT_CHARS = 8000;
    private static final int MAX_SCREEN_DIMENSION = 2048;
    private static final int MAX_SCREEN_PIXELS = 2_000_000;
    private static final int MAX_SCREEN_PNG_BYTES = 4 * 1024 * 1024;

    private volatile Attachment attachment;
    private final SwingActionJournal actions;
    private final Function<JComponent, JComponent> activeRootResolver;

    public SwingHarnessBridge() {
        this(SwingHarnessBridge::ownedModalRoot);
    }

    SwingHarnessBridge(Function<JComponent, JComponent> activeRootResolver) {
        this.activeRootResolver = Objects.requireNonNull(activeRootResolver, "activeRootResolver");
        actions = new SwingActionJournal();
    }

    /** An execution view pins target resolution without holding a lock through modal callbacks. */
    private SwingHarnessBridge(Attachment current, JComponent active, SwingActionJournal actions) {
        attachment = current;
        activeRootResolver = ignored -> active;
        this.actions = actions;
    }

    HarnessExecution dispatch(HarnessCommand command, Function<SwingHarnessBridge, HarnessExecution> action) {
        Optional<HarnessExecution> replay = actions.replay(command);
        if (replay.isPresent()) {
            return replay.get();
        }
        String expectedContext = expectedContext(command);
        Attachment current = requireAttachment();
        return actions.dispatch(command, () -> {
            requireCurrentAttachment(current, expectedContext);
            JComponent active = activeRoot(current);
            if (expectedContext != null
                    && !current.context().matches(expectedContext, active)) {
                throw new DesktopContextChangedException();
            }
            synchronized (this) {
                // Attachment replacement is allowed off the EDT. Linearize the start,
                // then keep every target lookup on the reviewed attachment/root.
                requireCurrentAttachment(current, expectedContext);
            }
            return action.apply(new SwingHarnessBridge(current, active, actions));
        });
    }

    private void requireCurrentAttachment(Attachment current, String expectedContext) {
        if (attachment != current) {
            if (expectedContext != null) {
                throw new DesktopContextChangedException();
            }
            throw new IllegalStateException("Desktop attachment changed before dispatch");
        }
    }

    private static String expectedContext(HarnessCommand command) {
        if (!command.arguments().containsKey("expected-context")) {
            return null;
        }
        Object value = command.arguments().get("expected-context");
        if (!(value instanceof String token) || token.isBlank() || token.length() > 160) {
            throw new IllegalArgumentException(
                    "expected-context must be a non-blank string of at most 160 characters");
        }
        return token;
    }

    Optional<HarnessExecution> replay(HarnessCommand command) {
        return actions.replay(command);
    }

    HarnessExecution actionStatus(String requestId) {
        return actions.status(requestId);
    }

    public synchronized void attach(JComponent root, Function<String, Boolean> navigation) {
        Attachment previous = attachment;
        attachment = new Attachment(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(navigation, "navigation"), new SwingObservationContext());
        clearContext(previous);
    }

    public synchronized void detach() {
        Attachment previous = attachment;
        attachment = null;
        clearContext(previous);
    }

    private static void clearContext(Attachment previous) {
        if (previous == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            previous.context().clear();
        } else {
            SwingUtilities.invokeLater(previous.context()::clear);
        }
    }

    public HarnessSnapshot snapshot(String pluginId) {
        Attachment current = attachment;
        if (current == null) {
            return new HarnessSnapshot(pluginId, Instant.now(), List.of(), List.of(),
                    Map.of("ready", false, "reason", "Desktop window is not attached"));
        }
        try {
            return onEdtBounded(() -> snapshot(current, pluginId));
        } catch (DesktopBusyException exception) {
            return new HarnessSnapshot(pluginId, Instant.now(), List.of(), List.of(),
                    Map.of("ready", false, "reason", "Desktop event loop is busy",
                            "pendingActions", actions.pendingIds()));
        }
    }

    private HarnessSnapshot snapshot(Attachment current, String pluginId) {
        if (attachment != current) {
            return changedObservation(current, pluginId);
        }
        JComponent active = activeRoot(current);
        if (!hasCurrentRoot(current, active)) {
            return changedObservation(current, pluginId);
        }
        // Initialize lazy Swing state and bind identities before describing controls.
        String context = current.context().observe(active);
        if (!hasCurrentRoot(current, active)) {
            return changedObservation(current, pluginId);
        }
        List<RawControl> raw = new ArrayList<>();
        collectInteractive(active, raw);
        List<HarnessControl> controls = raw.stream()
                .map(control -> observedControl(active, control))
                .toList();
        long enabled = controls.stream().filter(HarnessControl::enabled).count();
        long ambiguous = controls.stream().filter(control -> !control.commandable()).count();
        List<List<String>> routes = active == current.root() ? routeCatalog(current.root()) : List.of();
        Map<String, Object> metadata = new LinkedHashMap<>(Map.of(
                "ready", true, "controlCount", controls.size(),
                "enabledControlCount", enabled, "unaddressableControlCount", ambiguous,
                "routeCount", routes.size(), "routes", routes,
                "menuPaths", menuPaths(active), "fieldValuesIncluded", false));
        metadata.put("modalActive", active != current.root());
        metadata.put("pendingActions", actions.pendingIds());
        List<RawControl> supplemental = new ArrayList<>();
        collectSupplemental(active, supplemental);
        List<HarnessControl> readable = supplemental.stream()
                .map(control -> observedControl(active, control))
                .filter(HarnessControl::commandable).toList();
        metadata.put("readableControls", readable.stream()
                .filter(control -> !isLayoutKind(control.kind())).toList());
        metadata.put("layoutControls", readable.stream()
                .filter(control -> isLayoutKind(control.kind())).toList());
        List<String> selectedRoute = route(current.root());
        if (!hasCurrentRoot(current, active) || !current.context().matches(context, active)
                || attachment != current) {
            return changedObservation(current, pluginId);
        }
        metadata.put("contextToken", context);
        return new HarnessSnapshot(pluginId, Instant.now(), selectedRoute, controls, metadata);
    }

    private static HarnessSnapshot changedObservation(Attachment current, String pluginId) {
        // Capture callbacks can reattach synchronously, after the old context was cleared.
        // Release any subscriptions that the interrupted capture installed afterward.
        current.context().clear();
        return new HarnessSnapshot(pluginId, Instant.now(), List.of(), List.of(),
                Map.of("ready", false, "reason", "Desktop changed during observation"));
    }

    public void navigate(String tabTitle) {
        Attachment current = requireAttachment();
        boolean selected = onEdt(() -> {
            requireMainWindow(current);
            return current.navigation().apply(tabTitle);
        });
        if (!selected) {
            throw new IllegalArgumentException("No desktop tab named '" + tabTitle + "'");
        }
    }

    /** Opens one exact primary/nested route published by snapshot metadata. */
    public void navigateRoute(List<String> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("A non-empty desktop route is required");
        }
        List<String> routePath = path.stream().map(step -> {
            if (step == null || step.isBlank()) {
                throw new IllegalArgumentException("Desktop route steps must not be blank");
            }
            return step.trim();
        }).toList();
        Attachment current = requireAttachment();
        onEdt(() -> {
            requireMainWindow(current);
            List<List<String>> available = routeCatalog(current.root());
            if (!available.contains(routePath)) {
                throw new IllegalArgumentException("No desktop route '"
                        + String.join(" > ", routePath) + "'. Available routes: " + available);
            }
            List<TabTarget> targets = structuralRouteTargets(
                    current.root(), routePath, 0);
            if (targets.size() != routePath.size()) {
                throw new IllegalArgumentException("Desktop route is structurally ambiguous: "
                        + routePath);
            }
            for (int index = 0; index < targets.size(); index++) {
                TabTarget target = targets.get(index);
                requireEnabled(target.tabs(), "Tab set", routePath.get(index));
                if (!target.tabs().isEnabledAt(target.index())) {
                    throw new IllegalArgumentException("Tab '" + routePath.get(index)
                            + "' is disabled");
                }
            }
            if (!current.navigation().apply(routePath.get(0))) {
                throw new IllegalArgumentException("No desktop tab named '"
                        + routePath.get(0) + "'");
            }
            for (int depth = 1; depth < targets.size(); depth++) {
                TabTarget target = targets.get(depth);
                requireEnabled(target.tabs(), "Tab set", routePath.get(depth));
                if (!target.tabs().isEnabledAt(target.index())) {
                    throw new IllegalArgumentException("Tab '" + routePath.get(depth)
                            + "' is disabled");
                }
                target.tabs().setSelectedIndex(target.index());
            }
            List<String> selected = route(current.root());
            if (!selected.equals(routePath)) {
                throw new IllegalStateException("Desktop selected route " + selected
                        + " after requesting " + routePath);
            }
            return null;
        });
    }

    public void replaceText(String selector, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Text value is required");
        }
        if (value.length() > 8000) {
            throw new IllegalArgumentException("Text value must contain at most 8000 characters");
        }
        JTextComponent field = unique(selector, JTextComponent.class);
        onEdt(() -> {
            if (field instanceof JPasswordField) {
                throw new IllegalArgumentException(
                        "Password fields are never writable through the automation harness");
            }
            if (!field.isEnabled() || !field.isEditable()) {
                throw new IllegalArgumentException("Field '" + selector + "' is not editable");
            }
            field.setText(value);
            if (!field.getText().equals(value)) {
                throw new IllegalStateException("Field did not retain the requested replacement text");
            }
            return null;
        });
    }

    /** Changes a bounded segment through the field's normal document/filter path. */
    public void editText(String selector, int offset, int length, String value) {
        if (value == null || value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("Replacement text must contain at most 8000 characters");
        }
        onEdt(() -> {
            JTextComponent field = unique(selector, JTextComponent.class);
            if (field instanceof JPasswordField || !field.isEditable() || !field.isEnabled()) {
                throw new IllegalArgumentException("Field is not editable through the harness");
            }
            Document document = field.getDocument();
            int size = document.getLength();
            if (offset < 0 || length < 0 || (long) offset + length > size
                    || (long) size - length + value.length() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Text edit range is outside the current document");
            }
            if (document instanceof AbstractDocument abstractDocument) {
                abstractDocument.replace(offset, length, value, null);
            } else {
                document.remove(offset, length);
                document.insertString(offset, value, null);
            }
            if (field.getDocument() != document
                    || document.getLength() != size - length + value.length()
                    || !document.getText(offset, value.length()).equals(value)) {
                throw new IllegalStateException("Field did not retain the requested text segment");
            }
            return null;
        });
    }

    public void chooseOption(String selector, String displayedValue) {
        if (displayedValue == null || displayedValue.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("Option text is required and must contain at most "
                    + MAX_TEXT_CHARS + " characters");
        }
        JComboBox<?> combo = unique(selector, JComboBox.class);
        onEdt(() -> {
            requireEnabled(combo, "Selector", selector);
            SwingPasswordGuard.requireNoPasswordEditor(combo);
            int match = matchingOptionIndex(combo, displayedValue);
            // A renderer callback can replace the editor while matching labels.
            SwingPasswordGuard.requireNoPasswordEditor(combo);
            if (match < 0) {
                if (!combo.isEditable()) {
                    throw new IllegalArgumentException(
                            "No option displayed as '" + displayedValue + "'");
                }
                Component editor = combo.getEditor().getEditorComponent();
                if (!editor.isEnabled() || editor instanceof JTextComponent field && !field.isEditable()) {
                    throw new IllegalArgumentException("Selector editor '" + selector + "' is not editable");
                }
                combo.getEditor().setItem(displayedValue);
                Object requested = combo.getEditor().getItem();
                if (editor instanceof JTextComponent field && !displayedValue.equals(field.getText())) {
                    throw new IllegalStateException("Selector editor did not retain the requested text");
                }
                combo.setSelectedItem(requested);
                if (!Objects.equals(combo.getSelectedItem(), requested)) {
                    throw new IllegalStateException("Selector did not retain the requested custom option");
                }
            } else {
                Object requested = combo.getItemAt(match);
                combo.setSelectedIndex(match);
                if (!Objects.equals(combo.getSelectedItem(), requested)) {
                    throw new IllegalStateException("Selector did not retain the requested option");
                }
            }
            return null;
        });
    }

    /** Selects an exact observed option even when displayed labels are repeated. */
    public void chooseOptionIndex(String selector, int index) {
        onEdt(() -> {
            JComboBox<?> combo = unique(selector, JComboBox.class);
            requireEnabled(combo, "Selector", selector);
            if (index < 0 || index >= combo.getItemCount()) {
                throw new IllegalArgumentException("Option index is outside the selector's current model");
            }
            Object requested = combo.getItemAt(index);
            SwingPasswordGuard.requireNoPasswordEditor(combo);
            combo.setSelectedIndex(index);
            if (combo.getSelectedIndex() != index || !Objects.equals(combo.getSelectedItem(), requested)) {
                throw new IllegalStateException("Selector did not retain the requested option index");
            }
            return null;
        });
    }

    public void setToggle(String selector, boolean selected) {
        onEdt(() -> {
            SwingControlActions.setToggle(unique(selector, AbstractButton.class), selected);
            return null;
        });
    }

    void setToggle(HarnessCommand command, boolean selected) {
        onEdt(() -> {
            ControlTarget target = controlTarget(command, true);
            if (!(target.component() instanceof AbstractButton button)) {
                throw new IllegalArgumentException("The control is not a toggle");
            }
            SwingControlActions.setToggle(button, selected);
            return null;
        });
    }

    public void scroll(String selector, String axis, int value) {
        onEdt(() -> {
            SwingControlActions.scroll(layoutTarget(selector, JScrollPane.class), axis, value);
            return null;
        });
    }

    public void setDivider(String selector, int value) {
        onEdt(() -> {
            SwingControlActions.setDivider(layoutTarget(selector, JSplitPane.class), value);
            return null;
        });
    }

    public void clearSelection(String selector) {
        onEdt(() -> {
            JComponent control = unique(selector, JComponent.class);
            requireEnabled(control, "Control", selector);
            if (control instanceof JTable table) {
                table.clearSelection();
                if (!table.getSelectionModel().isSelectionEmpty()
                        || !table.getColumnModel().getSelectionModel().isSelectionEmpty()) {
                    throw new IllegalStateException("Table did not retain the cleared selection");
                }
            } else if (control instanceof JList<?> list) {
                list.clearSelection();
                if (!list.isSelectionEmpty()) {
                    throw new IllegalStateException("List did not retain the cleared selection");
                }
            } else {
                throw new IllegalArgumentException("Selection can only be cleared on a table or list");
            }
            return null;
        });
    }

    private <T extends JComponent> T layoutTarget(String selector, Class<T> type) {
        Component candidate = unique(selector, JComponent.class);
        JComponent active = activeRoot(requireAttachment());
        while (candidate != null) {
            if (type.isInstance(candidate)) {
                return type.cast(candidate);
            }
            if (candidate == active) {
                break;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalArgumentException("No active " + type.getSimpleName()
                + " contains control '" + selector + "'");
    }

    /** Completes the active application chooser through its normal approval callback. */
    public void chooseFile(String selector, String path) {
        if (path == null || path.isBlank() || path.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("A bounded absolute file path is required");
        }
        Path selected = Path.of(path).normalize();
        if (!selected.isAbsolute()) {
            throw new IllegalArgumentException("The selected file path must be absolute");
        }
        JFileChooser chooser = unique(selector, JFileChooser.class);
        onEdt(() -> {
            requireEnabled(chooser, "File chooser", selector);
            boolean directory = Files.isDirectory(selected);
            if (chooser.getFileSelectionMode() == JFileChooser.FILES_ONLY && directory
                    || chooser.getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY && !directory) {
                throw new IllegalArgumentException("The selected path has the wrong file type");
            }
            if (chooser.getDialogType() == JFileChooser.OPEN_DIALOG && !Files.exists(selected)) {
                throw new IllegalArgumentException("The selected input path does not exist");
            }
            if (!directory && chooser.getFileFilter() != null
                    && !chooser.getFileFilter().accept(selected.toFile())) {
                throw new IllegalArgumentException("The selected file does not match the active filter");
            }
            chooser.setSelectedFile(selected.toFile());
            chooser.approveSelection();
            return null;
        });
    }

    public void cancelFileDialog(String selector) {
        JFileChooser chooser = unique(selector, JFileChooser.class);
        onEdt(() -> {
            requireEnabled(chooser, "File chooser", selector);
            chooser.cancelSelection();
            return null;
        });
    }

    public void selectTab(String selector, String tabTitle) {
        JTabbedPane tabs = unique(selector, JTabbedPane.class);
        onEdt(() -> {
            requireEnabled(tabs, "Tab set", selector);
            int match = -1;
            for (int index = 0; index < tabs.getTabCount(); index++) {
                if (tabTitle.equals(tabs.getTitleAt(index))) {
                    if (match >= 0) {
                        throw new IllegalArgumentException(
                                "Several tabs are titled '" + tabTitle + "'");
                    }
                    match = index;
                }
            }
            if (match < 0) {
                throw new IllegalArgumentException("No tab titled '" + tabTitle + "'");
            }
            if (!tabs.isEnabledAt(match)) {
                throw new IllegalArgumentException("Tab '" + tabTitle + "' is disabled");
            }
            Component requested = tabs.getComponentAt(match);
            tabs.setSelectedIndex(match);
            if (tabs.getSelectedComponent() != requested) {
                throw new IllegalStateException("Tab set did not retain the requested tab");
            }
            return null;
        });
    }

    public void selectRow(String tableSelector, int viewRow) {
        JTable table = unique(tableSelector, JTable.class);
        onEdt(() -> {
            requireEnabled(table, "Table", tableSelector);
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                throw new IllegalArgumentException("Row " + viewRow + " is outside table '"
                        + tableSelector + "' with " + table.getRowCount() + " row(s)");
            }
            table.setRowSelectionInterval(viewRow, viewRow);
            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
            requireSelectedRows(table, List.of(viewRow));
            return null;
        });
    }

    public void selectRows(String tableSelector, List<Integer> viewRows) {
        if (viewRows == null || viewRows.isEmpty()) {
            throw new IllegalArgumentException("At least one table row is required");
        }
        JTable table = unique(tableSelector, JTable.class);
        onEdt(() -> {
            requireEnabled(table, "Table", tableSelector);
            for (Integer viewRow : viewRows) {
                if (viewRow == null || viewRow < 0 || viewRow >= table.getRowCount()) {
                    throw new IllegalArgumentException("Row " + viewRow
                            + " is outside table '" + tableSelector + "' with "
                            + table.getRowCount() + " row(s)");
                }
            }
            List<Integer> rows = viewRows.stream().distinct().sorted().toList();
            ListSelectionModel selection = table.getSelectionModel();
            if (rows.size() > 1 && selection.getSelectionMode()
                    == ListSelectionModel.SINGLE_SELECTION) {
                throw new IllegalArgumentException(
                        "Table '" + tableSelector + "' permits only one selected row");
            }
            if (selection.getSelectionMode() == ListSelectionModel.SINGLE_INTERVAL_SELECTION
                    && rows.get(rows.size() - 1) - rows.get(0) + 1 != rows.size()) {
                throw new IllegalArgumentException(
                        "Table '" + tableSelector + "' permits only contiguous selected rows");
            }
            boolean adjusting = selection.getValueIsAdjusting();
            selection.setValueIsAdjusting(true);
            try {
                table.clearSelection();
                for (Integer viewRow : rows) {
                    table.addRowSelectionInterval(viewRow, viewRow);
                }
            } finally {
                selection.setValueIsAdjusting(adjusting);
            }
            int finalRow = viewRows.get(viewRows.size() - 1);
            table.scrollRectToVisible(table.getCellRect(finalRow, 0, true));
            requireSelectedRows(table, rows);
            return null;
        });
    }

    public void sortTable(String tableSelector, String columnTitle, String direction) {
        JTable table = unique(tableSelector, JTable.class);
        onEdt(() -> {
            requireEnabled(table, "Table", tableSelector);
            RowSorter<?> sorter = table.getRowSorter();
            if (sorter == null) {
                throw new IllegalArgumentException(
                        "Table '" + tableSelector + "' is not sortable");
            }
            int modelColumn = -1;
            for (int viewColumn = 0; viewColumn < table.getColumnCount(); viewColumn++) {
                if (columnTitle.equals(SwingControlReader.tableColumnTitle(table, viewColumn))) {
                    if (modelColumn >= 0) {
                        throw new IllegalArgumentException(
                                "Several columns are titled '" + columnTitle + "'");
                    }
                    modelColumn = table.convertColumnIndexToModel(viewColumn);
                }
            }
            if (modelColumn < 0) {
                throw new IllegalArgumentException(
                        "No column titled '" + columnTitle + "' in '" + tableSelector + "'");
            }
            if (sorter instanceof DefaultRowSorter<?, ?> defaultSorter
                    && !defaultSorter.isSortable(modelColumn)) {
                throw new IllegalArgumentException("Column '" + columnTitle
                        + "' in '" + tableSelector + "' is not sortable");
            }
            SortOrder order;
            try {
                order = SortOrder.valueOf(direction.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Sort direction must be ASCENDING or DESCENDING", exception);
            }
            if (order == SortOrder.UNSORTED) {
                throw new IllegalArgumentException(
                        "Sort direction must be ASCENDING or DESCENDING");
            }
            List<RowSorter.SortKey> requested = List.of(new RowSorter.SortKey(modelColumn, order));
            sorter.setSortKeys(requested);
            if (table.getRowSorter() != sorter || !sorter.getSortKeys().equals(requested)) {
                throw new IllegalStateException("Table did not retain the requested sort order");
            }
            return null;
        });
    }

    public void selectListRow(String selector, int row) {
        JList<?> list = unique(selector, JList.class);
        onEdt(() -> {
            requireEnabled(list, "List", selector);
            if (row < 0 || row >= list.getModel().getSize()) {
                throw new IllegalArgumentException("Row " + row + " is outside list '"
                        + selector + "' with " + list.getModel().getSize() + " row(s)");
            }
            list.setSelectedIndex(row);
            list.ensureIndexIsVisible(row);
            if (!Arrays.equals(list.getSelectedIndices(), new int[]{row})) {
                throw new IllegalStateException("List did not retain the requested row selection");
            }
            return null;
        });
    }

    public void setSpinner(String selector, String displayedValue) {
        JSpinner spinner = unique(selector, JSpinner.class);
        onEdt(() -> {
            SwingSpinnerActions.setValue(spinner, displayedValue);
            return null;
        });
    }

    public void setSlider(String selector, int value) {
        JSlider slider = unique(selector, JSlider.class);
        onEdt(() -> {
            if (!slider.isEnabled()) {
                throw new IllegalArgumentException("Slider '" + selector + "' is disabled");
            }
            if (value < slider.getMinimum() || value > slider.getMaximum()) {
                throw new IllegalArgumentException("Value " + value + " is outside slider '"
                        + selector + "' range " + slider.getMinimum() + ".."
                        + slider.getMaximum());
            }
            slider.setValue(value);
            if (slider.getValue() != value) {
                throw new IllegalStateException("Slider did not retain the requested value");
            }
            return null;
        });
    }

    public void activateButton(String selector) {
        AbstractButton button = unique(selector, AbstractButton.class);
        onEdt(() -> {
            if (!button.isEnabled()) {
                throw new IllegalArgumentException("Button '" + selector + "' is disabled");
            }
            button.doClick();
            return null;
        });
    }

    public void submitField(String selector) {
        JTextField field = unique(selector, JTextField.class);
        onEdt(() -> {
            if (field instanceof JPasswordField) {
                throw new IllegalArgumentException(
                        "Password fields are never submitted through the automation harness");
            }
            if (!field.isEnabled() || !field.isEditable()) {
                throw new IllegalArgumentException("Field '" + selector + "' is not editable");
            }
            field.postActionEvent();
            return null;
        });
    }

    public void doubleClickRow(String tableSelector, int viewRow) {
        JTable table = unique(tableSelector, JTable.class);
        onEdt(() -> {
            requireTableRow(table, tableSelector, viewRow);
            TableModel model = table.getModel();
            int modelRow = table.convertRowIndexToModel(viewRow);
            boolean[] changed = {false};
            TableModelListener listener = event -> changed[0] = true;
            model.addTableModelListener(listener);
            Rectangle cell;
            try {
                table.setRowSelectionInterval(viewRow, viewRow);
                cell = table.getCellRect(viewRow, 0, true);
                table.scrollRectToVisible(cell);
                requireSelectedRows(table, List.of(viewRow));
                if (changed[0] || table.getModel() != model
                        || viewRow >= table.getRowCount() || table.convertRowIndexToModel(viewRow) != modelRow) {
                    throw new IllegalStateException("Table row changed before the double-click could be delivered");
                }
            } finally {
                model.removeTableModelListener(listener);
            }
            MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0,
                    cell.x + Math.max(1, cell.width / 2),
                    cell.y + Math.max(1, cell.height / 2),
                    2, false, MouseEvent.BUTTON1);
            table.dispatchEvent(event);
            return null;
        });
    }

    private static void requireSelectedRows(JTable table, List<Integer> requested) {
        if (!Arrays.stream(table.getSelectedRows()).boxed().toList().equals(requested)) {
            throw new IllegalStateException("Table did not retain the requested row selection");
        }
    }

    public void activateMenu(List<String> path) {
        Attachment current = requireAttachment();
        onEdt(() -> {
            SwingMenuTarget.resolve(activeRoot(current), path, true).item().doClick();
            return null;
        });
    }
    public void invokeShortcut(String shortcut) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcut);
        if (keyStroke == null) {
            throw new IllegalArgumentException(
                    "Shortcut '" + shortcut + "' is not a valid Swing keystroke");
        }
        Attachment current = requireAttachment();
        onEdt(() -> {
            SwingShortcutResolver.invoke(activeRoot(current), keyStroke);
            return null;
        });
    }

    public Map<String, Object> readControl(String selector) {
        return onEdtBounded(() -> SwingControlReader.read(
                unique(selector, JComponent.class), selector, null, null, null, null));
    }

    Map<String, Object> readControl(HarnessCommand command) {
        return readBound(command, target -> {
            ControlTarget control = target.controlTarget(command, false);
            Map<String, Object> result = new LinkedHashMap<>(SwingControlReader.read(
                    control.component(), control.selector(),
                    optionalInteger(command, "offset"), optionalInteger(command, "limit"),
                    optionalInteger(command, "column-offset"), optionalInteger(command, "column-limit")));
            result.put("enabled", control.enabled());
            return result;
        });
    }

    private ControlTarget controlTarget(HarnessCommand command, boolean requireEnabled) {
        boolean hasSelector = command.arguments().containsKey("selector");
        boolean hasPath = command.arguments().containsKey("path");
        if (hasSelector == hasPath) {
            throw new IllegalArgumentException("Provide exactly one of selector or menu path");
        }
        if (hasPath) {
            List<String> path = command.texts("path");
            SwingMenuTarget.Target target = SwingMenuTarget.resolve(
                    activeRoot(requireAttachment()), path, requireEnabled);
            return new ControlTarget(target.item(), String.join(" > ", path), target.enabled());
        }
        String selector = command.text("selector");
        JComponent component = unique(selector, JComponent.class);
        if (requireEnabled) {
            requireEnabled(component, "Control", selector);
        }
        return new ControlTarget(component, selector, component.isEnabled());
    }

    Map<String, Object> readScreen(HarnessCommand command) {
        return readBound(command, target -> {
            Integer offset = optionalInteger(command, "offset");
            Integer limit = optionalInteger(command, "limit");
            return SwingScreenReader.read(target.activeRoot(target.requireAttachment()),
                    offset == null ? 0 : offset, limit == null ? MAX_TEXT_CHARS : limit);
        });
    }

    Map<String, Object> readItem(HarnessCommand command) {
        return readBound(command, target -> {
            String selector = command.text("selector");
            return SwingControlReader.readItem(target.unique(selector, JComponent.class), selector,
                    command.integer("index"), optionalInteger(command, "column"),
                    optionalInteger(command, "offset"), optionalInteger(command, "limit"));
        });
    }

    Map<String, Object> readLayout(HarnessCommand command) {
        return readBound(command, target -> {
            String selector = command.text("selector");
            JComponent pane = switch (command.text("kind").toLowerCase(java.util.Locale.ROOT)) {
                case "scroll" -> target.layoutTarget(selector, JScrollPane.class);
                case "split" -> target.layoutTarget(selector, JSplitPane.class);
                default -> throw new IllegalArgumentException("Layout kind must be scroll or split");
            };
            return SwingControlReader.read(pane, selector, null, null, null, null);
        });
    }

    private Map<String, Object> readBound(HarnessCommand command,
                                         Function<SwingHarnessBridge, Map<String, Object>> read) {
        String expected = expectedContext(command);
        Attachment current = requireAttachment();
        return onEdtBounded(() -> {
            requireCurrentAttachment(current, expected);
            JComponent active = activeRoot(current);
            if (expected != null && !current.context().matches(expected, active)) {
                throw new DesktopContextChangedException();
            }
            String readContext = current.context().observe(active);
            Map<String, Object> output = new LinkedHashMap<>(
                    read.apply(new SwingHarnessBridge(current, active, actions)));
            requireCurrentAttachment(current, readContext);
            if (activeRoot(current) != active || !current.context().matches(readContext, active)) {
                throw new DesktopContextChangedException();
            }
            requireCurrentAttachment(current, readContext);
            output.put("contextToken", readContext);
            return Map.copyOf(output);
        });
    }

    private static Integer optionalInteger(HarnessCommand command, String name) {
        return command.arguments().containsKey(name) ? command.integer(name) : null;
    }

    public ScreenCapture captureScreen() {
        Attachment current = requireAttachment();
        return onEdtBounded(() -> {
            if (attachment != current) {
                throw new DesktopContextChangedException();
            }
            JComponent active = activeRoot(current);
            if (!hasCurrentRoot(current, active)) {
                throw new DesktopContextChangedException();
            }
            ScreenCapture result = capture(active);
            if (!hasCurrentRoot(current, active)) {
                throw new DesktopContextChangedException();
            }
            return result;
        });
    }

    private Attachment requireAttachment() {
        Attachment current = attachment;
        if (current == null) {
            throw new IllegalStateException("Desktop window is not attached");
        }
        return current;
    }

    private <T extends Component> T unique(String selector, Class<T> type) {
        Attachment current = requireAttachment();
        return onEdt(() -> {
            // Discovery and every action share one address space. A typed lookup
            // must not silently choose a button whose name also identifies a field.
            List<JComponent> matches = new ArrayList<>();
            collectMatches(activeRoot(current), selector, JComponent.class, matches);
            if (matches.size() != 1) {
                throw new IllegalArgumentException("Expected one visible "
                        + type.getSimpleName() + " named '" + selector + "' but found "
                        + matches.size());
            }
            JComponent target = matches.get(0);
            if (!type.isInstance(target)) {
                throw new IllegalArgumentException("Control '" + selector
                        + "' is not a " + type.getSimpleName());
            }
            return type.cast(target);
        });
    }

    private void requireMainWindow(Attachment current) {
        if (activeRoot(current) != current.root()) {
            throw new IllegalArgumentException("Close the active desktop dialog before navigating");
        }
    }

    private JComponent activeRoot(Attachment current) {
        return activeRootResolver.apply(current.root());
    }

    private boolean hasCurrentRoot(Attachment current, JComponent active) {
        return attachment == current && activeRoot(current) == active && attachment == current;
    }

    private static JComponent ownedModalRoot(JComponent root) {
        Window owner = SwingUtilities.getWindowAncestor(root);
        Window modal = owner == null ? null : activeOwnedModal(owner);
        return modal instanceof RootPaneContainer container ? container.getRootPane() : root;
    }

    static Window activeOwnedModal(Window owner) {
        Window candidate = null;
        for (Window child : owner.getOwnedWindows()) {
            if (!child.isShowing()) {
                continue;
            }
            Window nested = activeOwnedModal(child);
            if (nested != null) {
                return nested;
            }
            if (child instanceof Dialog dialog && dialog.isModal()) {
                if (child.isActive()) {
                    return child;
                }
                candidate = child;
            }
        }
        return candidate;
    }

    private static <T> T onEdtBounded(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return call(task);
        }
        FutureTask<T> pending = new FutureTask<>(task);
        SwingUtilities.invokeLater(pending);
        try {
            return pending.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(false);
            throw new DesktopBusyException();
        } catch (InterruptedException exception) {
            pending.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while observing Swing", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Swing observation failed", exception.getCause());
        }
    }

    private static final class DesktopBusyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private DesktopBusyException() {
            super("Desktop event loop is busy");
        }
    }

    private static <T extends Component> void collectMatches(
            Component component, String selector, Class<T> type, List<T> matches) {
        if (!component.isVisible()) {
            return;
        }
        if (type.isInstance(component) && matchesSelector(component, selector)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectMatches(child, selector, type, matches);
            }
        }
    }

    private static HarnessControl observedControl(JComponent root, RawControl control) {
        String label = control.component() instanceof AbstractButton button
                ? firstNonBlank(button.getText(), control.selector()) : control.selector();
        String componentName = control.component().getName();
        boolean named = componentName != null && !componentName.isBlank()
                && hasUniqueSelector(root, control.component(), componentName);
        String selector = named ? componentName : control.selector();
        boolean unique = named || !selector.isBlank()
                && hasUniqueSelector(root, control.component(), selector);
        return new HarnessControl(control.kind(), selector, control.enabled(),
                control.focusable(), control.supported() && unique, label);
    }

    private static boolean hasUniqueSelector(
            JComponent root, JComponent component, String selector) {
        List<JComponent> matches = new ArrayList<>();
        collectMatches(root, selector, JComponent.class, matches);
        return matches.size() == 1 && matches.get(0) == component;
    }

    private static void requireTableRow(
            JTable table, String selector, int viewRow) {
        requireEnabled(table, "Table", selector);
        if (viewRow < 0 || viewRow >= table.getRowCount()) {
            throw new IllegalArgumentException("Row " + viewRow + " is outside table '"
                    + selector + "' with " + table.getRowCount() + " row(s)");
        }
    }

    private static JMenuBar findMenuBar(Component component) {
        if (component instanceof JRootPane root && root.getJMenuBar() != null) {
            return root.getJMenuBar();
        }
        if (component instanceof JMenuBar menuBar) {
            return menuBar;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JMenuBar found = findMenuBar(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<List<String>> menuPaths(JComponent root) {
        JMenuBar menuBar = findMenuBar(root);
        if (menuBar == null) {
            return List.of();
        }
        List<List<String>> paths = new ArrayList<>();
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            if (menu != null) {
                collectMenuPaths(menu, List.of(menu.getText()), paths);
            }
        }
        return List.copyOf(paths);
    }

    private static void collectMenuPaths(
            JMenu menu, List<String> prefix, List<List<String>> paths) {
        for (Component component : menu.getMenuComponents()) {
            if (!(component instanceof JMenuItem item) || item.getText() == null) {
                continue;
            }
            List<String> path = new ArrayList<>(prefix);
            path.add(item.getText());
            if (item instanceof JMenu submenu) {
                collectMenuPaths(submenu, path, paths);
            } else {
                paths.add(List.copyOf(path));
            }
        }
    }

    private static ScreenCapture capture(JComponent root) {
        int sourceWidth = root.getWidth();
        int sourceHeight = root.getHeight();
        if (sourceWidth < 1 || sourceHeight < 1) {
            throw new IllegalStateException("Desktop window has no drawable size");
        }
        double scale = Math.min(1.0, Math.min(
                (double) MAX_SCREEN_DIMENSION / sourceWidth,
                (double) MAX_SCREEN_DIMENSION / sourceHeight));
        long sourcePixels = (long) sourceWidth * sourceHeight;
        if (sourcePixels > MAX_SCREEN_PIXELS) {
            scale = Math.min(scale, Math.sqrt((double) MAX_SCREEN_PIXELS / sourcePixels));
        }
        int width = Math.max(1, (int) Math.floor(sourceWidth * scale));
        int height = Math.max(1, (int) Math.floor(sourceHeight * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale((double) width / sourceWidth, (double) height / sourceHeight);
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No PNG writer is available");
            }
            png = output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode desktop screenshot", exception);
        }
        if (png.length > MAX_SCREEN_PNG_BYTES) {
            throw new IllegalStateException("Desktop screenshot exceeds the 4 MiB safety limit");
        }
        return new ScreenCapture(width, height, "image/png", "base64",
                Base64.getEncoder().encodeToString(png), sha256(png));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean matchesSelector(Component component, String expected) {
        if (expected == null) {
            return false;
        }
        if (expected.equals(component.getName())) {
            return true;
        }
        if (component instanceof JComponent swing
                && swing.getAccessibleContext() != null
                && expected.equals(swing.getAccessibleContext().getAccessibleName())) {
            return true;
        }
        return component instanceof AbstractButton button
                && expected.equals(button.getText());
    }

    private static void collectInteractive(Component component, List<RawControl> controls) {
        if (!component.isVisible() || component instanceof BasicArrowButton) {
            return;
        }
        if (component instanceof JMenuItem) {
            // Menus use exact terminal paths rather than generic button selectors.
            return;
        }
        if (component instanceof JFileChooser chooser) {
            controls.add(raw("file-chooser", chooser, accessibleName(chooser)));
            return;
        }
        if (component instanceof AbstractButton button) {
            controls.add(raw("button", button, firstNonBlank(
                    accessibleName(button), button.getText()),
                    button.getAction() != null
                            || button.getActionListeners().length > 0
                            || button.getItemListeners().length > 0));
            return;
        }
        if (component instanceof JComboBox<?> combo) {
            controls.add(raw("selector", combo, accessibleName(combo),
                    !SwingPasswordGuard.hasPasswordEditor(combo)));
            return;
        }
        if (component instanceof JTextComponent text && text.isEditable()) {
            controls.add(raw("field", text, accessibleName(text),
                    !(text instanceof JPasswordField)));
            return;
        }
        if (component instanceof JTabbedPane tabs) {
            controls.add(raw("tabs", tabs, accessibleName(tabs)));
            int selected = tabs.getSelectedIndex();
            if (selected >= 0) {
                collectInteractive(tabs.getComponentAt(selected), controls);
            }
            return;
        }
        if (component instanceof JTable || component instanceof JList<?>
                || component instanceof JSpinner || component instanceof JSlider) {
            JComponent interactive = (JComponent) component;
            controls.add(raw(component.getClass().getSimpleName(), interactive,
                    accessibleName(interactive)));
            return;
        }
        if (component instanceof JScrollPane scroll) {
            Component view = scroll.getViewport().getView();
            if (view != null) {
                collectInteractive(view, controls);
            }
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectInteractive(child, controls);
            }
        }
    }

    private static boolean isLayoutKind(String kind) {
        return kind.equals("scroll") || kind.equals("split");
    }

    /** Supplemental selectors never use a label's current text as a public name. */
    private static void collectSupplemental(Component component, List<RawControl> controls) {
        if (!component.isVisible() || component instanceof JPasswordField
                || component instanceof JMenuItem || component instanceof BasicArrowButton) {
            return;
        }
        if (component instanceof JLabel label) {
            if (label.getName() != null && !label.getName().isBlank()) {
                controls.add(raw("label", label, label.getName()));
            }
            return;
        }
        if (component instanceof JTextComponent text) {
            String selector = firstNonBlank(accessibleName(text), text.getName());
            if (!text.isEditable() && !selector.isBlank()) {
                controls.add(raw("text", text, selector));
            }
            return;
        }
        if (component instanceof AbstractButton || component instanceof JTable
                || component instanceof JList<?> || component instanceof JComboBox<?>
                || component instanceof JSpinner || component instanceof JSlider
                || component instanceof JFileChooser) {
            return;
        }
        if (component instanceof JTabbedPane tabs) {
            if (tabs.getSelectedComponent() != null) {
                collectSupplemental(tabs.getSelectedComponent(), controls);
            }
            return;
        }
        if (component instanceof JScrollPane scroll) {
            String selector = firstNonBlank(accessibleName(scroll), scroll.getName());
            if (!selector.isBlank()) {
                controls.add(raw("scroll", scroll, selector));
            }
            if (scroll.getViewport().getView() != null) {
                collectSupplemental(scroll.getViewport().getView(), controls);
            }
            return;
        }
        if (component instanceof JSplitPane split) {
            String selector = firstNonBlank(accessibleName(split), split.getName());
            if (!selector.isBlank()) {
                controls.add(raw("split", split, selector));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectSupplemental(child, controls);
            }
        }
    }

    private static RawControl raw(String kind, JComponent component, String selector) {
        return raw(kind, component, selector, true);
    }

    private static void requireEnabled(
            JComponent component, String kind, String selector) {
        if (!component.isEnabled()) {
            throw new IllegalArgumentException(kind + " '" + selector + "' is disabled");
        }
    }

    private static RawControl raw(
            String kind, JComponent component, String selector, boolean supported) {
        return new RawControl(kind, selector == null ? "" : selector.trim(),
                component.isEnabled(), component.isFocusable(), supported, component);
    }

    private static List<String> route(Component root) {
        List<String> route = new ArrayList<>();
        collectRoute(root, route);
        return List.copyOf(route);
    }

    private static List<List<String>> routeCatalog(Component root) {
        return routeSuffixes(root).stream()
                .filter(path -> !path.isEmpty())
                .distinct()
                .map(List::copyOf)
                .toList();
    }

    private static List<List<String>> routeSuffixes(Component component) {
        if (component instanceof JTabbedPane tabs) {
            List<List<String>> routes = new ArrayList<>();
            for (int index = 0; index < tabs.getTabCount(); index++) {
                String title = tabs.getTitleAt(index);
                String step = title == null || title.isBlank()
                        ? "tab " + (index + 1) : title;
                for (List<String> suffix : routeSuffixes(tabs.getComponentAt(index))) {
                    List<String> path = new ArrayList<>(suffix.size() + 1);
                    path.add(step);
                    path.addAll(suffix);
                    routes.add(List.copyOf(path));
                }
            }
            return routes;
        }
        if (!(component instanceof Container container)) {
            return List.of(List.of());
        }
        List<List<String>> combined = new ArrayList<>();
        combined.add(List.of());
        for (Component child : container.getComponents()) {
            List<List<String>> childRoutes = routeSuffixes(child);
            if (childRoutes.stream().allMatch(List::isEmpty)) {
                continue;
            }
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : combined) {
                for (List<String> suffix : childRoutes) {
                    List<String> path = new ArrayList<>(prefix.size() + suffix.size());
                    path.addAll(prefix);
                    path.addAll(suffix);
                    next.add(List.copyOf(path));
                }
            }
            combined = next;
        }
        return List.copyOf(combined);
    }

    private static List<TabTarget> structuralRouteTargets(
            Component component, List<String> path, int depth) {
        if (depth >= path.size()) {
            return List.of();
        }
        if (component instanceof JTabbedPane tabs) {
            List<TabTarget> candidates = new ArrayList<>();
            List<List<TabTarget>> continuations = new ArrayList<>();
            List<String> remaining = path.subList(depth + 1, path.size());
            for (int index = 0; index < tabs.getTabCount(); index++) {
                if (!path.get(depth).equals(tabs.getTitleAt(index))) {
                    continue;
                }
                Component child = tabs.getComponentAt(index);
                List<List<String>> childRoutes = routeSuffixes(child);
                if (!remaining.isEmpty() && !childRoutes.contains(remaining)) {
                    continue;
                }
                candidates.add(new TabTarget(tabs, index));
                continuations.add(remaining.isEmpty() ? List.of()
                        : structuralRouteTargets(child, path, depth + 1));
            }
            if (candidates.size() != 1) {
                return List.of();
            }
            List<TabTarget> result = new ArrayList<>();
            result.add(candidates.get(0));
            result.addAll(continuations.get(0));
            return List.copyOf(result);
        }
        if (component instanceof Container container) {
            List<List<TabTarget>> candidates = new ArrayList<>();
            List<String> remaining = path.subList(depth, path.size());
            for (Component child : container.getComponents()) {
                if (routeSuffixes(child).contains(remaining)) {
                    List<TabTarget> targets = structuralRouteTargets(child, path, depth);
                    if (!targets.isEmpty()) {
                        candidates.add(targets);
                    }
                }
            }
            return candidates.size() == 1 ? candidates.get(0) : List.of();
        }
        return List.of();
    }

    private static void collectRoute(Component component, List<String> route) {
        if (!component.isVisible()) {
            return;
        }
        if (component instanceof JTabbedPane tabs) {
            int selected = tabs.getSelectedIndex();
            if (selected >= 0) {
                String title = tabs.getTitleAt(selected);
                route.add(title == null || title.isBlank()
                        ? "tab " + (selected + 1) : title);
                collectRoute(tabs.getComponentAt(selected), route);
            }
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectRoute(child, route);
            }
        }
    }

    private static String accessibleName(Component component) {
        if (!(component instanceof JComponent swing)
                || swing.getAccessibleContext() == null
                || swing.getAccessibleContext().getAccessibleName() == null) {
            return "";
        }
        return swing.getAccessibleContext().getAccessibleName().trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim()
                : second == null ? "" : second.trim();
    }

    private static int matchingOptionIndex(JComboBox<?> combo, String displayedValue) {
        ComboBoxModel<?> model = combo.getModel();
        boolean[] changed = {false};
        ListDataListener listener = new ListDataListener() {
            @Override public void intervalAdded(ListDataEvent event) { changed[0] = true; }
            @Override public void intervalRemoved(ListDataEvent event) { changed[0] = true; }
            @Override public void contentsChanged(ListDataEvent event) { changed[0] = true; }
        };
        model.addListDataListener(listener);
        try {
            int match = -1;
            int count = model.getSize();
            for (int index = 0; index < count; index++) {
                String rendered = comboItemText(combo, index);
                if (changed[0] || combo.getModel() != model) {
                    throw new IllegalStateException("Selector options changed while matching the requested label");
                }
                if (displayedValue.equals(rendered)) {
                    if (match >= 0) {
                        throw new IllegalArgumentException(
                                "Several options are displayed as '" + displayedValue + "'");
                    }
                    match = index;
                }
            }
            return match;
        } finally {
            // Selection listeners may legitimately rebuild equal records after matching finishes.
            model.removeListDataListener(listener);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String comboItemText(JComboBox<?> combo, int index) {
        Object item = combo.getItemAt(index);
        javax.swing.ListCellRenderer renderer = combo.getRenderer();
        if (renderer != null) {
            Component rendered = renderer.getListCellRendererComponent(
                    new JList<>(), item, index, false, false);
            SwingPasswordGuard.requireNoPassword(rendered);
            if (rendered instanceof javax.swing.JLabel label && label.getText() != null) {
                return label.getText();
            }
        }
        return String.valueOf(item);
    }

    private static <T> T onEdt(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return call(task);
        }
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result[0] = task.call();
                } catch (Throwable throwable) {
                    failure[0] = throwable;
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while automating Swing", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IllegalStateException("Swing automation failed", exception.getCause());
        }
        if (failure[0] != null) {
            if (failure[0] instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure[0] instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Swing automation failed", failure[0]);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Swing automation failed", exception);
        }
    }

    private record Attachment(JComponent root, Function<String, Boolean> navigation,
                              SwingObservationContext context) {
    }

    private record ControlTarget(JComponent component, String selector, boolean enabled) {
    }

    private record TabTarget(JTabbedPane tabs, int index) {
    }

    public record ScreenCapture(int width, int height, String mediaType,
                                String encoding, String data, String sha256) {
    }

    private record RawControl(String kind, String selector,
                              boolean enabled, boolean focusable, boolean supported,
                              JComponent component) {
    }
}

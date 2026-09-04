package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessControl;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessSnapshot;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.DefaultRowSorter;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RootPaneContainer;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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
import java.util.function.Supplier;

import org.springframework.context.annotation.Profile;

/** Live, non-static attachment point between the desktop window and automation plugins. */
@org.springframework.stereotype.Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class SwingHarnessBridge {

    private static final int MAX_COLLECTION_ITEMS = 200;
    private static final int MAX_TABLE_CELLS = 2000;
    private static final int MAX_SELECTION_SCAN = 10000;
    private static final int MAX_ITEM_CHARS = 500;
    private static final int MAX_TEXT_CHARS = 8000;
    private static final int MAX_SCREEN_DIMENSION = 2048;
    private static final int MAX_SCREEN_PIXELS = 2_000_000;
    private static final int MAX_SCREEN_PNG_BYTES = 4 * 1024 * 1024;

    private volatile Attachment attachment;
    private final SwingActionJournal actions = new SwingActionJournal();
    private final Function<JComponent, JComponent> activeRootResolver;

    public SwingHarnessBridge() {
        this(SwingHarnessBridge::ownedModalRoot);
    }

    SwingHarnessBridge(Function<JComponent, JComponent> activeRootResolver) {
        this.activeRootResolver = Objects.requireNonNull(activeRootResolver, "activeRootResolver");
    }

    HarnessExecution dispatch(HarnessCommand command, Supplier<HarnessExecution> action) {
        Attachment current = requireAttachment();
        return actions.dispatch(command, () -> {
            if (attachment != current) {
                throw new IllegalStateException("Desktop attachment changed before dispatch");
            }
            return action.get();
        });
    }

    Optional<HarnessExecution> replay(HarnessCommand command) {
        return actions.replay(command);
    }

    HarnessExecution actionStatus(String requestId) {
        return actions.status(requestId);
    }

    public void attach(JComponent root, Function<String, Boolean> navigation) {
        attachment = new Attachment(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(navigation, "navigation"));
    }

    public void detach() {
        attachment = null;
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
        JComponent active = activeRoot(current);
        List<RawControl> raw = new ArrayList<>();
        collectInteractive(active, raw);
        Map<String, Integer> selectorCounts = new LinkedHashMap<>();
        raw.stream().filter(control -> !control.selector().isBlank())
                .forEach(control -> selectorCounts.merge(control.selector(), 1, Integer::sum));
        List<HarnessControl> controls = raw.stream().map(control ->
                new HarnessControl(control.kind(), control.selector(), control.enabled(),
                        control.focusable(), control.supported()
                        && !control.selector().isBlank()
                        && selectorCounts.getOrDefault(control.selector(), 0) == 1))
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
        return new HarnessSnapshot(pluginId, Instant.now(), route(current.root()), controls, metadata);
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
            int match = -1;
            for (int index = 0; index < combo.getItemCount(); index++) {
                if (displayedValue.equals(comboItemText(combo, index))) {
                    if (match >= 0) {
                        throw new IllegalArgumentException(
                                "Several options are displayed as '" + displayedValue + "'");
                    }
                    match = index;
                }
            }
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
                combo.setSelectedItem(combo.getEditor().getItem());
            } else {
                combo.setSelectedIndex(match);
            }
            return null;
        });
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
            tabs.setSelectedIndex(match);
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
                if (columnTitle.equals(table.getColumnName(viewColumn))) {
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
            sorter.setSortKeys(List.of(new RowSorter.SortKey(modelColumn, order)));
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
            return null;
        });
    }

    public void setSpinner(String selector, String displayedValue) {
        JSpinner spinner = unique(selector, JSpinner.class);
        onEdt(() -> {
            if (!spinner.isEnabled()) {
                throw new IllegalArgumentException("Spinner '" + selector + "' is disabled");
            }
            if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
                throw new IllegalArgumentException(
                        "Spinner '" + selector + "' has no text editor");
            }
            editor.getTextField().setText(displayedValue);
            try {
                spinner.commitEdit();
            } catch (java.text.ParseException exception) {
                throw new IllegalArgumentException("Value '" + displayedValue
                        + "' is invalid for spinner '" + selector + "'", exception);
            }
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
            table.setRowSelectionInterval(viewRow, viewRow);
            Rectangle cell = table.getCellRect(viewRow, 0, true);
            table.scrollRectToVisible(cell);
            MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0,
                    cell.x + Math.max(1, cell.width / 2),
                    cell.y + Math.max(1, cell.height / 2),
                    2, false, MouseEvent.BUTTON1);
            table.dispatchEvent(event);
            return null;
        });
    }

    public void activateMenu(List<String> path) {
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException(
                    "Menu path must contain a top-level menu and a command");
        }
        Attachment current = requireAttachment();
        onEdt(() -> {
            JMenuBar menuBar = requireMenuBar(activeRoot(current));
            requireVisibleMenuComponent(menuBar, "Menu bar");
            JMenuItem item = uniqueTopMenu(menuBar, path.get(0));
            requireVisibleMenuComponent(item, "Menu '" + item.getText() + "'");
            for (int index = 1; index < path.size(); index++) {
                if (!(item instanceof JMenu menu)) {
                    throw new IllegalArgumentException("Menu path continues after command '"
                            + item.getText() + "'");
                }
                item = uniqueMenuItem(menu, path.get(index));
                requireVisibleMenuComponent(item, "Menu command '" + item.getText() + "'");
            }
            if (item instanceof JMenu) {
                throw new IllegalArgumentException(
                        "Menu path must end at a command, not a submenu");
            }
            if (!item.isEnabled()) {
                throw new IllegalArgumentException("Menu command '" + item.getText()
                        + "' is disabled");
            }
            item.doClick();
            return null;
        });
    }

    private static void requireVisibleMenuComponent(JComponent component, String label) {
        if (!component.isVisible()) {
            throw new IllegalArgumentException(label + " is hidden");
        }
        if (!component.isEnabled()) {
            throw new IllegalArgumentException(label + " is disabled");
        }
    }

    public void invokeShortcut(String shortcut) {
        KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcut);
        if (keyStroke == null) {
            throw new IllegalArgumentException(
                    "Shortcut '" + shortcut + "' is not a valid Swing keystroke");
        }
        Attachment current = requireAttachment();
        onEdt(() -> {
            List<ShortcutBinding> matches = new ArrayList<>();
            collectShortcutBindings(activeRoot(current), keyStroke, matches);
            if (matches.size() != 1) {
                throw new IllegalArgumentException("Expected one visible installed shortcut '"
                        + shortcut + "' but found " + matches.size());
            }
            ShortcutBinding binding = matches.get(0);
            if (!binding.action().isEnabled()) {
                throw new IllegalArgumentException(
                        "Installed shortcut '" + shortcut + "' is disabled");
            }
            binding.action().actionPerformed(new ActionEvent(binding.source(),
                    ActionEvent.ACTION_PERFORMED, keyStroke.toString(),
                    System.currentTimeMillis(), keyStroke.getModifiers()));
            return null;
        });
    }

    public Map<String, Object> readControl(String selector) {
        return onEdtBounded(() -> controlValue(unique(selector, JComponent.class), selector));
    }

    public ScreenCapture captureScreen() {
        Attachment current = requireAttachment();
        return onEdtBounded(() -> capture(activeRoot(current)));
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
            List<T> matches = new ArrayList<>();
            collectMatches(activeRoot(current), selector, type, matches);
            if (matches.size() != 1) {
                throw new IllegalArgumentException("Expected one visible "
                        + type.getSimpleName() + " named '" + selector + "' but found "
                        + matches.size());
            }
            return matches.get(0);
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

    private static void requireTableRow(
            JTable table, String selector, int viewRow) {
        requireEnabled(table, "Table", selector);
        if (viewRow < 0 || viewRow >= table.getRowCount()) {
            throw new IllegalArgumentException("Row " + viewRow + " is outside table '"
                    + selector + "' with " + table.getRowCount() + " row(s)");
        }
    }

    private static JMenuBar requireMenuBar(JComponent root) {
        JMenuBar menuBar = findMenuBar(root);
        if (menuBar == null) {
            throw new IllegalStateException("Desktop window has no menu bar");
        }
        return menuBar;
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

    private static JMenu uniqueTopMenu(JMenuBar menuBar, String label) {
        List<JMenu> matches = new ArrayList<>();
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            if (menu != null && label.equals(menu.getText())) {
                matches.add(menu);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one top-level menu named '"
                    + label + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private static JMenuItem uniqueMenuItem(JMenu menu, String label) {
        List<JMenuItem> matches = new ArrayList<>();
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenuItem item && label.equals(item.getText())) {
                matches.add(item);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one menu command named '"
                    + label + "' below '" + menu.getText() + "' but found "
                    + matches.size());
        }
        return matches.get(0);
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

    private static void collectShortcutBindings(
            Component component, KeyStroke keyStroke, List<ShortcutBinding> matches) {
        if (!component.isVisible()) {
            return;
        }
        if (component instanceof JComponent swing) {
            addShortcutBinding(swing, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                    keyStroke, matches);
            addShortcutBinding(swing, JComponent.WHEN_IN_FOCUSED_WINDOW,
                    keyStroke, matches);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectShortcutBindings(child, keyStroke, matches);
            }
        }
    }

    private static void addShortcutBinding(
            JComponent component, int condition, KeyStroke keyStroke,
            List<ShortcutBinding> matches) {
        InputMap inputMap = component.getInputMap(condition);
        Object actionKey = inputMap == null ? null : inputMap.get(keyStroke);
        Action action = actionKey == null ? null : component.getActionMap().get(actionKey);
        if (action == null || matches.stream().anyMatch(binding -> binding.action() == action)) {
            return;
        }
        matches.add(new ShortcutBinding(component, action));
    }

    private static Map<String, Object> controlValue(
            JComponent component, String selector) {
        if (!component.isVisible()) {
            throw new IllegalArgumentException("Control '" + selector + "' is not visible");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        if (component instanceof JPasswordField) {
            throw new IllegalArgumentException(
                    "Password fields are never readable through the automation harness");
        }
        if (component instanceof JTextComponent text) {
            value.put("kind", "text");
            putText(value, "value", text.getText(), MAX_TEXT_CHARS);
        } else if (component instanceof JComboBox<?> combo) {
            value.put("kind", "selector");
            value.put("editable", combo.isEditable());
            value.put("selectedIndex", combo.getSelectedIndex());
            Object editorItem = combo.isEditable() ? combo.getEditor().getItem() : null;
            putText(value, "selected", combo.isEditable()
                    ? editorItem == null ? "" : String.valueOf(editorItem)
                    : combo.getSelectedIndex() < 0 ? ""
                    : comboItemText(combo, combo.getSelectedIndex()), MAX_ITEM_CHARS);
            value.put("options", comboOptions(combo, value));
            value.put("optionCount", combo.getItemCount());
            markTruncated(value, combo.getItemCount() > MAX_COLLECTION_ITEMS);
        } else if (component instanceof JFileChooser chooser) {
            value.put("kind", "file-chooser");
            value.put("dialogType", chooser.getDialogType() == JFileChooser.SAVE_DIALOG ? "SAVE" : "OPEN");
            putText(value, "title", chooser.getDialogTitle(), MAX_ITEM_CHARS);
            putText(value, "selectedPath", chooser.getSelectedFile() == null ? ""
                    : chooser.getSelectedFile().getAbsolutePath(), MAX_TEXT_CHARS);
            putText(value, "directory", chooser.getCurrentDirectory() == null ? ""
                    : chooser.getCurrentDirectory().getAbsolutePath(), MAX_TEXT_CHARS);
        } else if (component instanceof JTable table) {
            tableValue(table, value);
        } else if (component instanceof JList<?> list) {
            listValue(list, value);
        } else if (component instanceof JSpinner spinner) {
            value.put("kind", "spinner");
            putText(value, "value", String.valueOf(spinner.getValue()), MAX_ITEM_CHARS);
        } else if (component instanceof JSlider slider) {
            value.put("kind", "slider");
            value.put("value", slider.getValue());
            value.put("minimum", slider.getMinimum());
            value.put("maximum", slider.getMaximum());
        } else if (component instanceof JTabbedPane tabs) {
            value.put("kind", "tabs");
            value.put("selectedIndex", tabs.getSelectedIndex());
            putText(value, "selected", tabs.getSelectedIndex() < 0
                    ? "" : tabs.getTitleAt(tabs.getSelectedIndex()), MAX_ITEM_CHARS);
            List<String> titles = new ArrayList<>();
            int count = Math.min(tabs.getTabCount(), MAX_COLLECTION_ITEMS);
            for (int index = 0; index < count; index++) {
                titles.add(boundedItem(value, tabs.getTitleAt(index)));
            }
            value.put("tabs", List.copyOf(titles));
            value.put("tabCount", tabs.getTabCount());
            markTruncated(value, count < tabs.getTabCount());
        } else if (component instanceof AbstractButton button) {
            value.put("kind", "button");
            value.put("selected", button.isSelected());
            putText(value, "label", button.getText(), MAX_ITEM_CHARS);
        } else {
            throw new IllegalArgumentException("Control '" + selector
                    + "' has no supported readable value");
        }
        value.put("enabled", component.isEnabled());
        return Map.copyOf(value);
    }

    private static List<String> comboOptions(JComboBox<?> combo, Map<String, Object> value) {
        int count = Math.min(combo.getItemCount(), MAX_COLLECTION_ITEMS);
        List<String> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(boundedItem(value, comboItemText(combo, index)));
        }
        return List.copyOf(options);
    }

    private static void tableValue(JTable table, Map<String, Object> value) {
        value.put("kind", "table");
        int columnCount = table.getColumnCount();
        int returnedColumns = Math.min(columnCount, 50);
        List<String> columns = new ArrayList<>(returnedColumns);
        for (int column = 0; column < returnedColumns; column++) {
            columns.add(boundedItem(value, table.getColumnName(column)));
        }
        int cellBoundRows = returnedColumns == 0
                ? MAX_COLLECTION_ITEMS : MAX_TABLE_CELLS / returnedColumns;
        int returnedRows = Math.min(table.getRowCount(),
                Math.min(MAX_COLLECTION_ITEMS, cellBoundRows));
        List<List<String>> rows = new ArrayList<>(returnedRows);
        for (int row = 0; row < returnedRows; row++) {
            List<String> cells = new ArrayList<>(returnedColumns);
            for (int column = 0; column < returnedColumns; column++) {
                cells.add(boundedItem(value, tableCellText(table, row, column)));
            }
            rows.add(List.copyOf(cells));
        }
        value.put("columns", List.copyOf(columns));
        value.put("rows", List.copyOf(rows));
        value.put("rowCount", table.getRowCount());
        value.put("columnCount", columnCount);
        value.put("selectedRows", selectedRows(table.getSelectionModel(), table.getRowCount(), value));
        markTruncated(value, returnedRows < table.getRowCount()
                || returnedColumns < columnCount);
    }

    private static void listValue(JList<?> list, Map<String, Object> value) {
        value.put("kind", "list");
        int count = Math.min(list.getModel().getSize(), MAX_COLLECTION_ITEMS);
        List<String> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            items.add(boundedItem(value, listItemText(list, index)));
        }
        value.put("items", List.copyOf(items));
        value.put("itemCount", list.getModel().getSize());
        value.put("selectedRows", selectedRows(list.getSelectionModel(), list.getModel().getSize(), value));
        markTruncated(value, count < list.getModel().getSize());
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

    private static String boundedItem(Map<String, Object> value, String text) {
        markTruncated(value, text != null && text.length() > MAX_ITEM_CHARS);
        return truncate(text, MAX_ITEM_CHARS);
    }

    private static void markTruncated(Map<String, Object> value, boolean clipped) {
        value.put("truncated", clipped || Boolean.TRUE.equals(value.get("truncated")));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String listItemText(JList<?> list, int index) {
        Object item = list.getModel().getElementAt(index);
        javax.swing.ListCellRenderer renderer = list.getCellRenderer();
        Component rendered = renderer == null ? null : renderer.getListCellRendererComponent(
                list, item, index, false, false);
        if (rendered instanceof javax.swing.JLabel label && label.getText() != null) {
            return label.getText();
        }
        return String.valueOf(item);
    }

    private static String tableCellText(JTable table, int row, int column) {
        Component rendered = table.prepareRenderer(table.getCellRenderer(row, column),
                row, column);
        if (rendered instanceof javax.swing.JLabel label && label.getText() != null) {
            return label.getText();
        }
        return String.valueOf(table.getValueAt(row, column));
    }

    private static void putText(
            Map<String, Object> output, String key, String text, int limit) {
        String safe = text == null ? "" : text;
        output.put(key, truncate(safe, limit));
        markTruncated(output, safe.length() > limit);
    }

    private static String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        return text.substring(0, limit);
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
            controls.add(raw("selector", combo, accessibleName(combo)));
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
                component.isEnabled(), component.isFocusable(), supported);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String comboItemText(JComboBox<?> combo, int index) {
        Object item = combo.getItemAt(index);
        javax.swing.ListCellRenderer renderer = combo.getRenderer();
        if (renderer != null) {
            Component rendered = renderer.getListCellRendererComponent(
                    new JList<>(), item, index, false, false);
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

    private record Attachment(JComponent root, Function<String, Boolean> navigation) {
    }

    private record ShortcutBinding(JComponent source, Action action) {
    }

    private record TabTarget(JTabbedPane tabs, int index) {
    }

    public record ScreenCapture(int width, int height, String mediaType,
                                String encoding, String data, String sha256) {
    }

    private record RawControl(String kind, String selector,
                              boolean enabled, boolean focusable, boolean supported) {
    }
}

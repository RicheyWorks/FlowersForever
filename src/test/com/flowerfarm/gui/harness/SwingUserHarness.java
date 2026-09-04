package com.flowerfarm.gui.harness;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.DefaultRowSorter;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.InputMap;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Headless user driver for FlowersForever Swing journeys.
 *
 * <p>The driver deliberately finds form controls through accessible names and
 * visible button/tab labels. It operates on the event-dispatch thread and uses
 * the controls' normal listeners, including the production SwingWorkers.
 */
public final class SwingUserHarness {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final JComponent root;
    private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());

    private SwingUserHarness(JComponent root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public static SwingUserHarness on(JComponent root) {
        return new SwingUserHarness(root);
    }

    /** Watches failures that escape asynchronous Swing/background callbacks. */
    public static AsyncFailureMonitor monitorAsyncFailures() {
        return new AsyncFailureMonitor();
    }

    /**
     * Enters one named boundary-data case in declaration order, keeping a case
     * marker in the transcript so a matrix failure identifies its exact row.
     */
    public SwingUserHarness typeCase(String caseName, TextInput... inputs) {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("caseName is required");
        }
        if (inputs == null || inputs.length == 0) {
            throw new IllegalArgumentException("At least one text input is required");
        }
        transcript.add("CASE " + caseName);
        for (TextInput input : inputs) {
            TextInput required = Objects.requireNonNull(input, "input");
            type(required.accessibleName(), required.value());
        }
        return this;
    }

    public static TextInput input(String accessibleName, String value) {
        return new TextInput(accessibleName, value);
    }

    /** Replaces a field's value exactly as a worker typing a new value would. */
    public SwingUserHarness type(String accessibleName, String value) {
        JTextComponent field = uniqueAccessible(accessibleName, JTextComponent.class);
        onEdt(() -> {
            requireUsable(field, "field", accessibleName);
            if (!field.isEditable()) {
                throw new AssertionError("Field is not editable: " + accessibleName);
            }
            field.requestFocusInWindow();
            field.selectAll();
            field.replaceSelection(value == null ? "" : value);
            return null;
        });
        transcript.add("TYPE " + accessibleName + " = " + printable(value));
        return this;
    }

    /** Chooses a combo-box item by the text the user sees. */
    public SwingUserHarness select(String accessibleName, String displayedValue) {
        JComboBox<?> combo = uniqueAccessible(accessibleName, JComboBox.class);
        onEdt(() -> {
            requireUsable(combo, "selector", accessibleName);
            int match = -1;
            for (int index = 0; index < combo.getItemCount(); index++) {
                if (displayedValue.equals(comboItemText(combo, index))) {
                    if (match >= 0) {
                        throw new AssertionError("Multiple options named '" + displayedValue
                                + "' in " + accessibleName);
                    }
                    match = index;
                }
            }
            if (match < 0) {
                throw new AssertionError("No option named '" + displayedValue + "' in "
                        + accessibleName + ". Available: " + comboItems(combo));
            }
            combo.setSelectedIndex(match);
            return null;
        });
        transcript.add("SELECT " + accessibleName + " = " + displayedValue);
        return this;
    }

    /** Clicks one enabled, visible button using its displayed text. */
    public SwingUserHarness click(String buttonText) {
        AbstractButton button = uniqueButton(buttonText);
        onEdt(() -> {
            requireUsable(button, "button", buttonText);
            button.requestFocusInWindow();
            button.doClick();
            return null;
        });
        transcript.add("CLICK " + buttonText);
        return this;
    }

    /** Clicks a button by its accessible selector when visible labels are intentionally repeated. */
    public SwingUserHarness clickAccessible(String accessibleName) {
        AbstractButton button = uniqueAccessible(accessibleName, AbstractButton.class);
        onEdt(() -> {
            requireUsable(button, "button", accessibleName);
            button.requestFocusInWindow();
            button.doClick();
            return null;
        });
        transcript.add("CLICK ACCESSIBLE " + accessibleName);
        return this;
    }

    /** Fires the normal Enter action for a single-line form field. */
    public SwingUserHarness pressEnter(String accessibleName) {
        JTextField field = uniqueAccessible(accessibleName, JTextField.class);
        onEdt(() -> {
            requireUsable(field, "field", accessibleName);
            field.requestFocusInWindow();
            field.postActionEvent();
            return null;
        });
        transcript.add("ENTER " + accessibleName);
        return this;
    }

    /** Sets a checkbox or toggle through its normal click listener. */
    public SwingUserHarness setChecked(String accessibleName, boolean selected) {
        AbstractButton button = uniqueToggle(accessibleName);
        onEdt(() -> {
            requireUsable(button, "toggle", accessibleName);
            if (button.isSelected() != selected) {
                button.doClick();
            }
            return null;
        });
        transcript.add("CHECK " + accessibleName + " = " + selected);
        return this;
    }

    /** Sets a numeric or list spinner through its production change listeners. */
    public SwingUserHarness setSpinner(String accessibleName, Object value) {
        JSpinner spinner = uniqueAccessible(accessibleName, JSpinner.class);
        onEdt(() -> {
            requireUsable(spinner, "spinner", accessibleName);
            spinner.requestFocusInWindow();
            spinner.setValue(value);
            return null;
        });
        transcript.add("SPINNER " + accessibleName + " = " + value);
        return this;
    }

    /** Moves a slider through its production change listeners. */
    public SwingUserHarness setSlider(String accessibleName, int value) {
        JSlider slider = uniqueAccessible(accessibleName, JSlider.class);
        onEdt(() -> {
            requireUsable(slider, "slider", accessibleName);
            if (value < slider.getMinimum() || value > slider.getMaximum()) {
                throw new AssertionError("Slider value " + value + " is outside "
                        + accessibleName + " range " + slider.getMinimum() + ".."
                        + slider.getMaximum());
            }
            slider.requestFocusInWindow();
            slider.setValue(value);
            return null;
        });
        transcript.add("SLIDER " + accessibleName + " = " + value);
        return this;
    }

    /** Selects one row as a user would before invoking a table action. */
    public SwingUserHarness selectRow(String tableName, int viewRow) {
        JTable table = named(tableName, JTable.class);
        onEdt(() -> {
            requireUsable(table, "table", tableName);
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                throw new AssertionError("Row " + viewRow + " is outside table " + tableName
                        + " with " + table.getRowCount() + " rows");
            }
            table.requestFocusInWindow();
            table.setRowSelectionInterval(viewRow, viewRow);
            return null;
        });
        transcript.add("ROW " + tableName + " = " + viewRow);
        return this;
    }

    /** Selects several rows exactly as a Ctrl/Cmd multi-selection would. */
    public SwingUserHarness selectRows(String tableName, int... viewRows) {
        if (viewRows == null || viewRows.length == 0) {
            throw new IllegalArgumentException("At least one row is required");
        }
        JTable table = named(tableName, JTable.class);
        onEdt(() -> {
            requireUsable(table, "table", tableName);
            for (int viewRow : viewRows) {
                if (viewRow < 0 || viewRow >= table.getRowCount()) {
                    throw new AssertionError("Row " + viewRow + " is outside table "
                            + tableName + " with " + table.getRowCount() + " rows");
                }
            }
            int[] rows = java.util.Arrays.stream(viewRows).distinct().sorted().toArray();
            javax.swing.ListSelectionModel selection = table.getSelectionModel();
            if (rows.length > 1 && selection.getSelectionMode()
                    == javax.swing.ListSelectionModel.SINGLE_SELECTION) {
                throw new AssertionError("Table permits only one selected row: " + tableName);
            }
            if (selection.getSelectionMode()
                    == javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION
                    && rows[rows.length - 1] - rows[0] + 1 != rows.length) {
                throw new AssertionError("Table permits only contiguous selected rows: "
                        + tableName);
            }
            boolean adjusting = selection.getValueIsAdjusting();
            selection.setValueIsAdjusting(true);
            try {
                table.clearSelection();
                for (int viewRow : rows) {
                    table.addRowSelectionInterval(viewRow, viewRow);
                }
            } finally {
                selection.setValueIsAdjusting(adjusting);
            }
            table.requestFocusInWindow();
            table.scrollRectToVisible(table.getCellRect(viewRows[viewRows.length - 1], 0, true));
            return null;
        });
        transcript.add("ROWS " + tableName + " = " + java.util.Arrays.toString(viewRows));
        return this;
    }

    /** Dispatches a real two-click mouse event to a selected table row. */
    public SwingUserHarness doubleClickRow(String tableName, int viewRow) {
        JTable table = named(tableName, JTable.class);
        onEdt(() -> {
            requireUsable(table, "table", tableName);
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                throw new AssertionError("Row " + viewRow + " is outside table " + tableName
                        + " with " + table.getRowCount() + " rows");
            }
            table.setRowSelectionInterval(viewRow, viewRow);
            Rectangle cell = table.getCellRect(viewRow, 0, true);
            MouseEvent click = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, cell.x + Math.max(1, cell.width / 2),
                    cell.y + Math.max(1, cell.height / 2), 2, false,
                    MouseEvent.BUTTON1);
            table.dispatchEvent(click);
            return null;
        });
        transcript.add("DOUBLE CLICK ROW " + tableName + " = " + viewRow);
        return this;
    }

    /** Uses the table's normal row sorter as a header click would. */
    public SwingUserHarness sortTable(String tableName, String columnName) {
        JTable table = named(tableName, JTable.class);
        onEdt(() -> {
            requireUsable(table, "table", tableName);
            if (table.getRowSorter() == null) {
                throw new AssertionError("Table has no row sorter: " + tableName);
            }
            int column = -1;
            for (int index = 0; index < table.getColumnCount(); index++) {
                if (columnName.equals(table.getColumnName(index))) {
                    column = index;
                    break;
                }
            }
            if (column < 0) {
                throw new AssertionError("Column '" + columnName + "' not found in "
                        + tableName);
            }
            table.getRowSorter().toggleSortOrder(table.convertColumnIndexToModel(column));
            return null;
        });
        transcript.add("SORT " + tableName + " BY " + columnName);
        return this;
    }

    /** Invokes an installed Swing key binding on the same production scope. */
    public SwingUserHarness pressShortcut(String componentName, KeyStroke keyStroke,
                                          int inputCondition) {
        Objects.requireNonNull(keyStroke, "keyStroke");
        JComponent scope = named(componentName, JComponent.class);
        onEdt(() -> {
            requireUsable(scope, "shortcut scope", componentName);
            InputMap input = scope.getInputMap(inputCondition);
            Object actionKey = input == null ? null : input.get(keyStroke);
            ActionMap actions = scope.getActionMap();
            Action action = actionKey == null || actions == null
                    ? null : actions.get(actionKey);
            if (action == null) {
                throw new AssertionError("No action for shortcut " + keyStroke + " on "
                        + componentName + " (condition " + inputCondition + ")");
            }
            if (!action.isEnabled()) {
                throw new AssertionError("Shortcut action is disabled: " + keyStroke);
            }
            action.actionPerformed(new ActionEvent(
                    scope, ActionEvent.ACTION_PERFORMED, String.valueOf(actionKey)));
            return null;
        });
        transcript.add("SHORTCUT " + componentName + " = " + keyStroke);
        return this;
    }

    /** Selects the one visible row whose named column exactly matches a worker-facing value. */
    public SwingUserHarness selectRow(String tableName, String columnName,
                                      Object displayedValue) {
        JTable table = named(tableName, JTable.class);
        onEdt(() -> {
            requireUsable(table, "table", tableName);
            int column = -1;
            for (int candidate = 0; candidate < table.getColumnCount(); candidate++) {
                if (columnName.equals(table.getColumnName(candidate))) {
                    column = candidate;
                    break;
                }
            }
            if (column < 0) {
                throw new AssertionError("Column '" + columnName + "' not found in "
                        + tableName);
            }
            int match = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Objects.equals(displayedValue, table.getValueAt(row, column))) {
                    if (match >= 0) {
                        throw new AssertionError("Multiple rows in " + tableName
                                + " have " + columnName + " = " + displayedValue);
                    }
                    match = row;
                }
            }
            if (match < 0) {
                throw new AssertionError("No row in " + tableName + " has " + columnName
                        + " = " + displayedValue);
            }
            table.requestFocusInWindow();
            table.setRowSelectionInterval(match, match);
            table.scrollRectToVisible(table.getCellRect(match, column, true));
            return null;
        });
        transcript.add("ROW " + tableName + " WHERE " + columnName + " = "
                + displayedValue);
        return this;
    }

    /** Changes the selected tab through its visible title. */
    public SwingUserHarness selectTab(String title) {
        List<JTabbedPane> candidates = onEdt(() -> components(JTabbedPane.class).stream()
                .filter(Component::isVisible)
                .filter(tabs -> tabIndex(tabs, title) >= 0)
                .toList());
        if (candidates.size() != 1) {
            throw new AssertionError("Expected one visible tab named '" + title
                    + "' but found " + candidates.size());
        }
        onEdt(() -> {
            JTabbedPane tabs = candidates.get(0);
            tabs.setSelectedIndex(tabIndex(tabs, title));
            return null;
        });
        transcript.add("TAB " + title);
        return this;
    }

    /** Waits until any visible label, button, field, or accessible name contains text. */
    public SwingUserHarness awaitText(String expected) {
        return awaitText(expected, DEFAULT_TIMEOUT);
    }

    public SwingUserHarness awaitText(String expected, Duration timeout) {
        await("text containing '" + expected + "'", timeout,
                () -> visibleTexts().stream().anyMatch(text -> text.contains(expected)));
        transcript.add("AWAIT TEXT " + expected);
        return this;
    }

    /** Waits for an asynchronously loaded combo option. */
    public SwingUserHarness awaitOption(String accessibleName, String displayedValue) {
        await("option '" + displayedValue + "' in " + accessibleName, DEFAULT_TIMEOUT, () -> {
            JComboBox<?> combo = uniqueAccessible(accessibleName, JComboBox.class);
            return onEdt(() -> comboItems(combo).contains(displayedValue));
        });
        transcript.add("AWAIT OPTION " + accessibleName + " = " + displayedValue);
        return this;
    }

    /** Gets one named component for assertions on a stable production identifier. */
    public <T extends Component> T named(String name, Class<T> type) {
        List<T> matches = onEdt(() -> components(type).stream()
                .filter(component -> name.equals(component.getName()))
                .toList());
        if (matches.size() != 1) {
            throw new AssertionError("Expected one " + type.getSimpleName() + " named '"
                    + name + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    public String text(String componentName) {
        Component component = named(componentName, Component.class);
        return onEdt(() -> textOf(component));
    }

    /** Reads the current value of one user-addressable text field. */
    public String fieldValue(String accessibleName) {
        JTextComponent field = uniqueAccessible(accessibleName, JTextComponent.class);
        return onEdt(field::getText);
    }

    /** Reads the current displayed choice of one user-addressable selector. */
    public String selectedOption(String accessibleName) {
        JComboBox<?> combo = uniqueAccessible(accessibleName, JComboBox.class);
        return onEdt(() -> combo.getSelectedIndex() < 0 ? ""
                : comboItemText(combo, combo.getSelectedIndex()));
    }

    /** Reports whether a visible checkbox or toggle is selected. */
    public boolean isChecked(String accessibleName) {
        AbstractButton button = uniqueToggle(accessibleName);
        return onEdt(button::isSelected);
    }

    /** Reports whether a visible button can currently be activated. */
    public boolean isButtonEnabled(String buttonText) {
        AbstractButton button = uniqueButton(buttonText);
        return onEdt(button::isEnabled);
    }

    /** Waits for a background workflow to lock or restore a visible action. */
    public SwingUserHarness awaitButtonEnabled(String buttonText, boolean expected) {
        AbstractButton button = uniqueButton(buttonText);
        await("button '" + buttonText + "' enabled = " + expected, DEFAULT_TIMEOUT,
                () -> onEdt(button::isEnabled) == expected);
        transcript.add("AWAIT BUTTON " + buttonText + " ENABLED = " + expected);
        return this;
    }

    /** Reports whether a user-addressable field accepts editing right now. */
    public boolean isFieldEditable(String accessibleName) {
        JTextComponent field = uniqueAccessible(accessibleName, JTextComponent.class);
        return onEdt(() -> field.isEnabled() && field.isEditable());
    }

    public Object tableValue(String tableName, int row, int column) {
        JTable table = named(tableName, JTable.class);
        return onEdt(() -> table.getValueAt(row, column));
    }

    public int tableRows(String tableName) {
        JTable table = named(tableName, JTable.class);
        return onEdt(table::getRowCount);
    }

    /**
     * Lays out a production surface at an exact viewport size so compact-window
     * journeys can detect controls that exist in the tree but are clipped away.
     */
    public SwingUserHarness layoutAt(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Layout dimensions must be positive");
        }
        onEdt(() -> {
            root.setSize(width, height);
            root.validate();
            layoutRecursively(root);
            return null;
        });
        transcript.add("LAYOUT " + width + "x" + height);
        return this;
    }

    /** Returns the portion of a named component actually visible through every ancestor. */
    public VisibleExtent visibleExtent(String componentName) {
        Component component = named(componentName, Component.class);
        return onEdt(() -> {
            if (!component.isVisible()) {
                return new VisibleExtent(0, 0);
            }
            Rectangle visible = SwingUtilities.convertRectangle(component,
                    new Rectangle(0, 0, component.getWidth(), component.getHeight()), root);
            Component ancestor = component.getParent();
            while (ancestor != null) {
                if (!ancestor.isVisible()) {
                    return new VisibleExtent(0, 0);
                }
                Rectangle ancestorBounds = SwingUtilities.convertRectangle(ancestor,
                        new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root);
                visible = visible.intersection(ancestorBounds);
                if (visible.isEmpty() || ancestor == root) {
                    break;
                }
                ancestor = ancestor.getParent();
            }
            return new VisibleExtent(
                    Math.max(0, visible.width), Math.max(0, visible.height));
        });
    }

    /** Waits for an asynchronously refreshed table to reach the expected size. */
    public SwingUserHarness awaitTableRows(String tableName, int expectedRows) {
        JTable table = named(tableName, JTable.class);
        await(expectedRows + " row(s) in " + tableName, DEFAULT_TIMEOUT,
                () -> onEdt(table::getRowCount) == expectedRows);
        transcript.add("AWAIT ROWS " + tableName + " = " + expectedRows);
        return this;
    }

    /** Waits for one visible table cell to reflect an asynchronous refresh. */
    public SwingUserHarness awaitTableValue(
            String tableName, int row, int column, Object expectedValue) {
        JTable table = named(tableName, JTable.class);
        await("value '" + expectedValue + "' at " + tableName + "[" + row + ","
                + column + "]", DEFAULT_TIMEOUT, () -> onEdt(() ->
                row >= 0 && row < table.getRowCount()
                        && column >= 0 && column < table.getColumnCount()
                        && Objects.equals(expectedValue, table.getValueAt(row, column))));
        transcript.add("AWAIT CELL " + tableName + "[" + row + "," + column
                + "] = " + expectedValue);
        return this;
    }

    /**
     * Describes the visible, user-operable surface and identifies controls that
     * cannot be selected reliably through their accessible user-facing label.
     */
    public SurfaceAudit auditInteractiveSurface() {
        return onEdt(() -> {
            List<Control> controls = new ArrayList<>();
            List<String> defects = new ArrayList<>();
            Map<String, Integer> selectorCounts = new LinkedHashMap<>();
            collectInteractive(root, controls);
            for (Control control : controls) {
                if (control.selector().isBlank()) {
                    defects.add(control.kind() + " has no accessible name or visible label");
                } else {
                    selectorCounts.merge(control.kind() + "\u0000" + control.selector(), 1,
                            Integer::sum);
                }
                if ("button".equals(control.kind()) && !control.wired()) {
                    defects.add("button '" + control.selector()
                            + "' has no action or item listener");
                } else if ("JSlider".equals(control.kind()) && !control.wired()) {
                    defects.add("slider '" + control.selector()
                            + "' has no change listener");
                }
                if (control.enabled() && !control.focusable()) {
                    defects.add(control.kind() + " '" + control.selector()
                            + "' is enabled but cannot receive keyboard focus");
                }
            }
            selectorCounts.forEach((selector, count) -> {
                if (count > 1) {
                    String[] parts = selector.split("\u0000", -1);
                    defects.add(count + " visible " + parts[0] + " controls share selector '"
                            + parts[1] + "'");
                }
            });
            return new SurfaceAudit(List.copyOf(controls), List.copyOf(defects));
        });
    }

    /**
     * Audits every reachable nested-tab combination instead of inspecting only
     * the default selected tab. The original selection is restored afterward.
     */
    public List<RoutedSurfaceAudit> auditEveryTabSurface() {
        List<RoutedSurfaceAudit> audits = new ArrayList<>();
        Set<JTabbedPane> activePath = Collections.newSetFromMap(new IdentityHashMap<>());
        visitEveryTabSurface(activePath, new ArrayList<>(), () -> audits.add(
                new RoutedSurfaceAudit(currentRoute(), auditInteractiveSurface())));
        transcript.add("SURFACE CATALOG " + audits.size() + " tab state(s)");
        return List.copyOf(audits);
    }

    /**
     * Exercises every finite user choice and every practical boundary on every
     * reachable tab route, then proves that the original component state was
     * restored. This complements the button storm: selectors, rows, sort keys,
     * list entries, spinner values, slider positions, toggle states, editable
     * text states, and Enter listeners all receive their own evidence.
     */
    public List<RoutedOptionSweep> exerciseEveryOptionOnEveryTabSurface() {
        List<RoutedOptionSweep> sweeps = new ArrayList<>();
        Set<Component> alreadyExercised =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JTabbedPane> reportedTabs =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JTabbedPane> activePath = Collections.newSetFromMap(new IdentityHashMap<>());
        visitEveryTabSurface(activePath, new ArrayList<>(), () -> {
            String route = currentRoute();
            onEdt(() -> {
                for (JTabbedPane tabs : components(JTabbedPane.class)) {
                    if (visibleThroughRoot(tabs) && reportedTabs.add(tabs)) {
                        List<String> titles = new ArrayList<>();
                        for (int index = 0; index < tabs.getTabCount(); index++) {
                            titles.add("tab " + index + " · " + tabs.getTitleAt(index));
                        }
                        sweeps.add(new RoutedOptionSweep(route, "tabs",
                                componentSelector(tabs), List.copyOf(titles),
                                List.copyOf(titles), true));
                    }
                }
                return null;
            });
            exerciseVisibleOptions(route, JComboBox.class, alreadyExercised, sweeps,
                    this::exerciseCombo);
            exerciseVisibleOptions(route, JSpinner.class, alreadyExercised, sweeps,
                    this::exerciseSpinner);
            exerciseVisibleOptions(route, JSlider.class, alreadyExercised, sweeps,
                    this::exerciseSlider);
            exerciseVisibleOptions(route, JScrollPane.class, alreadyExercised, sweeps,
                    this::exerciseScroll);
            exerciseVisibleOptions(route, JTable.class, alreadyExercised, sweeps,
                    this::exerciseTable);
            exerciseVisibleOptions(route, JList.class, alreadyExercised, sweeps,
                    this::exerciseList);
            exerciseVisibleOptions(route, JToggleButton.class, alreadyExercised, sweeps,
                    this::exerciseToggle);
            exerciseVisibleOptions(route, JTextComponent.class, alreadyExercised, sweeps,
                    this::exerciseText);
        });
        transcript.add("ROUTED OPTION MATRIX COMPLETE · controls " + sweeps.size()
                + " · choices " + sweeps.stream()
                .mapToInt(sweep -> sweep.exercised().size()).sum());
        return List.copyOf(sweeps);
    }

    private <T extends JComponent> void exerciseVisibleOptions(
            String route, Class<T> type, Set<Component> alreadyExercised,
            List<RoutedOptionSweep> sweeps, OptionExercise<T> exercise) {
        List<T> candidates = onEdt(() -> components(type).stream()
                .filter(this::visibleThroughRoot)
                .filter(Component::isEnabled)
                .filter(component -> !(component instanceof JTextComponent text)
                        || text.isEditable())
                .filter(component -> !implementationDetail(component))
                .filter(component -> !alreadyExercised.contains(component))
                .toList());
        alreadyExercised.addAll(candidates);
        for (T component : candidates) {
            RoutedOptionSweep sweep = exercise.run(route, component);
            sweeps.add(sweep);
            transcript.add("ROUTED OPTION MATRIX " + route + " · " + sweep.kind()
                    + " · " + sweep.selector() + " · " + sweep.exercised().size()
                    + "/" + sweep.available().size() + " · restored=" + sweep.restored());
        }
    }

    private RoutedOptionSweep exerciseCombo(String route, JComboBox<?> combo) {
        int original = onEdt(combo::getSelectedIndex);
        List<String> available = onEdt(() -> {
            List<String> options = new ArrayList<>();
            for (int index = 0; index < combo.getItemCount(); index++) {
                options.add("option " + index + " · " + comboItemText(combo, index));
            }
            return List.copyOf(options);
        });
        List<String> exercised = new ArrayList<>();
        for (int index = 0; index < available.size(); index++) {
            int selected = index;
            onEdt(() -> {
                requireUsable(combo, "selector", componentSelector(combo));
                combo.setSelectedIndex(selected);
                return null;
            });
            exercised.add(available.get(index));
        }
        onEdt(() -> {
            if (original >= 0 && original < combo.getItemCount()) {
                combo.setSelectedIndex(original);
            } else {
                combo.setSelectedIndex(-1);
            }
            return null;
        });
        boolean restored = onEdt(combo::getSelectedIndex) == original;
        settleSurface();
        return sweep(route, "selector", combo, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseSpinner(String route, JSpinner spinner) {
        Object original = onEdt(spinner::getValue);
        List<Object> values = onEdt(() -> spinnerValues(spinner));
        List<String> available = values.stream().map(String::valueOf).toList();
        List<String> exercised = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            onEdt(() -> {
                requireUsable(spinner, "spinner", componentSelector(spinner));
                spinner.setValue(value);
                return null;
            });
            exercised.add(available.get(index));
        }
        onEdt(() -> {
            spinner.setValue(original);
            return null;
        });
        boolean restored = onEdt(() -> Objects.equals(original, spinner.getValue()));
        settleSurface();
        return sweep(route, "spinner", spinner, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseSlider(String route, JSlider slider) {
        int original = onEdt(slider::getValue);
        int minimum = onEdt(slider::getMinimum);
        int maximum = onEdt(slider::getMaximum);
        if ((long) maximum - minimum > 5000) {
            throw new AssertionError("Slider '" + componentSelector(slider)
                    + "' exposes more than 5,001 integer choices; add a scoped matrix");
        }
        List<String> available = new ArrayList<>();
        for (int value = minimum; value <= maximum; value++) {
            available.add(String.valueOf(value));
        }
        List<String> exercised = new ArrayList<>();
        for (int value = minimum; value <= maximum; value++) {
            int selected = value;
            onEdt(() -> {
                slider.setValue(selected);
                return null;
            });
            exercised.add(String.valueOf(value));
        }
        onEdt(() -> {
            slider.setValue(original);
            return null;
        });
        boolean restored = onEdt(slider::getValue) == original;
        settleSurface();
        return sweep(route, "slider", slider, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseScroll(String route, JScrollPane scroll) {
        Point original = onEdt(() -> scroll.getViewport().getViewPosition());
        List<String> available = new ArrayList<>();
        List<String> exercised = new ArrayList<>();
        exerciseScrollAxis("vertical", scroll.getVerticalScrollBar(), available, exercised);
        exerciseScrollAxis("horizontal", scroll.getHorizontalScrollBar(), available, exercised);
        onEdt(() -> {
            scroll.getViewport().setViewPosition(original);
            return null;
        });
        boolean restored = onEdt(() -> Objects.equals(
                original, scroll.getViewport().getViewPosition()));
        settleSurface();
        return sweep(route, "scroll", scroll, available, exercised, restored);
    }

    private static void exerciseScrollAxis(
            String axis, JScrollBar bar, List<String> available, List<String> exercised) {
        int minimum = onEdt(bar::getMinimum);
        int maximum = onEdt(() -> Math.max(minimum,
                bar.getMaximum() - bar.getVisibleAmount()));
        if ((long) maximum - minimum > 5000) {
            throw new AssertionError(axis + " scroll range exposes more than 5,001 positions");
        }
        for (int value = minimum; value <= maximum; value++) {
            String option = axis + " " + value;
            available.add(option);
            int selected = value;
            onEdt(() -> {
                bar.setValue(selected);
                return null;
            });
            exercised.add(option);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RoutedOptionSweep exerciseTable(String route, JTable table) {
        int[] selectedModelRows = onEdt(() -> java.util.Arrays.stream(table.getSelectedRows())
                .map(table::convertRowIndexToModel).toArray());
        RowSorter sorter = onEdt(table::getRowSorter);
        List originalKeys = sorter == null ? List.of() : onEdt(sorter::getSortKeys);
        List<String> available = new ArrayList<>();
        List<String> exercised = new ArrayList<>();
        int rows = onEdt(table::getRowCount);
        for (int row = 0; row < rows; row++) {
            String option = "row " + row;
            available.add(option);
            int selected = row;
            onEdt(() -> {
                table.setRowSelectionInterval(selected, selected);
                table.scrollRectToVisible(table.getCellRect(selected, 0, true));
                return null;
            });
            exercised.add(option);
        }
        if (rows > 1 && table.getSelectionModel().getSelectionMode()
                != javax.swing.ListSelectionModel.SINGLE_SELECTION) {
            available.add("all rows");
            onEdt(() -> {
                table.setRowSelectionInterval(0, table.getRowCount() - 1);
                return null;
            });
            exercised.add("all rows");
        }
        if (sorter != null) {
            int columns = onEdt(table::getColumnCount);
            for (int viewColumn = 0; viewColumn < columns; viewColumn++) {
                int selectedColumn = viewColumn;
                int modelColumn = onEdt(() ->
                        table.convertColumnIndexToModel(selectedColumn));
                if (sorter instanceof DefaultRowSorter defaultSorter
                        && !defaultSorter.isSortable(modelColumn)) {
                    continue;
                }
                String title = onEdt(() -> table.getColumnName(selectedColumn));
                for (SortOrder order : List.of(SortOrder.ASCENDING, SortOrder.DESCENDING)) {
                    String option = "sort " + title + " " + order;
                    available.add(option);
                    onEdt(() -> {
                        sorter.setSortKeys(List.of(new RowSorter.SortKey(modelColumn, order)));
                        return null;
                    });
                    exercised.add(option);
                }
            }
        }
        onEdt(() -> {
            if (sorter != null) {
                sorter.setSortKeys(originalKeys);
            }
            table.clearSelection();
            for (int modelRow : selectedModelRows) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.addRowSelectionInterval(viewRow, viewRow);
                }
            }
            return null;
        });
        boolean restored = onEdt(() -> java.util.Arrays.equals(selectedModelRows,
                java.util.Arrays.stream(table.getSelectedRows())
                        .map(table::convertRowIndexToModel).toArray())
                && (sorter == null || Objects.equals(originalKeys, sorter.getSortKeys())));
        settleSurface();
        return sweep(route, "table", table, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseList(String route, JList<?> list) {
        int[] original = onEdt(list::getSelectedIndices);
        int size = onEdt(() -> list.getModel().getSize());
        List<String> available = new ArrayList<>();
        List<String> exercised = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            int selected = row;
            String option = "row " + row + " · " + String.valueOf(
                    onEdt(() -> list.getModel().getElementAt(selected)));
            available.add(option);
            onEdt(() -> {
                list.setSelectedIndex(selected);
                list.ensureIndexIsVisible(selected);
                return null;
            });
            exercised.add(option);
        }
        if (size > 1 && list.getSelectionMode()
                != javax.swing.ListSelectionModel.SINGLE_SELECTION) {
            int[] all = java.util.stream.IntStream.range(0, size).toArray();
            available.add("all rows");
            onEdt(() -> {
                list.setSelectedIndices(all);
                return null;
            });
            exercised.add("all rows");
        }
        onEdt(() -> {
            list.setSelectedIndices(original);
            return null;
        });
        boolean restored = onEdt(() -> java.util.Arrays.equals(
                original, list.getSelectedIndices()));
        settleSurface();
        return sweep(route, "list", list, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseToggle(String route, JToggleButton button) {
        boolean original = onEdt(button::isSelected);
        ButtonGroup group = onEdt(() -> button.getModel() instanceof DefaultButtonModel model
                ? model.getGroup() : null);
        ButtonModel originalGroupSelection = group == null
                ? null : onEdt(group::getSelection);
        List<Boolean> states = button instanceof JRadioButton
                ? List.of(true) : List.of(false, true);
        List<String> available = states.stream().map(String::valueOf).toList();
        List<String> exercised = new ArrayList<>();
        for (boolean state : states) {
            onEdt(() -> {
                requireUsable(button, "toggle", componentSelector(button));
                if (button.isSelected() != state) {
                    button.doClick();
                }
                return null;
            });
            if (onEdt(button::isSelected) == state) {
                exercised.add(String.valueOf(state));
            }
        }
        onEdt(() -> {
            if (group != null) {
                if (originalGroupSelection == null) {
                    group.clearSelection();
                } else {
                    group.setSelected(originalGroupSelection, true);
                }
            } else if (button.isSelected() != original && button.isEnabled()) {
                button.doClick();
            }
            return null;
        });
        boolean restored = onEdt(() -> button.isSelected() == original
                && (group == null || group.getSelection() == originalGroupSelection));
        settleSurface();
        return sweep(route, "toggle", button, available, exercised, restored);
    }

    private RoutedOptionSweep exerciseText(String route, JTextComponent field) {
        String original = onEdt(field::getText);
        String probe = original.isBlank() ? "Harness Å花 123" : "";
        List<String> available = new ArrayList<>(List.of(
                original.isBlank() ? "unicode probe" : "blank", "restore original"));
        List<String> exercised = new ArrayList<>();
        onEdt(() -> {
            field.setText(probe);
            return null;
        });
        if (Objects.equals(onEdt(field::getText), probe)) {
            exercised.add(available.get(0));
        }
        onEdt(() -> {
            field.setText(original);
            return null;
        });
        exercised.add("restore original");
        if (field instanceof JTextField singleLine
                && onEdt(() -> singleLine.getActionListeners().length) > 0) {
            available.add("Enter");
            onEdt(() -> {
                singleLine.postActionEvent();
                return null;
            });
            exercised.add("Enter");
            settleSurface();
        }
        boolean restored = Objects.equals(onEdt(field::getText), original);
        String kind = field instanceof JPasswordField ? "password-field" : "field";
        return sweep(route, kind, field, available, exercised, restored);
    }

    private static List<Object> spinnerValues(JSpinner spinner) {
        Object current = spinner.getValue();
        LinkedHashSet<Object> values = new LinkedHashSet<>();
        if (spinner.getModel() instanceof SpinnerListModel list) {
            values.addAll(list.getList());
        } else if (spinner.getModel() instanceof SpinnerNumberModel number) {
            addNumberValues(number, values);
        } else if (spinner.getModel() instanceof SpinnerDateModel date) {
            if (date.getStart() != null) {
                values.add(date.getStart());
            }
            Object previous = date.getPreviousValue();
            if (previous != null) {
                values.add(previous);
            }
            values.add(current);
            Object next = date.getNextValue();
            if (next != null) {
                values.add(next);
            }
            if (date.getEnd() != null) {
                values.add(date.getEnd());
            }
        } else {
            Object previous = spinner.getPreviousValue();
            if (previous != null) {
                values.add(previous);
            }
            values.add(current);
            Object next = spinner.getNextValue();
            if (next != null) {
                values.add(next);
            }
        }
        values.add(current);
        return List.copyOf(values);
    }

    private static void addNumberValues(
            SpinnerNumberModel model, LinkedHashSet<Object> values) {
        Number current = model.getNumber();
        Comparable<?> minimum = model.getMinimum();
        Comparable<?> maximum = model.getMaximum();
        BigDecimal step = new BigDecimal(String.valueOf(model.getStepSize()));
        if (minimum instanceof Number min && maximum instanceof Number max
                && step.signum() > 0) {
            BigDecimal start = new BigDecimal(String.valueOf(min));
            BigDecimal end = new BigDecimal(String.valueOf(max));
            BigDecimal choiceCount = end.subtract(start).divideToIntegralValue(step)
                    .add(BigDecimal.ONE);
            if (choiceCount.compareTo(BigDecimal.valueOf(1000)) <= 0) {
                for (BigDecimal value = start; value.compareTo(end) <= 0;
                     value = value.add(step)) {
                    values.add(convertNumber(value, current));
                }
                return;
            }
            values.add(convertNumber(start, current));
            values.add(current);
            values.add(convertNumber(end, current));
            return;
        }
        Object previous = model.getPreviousValue();
        if (previous != null) {
            values.add(previous);
        }
        values.add(current);
        Object next = model.getNextValue();
        if (next != null) {
            values.add(next);
        }
    }

    private static Number convertNumber(BigDecimal value, Number template) {
        if (template instanceof Integer) {
            return value.intValueExact();
        }
        if (template instanceof Long) {
            return value.longValueExact();
        }
        if (template instanceof Short) {
            return value.shortValueExact();
        }
        if (template instanceof Byte) {
            return value.byteValueExact();
        }
        if (template instanceof Float) {
            return value.floatValue();
        }
        if (template instanceof Double) {
            return value.doubleValue();
        }
        return value;
    }

    private static boolean implementationDetail(Component component) {
        Component parent = component.getParent();
        while (parent != null) {
            if (parent instanceof JComboBox<?> || parent instanceof JSpinner) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static RoutedOptionSweep sweep(
            String route, String kind, JComponent component,
            List<String> available, List<String> exercised, boolean restored) {
        return new RoutedOptionSweep(route, kind, componentSelector(component),
                List.copyOf(available), List.copyOf(exercised), restored);
    }

    private static String componentSelector(JComponent component) {
        String selector = firstNonBlank(accessibleName(component), component.getName());
        if (selector.isBlank() && component instanceof AbstractButton button) {
            selector = firstNonBlank(selector, button.getText());
        }
        if (selector.isBlank() && component instanceof JScrollPane scroll
                && scroll.getViewport().getView() instanceof JComponent view) {
            selector = firstNonBlank(accessibleName(view), view.getName());
        }
        return selector.isBlank() ? component.getClass().getSimpleName() : selector;
    }

    /**
     * Activates each distinct enabled application button once while walking all
     * reachable nested tabs. Dialog and file boundaries must be supplied by the
     * caller so destructive actions can be cancelled safely.
     */
    public List<RoutedButtonSweep> clickEveryEnabledButtonOnEveryTabSurface() {
        List<RoutedButtonSweep> sweeps = new ArrayList<>();
        Set<AbstractButton> alreadyExercised =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JTabbedPane> activePath = Collections.newSetFromMap(new IdentityHashMap<>());
        visitEveryTabSurface(activePath, new ArrayList<>(), () -> {
            String route = currentRoute();
            List<AbstractButton> buttons = onEdt(() ->
                    components(AbstractButton.class).stream()
                            .filter(button -> !(button instanceof BasicArrowButton))
                            .filter(this::visibleThroughRoot)
                            .filter(AbstractButton::isEnabled)
                            .filter(button -> !alreadyExercised.contains(button))
                            .toList());
            alreadyExercised.addAll(buttons);
            List<String> discovered = buttons.stream()
                    .map(SwingUserHarness::buttonSelector).toList();
            List<String> clicked = new ArrayList<>();
            List<String> unavailable = new ArrayList<>();
            transcript.add("ROUTED BUTTON STORM " + route + " · " + discovered);
            for (AbstractButton button : buttons) {
                String selector = buttonSelector(button);
                boolean usable = onEdt(() -> visibleThroughRoot(button) && button.isEnabled());
                if (!usable) {
                    unavailable.add(selector);
                    transcript.add("ROUTED BUTTON STORM UNAVAILABLE " + route + " · "
                            + selector);
                    continue;
                }
                onEdt(() -> {
                    button.requestFocusInWindow();
                    button.doClick();
                    return null;
                });
                clicked.add(selector);
                transcript.add("ROUTED BUTTON STORM CLICK " + route + " · " + selector);
                settleSurface();
            }
            sweeps.add(new RoutedButtonSweep(route, List.copyOf(discovered),
                    List.copyOf(clicked), List.copyOf(unavailable)));
        });
        transcript.add("ROUTED BUTTON STORM COMPLETE · unique buttons "
                + alreadyExercised.size());
        return List.copyOf(sweeps);
    }

    /**
     * Activates every application-owned button that is enabled on the current
     * visible surface. The snapshot is taken before the first click; controls
     * that a preceding action legitimately hides or locks are reported rather
     * than silently disappearing from coverage.
     */
    public ButtonSweep clickEveryEnabledButton() {
        List<AbstractButton> discoveredButtons = onEdt(() ->
                components(AbstractButton.class).stream()
                        .filter(button -> !(button instanceof BasicArrowButton))
                        .filter(this::visibleThroughRoot)
                        .filter(AbstractButton::isEnabled)
                        .toList());
        List<String> discovered = discoveredButtons.stream()
                .map(SwingUserHarness::buttonSelector)
                .toList();
        List<String> clicked = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        transcript.add("BUTTON STORM DISCOVERED " + discovered.size() + " · " + discovered);
        for (AbstractButton button : discoveredButtons) {
            String selector = buttonSelector(button);
            boolean usable = onEdt(() -> visibleThroughRoot(button) && button.isEnabled());
            if (!usable) {
                skipped.add(selector);
                transcript.add("BUTTON STORM SKIP " + selector + " · hidden or locked");
                continue;
            }
            onEdt(() -> {
                button.requestFocusInWindow();
                button.doClick();
                return null;
            });
            clicked.add(selector);
            transcript.add("BUTTON STORM CLICK " + selector);
            settleSurface();
        }
        transcript.add("BUTTON STORM COMPLETE · clicked " + clicked.size()
                + " · skipped " + skipped.size());
        return new ButtonSweep(List.copyOf(discovered), List.copyOf(clicked),
                List.copyOf(skipped));
    }

    public List<String> transcript() {
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    private void visitEveryTabSurface(Set<JTabbedPane> activePath,
                                      List<String> route, Runnable leafAction) {
        JTabbedPane next = onEdt(() -> components(JTabbedPane.class).stream()
                .filter(this::visibleThroughRoot)
                .filter(tabs -> !activePath.contains(tabs))
                .findFirst().orElse(null));
        if (next == null) {
            routeSnapshot.set(List.copyOf(route));
            leafAction.run();
            return;
        }
        int original = onEdt(next::getSelectedIndex);
        activePath.add(next);
        try {
            int count = onEdt(next::getTabCount);
            for (int index = 0; index < count; index++) {
                int selected = index;
                String title = onEdt(() -> next.getTitleAt(selected));
                onEdt(() -> {
                    next.setSelectedIndex(selected);
                    layoutRecursively(root);
                    return null;
                });
                route.add(title == null || title.isBlank()
                        ? "tab " + (selected + 1) : title);
                visitEveryTabSurface(activePath, route, leafAction);
                route.remove(route.size() - 1);
            }
        } finally {
            activePath.remove(next);
            if (original >= 0 && original < onEdt(next::getTabCount)) {
                onEdt(() -> {
                    next.setSelectedIndex(original);
                    layoutRecursively(root);
                    return null;
                });
            }
        }
    }

    private final ThreadLocal<List<String>> routeSnapshot =
            ThreadLocal.withInitial(List::of);

    private String currentRoute() {
        List<String> route = routeSnapshot.get();
        return route.isEmpty() ? "default surface" : String.join(" → ", route);
    }

    private <T extends Component> T uniqueAccessible(String accessibleName, Class<T> type) {
        List<T> matches = onEdt(() -> components(type).stream()
                .filter(Component::isVisible)
                .filter(component -> accessibleName.equals(accessibleName(component)))
                .toList());
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible " + type.getSimpleName()
                    + " with accessible name '" + accessibleName + "' but found "
                    + matches.size());
        }
        return matches.get(0);
    }

    private AbstractButton uniqueButton(String text) {
        List<AbstractButton> matches = onEdt(() -> components(AbstractButton.class).stream()
                .filter(Component::isVisible)
                .filter(button -> text.equals(button.getText()))
                .toList());
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible button named '" + text
                    + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private AbstractButton uniqueToggle(String accessibleName) {
        List<AbstractButton> matches = onEdt(() -> components(AbstractButton.class).stream()
                .filter(Component::isVisible)
                .filter(button -> accessibleName.equals(accessibleName(button))
                        || accessibleName.equals(button.getText()))
                .toList());
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible toggle named '" + accessibleName
                    + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private List<String> visibleTexts() {
        return onEdt(() -> components(Component.class).stream()
                .filter(Component::isVisible)
                .flatMap(component -> List.of(
                        textOf(component), accessibleName(component)).stream())
                .filter(text -> text != null && !text.isBlank())
                .toList());
    }

    private <T extends Component> List<T> components(Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, result);
        return result;
    }

    private boolean visibleThroughRoot(Component component) {
        Component cursor = component;
        while (cursor != null) {
            if (!cursor.isVisible()) {
                return false;
            }
            if (cursor == root) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private void settleSurface() {
        long minimum = System.nanoTime() + Duration.ofMillis(150).toNanos();
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        String previous = null;
        int stableSamples = 0;
        while (System.nanoTime() < deadline) {
            String current = onEdt(this::surfaceFingerprint);
            if (Objects.equals(previous, current)) {
                stableSamples++;
            } else {
                previous = current;
                stableSamples = 0;
            }
            if (System.nanoTime() >= minimum && stableSamples >= 3) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while settling the Swing surface",
                        exception);
            }
        }
        transcript.add("SURFACE SETTLE TIMEOUT · surface still changing");
        throw new AssertionError("Swing surface did not settle within 2 seconds; "
                + "last observed controls: " + previous);
    }

    private String surfaceFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        for (AbstractButton button : components(AbstractButton.class)) {
            if (!(button instanceof BasicArrowButton) && visibleThroughRoot(button)) {
                fingerprint.append(buttonSelector(button)).append('=')
                        .append(button.isEnabled()).append('/')
                        .append(button.isSelected()).append(';');
            }
        }
        for (JComboBox<?> combo : components(JComboBox.class)) {
            if (visibleThroughRoot(combo)) {
                fingerprint.append(componentSelector(combo)).append('=')
                        .append(combo.isEnabled()).append('/')
                        .append(combo.getItemCount()).append('/')
                        .append(combo.getSelectedIndex()).append(';');
            }
        }
        for (JTable table : components(JTable.class)) {
            if (visibleThroughRoot(table)) {
                fingerprint.append(componentSelector(table)).append('=')
                        .append(table.isEnabled()).append('/')
                        .append(table.getRowCount()).append('/')
                        .append(table.getSelectedRowCount()).append(';');
            }
        }
        return fingerprint.toString();
    }

    private static <T extends Component> void collect(
            Component component, Class<T> type, List<T> result) {
        if (type.isInstance(component)) {
            result.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, result);
            }
        }
    }

    private static void collectInteractive(Component component, List<Control> controls) {
        if (!component.isVisible()) {
            return;
        }
        // Swing installs unlabeled arrow buttons inside split panes, scroll bars,
        // combo boxes, and spinners. They are look-and-feel implementation details,
        // not application-owned controls that a journey can address by label.
        if (component instanceof BasicArrowButton) {
            return;
        }
        if (component instanceof AbstractButton button) {
            controls.add(new Control("button", firstNonBlank(
                    accessibleName(button), button.getText()), button.isEnabled(),
                    button.getAction() != null
                            || button.getActionListeners().length > 0
                            || button.getItemListeners().length > 0,
                    button.isFocusable()));
            return;
        }
        if (component instanceof JComboBox<?> combo) {
            controls.add(new Control(
                    "selector", accessibleName(combo), combo.isEnabled(), true,
                    combo.isFocusable()));
            return;
        }
        if (component instanceof JTextComponent text && text.isEditable()) {
            controls.add(new Control(
                    "field", accessibleName(text), text.isEnabled(), true,
                    text.isFocusable()));
            return;
        }
        if (component instanceof JTabbedPane tabs) {
            controls.add(new Control("tabs", accessibleName(tabs), tabs.isEnabled(), true,
                    tabs.isFocusable()));
            for (int index = 0; index < tabs.getTabCount(); index++) {
                Component child = tabs.getComponentAt(index);
                if (index == tabs.getSelectedIndex()) {
                    collectInteractive(child, controls);
                }
            }
            return;
        }
        if (component instanceof JTable || component instanceof JList<?>
                || component instanceof JSpinner || component instanceof JSlider) {
            if (component instanceof JComponent interactive) {
                boolean wired = !(component instanceof JSlider slider)
                        || slider.getChangeListeners().length > 0;
                controls.add(new Control(component.getClass().getSimpleName(),
                        accessibleName(interactive), interactive.isEnabled(), wired,
                        interactive.isFocusable()));
            }
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

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static int tabIndex(JTabbedPane tabs, String title) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (title.equals(tabs.getTitleAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> comboItems(JComboBox<?> combo) {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            items.add(comboItemText(combo, index));
        }
        return items;
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

    private static String accessibleName(Component component) {
        if (!(component instanceof JComponent swing)
                || swing.getAccessibleContext() == null) {
            return "";
        }
        String name = swing.getAccessibleContext().getAccessibleName();
        return name == null ? "" : name.trim();
    }

    private static String textOf(Component component) {
        if (component instanceof JTextComponent text) {
            return text.getText();
        }
        if (component instanceof AbstractButton button) {
            return button.getText();
        }
        if (component instanceof javax.swing.JLabel label) {
            return label.getText();
        }
        return "";
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first
                : second == null ? "" : second.trim();
    }

    private static String buttonSelector(AbstractButton button) {
        return firstNonBlank(accessibleName(button), button.getText());
    }

    private static void requireUsable(Component component, String kind, String selector) {
        if (!component.isVisible()) {
            throw new AssertionError("The " + kind + " is hidden: " + selector);
        }
        if (!component.isEnabled()) {
            throw new AssertionError("The " + kind + " is disabled: " + selector);
        }
    }

    private static String printable(String value) {
        return value == null ? "<blank>" : '"' + value.replace("\n", "\\n") + '"';
    }

    private static void await(String description, Duration timeout, Callable<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.call()) {
                    return;
                }
            } catch (Throwable failure) {
                lastFailure = failure;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description,
                        exception);
            }
        }
        AssertionError timeoutFailure = new AssertionError("Timed out waiting for " + description);
        if (lastFailure != null) {
            timeoutFailure.initCause(lastFailure);
        }
        throw timeoutFailure;
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
            throw new AssertionError("Interrupted while interacting with Swing", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new AssertionError("Swing event-dispatch failure", exception.getCause());
        }
        if (failure[0] != null) {
            if (failure[0] instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure[0] instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Swing action failed", failure[0]);
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
            throw new AssertionError("Swing action failed", exception);
        }
    }

    /** One visible interactive control and its user-operability contract. */
    public record Control(String kind, String selector, boolean enabled, boolean wired,
                          boolean focusable) { }

    public record SurfaceAudit(List<Control> controls, List<String> defects) { }

    /** One complete leaf route through all visible nested tab sets. */
    public record RoutedSurfaceAudit(String route, SurfaceAudit audit) { }

    /** Exact option-by-option evidence for one control on one nested-tab route. */
    public record RoutedOptionSweep(String route, String kind, String selector,
                                    List<String> available, List<String> exercised,
                                    boolean restored) { }

    /** Exact coverage evidence from one current-surface button storm. */
    public record ButtonSweep(List<String> discovered, List<String> clicked,
                              List<String> skipped) { }

    /** Exact unique-button evidence for one nested-tab route. */
    public record RoutedButtonSweep(String route, List<String> discovered,
                                    List<String> clicked, List<String> unavailable) { }

    @FunctionalInterface
    private interface OptionExercise<T extends JComponent> {
        RoutedOptionSweep run(String route, T component);
    }

    /**
     * Scoped guard for the failure mode where the AWT event thread prints an
     * exception but the owning JUnit journey would otherwise stay green.
     */
    public static final class AsyncFailureMonitor implements AutoCloseable {
        private final Thread.UncaughtExceptionHandler previous;
        private final Thread.UncaughtExceptionHandler installed;
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();
        private boolean closed;

        private AsyncFailureMonitor() {
            previous = Thread.getDefaultUncaughtExceptionHandler();
            installed = (thread, failure) -> {
                failures.add(failure);
                if (previous != null && !thread.getName().startsWith("AWT-EventQueue")) {
                    previous.uncaughtException(thread, failure);
                }
            };
            Thread.setDefaultUncaughtExceptionHandler(installed);
        }

        public void assertNone() {
            onEdt(() -> null);
            if (!failures.isEmpty()) {
                AssertionError error = new AssertionError(
                        failures.size() + " uncaught asynchronous UI failure(s); first: "
                                + failures.get(0));
                error.initCause(failures.get(0));
                for (int index = 1; index < failures.size(); index++) {
                    error.addSuppressed(failures.get(index));
                }
                throw error;
            }
        }

        public List<Throwable> failures() {
            return List.copyOf(failures);
        }

        @Override
        public void close() {
            if (!closed && Thread.getDefaultUncaughtExceptionHandler() == installed) {
                Thread.setDefaultUncaughtExceptionHandler(previous);
            }
            closed = true;
        }
    }

    /** Width and height that survive clipping by split panes, tabs, and scroll viewports. */
    public record VisibleExtent(int width, int height) { }

    /** One accessible field/value pair in a deterministic data-entry case. */
    public record TextInput(String accessibleName, String value) {
        public TextInput {
            if (accessibleName == null || accessibleName.isBlank()) {
                throw new IllegalArgumentException("accessibleName is required");
            }
        }
    }
}

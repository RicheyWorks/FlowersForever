package com.flowerfarm.gui.harness;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in driver for interactions that require an installed desktop.
 *
 * <p>Unlike {@link SwingUserHarness}, this driver opens a real window and sends
 * native pointer and keyboard input through {@link Robot}. It is deliberately
 * excluded from the ordinary headless suite; use the installed-desktop Maven
 * profile on a workstation whose screen can be temporarily controlled.
 */
public final class InstalledDesktopHarness implements AutoCloseable {

    private static final Duration INPUT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration NATIVE_RETRY_DELAY = Duration.ofMillis(900);

    private final JComponent root;
    private final JFrame frame;
    private final Robot robot;
    private final List<String> transcript = new ArrayList<>();

    private InstalledDesktopHarness(String title, JComponent root, Dimension size) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                    "Installed desktop smoke requires a non-headless graphics environment");
        }
        this.root = Objects.requireNonNull(root, "root");
        frame = onEdt(() -> {
            JFrame window = new JFrame(title);
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            // The opt-in profile intentionally owns the workstation while it runs.
            // Keeping the probe above the IDE/browser prevents native Robot input
            // from landing in whichever window emitted the Maven command.
            window.setAlwaysOnTop(true);
            window.setContentPane(root);
            window.setSize(size);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            window.toFront();
            window.requestFocus();
            return window;
        });
        try {
            robot = new Robot(frame.getGraphicsConfiguration().getDevice());
        } catch (AWTException exception) {
            onEdt(() -> {
                frame.dispose();
                return null;
            });
            throw new IllegalStateException("Could not create the native desktop input driver",
                    exception);
        }
        robot.setAutoDelay(45);
        robot.setAutoWaitForIdle(true);
        try {
            activateWindow();
            await("window activation", () -> onEdt(frame::isActive));
        } catch (RuntimeException | Error failure) {
            onEdt(() -> {
                frame.dispose();
                return null;
            });
            throw failure;
        }
        transcript.add("OPEN " + title + " " + size.width + "x" + size.height);
    }

    /** Opens one production component tree in a genuine top-level window. */
    public static InstalledDesktopHarness open(
            String title, JComponent root, Dimension size) {
        return new InstalledDesktopHarness(title, root, size);
    }

    /** Physically clicks the center of one named or accessible component. */
    public InstalledDesktopHarness click(String selector) {
        Component target = unique(selector, Component.class);
        pointerClick(target, selector, true);
        transcript.add("POINTER CLICK " + selector);
        return this;
    }

    /**
     * Physically opens a modal window without requiring focus to remain on the
     * launcher after its listener enters Swing's nested modal event loop.
     */
    public InstalledDesktopHarness clickAndAwaitWindow(String selector, String title) {
        Component target = unique(selector, Component.class);
        pointerClick(target, selector, false);
        await("window '" + title + "' to open", () -> onEdt(() ->
                matchingWindows(title).size() == 1));
        await("window '" + title + "' to receive focus", () -> onEdt(() ->
                focusInside(uniqueWindow(title))));
        transcript.add("POINTER OPEN WINDOW " + selector + " -> " + title);
        return this;
    }

    /** Clicks a control in a modal window and proves that the window closes. */
    public InstalledDesktopHarness clickInWindowAndAwaitClosed(
            String title, String selector) {
        Window window = onEdt(() -> uniqueWindow(title));
        Component target = unique(window, selector, Component.class);
        pointerClick(target, title + " / " + selector, false);
        await("window '" + title + "' to close", () -> onEdt(() ->
                matchingWindows(title).isEmpty()));
        await("application window to regain focus", () -> onEdt(() ->
                frame.isActive() && focusInside(frame)));
        transcript.add("POINTER CLOSE WINDOW " + title + " / " + selector);
        return this;
    }

    /** Returns every visible button label in one installed modal window. */
    public List<String> windowButtonSelectors(String title) {
        return onEdt(() -> {
            Window window = uniqueWindow(title);
            List<AbstractButton> buttons = new ArrayList<>();
            collect(window, AbstractButton.class, buttons);
            return buttons.stream()
                    .filter(Component::isShowing)
                    .filter(button -> !(button instanceof BasicArrowButton))
                    .map(InstalledDesktopHarness::selector)
                    .distinct()
                    .toList();
        });
    }

    /** Proves that native focus is owned by the requested top-level window. */
    public boolean windowOwnsFocus(String title) {
        return onEdt(() -> focusInside(uniqueWindow(title)));
    }

    /** Returns the file selected by the real JFileChooser before approval/cancel. */
    public Path selectedFileInChooser(String title) {
        return onEdt(() -> {
            Window window = uniqueWindow(title);
            List<JFileChooser> choosers = new ArrayList<>();
            collect(window, JFileChooser.class, choosers);
            if (choosers.size() != 1) {
                throw new AssertionError("Expected one visible JFileChooser in '"
                        + title + "' but found " + choosers.size());
            }
            java.io.File selected = choosers.get(0).getSelectedFile();
            if (selected == null) {
                throw new AssertionError("JFileChooser in '" + title
                        + "' has no suggested file");
            }
            return selected.toPath().toAbsolutePath().normalize();
        });
    }

    private void pointerClick(Component target, String description, boolean expectFocus) {
        AtomicBoolean received = new AtomicBoolean();
        MouseAdapter probe = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                received.set(true);
            }
        };
        onEdt(() -> {
            target.addMouseListener(probe);
            return null;
        });
        try {
            clickCenter(target);
            await("native pointer event on " + description, received::get);
            if (expectFocus && onEdt(target::isFocusable)) {
                await("focus after clicking " + description,
                        () -> onEdt(target::isFocusOwner));
            }
        } finally {
            onEdt(() -> {
                target.removeMouseListener(probe);
                return null;
            });
        }
    }

    /** Physically clicks one visible table row, preserving Swing's normal selection handling. */
    public InstalledDesktopHarness clickRow(String tableName, int viewRow) {
        JTable table = unique(tableName, JTable.class);
        clickTableRow(tableName, table, viewRow);
        await("row " + viewRow + " selection in " + tableName,
                () -> onEdt(() -> table.isRowSelected(viewRow)));
        transcript.add("POINTER ROW " + tableName + " = " + viewRow);
        return this;
    }

    /** Selects several table rows through physical click then Ctrl/Cmd-click input. */
    public InstalledDesktopHarness selectRows(String tableName, int... viewRows) {
        if (viewRows == null || viewRows.length == 0) {
            throw new IllegalArgumentException("At least one row is required");
        }
        JTable table = unique(tableName, JTable.class);
        clickTableRow(tableName, table, viewRows[0]);
        int modifier = menuShortcutKey();
        for (int index = 1; index < viewRows.length; index++) {
            keyDown(modifier);
            try {
                clickTableRow(tableName, table, viewRows[index]);
            } finally {
                keyUp(modifier);
            }
        }
        await("multi-row selection in " + tableName,
                () -> onEdt(() -> table.getSelectedRowCount() == viewRows.length
                        && java.util.Arrays.stream(viewRows).allMatch(table::isRowSelected)));
        transcript.add("POINTER MULTI-ROW " + tableName + " = "
                + java.util.Arrays.toString(viewRows));
        return this;
    }

    /** Selects a rendered combo option with native Home/Down/Enter keys. */
    public InstalledDesktopHarness select(String selector, String displayedValue) {
        JComboBox<?> combo = unique(selector, JComboBox.class);
        int option = onEdt(() -> comboIndex(combo, displayedValue));
        if (onEdt(combo::isPopupVisible)) {
            tap(KeyEvent.VK_ESCAPE);
            await("combo popup to close for " + selector,
                    () -> onEdt(() -> !combo.isPopupVisible()));
        }
        click(selector);
        await("combo popup to open for " + selector,
                () -> onEdt(combo::isPopupVisible));
        comboHome(combo, selector);
        for (int index = 0; index < option; index++) {
            int expected = index + 1;
            comboDown(combo, selector, expected);
        }
        comboEnter(combo, selector, displayedValue, option);
        transcript.add("KEYBOARD SELECT " + selector + " = " + displayedValue);
        return this;
    }

    /** Selects every rendered combo choice through native keys, then restores it. */
    public List<String> exerciseEveryOption(String selector) {
        JComboBox<?> combo = unique(selector, JComboBox.class);
        int original = onEdt(combo::getSelectedIndex);
        List<String> options = comboOptions(selector);
        for (String option : options) {
            select(selector, option);
        }
        if (original >= 0 && original < options.size()) {
            select(selector, options.get(original));
        }
        transcript.add("KEYBOARD OPTION MATRIX " + selector + " = " + options);
        return options;
    }

    /** Physically clicks a visible table header and proves the row sorter changed. */
    public InstalledDesktopHarness sortByHeader(String tableName, String columnName) {
        JTable table = unique(tableName, JTable.class);
        JTableHeader header = onEdt(table::getTableHeader);
        int viewColumn = onEdt(() -> viewColumn(table, columnName));
        if (onEdt(table::getRowSorter) == null) {
            throw new AssertionError(tableName + " has no production row sorter");
        }
        List<? extends javax.swing.RowSorter.SortKey> before = onEdt(() ->
                List.copyOf(table.getRowSorter().getSortKeys()));
        Rectangle bounds = onEdt(() -> header.getHeaderRect(viewColumn));
        AtomicBoolean received = new AtomicBoolean();
        MouseAdapter probe = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                received.set(true);
            }
        };
        onEdt(() -> {
            header.addMouseListener(probe);
            return null;
        });
        try {
            robot.delay(180);
            clickAt(header, bounds.x + Math.max(4, bounds.width / 2),
                    bounds.y + Math.max(4, bounds.height / 2));
            await("native pointer event on " + columnName + " header in " + tableName,
                    received::get);
        } finally {
            onEdt(() -> {
                header.removeMouseListener(probe);
                return null;
            });
        }
        await("sort order for " + columnName + " in " + tableName, () -> onEdt(() ->
                table.getRowSorter() != null
                        && !before.equals(table.getRowSorter().getSortKeys())));
        robot.delay(180);
        transcript.add("POINTER SORT " + tableName + " = " + columnName);
        return this;
    }

    /** Replaces a field value using only native Ctrl+A and key events. */
    public InstalledDesktopHarness replaceText(String selector, String text) {
        click(selector);
        chord(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        for (int index = 0; index < text.length(); index++) {
            typeCharacter(text.charAt(index));
        }
        transcript.add("KEYBOARD TYPE " + selector + " = " + text);
        return this;
    }

    /** Sends a physical Tab and returns the focus reached after traversal settles. */
    public FocusTarget pressTab() {
        Component previous = onEdt(this::focusOwner);
        FocusTarget target = traverseFocus("Tab", previous,
                () -> tap(KeyEvent.VK_TAB));
        transcript.add("TAB -> " + target.selector());
        return target;
    }

    /** Sends a physical Shift+Tab and returns the focus reached after traversal settles. */
    public FocusTarget pressShiftTab() {
        Component previous = onEdt(this::focusOwner);
        FocusTarget target = traverseFocus("Shift+Tab", previous, () -> {
            keyDown(KeyEvent.VK_SHIFT);
            try {
                tap(KeyEvent.VK_TAB);
            } finally {
                keyUp(KeyEvent.VK_SHIFT);
            }
        });
        transcript.add("SHIFT+TAB -> " + target.selector());
        return target;
    }

    /**
     * Walks a worker-sized portion of the forward focus cycle and proves that
     * native traversal never leaves the mounted production component tree.
     */
    public List<FocusTarget> walkForwardFocusCycle(int steps) {
        if (steps < 1) {
            throw new IllegalArgumentException("steps must be positive");
        }
        List<FocusTarget> targets = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            FocusTarget target;
            try {
                target = pressForwardTraversal();
            } catch (AssertionError failure) {
                throw new AssertionError("Forward focus step " + (index + 1)
                        + " failed after " + targets + "; current="
                        + onEdt(this::currentFocus), failure);
            }
            if (!target.insideRoot()) {
                throw new AssertionError("Tab traversal escaped the production surface: "
                        + target);
            }
            targets.add(target);
        }
        return List.copyOf(targets);
    }

    private FocusTarget pressForwardTraversal() {
        Component previous = onEdt(this::focusOwner);
        if (previous instanceof JTable || previous instanceof JTextArea) {
            FocusTarget target = traverseFocus("Ctrl+Tab", previous,
                    () -> chord(KeyEvent.VK_CONTROL, KeyEvent.VK_TAB));
            transcript.add("CTRL+TAB -> " + target.selector());
            return target;
        }
        return pressTab();
    }

    /** Captures the actual pixels occupied by the installed application window. */
    public Path screenshot(Path destination) {
        Rectangle bounds = onEdt(frame::getBounds);
        return screenshot(bounds, destination, "SCREENSHOT ");
    }

    /** Captures the complete native dialog or file chooser, including window chrome. */
    public Path screenshotWindow(String title, Path destination) {
        Rectangle bounds = onEdt(() -> uniqueWindow(title).getBounds());
        return screenshot(bounds, destination, "WINDOW SCREENSHOT " + title + " ");
    }

    private Path screenshot(Rectangle bounds, Path destination, String transcriptPrefix) {
        BufferedImage image = robot.createScreenCapture(bounds);
        try {
            Path absolute = destination.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            ImageIO.write(image, "png", absolute.toFile());
            transcript.add(transcriptPrefix + absolute);
            return absolute;
        } catch (IOException exception) {
            throw new AssertionError("Could not write installed desktop screenshot", exception);
        }
    }

    public String text(String selector) {
        Component component = unique(selector, Component.class);
        return onEdt(() -> {
            if (component instanceof JTextComponent text) {
                return text.getText();
            }
            if (component instanceof JLabel label) {
                return label.getText();
            }
            if (component instanceof AbstractButton button) {
                return button.getText();
            }
            throw new AssertionError(selector + " does not expose visible text");
        });
    }

    /** Returns all rendered choices in one visible combo. */
    public List<String> comboOptions(String selector) {
        JComboBox<?> combo = unique(selector, JComboBox.class);
        return onEdt(() -> {
            List<String> options = new ArrayList<>();
            for (int index = 0; index < combo.getItemCount(); index++) {
                options.add(comboItemText(combo, index));
            }
            return List.copyOf(options);
        });
    }

    public String selectedOption(String selector) {
        JComboBox<?> combo = unique(selector, JComboBox.class);
        return onEdt(() -> combo.getSelectedIndex() < 0 ? ""
                : comboItemText(combo, combo.getSelectedIndex()));
    }

    /** Exact inventory used to make newly added, untested buttons fail a smoke. */
    public List<String> applicationButtonSelectors() {
        return onEdt(() -> components(AbstractButton.class).stream()
                .filter(Component::isShowing)
                .filter(button -> !(button instanceof BasicArrowButton))
                .map(InstalledDesktopHarness::selector)
                .distinct()
                .toList());
    }

    /** Exact inventory of fields a native worker can edit on the mounted screen. */
    public List<String> editableTextSelectors() {
        return onEdt(() -> components(JTextComponent.class).stream()
                .filter(Component::isShowing)
                .filter(JTextComponent::isEditable)
                .map(InstalledDesktopHarness::selector)
                .distinct()
                .toList());
    }

    public int tableRows(String tableName) {
        JTable table = unique(tableName, JTable.class);
        return onEdt(table::getRowCount);
    }

    public List<String> tableColumns(String tableName) {
        JTable table = unique(tableName, JTable.class);
        return onEdt(() -> {
            List<String> columns = new ArrayList<>();
            for (int index = 0; index < table.getColumnCount(); index++) {
                columns.add(table.getColumnName(index));
            }
            return List.copyOf(columns);
        });
    }

    /** Returns one visible column value for every selected view row. */
    public List<String> selectedTableValues(String tableName, String columnName) {
        JTable table = unique(tableName, JTable.class);
        return onEdt(() -> {
            int column = viewColumn(table, columnName);
            List<String> values = new ArrayList<>();
            for (int row : table.getSelectedRows()) {
                values.add(String.valueOf(table.getValueAt(row, column)));
            }
            return List.copyOf(values);
        });
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    @Override
    public void close() {
        onEdt(() -> {
            frame.dispose();
            return null;
        });
        transcript.add("CLOSE");
    }

    private FocusTarget awaitFocus(String action, Component previous) {
        final FocusTarget[] observed = new FocusTarget[1];
        await(action + " focus traversal", () -> {
            Component current = onEdt(this::focusOwner);
            observed[0] = onEdt(this::currentFocus);
            return current != null && current != previous && observed[0] != null;
        });
        return observed[0];
    }

    private FocusTarget traverseFocus(
            String action, Component previous, Runnable nativeInput) {
        if (previous == null) {
            throw new AssertionError(action + " has no starting focus owner");
        }
        nativeInput.run();
        try {
            return awaitFocus(action, previous);
        } catch (AssertionError firstFailure) {
            onEdt(() -> {
                previous.requestFocusInWindow();
                return null;
            });
            ensureWindowActive(previous);
            await(action + " focus recovery", () -> onEdt(previous::isFocusOwner));
            transcript.add("FOCUS RETRY " + action + " FROM " + selector(previous));
            nativeInput.run();
            try {
                return awaitFocus(action + " retry", previous);
            } catch (AssertionError retryFailure) {
                retryFailure.addSuppressed(firstFailure);
                throw retryFailure;
            }
        }
    }

    private FocusTarget currentFocus() {
        Component owner = focusOwner();
        if (owner == null) {
            return null;
        }
        return new FocusTarget(selector(owner), owner.getClass().getSimpleName(),
                owner == root || SwingUtilities.isDescendingFrom(owner, root));
    }

    private Component focusOwner() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    private void clickCenter(Component target) {
        Dimension size = onEdt(target::getSize);
        clickAt(target, Math.max(1, size.width / 2), Math.max(1, size.height / 2));
    }

    private void clickTableRow(String tableName, JTable table, int viewRow) {
        Rectangle cell = onEdt(() -> {
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                throw new AssertionError("Row " + viewRow + " is outside " + tableName);
            }
            return table.getCellRect(viewRow, 0, true);
        });
        clickAt(table, cell.x + Math.max(4, cell.width / 2),
                cell.y + Math.max(4, cell.height / 2));
    }

    private void activateWindow() {
        onEdt(() -> {
            frame.toFront();
            frame.requestFocus();
            return null;
        });
        Rectangle bounds = onEdt(frame::getBounds);
        robot.mouseMove(bounds.x + bounds.width / 2, bounds.y + 10);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private void clickAt(Component target, int x, int y) {
        ensureWindowActive(target);
        Point location = onEdt(target::getLocationOnScreen);
        robot.mouseMove(location.x + x, location.y + y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private void ensureWindowActive(Component target) {
        Window targetWindow = onEdt(() -> SwingUtilities.getWindowAncestor(target));
        if (targetWindow == null) {
            targetWindow = frame;
        }
        Window expectedWindow = targetWindow;
        onEdt(() -> {
            expectedWindow.toFront();
            expectedWindow.requestFocus();
            return null;
        });
        await("installed smoke window activation", () -> onEdt(() ->
                expectedWindow.isActive() && focusInside(expectedWindow)));
    }

    private void chord(int modifier, int key) {
        keyDown(modifier);
        try {
            tap(key);
        } finally {
            keyUp(modifier);
        }
    }

    private void typeCharacter(char character) {
        int keyCode = KeyEvent.getExtendedKeyCodeForChar(character);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            throw new IllegalArgumentException("Robot cannot type character: " + character);
        }
        boolean shift = Character.isUpperCase(character);
        if (shift) {
            keyDown(KeyEvent.VK_SHIFT);
        }
        try {
            tap(keyCode);
        } finally {
            if (shift) {
                keyUp(KeyEvent.VK_SHIFT);
            }
        }
    }

    private void tap(int keyCode) {
        keyDown(keyCode);
        keyUp(keyCode);
    }

    private void comboHome(JComboBox<?> combo, String selector) {
        tap(KeyEvent.VK_HOME);
        Callable<Boolean> selected = () -> onEdt(() -> combo.getSelectedIndex() == 0);
        if (!waitFor(NATIVE_RETRY_DELAY, selected)) {
            ensureWindowActive(combo);
            transcript.add("KEY RETRY HOME IN " + selector);
            tap(KeyEvent.VK_HOME);
            await("Home selection in " + selector, selected);
        }
    }

    private void comboDown(JComboBox<?> combo, String selector, int expected) {
        int before = onEdt(combo::getSelectedIndex);
        tap(KeyEvent.VK_DOWN);
        Callable<Boolean> selected = () -> onEdt(() ->
                combo.getSelectedIndex() == expected);
        if (!waitFor(NATIVE_RETRY_DELAY, selected)) {
            int current = onEdt(combo::getSelectedIndex);
            if (current != before) {
                throw new AssertionError("Down in " + selector + " expected index "
                        + expected + " but reached " + current);
            }
            ensureWindowActive(combo);
            transcript.add("KEY RETRY DOWN IN " + selector + " TO " + expected);
            tap(KeyEvent.VK_DOWN);
            await("Down selection " + expected + " in " + selector, selected);
        }
    }

    private void comboEnter(
            JComboBox<?> combo, String selector, String displayedValue, int option) {
        tap(KeyEvent.VK_ENTER);
        Callable<Boolean> selected = () -> onEdt(() ->
                combo.getSelectedIndex() == option && !combo.isPopupVisible());
        if (!waitFor(NATIVE_RETRY_DELAY, selected)) {
            ensureWindowActive(combo);
            transcript.add("KEY RETRY ENTER IN " + selector);
            tap(KeyEvent.VK_ENTER);
            await("combo selection " + displayedValue + " in " + selector, selected);
        }
    }

    private void keyDown(int keyCode) {
        robot.keyPress(keyCode);
    }

    private void keyUp(int keyCode) {
        robot.keyRelease(keyCode);
    }

    private static int comboIndex(JComboBox<?> combo, String displayedValue) {
        int match = -1;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (displayedValue.equals(comboItemText(combo, index))) {
                if (match >= 0) {
                    throw new AssertionError("Multiple combo options named '"
                            + displayedValue + "'");
                }
                match = index;
            }
        }
        if (match < 0) {
            List<String> available = new ArrayList<>();
            for (int index = 0; index < combo.getItemCount(); index++) {
                available.add(comboItemText(combo, index));
            }
            throw new AssertionError("No combo option named '" + displayedValue
                    + "'. Available: " + available);
        }
        return match;
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

    private static int viewColumn(JTable table, String columnName) {
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (columnName.equals(table.getColumnName(column))) {
                return column;
            }
        }
        throw new AssertionError("No visible table column named '" + columnName + "'");
    }

    private static int menuShortcutKey() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        return (mask & InputEvent.META_DOWN_MASK) != 0
                ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
    }

    private <T extends Component> T unique(String selector, Class<T> type) {
        List<T> matches = onEdt(() -> {
            List<T> result = new ArrayList<>();
            collect(root, selector, type, result);
            return result;
        });
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible " + type.getSimpleName()
                    + " named '" + selector + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private <T extends Component> T unique(
            Component searchRoot, String selector, Class<T> type) {
        List<T> matches = onEdt(() -> {
            List<T> result = new ArrayList<>();
            collect(searchRoot, selector, type, result);
            return result;
        });
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible " + type.getSimpleName()
                    + " named '" + selector + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private static Window uniqueWindow(String title) {
        List<Window> matches = matchingWindows(title);
        if (matches.size() != 1) {
            throw new AssertionError("Expected one visible window titled '"
                    + title + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private static List<Window> matchingWindows(String title) {
        List<Window> matches = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && title.equals(windowTitle(window))) {
                matches.add(window);
            }
        }
        return matches;
    }

    private static String windowTitle(Window window) {
        if (window instanceof Dialog dialog) {
            return dialog.getTitle();
        }
        if (window instanceof Frame topLevelFrame) {
            return topLevelFrame.getTitle();
        }
        return "";
    }

    private static boolean focusInside(Window window) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusOwner();
        return owner != null && (owner == window
                || SwingUtilities.isDescendingFrom(owner, window));
    }

    private <T extends Component> List<T> components(Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, result);
        return result;
    }

    private static <T extends Component> void collect(
            Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, matches);
            }
        }
    }

    private static <T extends Component> void collect(
            Component component, String selector, Class<T> type, List<T> matches) {
        if (component.isShowing() && type.isInstance(component)
                && matchesSelector(component, selector)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, selector, type, matches);
            }
        }
    }

    private static boolean matchesSelector(Component component, String expected) {
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

    private static String selector(Component component) {
        if (component instanceof JComponent swing
                && swing.getAccessibleContext() != null
                && swing.getAccessibleContext().getAccessibleName() != null
                && !swing.getAccessibleContext().getAccessibleName().isBlank()) {
            return swing.getAccessibleContext().getAccessibleName();
        }
        if (component instanceof AbstractButton button && button.getText() != null
                && !button.getText().isBlank()) {
            return button.getText();
        }
        if (component.getName() != null && !component.getName().isBlank()) {
            return component.getName();
        }
        return component.getClass().getSimpleName();
    }

    private static void await(String description, Callable<Boolean> condition) {
        if (!waitFor(INPUT_TIMEOUT, condition)) {
            throw new AssertionError("Timed out waiting for " + description);
        }
    }

    private static boolean waitFor(Duration timeout, Callable<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (call(condition)) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for native state",
                        exception);
            }
        }
        return false;
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
            throw new AssertionError("Desktop harness action failed", exception);
        }
    }

    /** A native focus observation produced after a physical traversal key. */
    public record FocusTarget(String selector, String componentType, boolean insideRoot) { }

    /** Returns unique selectors from a focus walk without losing their encounter order. */
    public static Set<String> uniqueSelectors(List<FocusTarget> targets) {
        Set<String> selectors = new LinkedHashSet<>();
        targets.forEach(target -> selectors.add(target.selector()));
        return Collections.unmodifiableSet(selectors);
    }
}

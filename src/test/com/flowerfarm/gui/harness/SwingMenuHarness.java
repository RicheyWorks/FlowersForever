package com.flowerfarm.gui.harness;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

/** User-facing driver and wiring audit for the installed application's menu bar. */
public final class SwingMenuHarness {

    private final JMenuBar menuBar;
    private final List<String> transcript = new ArrayList<>();

    private SwingMenuHarness(JMenuBar menuBar) {
        this.menuBar = Objects.requireNonNull(menuBar, "menuBar");
    }

    public static SwingMenuHarness on(JMenuBar menuBar) {
        return new SwingMenuHarness(menuBar);
    }

    /** Audits labels, mnemonics, descriptions, unique paths, and action listeners. */
    public MenuAudit audit() {
        return onEdt(() -> {
            List<MenuControl> controls = new ArrayList<>();
            List<String> defects = new ArrayList<>();
            Set<String> paths = new LinkedHashSet<>();
            for (int menuIndex = 0; menuIndex < menuBar.getMenuCount(); menuIndex++) {
                JMenu menu = menuBar.getMenu(menuIndex);
                if (menu == null) {
                    continue;
                }
                String menuLabel = safe(menu.getText());
                if (menuLabel.isBlank()) {
                    defects.add("top-level menu has no visible label");
                }
                if (menu.getMnemonic() == 0) {
                    defects.add("menu '" + menuLabel + "' has no mnemonic");
                }
                if (description(menu).isBlank()) {
                    defects.add("menu '" + menuLabel + "' has no accessible description");
                }
                collect(menu, menuLabel, controls, defects, paths);
            }
            transcript.add("MENU AUDIT " + controls.size() + " item(s)");
            return new MenuAudit(List.copyOf(controls), List.copyOf(defects));
        });
    }

    /** Activates a menu item through its normal action listener. */
    public SwingMenuHarness click(String label) {
        JMenuItem item = uniqueItem(label);
        onEdt(() -> {
            if (!available(item)) {
                throw new AssertionError("Menu item or its parent is hidden or disabled: " + label);
            }
            item.doClick();
            return null;
        });
        transcript.add("MENU CLICK " + label);
        return this;
    }

    /** Exercises both states of every enabled menu toggle and restores the shell. */
    public List<MenuOptionSweep> exerciseEveryToggleState() {
        List<MenuOptionSweep> sweeps = new ArrayList<>();
        for (JMenuItem item : onEdt(this::items)) {
            if (!(item instanceof JCheckBoxMenuItem toggle) || !onEdt(() -> available(toggle))) {
                continue;
            }
            boolean original = onEdt(toggle::isSelected);
            List<String> available = List.of("false", "true");
            List<String> exercised = new ArrayList<>();
            for (boolean state : List.of(false, true)) {
                onEdt(() -> {
                    if (toggle.isSelected() != state) {
                        toggle.doClick();
                    }
                    return null;
                });
                if (onEdt(toggle::isSelected) == state) {
                    exercised.add(String.valueOf(state));
                }
            }
            onEdt(() -> {
                if (toggle.isSelected() != original) {
                    toggle.doClick();
                }
                return null;
            });
            boolean restored = onEdt(toggle::isSelected) == original;
            String label = onEdt(toggle::getText);
            transcript.add("MENU OPTION MATRIX " + label + " · " + exercised.size()
                    + "/" + available.size() + " · restored=" + restored);
            sweeps.add(new MenuOptionSweep(label, available,
                    List.copyOf(exercised), restored));
        }
        return List.copyOf(sweeps);
    }

    /**
     * Activates every enabled item except explicit process-ending or externally
     * coordinated actions. Exclusions remain visible in the returned evidence.
     */
    public MenuSweep clickEveryEnabledExcept(String... excludedLabels) {
        Set<String> excluded = new LinkedHashSet<>(Arrays.asList(excludedLabels));
        List<JMenuItem> items = onEdt(this::items);
        List<String> discovered = new ArrayList<>();
        List<String> clicked = new ArrayList<>();
        List<String> intentionallyExcluded = new ArrayList<>();
        for (JMenuItem item : items) {
            if (!onEdt(() -> available(item))) {
                continue;
            }
            String label = onEdt(item::getText);
            discovered.add(label);
            if (excluded.contains(label)) {
                intentionallyExcluded.add(label);
                transcript.add("MENU EXCLUDE " + label);
                continue;
            }
            click(label);
            clicked.add(label);
        }
        return new MenuSweep(List.copyOf(discovered), List.copyOf(clicked),
                List.copyOf(intentionallyExcluded));
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    private boolean available(JMenuItem item) {
        java.awt.Component current = item;
        while (current != null) {
            if (current instanceof JPopupMenu popup) {
                // A closed popup is still reachable by opening its owning menu.
                current = popup.getInvoker();
                continue;
            }
            if (!current.isEnabled() || !current.isVisible()) {
                return false;
            }
            if (current == menuBar) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private JMenuItem uniqueItem(String label) {
        List<JMenuItem> matches = onEdt(() -> items().stream()
                .filter(item -> label.equals(item.getText())).toList());
        if (matches.size() != 1) {
            throw new AssertionError("Expected one menu item named '" + label
                    + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private List<JMenuItem> items() {
        List<JMenuItem> result = new ArrayList<>();
        for (int menuIndex = 0; menuIndex < menuBar.getMenuCount(); menuIndex++) {
            JMenu menu = menuBar.getMenu(menuIndex);
            if (menu != null) {
                collectItems(menu, result);
            }
        }
        return result;
    }

    private static void collectItems(JMenu menu, List<JMenuItem> result) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item == null) {
                continue;
            }
            if (item instanceof JMenu submenu) {
                collectItems(submenu, result);
            } else {
                result.add(item);
            }
        }
    }

    private static void collect(JMenu menu, String parentPath,
                                List<MenuControl> controls, List<String> defects,
                                Set<String> paths) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item == null) {
                continue;
            }
            String label = safe(item.getText());
            String path = parentPath + " → " + label;
            if (!paths.add(path)) {
                defects.add("duplicate menu path '" + path + "'");
            }
            if (label.isBlank()) {
                defects.add("menu item under '" + parentPath + "' has no label");
            }
            boolean wired = item.getAction() != null || item.getActionListeners().length > 0;
            if (item.isEnabled() && !wired) {
                defects.add("enabled menu item '" + path + "' has no action listener");
            }
            if (item.isEnabled() && item.getMnemonic() == 0) {
                defects.add("enabled menu item '" + path + "' has no mnemonic");
            }
            if (description(item).isBlank()) {
                defects.add("menu item '" + path + "' has no accessible description");
            }
            controls.add(new MenuControl(path, item.isEnabled(), wired,
                    item.getMnemonic(), item.getAccelerator(), description(item)));
            if (item instanceof JMenu submenu) {
                collect(submenu, path, controls, defects, paths);
            }
        }
    }

    private static String description(JMenuItem item) {
        String value = item.getAccessibleContext().getAccessibleDescription();
        return safe(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
        } catch (Exception exception) {
            throw new AssertionError("Could not operate the application menu", exception);
        }
        if (failure[0] != null) {
            throw new AssertionError("Application menu action failed", failure[0]);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception exception) {
            throw new AssertionError("Application menu action failed", exception);
        }
    }

    public record MenuControl(String path, boolean enabled, boolean wired, int mnemonic,
                              KeyStroke accelerator, String description) { }

    public record MenuAudit(List<MenuControl> controls, List<String> defects) { }

    public record MenuSweep(List<String> discovered, List<String> clicked,
                            List<String> intentionallyExcluded) { }

    public record MenuOptionSweep(String label, List<String> available,
                                  List<String> exercised, boolean restored) { }
}

package com.flowerfarm.gui.automation;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves a logical menu command without opening its popup or invoking the command. */
final class SwingMenuTarget {
    private SwingMenuTarget() {
    }

    static Target resolve(JComponent activeRoot, List<String> path, boolean requireEnabled) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Desktop menu targets require the event-dispatch thread");
        }
        Objects.requireNonNull(activeRoot, "activeRoot");
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException("Menu path must contain a top-level menu and a command");
        }
        for (String step : path) {
            if (step == null || step.isBlank()) {
                throw new IllegalArgumentException("Menu path steps must be non-blank strings");
            }
        }
        List<MenuBarTarget> bars = new ArrayList<>();
        findMenuBars(activeRoot, true, bars);
        if (bars.isEmpty()) {
            if (containsMenuBar(activeRoot)) {
                throw new IllegalArgumentException("Menu bar or its ancestor is hidden");
            }
            throw new IllegalStateException("Desktop active root has no visible menu bar");
        }
        if (bars.size() != 1) {
            throw new IllegalArgumentException("Expected one visible menu bar but found " + bars.size());
        }
        MenuBarTarget bar = bars.get(0);
        boolean enabled = bar.enabled();
        if (requireEnabled && !enabled) {
            throw new IllegalArgumentException("Menu bar or its ancestor is disabled");
        }
        JMenuItem item = uniqueTopMenu(bar.bar(), path.get(0));
        enabled = requireVisible(item, requireEnabled) && enabled;
        for (int index = 1; index < path.size(); index++) {
            if (!(item instanceof JMenu menu)) {
                throw new IllegalArgumentException("Menu path continues after a command");
            }
            item = uniqueMenuItem(menu, path.get(index));
            enabled = requireVisible(item, requireEnabled) && enabled;
        }
        if (item instanceof JMenu) {
            throw new IllegalArgumentException("Menu path must end at a command, not a submenu");
        }
        if (requireEnabled && !enabled) {
            throw new IllegalArgumentException("Menu command or its ancestor is disabled");
        }
        return new Target(item, enabled);
    }

    private static boolean requireVisible(JMenuItem item, boolean requireEnabled) {
        if (!item.isVisible()) {
            throw new IllegalArgumentException("Menu '" + item.getText() + "' is hidden");
        }
        if (requireEnabled && !item.isEnabled()) {
            throw new IllegalArgumentException("Menu '" + item.getText() + "' is disabled");
        }
        return item.isEnabled();
    }

    private static JMenu uniqueTopMenu(JMenuBar bar, String label) {
        List<JMenu> matches = new ArrayList<>();
        for (int index = 0; index < bar.getMenuCount(); index++) {
            JMenu menu = bar.getMenu(index);
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
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item && label.equals(item.getText())) {
                matches.add(item);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one menu command named '"
                    + label + "' below '" + menu.getText() + "' but found " + matches.size());
        }
        return matches.get(0);
    }

    private static void findMenuBars(Component component, boolean enabled, List<MenuBarTarget> bars) {
        if (!component.isVisible() || component instanceof JPopupMenu) {
            return;
        }
        boolean effectiveEnabled = enabled && component.isEnabled();
        if (component instanceof JMenuBar bar) {
            bars.add(new MenuBarTarget(bar, effectiveEnabled));
        } else if (component instanceof JTabbedPane tabs) {
            Component selected = tabs.getSelectedComponent();
            if (selected != null) {
                findMenuBars(selected, effectiveEnabled, bars);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                findMenuBars(child, effectiveEnabled, bars);
            }
        }
    }

    private static boolean containsMenuBar(Component component) {
        if (component instanceof JMenuBar) {
            return true;
        }
        if (component instanceof JTabbedPane tabs) {
            return tabs.getSelectedComponent() != null && containsMenuBar(tabs.getSelectedComponent());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsMenuBar(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    record Target(JMenuItem item, boolean enabled) {
    }

    private record MenuBarTarget(JMenuBar bar, boolean enabled) {
    }
}

package com.flowerfarm.gui.automation;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves installed shortcuts without opening menus or sending native keyboard events. */
final class SwingShortcutResolver {
    private SwingShortcutResolver() { }

    static void invoke(JComponent activeRoot, KeyStroke shortcut) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Desktop shortcuts require the event-dispatch thread");
        }
        Objects.requireNonNull(activeRoot, "activeRoot");
        Objects.requireNonNull(shortcut, "shortcut");
        Candidates candidates = new Candidates();
        collectBindings(activeRoot, shortcut, candidates);
        if (candidates.bindings.size() > 1) {
            throw ambiguous(shortcut, candidates.bindings.size());
        }
        if (candidates.bindings.size() == 1) {
            Binding binding = candidates.bindings.get(0);
            binding.action().actionPerformed(new ActionEvent(binding.source(),
                    ActionEvent.ACTION_PERFORMED, shortcut.toString(),
                    System.currentTimeMillis(), shortcut.getModifiers()));
            return;
        }

        // Existing explicit bindings retain priority over a duplicate menu accelerator (F5).
        collectMenuBars(activeRoot, true, shortcut, candidates);
        if (candidates.menus.size() > 1) {
            throw ambiguous(shortcut, candidates.menus.size());
        }
        if (candidates.menus.isEmpty()) {
            if (candidates.disabled) {
                throw new IllegalArgumentException("Installed shortcut '" + shortcut + "' is disabled");
            }
            throw ambiguous(shortcut, 0);
        }
        MenuBinding menu = candidates.menus.get(0);
        SwingMenuTarget.Target target = SwingMenuTarget.resolve(activeRoot, menu.path(), true);
        if (target.item() != menu.item()) {
            throw new IllegalArgumentException("Shortcut menu path does not identify the requested command");
        }
        target.item().doClick();
    }

    private static IllegalArgumentException ambiguous(KeyStroke shortcut, int count) {
        return new IllegalArgumentException("Expected one visible installed shortcut '"
                + shortcut + "' but found " + count);
    }

    private static void collectBindings(Component component, KeyStroke shortcut, Candidates candidates) {
        if (!component.isVisible() || component instanceof JPopupMenu) {
            return;
        }
        if (component instanceof JComponent swing) {
            for (int condition : new int[]{JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                    JComponent.WHEN_IN_FOCUSED_WINDOW}) {
                InputMap input = swing.getInputMap(condition);
                Object key = input == null ? null : input.get(shortcut);
                Action action = key == null ? null : swing.getActionMap().get(key);
                if (action == null) {
                    continue;
                }
                if (!swing.isEnabled() || !action.isEnabled()) {
                    candidates.disabled = true;
                } else if (candidates.bindings.stream().noneMatch(binding ->
                        binding.source() == swing && binding.action() == action)) {
                    candidates.bindings.add(new Binding(swing, action));
                }
            }
        }
        if (component instanceof JTabbedPane tabs) {
            if (tabs.getSelectedComponent() != null) {
                collectBindings(tabs.getSelectedComponent(), shortcut, candidates);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectBindings(child, shortcut, candidates);
            }
        }
    }

    private static void collectMenuBars(Component component, boolean ancestorsEnabled,
                                        KeyStroke shortcut, Candidates candidates) {
        if (!component.isVisible() || component instanceof JPopupMenu) {
            return;
        }
        boolean enabled = ancestorsEnabled && component.isEnabled();
        if (component instanceof JMenuBar bar) {
            for (int index = 0; index < bar.getMenuCount(); index++) {
                JMenu menu = bar.getMenu(index);
                if (menu != null) {
                    collectMenu(menu, List.of(), enabled, shortcut, candidates);
                }
            }
        } else if (component instanceof JTabbedPane tabs) {
            if (tabs.getSelectedComponent() != null) {
                collectMenuBars(tabs.getSelectedComponent(), enabled, shortcut, candidates);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectMenuBars(child, enabled, shortcut, candidates);
            }
        }
    }

    private static void collectMenu(JMenuItem item, List<String> prefix, boolean ancestorsEnabled,
                                    KeyStroke shortcut, Candidates candidates) {
        if (!item.isVisible()) {
            return;
        }
        boolean enabled = ancestorsEnabled && item.isEnabled();
        List<String> path = new ArrayList<>(prefix);
        path.add(item.getText());
        if (item instanceof JMenu menu) {
            for (Component child : menu.getMenuComponents()) {
                if (child instanceof JMenuItem childItem) {
                    collectMenu(childItem, path, enabled, shortcut, candidates);
                }
            }
        } else if (shortcut.equals(item.getAccelerator())) {
            if (enabled && (item.getAction() == null || item.getAction().isEnabled())) {
                candidates.menus.add(new MenuBinding(item, path));
            } else {
                candidates.disabled = true;
            }
        }
    }

    private static final class Candidates {
        private final List<Binding> bindings = new ArrayList<>();
        private final List<MenuBinding> menus = new ArrayList<>();
        private boolean disabled;
    }

    private record Binding(JComponent source, Action action) { }
    private record MenuBinding(JMenuItem item, List<String> path) { }
}

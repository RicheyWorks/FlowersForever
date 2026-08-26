package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.ReferenceTab;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Owns primary-tab population, refresh, selection, and keyboard navigation.
 */
public final class FarmTabNavigator {

    private final JTabbedPane pane;
    private final List<FlowerFarmTab> tabs;

    public FarmTabNavigator(JTabbedPane pane, List<FlowerFarmTab> tabs) {
        this.pane = pane;
        this.tabs = List.copyOf(tabs);
    }

    public void populate() {
        pane.putClientProperty("JTabbedPane.tabType", "card");
        pane.putClientProperty("JTabbedPane.tabAreaAlignment", "leading");
        pane.putClientProperty("JTabbedPane.minimumTabWidth", 190);
        pane.putClientProperty("JTabbedPane.maximumTabWidth", 220);
        pane.putClientProperty("JTabbedPane.tabInsets", "8,12,8,12");
        String previousGroup = null;
        for (FlowerFarmTab tab : tabs) {
            javax.swing.Icon icon = tab.getIcon() == null
                    ? FarmUi.navigationIcon(tab.getTabTitle()) : tab.getIcon();
            pane.addTab(tab.getTabTitle(), icon, tab.getUIComponent(), tab.getDescription());
            int index = pane.getTabCount() - 1;
            String group = groupFor(tab.getTabTitle());
            pane.setTabComponentAt(index, navigationLabel(
                    tab.getTabTitle(), icon, group.equals(previousGroup) ? null : group,
                    shortcutLabel(index, tab.getTabTitle())));
            pane.setMnemonicAt(index, mnemonicFor(tab.getTabTitle()));
            previousGroup = group;
            tab.initialize();
        }
    }

    private static JPanel navigationLabel(String title, javax.swing.Icon icon, String group,
                                          String shortcut) {
        JPanel tab = new JPanel();
        tab.setName("farm.navigationItem");
        tab.setOpaque(false);
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        if (group != null) {
            JLabel heading = new JLabel(group.toUpperCase(java.util.Locale.ROOT));
            heading.setName("farm.navigationGroup");
            heading.setForeground(FarmUi.mutedColor());
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 9f));
            heading.setBorder(BorderFactory.createEmptyBorder(2, 4, 3, 0));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            tab.add(heading);
        }
        String visibleTitle = "Reference & Planning".equals(title) ? "Reference" : title;
        JLabel item = new JLabel(visibleTitle, icon, JLabel.LEFT);
        item.setFont(item.getFont().deriveFont(Font.PLAIN, 12f));
        item.setIconTextGap(10);
        item.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(item, BorderLayout.WEST);
        JLabel shortcutHint = FarmUi.mutedLabel(shortcut);
        shortcutHint.setFont(shortcutHint.getFont().deriveFont(Font.PLAIN, 10f));
        shortcutHint.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 5));
        row.add(shortcutHint, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(205, 32));
        tab.add(row);
        tab.getAccessibleContext().setAccessibleName(
                title + " navigation item, shortcut " + shortcut);
        return tab;
    }

    private static String shortcutLabel(int index, String title) {
        return index < 9 ? "Alt+" + (index + 1)
                : "Alt+" + (char) mnemonicFor(title);
    }

    private static String groupFor(String title) {
        return switch (title) {
            case "Dashboard", "Market Day" -> "Today";
            case "Harvest Log", "Inventory", "Add Item", "Irrigation & Care" -> "Grow & stock";
            case "Customers & Orders" -> "Sales & fulfillment";
            case "Reports", "Trend Analysis", "Activity History" -> "Reports & admin";
            case "Reference & Planning" -> "Reference";
            default -> "Other";
        };
    }

    private static int mnemonicFor(String title) {
        return switch (title) {
            case "Dashboard" -> 'D';
            case "Market Day" -> 'M';
            case "Harvest Log" -> 'H';
            case "Inventory" -> 'I';
            case "Add Item" -> 'A';
            case "Customers & Orders" -> 'C';
            case "Irrigation & Care" -> 'G';
            case "Reports" -> 'R';
            case "Trend Analysis" -> 'T';
            case "Activity History" -> 'S';
            case "Rose Varieties" -> 'V';
            case "Pricing Info" -> 'P';
            case "Rose Visualizer" -> 'Z';
            case "Reference & Planning" -> 'L';
            default -> title.isEmpty() ? 'F' : Character.toUpperCase(title.charAt(0));
        };
    }

    public void refreshSelected(IntConsumer selectionListener) {
        int index = pane.getSelectedIndex();
        if (index >= 0 && index < tabs.size()) {
            tabs.get(index).refreshData();
            selectionListener.accept(index);
            javax.swing.SwingUtilities.invokeLater(() ->
                    FarmUi.focusFirstInteractive(tabs.get(index).getUIComponent()));
        }
    }

    public void refreshAll() {
        tabs.forEach(FlowerFarmTab::refreshData);
    }

    public boolean select(String title) {
        for (int i = 0; i < tabs.size(); i++) {
            FlowerFarmTab tab = tabs.get(i);
            if (tab instanceof InventoryWorkspaceTab inventory
                    && inventory.selectWorkspace(title)) {
                pane.setSelectedIndex(i);
                return true;
            }
            if (tab.getTabTitle().equals(title)) {
                pane.setSelectedIndex(i);
                return true;
            }
            if (tab instanceof ReferenceTab reference && reference.selectReference(title)) {
                pane.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    public void installShortcuts(JRootPane root, Runnable refreshAction) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refreshAll");
        root.getActionMap().put("refreshAll", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                refreshAction.run();
            }
        });
        int shortcutCount = Math.min(9, tabs.size());
        for (int i = 0; i < shortcutCount; i++) {
            int tabIndex = i;
            String actionKey = "selectTab" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK),
                    actionKey);
            root.getActionMap().put(actionKey, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    pane.setSelectedIndex(tabIndex);
                }
            });
        }
    }
}

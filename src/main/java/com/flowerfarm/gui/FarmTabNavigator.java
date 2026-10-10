package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.ReferenceTab;
import com.flowerfarm.model.FarmWorkspace;

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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Owns primary-tab population, refresh, selection, and keyboard navigation.
 */
public final class FarmTabNavigator {

    private final JTabbedPane pane;
    private final List<FlowerFarmTab> tabs;
    private final FarmWorkspace workspace;
    private final boolean combined;

    public FarmTabNavigator(JTabbedPane pane, List<FlowerFarmTab> tabs) {
        this(pane, tabs, FarmWorkspace.ROSE_AND_EVENTS);
    }

    public FarmTabNavigator(JTabbedPane pane, List<FlowerFarmTab> tabs,
                            FarmWorkspace workspace) {
        this(pane, tabs, workspace, false);
    }

    public FarmTabNavigator(JTabbedPane pane, List<FlowerFarmTab> tabs,
                            FarmWorkspace workspace, boolean combined) {
        this.pane = pane;
        this.tabs = List.copyOf(tabs);
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.combined = combined;
    }

    public void populate() {
        pane.putClientProperty("JTabbedPane.tabType", "card");
        pane.putClientProperty("JTabbedPane.tabAreaAlignment", "leading");
        pane.putClientProperty("JTabbedPane.minimumTabWidth", 190);
        pane.putClientProperty("JTabbedPane.maximumTabWidth", 220);
        pane.putClientProperty("JTabbedPane.tabHeight", 32);
        // Keep every primary workflow visible at the supported 1100 x 700 minimum.
        // The custom labels already provide generous internal spacing; adding eight
        // more pixels above and below every tab pushed the final workflows behind
        // FlatLaf's scroll arrows on compact laptops.
        pane.putClientProperty("JTabbedPane.tabInsets", new Insets(1, 1, 1, 1));
        String previousGroup = null;
        Set<Integer> usedMnemonics = new HashSet<>();
        for (FlowerFarmTab tab : tabs) {
            javax.swing.Icon icon = tab.getIcon() == null
                    ? FarmUi.navigationIcon(tab.getTabTitle()) : tab.getIcon();
            pane.addTab(tab.getTabTitle(), icon, tab.getUIComponent(), tab.getDescription());
            int index = pane.getTabCount() - 1;
            int mnemonic = uniqueMnemonic(tab.getTabTitle(), usedMnemonics);
            String group = groupFor(tab.getTabTitle());
            pane.setTabComponentAt(index, navigationLabel(
                    tab.getTabTitle(), icon, group.equals(previousGroup) ? null : group,
                    shortcutLabel(index, mnemonic)));
            pane.setMnemonicAt(index, mnemonic);
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
            JLabel heading = FarmUi.mutedLabel(
                    group.toUpperCase(java.util.Locale.ROOT));
            heading.setName("farm.navigationGroup");
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 9f));
            heading.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            tab.add(heading);
        }
        String visibleTitle = "Reference & Planning".equals(title) ? "Reference" : title;
        JLabel item = new JLabel(visibleTitle, icon, JLabel.LEFT);
        item.setFont(item.getFont().deriveFont(Font.PLAIN, 12f));
        item.setIconTextGap(10);
        item.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(item, BorderLayout.WEST);
        JLabel shortcutHint = FarmUi.mutedLabel(shortcut);
        shortcutHint.setFont(shortcutHint.getFont().deriveFont(Font.PLAIN, 10f));
        shortcutHint.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 5));
        row.add(shortcutHint, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(205, 22));
        tab.add(row);
        tab.getAccessibleContext().setAccessibleName(
                title + " navigation item" + (shortcut.isEmpty() ? "" : ", shortcut " + shortcut));
        return tab;
    }

    private static String shortcutLabel(int index, int mnemonic) {
        return index < 9 ? "Alt+" + (index + 1)
                : mnemonic == 0 ? "" : "Alt+" + (char) mnemonic;
    }

    private String groupFor(String title) {
        if (combined && ("Cultivation".equals(title) || "Field plans".equals(title)
                || "Orchard blocks".equals(title) || "Berry blocks".equals(title)
                || "Vineyard blocks".equals(title)
                || "Hay cuttings".equals(title) || "Hop yards".equals(title)
                || "Grain fields".equals(title)
                || "Seed fields".equals(title)
                || "Nut blocks".equals(title)
                || "Vegetable plantings".equals(title)
                || "Aquatic cohorts".equals(title)
                || "Tree blocks".equals(title)
                || "Hive checks".equals(title)
                || "Mushroom batches".equals(title)
                || "Cranberry beds".equals(title)
                || "Pumpkin patches".equals(title)
                || "Growing batches".equals(title) || "Season Planner".equals(title)
                || "Herd groups".equals(title) || "Egg collections".equals(title)
                || "Milk collections".equals(title)
                || "Harvest Log".equals(title) || "Inventory".equals(title)
                || "Irrigation & Care".equals(title))) {
            return "Farm tools & stock";
        }
        return switch (title) {
            case "Workspace", "Dashboard", "Market Day" -> "Today";
            case "Cultivation", "Field plans", "Orchard blocks", "Berry blocks",
                    "Vineyard blocks", "Hay cuttings", "Hop yards", "Grain fields",
                    "Seed fields", "Nut blocks", "Vegetable plantings",
                    "Aquatic cohorts", "Tree blocks", "Hive checks", "Mushroom batches",
                    "Cranberry beds", "Pumpkin patches",
                    "Growing batches", "Herd groups", "Egg collections",
                    "Milk collections",
                    "Season Planner", "Harvest Log", "Inventory",
                    "Add Item", "Irrigation & Care" ->
                    switch (workspace) {
                        case FLOWER_SHOP -> "Products & stock";
                        case CANNABIS_FARM -> "Cultivation & stock";
                        case POTATO_FARM -> "Fields & stock";
                        case APPLE_ORCHARD -> "Orchard & stock";
                        case BERRY_FARM -> "Berries & stock";
                        case NURSERY_FARM -> "Plants & stock";
                        case LIVESTOCK_FARM -> "Herds & stock";
                        case POULTRY_FARM -> "Eggs & stock";
                        case DAIRY_FARM -> "Milk & stock";
                        case VINEYARD_FARM -> "Vines & stock";
                        case HAY_FARM -> "Hay & stock";
                        case HOPS_FARM -> "Hops & stock";
                        case GRAIN_FARM -> "Grains & stock";
                        case SEED_FARM -> "Seeds & stock";
                        case NUT_ORCHARD -> "Nuts & stock";
                        case VEGETABLE_FARM -> "Vegetables & stock";
                        case AQUACULTURE_FARM -> "Aquatic plans & stock";
                        case CHRISTMAS_TREE_FARM -> "Trees & stock";
                        case APIARY_FARM -> "Hives & stock";
                        case MUSHROOM_FARM -> "Mushrooms & stock";
                        case CRANBERRY_FARM -> "Cranberries & stock";
                        case PUMPKIN_PATCH -> "Pumpkins & stock";
                        case CUSTOM_FARM -> "Records & stock";
                        case ROSE_AND_EVENTS, WILDFLOWER_FARM -> "Grow & stock";
                    };
            case "Knowledge Log" -> "Learn & improve";
            case "Customers & Orders" -> "Sales & fulfillment";
            case "Event Services", "Production Studio" -> "Design & service";
            case "Reports", "Trend Analysis", "Activity History" -> "Reports & admin";
            case "Reference & Planning", "Field science" -> "Reference";
            case "Equipment readings" -> "Farm conditions";
            default -> "Other";
        };
    }

    private static int mnemonicFor(String title) {
        return switch (title) {
            case "Workspace" -> 'W';
            case "Dashboard" -> 'D';
            case "Market Day" -> 'M';
            case "Cultivation" -> 'U';
            case "Field plans" -> 'F';
            case "Orchard blocks" -> 'B';
            case "Berry blocks" -> 'B';
            case "Growing batches" -> 'G';
            case "Harvest Log" -> 'H';
            case "Season Planner" -> 'E';
            case "Inventory" -> 'I';
            case "Add Item" -> 'A';
            case "Customers & Orders" -> 'C';
            case "Irrigation & Care" -> 'G';
            case "Reports" -> 'R';
            case "Trend Analysis" -> 'T';
            case "Activity History" -> 'S';
            case "Knowledge Log" -> 'K';
            case "Rose Varieties" -> 'V';
            case "Pricing Info" -> 'P';
            case "Rose Visualizer" -> 'Z';
            case "Reference & Planning" -> 'L';
            case "Field science" -> 'F';
            case "Event Services" -> 'V';
            case "Production Studio" -> 'P';
            default -> title.isEmpty() ? 'F' : Character.toUpperCase(title.charAt(0));
        };
    }

    private static int uniqueMnemonic(String title, Set<Integer> used) {
        int preferred = mnemonicFor(title);
        if (used.add(preferred)) {
            return preferred;
        }
        for (int index = 0; index < title.length(); index++) {
            char letter = Character.toUpperCase(title.charAt(index));
            if (letter >= 'A' && letter <= 'Z' && used.add((int) letter)) {
                return letter;
            }
        }
        for (int letter = 'A'; letter <= 'Z'; letter++) {
            if (used.add(letter)) {
                return letter;
            }
        }
        // A combined farm may expose more tools than there are letters. Those
        // tabs retain normal keyboard traversal and Workspace buttons without
        // claiming another tab's shortcut or preventing the saved mix from opening.
        return 0;
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
                selectAndFocus(i);
                return true;
            }
            if (tab.getTabTitle().equals(title)) {
                selectAndFocus(i);
                return true;
            }
            if (tab instanceof ReferenceTab reference && reference.selectReference(title)) {
                selectAndFocus(i);
                return true;
            }
        }
        return false;
    }

    public void installShortcuts(JRootPane root, Runnable refreshAction) {
        installShortcuts(root, refreshAction, () -> true);
    }

    /** Keeps tab shortcuts inert while the farm-type launcher is visible. */
    public void installShortcuts(JRootPane root, Runnable refreshAction,
                                 BooleanSupplier mayNavigate) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refreshAll");
        root.getActionMap().put("refreshAll", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                refreshAction.run();
            }
        });
        // A workspace change can shorten the list. Remove routes into the old pane.
        for (int i = 0; i < 9; i++) {
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(
                    KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK));
            root.getActionMap().remove("selectTab" + i);
        }
        int shortcutCount = Math.min(9, tabs.size());
        for (int i = 0; i < shortcutCount; i++) {
            int tabIndex = i;
            String actionKey = "selectTab" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK),
                    actionKey);
            root.getActionMap().put(actionKey, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    if (mayNavigate.getAsBoolean()) {
                        selectAndFocus(tabIndex);
                    }
                }
            });
        }
    }

    private void selectAndFocus(int index) {
        pane.setSelectedIndex(index);
        javax.swing.SwingUtilities.invokeLater(() ->
                FarmUi.focusFirstInteractive(tabs.get(index).getUIComponent()));
    }
}

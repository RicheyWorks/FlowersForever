package com.flowerfarm.gui.tabs;

import javax.swing.*;

/**
 * Contract for a modular, self-contained tab in the Flower Farm Manager GUI.
 *
 * <p>Each tab is responsible for its own UI construction, event handling,
 * and interaction with backend services. This enables clean separation of
 * concerns, easier testing, and simple addition of new features (Dashboard,
 * Reports, Harvest Log, Supplier Portal, etc.).
 *
 * <p>Implementation guidelines:
 * <ul>
 *   <li>Prefer composition over inheritance.</li>
 *   <li>Perform heavy work off the EDT using SwingWorker or Task abstractions.</li>
 *   <li>Call {@link #refreshData()} when the tab becomes visible or data changes globally.</li>
 * </ul>
 */
public interface FlowerFarmTab {

    /**
     * Human-readable title shown on the tab header.
     */
    String getTabTitle();

    /**
     * Optional icon for the tab (can return null).
     * Recommended: use FlatSVGIcon or ImageIcon loaded from resources.
     */
    default Icon getIcon() {
        return null;
    }

    /**
     * Returns the root Swing component to be added to the JTabbedPane.
     * This component is typically a JPanel with BorderLayout or other manager.
     */
    JComponent getUIComponent();

    /**
     * Called once after the tab is added to the tabbed pane.
     * Use this for initial data loading or wiring that requires the UI to exist.
     */
    default void initialize() {
        // no-op by default
    }

    /**
     * Called when the tab is selected by the user or when global data changes.
     * Implementations should refresh tables, charts, KPIs, etc.
     */
    default void refreshData() {
        // no-op by default
    }

    /**
     * Optional: return a short description or tooltip for the tab.
     */
    default String getDescription() {
        return "";
    }
}

package com.flowerfarm.gui.tabs;

/**
 * Coordination contract implemented by the main GUI orchestrator and handed to
 * the tabs that need to trigger cross-cutting actions — a global data refresh,
 * switching the visible tab, updating the status bar, or kicking off a trend
 * forecast — without holding a direct reference to the {@code JFrame} or to
 * their sibling tabs.
 *
 * <p>This keeps individual {@link FlowerFarmTab} implementations decoupled: a
 * tab depends only on the small surface it actually uses, and the orchestrator
 * remains the single owner of frame-level concerns.
 */
public interface TabHost {

    /** Refresh the data of every registered tab (e.g. after an inventory change). */
    void refreshAll();

    /** Bring the tab with the given title to the front. No-op if not found. */
    void selectTab(String tabTitle);

    /** Show a short message in the application status bar. */
    void setStatus(String message);

    /** Switch to the Trend Analysis tab and start a forecast on a background thread. */
    void runTrendAnalysis();

    /**
     * Whether the signed-in user may mutate data (HAND/OWNER, or auth off).
     * Default true for hosts that do not implement multi-user auth.
     */
    default boolean canMutateData() {
        return true;
    }

    /** Whether the user may clear the audit log (OWNER only when auth on). */
    default boolean canClearHistory() {
        return true;
    }
}

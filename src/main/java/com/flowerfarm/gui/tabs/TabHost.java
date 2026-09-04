package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.PnwPlantProfile;

import java.util.List;

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

    /**
     * Open Inventory with only the named products visible for a focused handoff.
     * Hosts without direct access to the inventory workflow still provide a
     * useful navigation and status fallback.
     */
    default void showInventoryProducts(List<String> productNames) {
        List<String> products = productNames == null ? List.of() : productNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        selectTab("Inventory");
        setStatus(products.isEmpty()
                ? "Opened Inventory to review stock."
                : "Opened Inventory for market shortfalls: " + String.join(", ", products) + ".");
    }

    /**
     * Open a focused Inventory queue with a route back to the workflow that
     * requested the stock review. Basic hosts may ignore the return context.
     */
    default void showInventoryProducts(List<String> productNames,
                                       String returnTab, String returnLabel) {
        showInventoryProducts(productNames);
    }

    /** Open Add Item with a missing product name prepared when safe. */
    default void showAddInventoryItem(String productName) {
        String safeName = productName == null ? "" : productName.trim();
        selectTab("Add Item");
        setStatus(safeName.isEmpty()
                ? "Opened Add Item."
                : "Opened Add Item for missing product “" + safeName + "”.");
    }

    /**
     * Open Customers & Orders with a status-focused order queue. Hosts without
     * direct access to CRM still provide a useful navigation fallback.
     */
    default void showOrdersByStatus(String status) {
        String safeStatus = status == null || status.isBlank() ? "ALL" : status.trim();
        selectTab("Customers & Orders");
        setStatus("Opened Customers & Orders for " + safeStatus + " orders.");
    }

    /** Open a status-focused order queue with a route back to its source. */
    default void showOrdersByStatus(String status, String returnTab, String returnLabel) {
        showOrdersByStatus(status);
    }

    /**
     * Open Customers & Orders with one exact order selected for review. Hosts
     * without direct access to CRM still provide a useful navigation fallback.
     */
    default void showOrder(long orderId) {
        selectTab("Customers & Orders");
        setStatus("Opened Customers & Orders for order #" + orderId + ".");
    }

    /**
     * Open one exact order with a route back to the workflow that requested the
     * review. Basic hosts may ignore the return context.
     */
    default void showOrder(long orderId, String returnTab, String returnLabel) {
        showOrder(orderId);
    }

    /**
     * Open a new CRM order draft and prepare a non-stock event-services brief.
     * Physical flowers and other goods remain separate inventory-tracked lines.
     */
    default boolean showEventOrderDraft(String occasion, String services, String brief) {
        String safeOccasion = occasion == null || occasion.isBlank()
                ? "event" : occasion.trim();
        selectTab("Customers & Orders");
        setStatus("Opened Customers & Orders for a " + safeOccasion + " service brief.");
        return false;
    }

    /**
     * Open Season Planner with a sourced plant profile prepared as an editable
     * farm-specific planting draft. The bundled reference remains immutable.
     */
    default boolean showSeasonPlanDraft(PnwPlantProfile plant) {
        if (plant == null) {
            return false;
        }
        selectTab("Season Planner");
        setStatus("Opened Season Planner for “" + plant.commonName()
                + "”; confirm farm dates, bed space, and expected stems before saving.");
        return false;
    }

    /**
     * Open one exact order and focus the saved product line that needs review.
     * Older hosts retain a safe exact-order fallback.
     */
    default void showOrderLine(long orderId, Long lineId, String productName,
                               String returnTab, String returnLabel) {
        showOrder(orderId, returnTab, returnLabel);
    }

    /** Open an exact saved product and prepare its correction fields when permitted. */
    default void showOrderLineCorrection(long orderId, Long lineId, String productName,
                                         String returnTab, String returnLabel) {
        showOrderLine(orderId, lineId, productName, returnTab, returnLabel);
    }

    /** Open Harvest Log with one exact saved harvest selected for review. */
    default void showHarvest(long harvestId) {
        selectTab("Harvest Log");
        setStatus("Opened Harvest Log for harvest #" + harvestId + ".");
    }

    /** Open one exact harvest with a route back to the requesting workflow. */
    default void showHarvest(long harvestId, String returnTab, String returnLabel) {
        showHarvest(harvestId);
    }

    /** Open the retained Market Day plan with a route back to its source. */
    default void showMarketDay(String returnTab, String returnLabel) {
        selectTab("Market Day");
        setStatus("Opened Market Day packing work.");
    }

    /** Open the retained irrigation plan with a route back to its source. */
    default void showIrrigation(String returnTab, String returnLabel) {
        selectTab("Irrigation & Care");
        setStatus("Opened Irrigation & Care.");
    }

    /** Open the retained single-harvest entry workspace with a return route. */
    default void showHarvestEntry(String returnTab, String returnLabel) {
        selectTab("Harvest Log");
        setStatus("Opened Harvest Log for entry.");
    }

    /** Switch to Trend Analysis and forecast from weekly harvest history in the background. */
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

    /** User-facing identity for permission feedback; never used for REST authorization. */
    default String sessionDisplayName() {
        return "local desktop session";
    }
}

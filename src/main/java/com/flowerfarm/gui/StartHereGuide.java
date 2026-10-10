package com.flowerfarm.gui;

/**
 * Plain-language first setup for a cut-flower farm that still lives in a
 * spreadsheet. Pure logic so tests and the desktop window share one path.
 */
public final class StartHereGuide {

    public static final String TITLE = "Start here";
    public static final String PREF_DONE = "startHere.done";

    public enum Choice {
        LOG_HARVEST,
        IMPORT_SPREADSHEET,
        ADD_PRODUCT,
        DISMISS
    }

    private StartHereGuide() {
    }

    /** True when the farm has no products and no harvest history yet. */
    public static boolean looksEmpty(int inventoryCount, int harvestCount) {
        return inventoryCount <= 0 && harvestCount <= 0;
    }

    public static String headline() {
        return "Start with today’s cut — or bring a spreadsheet";
    }

    public static String body() {
        return "FlowersForever is for cut-flower farm records: beds, harvest, stock, and orders.\n\n"
                + "You do not need to fill every screen. Pick one start:\n"
                + "• Log today’s first cut (crop + quantity is enough)\n"
                + "• Import products from a CSV or Excel spreadsheet\n"
                + "• Add one product by hand\n\n"
                + "You can open this again anytime from Help > Start here…";
    }

    public static String dashboardTitle() {
        return "New farm — start here";
    }

    public static String dashboardGuidance() {
        return "Most growers begin with today’s cut or a spreadsheet of products. "
                + "Other tools stay available; you do not need them on day one.";
    }

    public static String[] dialogOptions() {
        return new String[] {
                "Log today’s first cut",
                "Bring in a spreadsheet…",
                "Add a product by hand",
                "Not now"
        };
    }

    public static Choice choiceFromDialogIndex(int index) {
        return switch (index) {
            case 0 -> Choice.LOG_HARVEST;
            case 1 -> Choice.IMPORT_SPREADSHEET;
            case 2 -> Choice.ADD_PRODUCT;
            default -> Choice.DISMISS;
        };
    }

    public static String statusFor(Choice choice) {
        return switch (choice) {
            case LOG_HARVEST -> "Opened Harvest Log — enter crop and quantity, then Add harvest.";
            case IMPORT_SPREADSHEET -> "Choose the spreadsheet with your product list (CSV or Excel).";
            case ADD_PRODUCT -> "Opened Add Item — enter one flower or farm product.";
            case DISMISS -> "Start here is in Help whenever you need it.";
        };
    }
}

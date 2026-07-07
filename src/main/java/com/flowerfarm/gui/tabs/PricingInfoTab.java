package com.flowerfarm.gui.tabs;

/**
 * Static reference tab: pricing unit guidance for PNW flower farming.
 */
public class PricingInfoTab extends AbstractInfoTab {

    public PricingInfoTab() {
        setContent("""
                Pricing Guidelines — PNW West of the Cascades
                ─────────────────────────────────────────────
                • Per Stem        — cut flowers (roses, dahlias, tulips, ranunculus)
                • Per Weight (lb) — bulk herbs, mixed greens, peonies in volume
                • Per Unit        — supplies, tools, single-item sales
                • Per Gallon      — liquid products (fertiliser, fuel, amendments)
                • Per Hour        — equipment / tractor rentals

                Tailored for PNW flowers: Roses, Dahlias, Tulips, Ranunculus, Peonies.
                Adjust prices seasonally for Kitsap County availability and demand.
                """);
    }

    @Override
    public String getTabTitle() {
        return "Pricing Info";
    }

    @Override
    public String getDescription() {
        return "Reference pricing units for PNW flower sales";
    }
}

package com.flowerfarm.gui.tabs;

/**
 * Static reference tab: irrigation &amp; care notes for the maritime PNW.
 */
public class IrrigationInfoTab extends AbstractInfoTab {

    public IrrigationInfoTab() {
        setContent("""
                Irrigation & Care — Maritime PNW (Port Orchard / Kitsap County)
                ───────────────────────────────────────────────────────────────
                • 1–2 inches of water per week during dry summers (July–August).
                • Prefer drip irrigation to reduce fungal pressure in humid conditions.
                • Mulch heavily for moisture retention; deep soak every 3–5 days.
                • Reduce irrigation significantly in wet Port Orchard winters.
                • Watch for root rot in the heavy clay soils common to the area.
                """);
    }

    @Override
    public String getTabTitle() {
        return "Irrigation & Care";
    }

    @Override
    public String getDescription() {
        return "Watering and care tips for the maritime PNW";
    }
}

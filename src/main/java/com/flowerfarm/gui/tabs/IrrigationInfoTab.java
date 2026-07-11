package com.flowerfarm.gui.tabs;

import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.IrrigationAdvisorService.Priority;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

/**
 * Kitsap irrigation advisor — live Open-Meteo when online, climatology offline.
 * Harvest beds from the last 14 days are called out for targeted watering.
 */
public class IrrigationInfoTab implements FlowerFarmTab {

    private final IrrigationAdvisorService advisor;
    private final TabHost host;

    private JPanel panel;
    private JLabel modeBadge;
    private JLabel priorityBadge;
    private JLabel headlineLabel;
    private JTextArea detailArea;
    private JTextArea referenceArea;
    private JButton refreshLiveBtn;
    private JButton climateOnlyBtn;

    public IrrigationInfoTab(IrrigationAdvisorService advisor, TabHost host) {
        this.advisor = advisor;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Irrigation & Care";
    }

    @Override
    public String getDescription() {
        return "Kitsap weather-aware watering tips (live or climatology)";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshAdvice(true);
    }

    @Override
    public void refreshData() {
        // Keep last mode if possible; default to prefer-live on global refresh
        refreshAdvice(true);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel("Irrigation & Care — Port Orchard / Kitsap County (maritime PNW)");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));

        modeBadge = new JLabel(" ");
        modeBadge.setFont(modeBadge.getFont().deriveFont(Font.BOLD));
        priorityBadge = new JLabel(" ");
        priorityBadge.setFont(priorityBadge.getFont().deriveFont(Font.BOLD, 14f));

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        badges.add(modeBadge);
        badges.add(priorityBadge);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(header, BorderLayout.NORTH);
        north.add(badges, BorderLayout.CENTER);

        headlineLabel = new JLabel(" ");
        headlineLabel.setFont(headlineLabel.getFont().deriveFont(Font.PLAIN, 14f));
        north.add(headlineLabel, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        detailArea = new JTextArea(12, 48);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        detailArea.setBorder(BorderFactory.createTitledBorder("Today's plan"));

        referenceArea = new JTextArea(8, 48);
        referenceArea.setEditable(false);
        referenceArea.setLineWrap(true);
        referenceArea.setWrapStyleWord(true);
        referenceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        referenceArea.setBorder(BorderFactory.createTitledBorder("Standing maritime PNW notes"));
        referenceArea.setText(staticReference());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(detailArea), new JScrollPane(referenceArea));
        split.setResizeWeight(0.62);
        panel.add(split, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        refreshLiveBtn = new JButton("Refresh (try live weather)");
        refreshLiveBtn.setToolTipText("Open-Meteo forecast for Port Orchard (no API key). Falls back to climatology offline.");
        refreshLiveBtn.addActionListener(e -> refreshAdvice(true));
        climateOnlyBtn = new JButton("Climatology only");
        climateOnlyBtn.setToolTipText("Skip network — month-based Kitsap guidance only.");
        climateOnlyBtn.addActionListener(e -> refreshAdvice(false));
        JButton harvest = new JButton("Open Harvest Log");
        harvest.addActionListener(e -> {
            if (host != null) {
                host.selectTab("Harvest Log");
                host.setStatus("Log harvests with bed names to personalize irrigation targets.");
            }
        });
        south.add(refreshLiveBtn);
        south.add(climateOnlyBtn);
        south.add(harvest);
        panel.add(south, BorderLayout.SOUTH);
    }

    private void refreshAdvice(boolean preferLive) {
        if (advisor == null) {
            return;
        }
        if (host != null) {
            host.setStatus(preferLive
                    ? "⏳ Fetching Kitsap irrigation advice (live if available)…"
                    : "⏳ Building climatology irrigation advice…");
        }
        // Network call off the EDT
        new SwingWorker<IrrigationAdvice, Void>() {
            @Override
            protected IrrigationAdvice doInBackground() {
                return advisor.advise(preferLive);
            }

            @Override
            protected void done() {
                try {
                    applyAdvice(get());
                } catch (Exception ex) {
                    detailArea.setText("Could not load advice: " + ex.getMessage());
                    if (host != null) {
                        host.setStatus("Irrigation advice failed.");
                    }
                }
            }
        }.execute();
    }

    private void applyAdvice(IrrigationAdvice a) {
        if (a == null) {
            return;
        }
        modeBadge.setText("Mode: " + a.mode()
                + ("LIVE".equals(a.mode()) ? " (Open-Meteo)" : " (offline climatology)"));
        priorityBadge.setText("Priority: " + a.priority().name());
        priorityBadge.setForeground(colorFor(a.priority()));
        headlineLabel.setText("<html><b>" + escape(a.headline()) + "</b></html>");

        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(a.asOfDate())
                .append("  ·  Season band: ").append(a.season().name()).append('\n');
        sb.append("Location: ").append(a.location()).append('\n');
        if (a.weekPrecipInches() != null) {
            sb.append(String.format("7-day precip: %.1f\"   ET₀: %.1f\"   Deficit: %.1f\"%n",
                    a.weekPrecipInches(),
                    a.weekEtInches() != null ? a.weekEtInches() : 0.0,
                    a.moistureDeficitInches() != null ? a.moistureDeficitInches() : 0.0));
        }
        if (a.maxTempF() != null) {
            sb.append(String.format("Forecast temps: min %.0f°F / max %.0f°F%n",
                    a.minTempF() != null ? a.minTempF() : 0.0, a.maxTempF()));
        }
        sb.append('\n');
        if (a.climateNotes() != null) {
            sb.append(a.climateNotes()).append("\n\n");
        }
        sb.append("Actions\n───────\n");
        for (String action : a.actions()) {
            sb.append("• ").append(action).append('\n');
        }
        if (a.activeBeds() != null && !a.activeBeds().isEmpty()) {
            sb.append("\nActive beds: ")
                    .append(a.activeBeds().stream().collect(Collectors.joining(", ")))
                    .append('\n');
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);

        if (host != null) {
            host.setStatus("Irrigation: " + a.priority().name() + " · " + a.mode()
                    + " · " + a.headline());
        }
    }

    private static Color colorFor(Priority p) {
        if (p == null) {
            return UIManager.getColor("Label.foreground");
        }
        return switch (p) {
            case HIGH -> new Color(180, 40, 30);
            case MEDIUM -> new Color(160, 100, 20);
            case LOW -> new Color(40, 110, 50);
            case NONE -> new Color(70, 100, 140);
        };
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String staticReference() {
        return """
                Standing maritime PNW notes (Port Orchard / Kitsap)
                ──────────────────────────────────────────────────
                • 1–2 inches of water per week during dry summers (July–August).
                • Prefer drip irrigation to reduce fungal pressure in humid conditions.
                • Mulch heavily for moisture retention; deep soak every 3–5 days.
                • Reduce irrigation significantly in wet Port Orchard winters.
                • Watch for root rot in the heavy clay soils common to the area.
                • Live mode uses free Open-Meteo (no key) for 7-day precip + ET₀.
                • Climatology mode works fully offline for barn demos and CI.
                """;
    }
}

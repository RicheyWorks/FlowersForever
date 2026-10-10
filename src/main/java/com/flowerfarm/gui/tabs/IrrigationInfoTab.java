package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.IrrigationAdvisorService.Priority;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private JPanel actionChecklist;
    private JLabel actionProgressLabel;
    private JButton clearChecksBtn;
    private JPanel handoffBanner;
    private JLabel handoffLabel;
    private JButton returnFromIrrigationBtn;
    private final Map<Integer, JCheckBox> actionChecks = new LinkedHashMap<>();
    private String actionSnapshot = "";
    private boolean refreshingAdvice;
    private Boolean queuedPreferLive;
    private IrrigationAdvice lastAdvice;
    private String handoffReturnTab;
    private String handoffReturnLabel;

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

    /** Show the retained watering plan and preserve completed field checks. */
    public void showFrom(String returnTab, String returnLabel) {
        getUIComponent();
        String safeTab = returnTab == null ? "" : returnTab.trim();
        String safeLabel = returnLabel == null ? "" : returnLabel.trim();
        if (safeTab.isEmpty()) {
            handoffReturnTab = null;
            handoffReturnLabel = null;
            handoffBanner.setVisible(false);
            return;
        }
        handoffReturnTab = safeTab;
        handoffReturnLabel = safeLabel.isEmpty() ? safeTab : safeLabel;
        String context = "Watering plan opened from " + handoffReturnLabel;
        handoffLabel.setText(context);
        handoffLabel.setToolTipText(
                "The current forecast mode, field plan, and completed checks were preserved.");
        handoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromIrrigationBtn.setText("Return to " + handoffReturnLabel);
        returnFromIrrigationBtn.setToolTipText(
                "Return without rebuilding the Dashboard or morning checklist.");
        returnFromIrrigationBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " from Irrigation and Care");
        handoffBanner.setVisible(true);
        if (host != null) {
            host.setStatus("Opened retained irrigation plan from " + handoffReturnLabel + ".");
        }
    }

    private void returnFromIrrigation() {
        if (host == null || handoffReturnTab == null) {
            return;
        }
        String returnTab = handoffReturnTab;
        String returnLabel = handoffReturnLabel;
        handoffReturnTab = null;
        handoffReturnLabel = null;
        handoffBanner.setVisible(false);
        host.selectTab(returnTab);
        host.setStatus("Returned to " + returnLabel + " from Irrigation & Care.");
    }

    private void buildUI() {
        panel = FarmUi.page();

        modeBadge = FarmUi.wrappingStatusBadge("Weather pending", FarmUi.Tone.NEUTRAL);
        priorityBadge = FarmUi.statusBadge("Priority pending", FarmUi.Tone.NEUTRAL);
        JPanel badges = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        badges.setOpaque(false);
        badges.add(modeBadge, BorderLayout.CENTER);
        badges.add(priorityBadge, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        north.setOpaque(false);
        panel.add(FarmUi.pageHeader(
                "Irrigation & care",
                "Weather-aware watering guidance for Port Orchard and Kitsap beds",
                null), BorderLayout.NORTH);
        north.add(badges, BorderLayout.NORTH);

        handoffLabel = FarmUi.statusBadge("Irrigation handoff", FarmUi.Tone.INFO);
        handoffLabel.getAccessibleContext().setAccessibleName("Irrigation handoff context");
        returnFromIrrigationBtn = FarmUi.secondaryButton("Return to previous workflow");
        returnFromIrrigationBtn.setName("irrigation.returnFromHandoff");
        returnFromIrrigationBtn.addActionListener(event -> returnFromIrrigation());
        handoffBanner = FarmUi.toolbar(handoffLabel, returnFromIrrigationBtn);
        handoffBanner.setName("irrigation.handoffBanner");
        handoffBanner.setVisible(false);
        north.add(handoffBanner, BorderLayout.CENTER);

        headlineLabel = new JLabel(" ");
        headlineLabel.setFont(headlineLabel.getFont().deriveFont(Font.PLAIN, 14f));
        headlineLabel.getAccessibleContext().setAccessibleName("Irrigation recommendation headline");
        north.add(headlineLabel, BorderLayout.SOUTH);

        detailArea = FarmUi.displayArea(12, 48);
        detailArea.setName("irrigation.today");
        detailArea.getAccessibleContext().setAccessibleName("Today's irrigation plan");
        detailArea.setFont(UIManager.getFont("Label.font").deriveFont(14f));
        detailArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        actionChecklist = new ActionChecklist();
        actionChecklist.setName("irrigation.actionChecklist");
        actionChecklist.setLayout(new BoxLayout(actionChecklist, BoxLayout.Y_AXIS));
        actionChecklist.setOpaque(false);
        actionChecklist.setBorder(BorderFactory.createEmptyBorder(
                FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP,
                FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        actionChecklist.getAccessibleContext().setAccessibleName("Today's watering action checklist");

        actionProgressLabel = FarmUi.mutedLabel("Checklist pending");
        actionProgressLabel.setName("irrigation.actionProgress");
        actionProgressLabel.getAccessibleContext().setAccessibleName("Watering action progress");
        clearChecksBtn = FarmUi.secondaryButton("Clear checks");
        clearChecksBtn.setEnabled(false);
        clearChecksBtn.addActionListener(e -> {
            actionChecks.values().forEach(check -> check.setSelected(false));
            updateActionProgress(true);
        });
        JPanel checklistHeader = FarmUi.toolbar(actionProgressLabel, clearChecksBtn);
        JPanel checklistPane = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        checklistPane.setOpaque(false);
        checklistPane.setBorder(BorderFactory.createTitledBorder("Field checklist"));
        checklistPane.add(checklistHeader, BorderLayout.NORTH);
        JScrollPane checklistScroll = new JScrollPane(actionChecklist,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        checklistScroll.setName("irrigation.checklistScroll");
        checklistPane.add(checklistScroll, BorderLayout.CENTER);
        checklistPane.setMinimumSize(new Dimension(370, 200));
        checklistPane.setPreferredSize(new Dimension(400, 230));

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setMinimumSize(new Dimension(260, 200));
        detailScroll.setPreferredSize(new Dimension(440, 230));
        JSplitPane todayWork = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                detailScroll, checklistPane);
        todayWork.setName("irrigation.todayWorkspace");
        todayWork.setResizeWeight(0.55);
        todayWork.setContinuousLayout(true);

        referenceArea = FarmUi.displayArea(8, 48);
        referenceArea.setName("irrigation.reference");
        referenceArea.getAccessibleContext().setAccessibleName("Maritime PNW irrigation notes");
        referenceArea.setFont(UIManager.getFont("Label.font").deriveFont(14f));
        referenceArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        referenceArea.setText(staticReference());

        JPanel today = FarmUi.section("Today's plan", "Live forecast or offline climatology guidance.",
                todayWork);
        today.setMinimumSize(new Dimension(0, 270));
        today.setPreferredSize(new Dimension(840, 300));
        JPanel notes = FarmUi.section("Standing maritime PNW notes",
                "Seasonal guidance remains available without a network connection.",
                new JScrollPane(referenceArea));
        notes.setMinimumSize(new Dimension(0, 185));
        notes.setPreferredSize(new Dimension(840, 210));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                today, notes);
        split.setName("irrigation.workspace");
        split.setResizeWeight(0.62);
        split.setContinuousLayout(true);
        JPanel body = new IrrigationBody(north, split);
        body.setName("irrigation.body");
        FarmUi.revealFocusedControls(body);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("irrigation.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);

        refreshLiveBtn = FarmUi.primaryButton("Refresh live weather");
        FarmUi.initialFocus(refreshLiveBtn);
        refreshLiveBtn.setToolTipText("Open-Meteo forecast for Port Orchard (no API key). Falls back to climatology offline.");
        refreshLiveBtn.addActionListener(e -> refreshAdvice(true));
        climateOnlyBtn = FarmUi.secondaryButton("Use climatology only");
        climateOnlyBtn.setToolTipText("Skip network — month-based Kitsap guidance only.");
        climateOnlyBtn.addActionListener(e -> refreshAdvice(false));
        JButton harvest = FarmUi.secondaryButton("Open Harvest Log");
        harvest.addActionListener(e -> {
            if (host != null) {
                host.selectTab("Harvest Log");
                host.setStatus("Log harvests with bed names to personalize irrigation targets.");
            }
        });
        panel.add(FarmUi.toolbar(refreshLiveBtn, climateOnlyBtn, harvest), BorderLayout.SOUTH);
    }

    /** Keep both reading panes useful when the window is shorter than the daily plan. */
    private static final class IrrigationBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private final JComponent heading;

        private IrrigationBody(JComponent heading, JComponent workspace) {
            super(new BorderLayout(0, FarmUi.CONTROL_GAP));
            this.heading = heading;
            setOpaque(false);
            add(heading, BorderLayout.NORTH);
            add(workspace, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(840, heading.getPreferredSize().height + FarmUi.CONTROL_GAP + 515);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private static final class ActionChecklist extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Measure the action against its viewport, including the checkbox icon and padding. */
    private static final class ActionCheckBox extends JCheckBox {
        private static final long serialVersionUID = 1L;

        private ActionCheckBox(String text) { super(text); }

        @Override public Dimension getPreferredSize() {
            int available = getParent() == null || getParent().getWidth() <= 0 ? 360
                    : getParent().getWidth() - getParent().getInsets().left
                    - getParent().getInsets().right;
            Icon icon = getIcon() == null ? UIManager.getIcon("CheckBox.icon") : getIcon();
            int iconWidth = icon == null ? 20 : icon.getIconWidth();
            int textWidth = Math.max(100, available - iconWidth - getIconTextGap()
                    - getInsets().left - getInsets().right);
            javax.swing.plaf.basic.BasicHTML.updateRenderer(this,
                    "<html>" + escape(getText()) + "</html>");
            javax.swing.text.View content = (javax.swing.text.View)
                    getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            content.setSize(textWidth, 0);
            int textHeight = (int) Math.ceil(content.getPreferredSpan(javax.swing.text.View.Y_AXIS));
            return new Dimension(available, Math.max(icon == null ? 20 : icon.getIconHeight(), textHeight)
                    + getInsets().top + getInsets().bottom);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }

    void refreshAdvice(boolean preferLive) {
        if (advisor == null) {
            return;
        }
        if (refreshingAdvice) {
            queuedPreferLive = preferLive;
            showAdviceRefreshState("Refresh queued · current weather check finishing",
                    "The newest " + (preferLive ? "live-weather" : "climatology")
                            + " request will run next. Current field work remains visible.",
                    FarmUi.Tone.INFO);
            return;
        }
        refreshingAdvice = true;
        refreshLiveBtn.setEnabled(false);
        climateOnlyBtn.setEnabled(false);
        showAdviceRefreshState(
                preferLive ? "Loading live weather…" : "Loading climatology…",
                lastAdvice == null
                        ? "Building the first field plan."
                        : "Refreshing in the background; the current field plan and checks remain visible.",
                FarmUi.Tone.INFO);
        if (lastAdvice == null) {
            FarmUi.updateStatusBadge(priorityBadge, "Priority pending", FarmUi.Tone.NEUTRAL);
            headlineLabel.setText("Building today's irrigation plan…");
            detailArea.setText("Checking weather, moisture demand, and recently active beds…");
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
                    IrrigationAdvice advice = get();
                    if (queuedPreferLive == null) {
                        lastAdvice = advice;
                        applyAdvice(advice);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (queuedPreferLive == null) {
                        failAdviceRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (queuedPreferLive == null) {
                        failAdviceRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (queuedPreferLive == null) {
                        failAdviceRefresh(exception);
                    }
                } finally {
                    finishAdviceRefresh();
                }
            }
        }.execute();
    }

    private void failAdviceRefresh(Throwable failure) {
        String message = FarmUi.actionableError("Loading irrigation guidance", failure);
        if (lastAdvice == null) {
            showAdviceRefreshState("Weather unavailable", message, FarmUi.Tone.DANGER);
            FarmUi.updateStatusBadge(
                    priorityBadge, "Needs attention", FarmUi.Tone.DANGER);
            headlineLabel.setText("Irrigation guidance could not be loaded");
            detailArea.setText(message
                    + "\n\nStanding maritime guidance remains available below.");
            clearActionChecklist("Checklist unavailable",
                    "Reload guidance to rebuild the field checklist.");
        } else {
            showAdviceRefreshState("Weather refresh failed · last plan kept",
                    message + " The plan and completed checks from "
                            + lastAdvice.asOfDate()
                            + " remain visible; verify conditions before continuing.",
                    FarmUi.Tone.DANGER);
            FarmUi.updateStatusBadge(priorityBadge,
                    "Priority: " + lastAdvice.priority().name()
                            + " · verify conditions",
                    FarmUi.Tone.DANGER);
        }
        if (host != null) {
            host.setStatus(lastAdvice == null
                    ? "Irrigation advice failed; retry or use climatology."
                    : "Irrigation refresh failed. Last field plan and checks were kept; verify conditions.");
        }
    }

    private void finishAdviceRefresh() {
        refreshingAdvice = false;
        refreshLiveBtn.setEnabled(true);
        climateOnlyBtn.setEnabled(true);
        if (queuedPreferLive != null) {
            boolean preferLive = queuedPreferLive;
            queuedPreferLive = null;
            refreshAdvice(preferLive);
        }
    }

    private void showAdviceRefreshState(String message, String description,
                                        FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(modeBadge, message, tone);
        modeBadge.setToolTipText(description);
        modeBadge.getAccessibleContext().setAccessibleName(
                "Irrigation weather refresh status");
        modeBadge.getAccessibleContext().setAccessibleDescription(description);
    }

    private void applyAdvice(IrrigationAdvice a) {
        if (a == null) {
            return;
        }
        modeBadge.setText("Mode: " + a.mode()
                + ("LIVE".equals(a.mode()) ? " (Open-Meteo)" : " (offline climatology)"));
        FarmUi.styleStatusBadge(modeBadge,
                "LIVE".equals(a.mode()) ? FarmUi.Tone.INFO : FarmUi.Tone.NEUTRAL);
        modeBadge.setToolTipText("Current irrigation plan for " + a.asOfDate()
                + " using " + a.mode() + " data.");
        modeBadge.getAccessibleContext().setAccessibleDescription(modeBadge.getToolTipText());
        priorityBadge.setText("Priority: " + a.priority().name());
        FarmUi.styleStatusBadge(priorityBadge, toneFor(a.priority()));
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
        if (a.activeBeds() != null && !a.activeBeds().isEmpty()) {
            sb.append("Active beds: ")
                    .append(a.activeBeds().stream().collect(Collectors.joining(", ")))
                    .append('\n');
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
        rebuildActionChecklist(a);

        if (host != null) {
            host.setStatus("Irrigation: " + a.priority().name() + " · " + a.mode()
                    + " · " + a.headline());
        }
    }

    private void rebuildActionChecklist(IrrigationAdvice advice) {
        List<String> actions = advice.actions() == null ? List.of() : advice.actions();
        String nextSnapshot = String.join("\u001f",
                Objects.toString(advice.asOfDate(), ""),
                Objects.toString(advice.mode(), ""),
                Objects.toString(advice.priority(), ""),
                Objects.toString(advice.headline(), ""),
                String.join("\u001e", actions),
                advice.activeBeds() == null ? "" : String.join("\u001e", advice.activeBeds()));
        Set<Integer> completed = new HashSet<>();
        if (nextSnapshot.equals(actionSnapshot)) {
            actionChecks.forEach((index, check) -> {
                if (check.isSelected()) {
                    completed.add(index);
                }
            });
        }

        actionSnapshot = nextSnapshot;
        actionChecks.clear();
        actionChecklist.removeAll();
        if (actions.isEmpty()) {
            actionChecklist.add(FarmUi.formHint("No watering actions are needed for this plan."));
            updateActionProgress(false);
            refreshChecklistLayout();
            return;
        }

        for (int index = 0; index < actions.size(); index++) {
            String action = actions.get(index);
            JCheckBox check = new ActionCheckBox(action);
            check.setName("irrigation.action." + index);
            check.setOpaque(false);
            check.setAlignmentX(Component.LEFT_ALIGNMENT);
            check.setSelected(completed.contains(index));
            check.getAccessibleContext().setAccessibleName(
                    "Watering action " + (index + 1) + ": " + action);
            check.addActionListener(e -> updateActionProgress(true));
            actionChecks.put(index, check);
            actionChecklist.add(check);
            if (index < actions.size() - 1) {
                actionChecklist.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
            }
        }
        updateActionProgress(false);
        refreshChecklistLayout();
    }

    private void clearActionChecklist(String progress, String guidance) {
        actionSnapshot = "";
        actionChecks.clear();
        actionChecklist.removeAll();
        actionChecklist.add(FarmUi.formHint(guidance));
        actionProgressLabel.setText(progress);
        clearChecksBtn.setEnabled(false);
        refreshChecklistLayout();
    }

    private void updateActionProgress(boolean announce) {
        int total = actionChecks.size();
        int completed = (int) actionChecks.values().stream()
                .filter(AbstractButton::isSelected)
                .count();
        if (total == 0) {
            actionProgressLabel.setText("No field actions");
            clearChecksBtn.setEnabled(false);
            return;
        }
        int remaining = total - completed;
        actionProgressLabel.setText(completed + " of " + total + " done · "
                + remaining + " remaining");
        clearChecksBtn.setEnabled(completed > 0);
        if (announce && host != null) {
            host.setStatus("Irrigation progress: " + completed + " of " + total
                    + " actions done.");
        }
    }

    private void refreshChecklistLayout() {
        FarmUi.revealFocusedControls(actionChecklist);
        actionChecklist.revalidate();
        actionChecklist.repaint();
    }

    private static FarmUi.Tone toneFor(Priority priority) {
        if (priority == null) {
            return FarmUi.Tone.NEUTRAL;
        }
        return switch (priority) {
            case HIGH -> FarmUi.Tone.DANGER;
            case MEDIUM -> FarmUi.Tone.WARNING;
            case LOW -> FarmUi.Tone.SUCCESS;
            case NONE -> FarmUi.Tone.INFO;
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

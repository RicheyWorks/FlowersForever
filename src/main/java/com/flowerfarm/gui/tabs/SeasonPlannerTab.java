package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmClipboard;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.SeasonPlanService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.accessibility.AccessibleContext;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Visual season and bed planning from first sow through final harvest. */
public class SeasonPlannerTab implements FlowerFarmTab {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter INSIGHT_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu");
    private static final int SUCCESSION_SHIFT_DAYS = 14;
    private static final String[] COLUMNS = {
            "Bed / field", "Crop / variety", "Sow", "Transplant", "Harvest window",
            "Bed ft", "Expected", "Actual", "% plan", "Pace", "Status"
    };

    private final SeasonPlanService seasonPlanService;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmClipboard clipboard;

    private JPanel panel;
    private JComboBox<Integer> seasonPicker;
    private JButton addButton;
    private JButton refreshButton;
    private JButton editButton;
    private JButton successionButton;
    private JButton deleteButton;
    private JButton reviewDraftsButton;
    private JLabel blocksValue;
    private JLabel bedsValue;
    private JLabel stemsValue;
    private JLabel harvestValue;
    private JLabel blocksDetail;
    private JLabel bedsDetail;
    private JLabel stemsDetail;
    private JLabel harvestDetail;
    private JLabel statusBadge;
    private SeasonTimeline timeline;
    private WeeklyPaceChart weeklyChart;
    private JLabel weeklyStatus;
    private JTabbedPane workspace;
    private JPanel insightsList;
    private JLabel insightsSummary;
    private DefaultTableModel availabilityTableModel;
    private JLabel availabilityStatus;
    private JButton copyAvailabilityButton;
    private JTable table;
    private DefaultTableModel tableModel;
    private List<SeasonPlanEntry> lastView = List.of();
    private List<SeasonPlanService.PlanPerformance> lastPerformance = List.of();
    private List<AvailabilityRow> lastAvailability = List.of();
    private Long requestedSelectionId;
    private final Map<Long, PendingPlanting> retainedDrafts = new LinkedHashMap<>();
    private long nextDraftKey;
    private long decisionRevision;
    private long selectionRevision;
    private boolean refreshQueued;
    private boolean refreshFailed;
    private String acknowledgedChange;
    private String hostRefreshWarning;
    private boolean canWrite = true;
    private boolean loading;
    private boolean saving;
    private boolean savingDraft;
    private boolean updatingSeasons;

    public SeasonPlannerTab(SeasonPlanService seasonPlanService, TabHost host) {
        this(seasonPlanService, host, FarmDialogs.swing());
    }

    public SeasonPlannerTab(SeasonPlanService seasonPlanService, TabHost host,
                            FarmDialogs dialogs) {
        this(seasonPlanService, host, dialogs, FarmClipboard.system());
    }

    public SeasonPlannerTab(SeasonPlanService seasonPlanService, TabHost host,
                            FarmDialogs dialogs, FarmClipboard clipboard) {
        this.seasonPlanService = seasonPlanService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.clipboard = java.util.Objects.requireNonNull(clipboard, "clipboard");
    }

    @Override
    public String getTabTitle() {
        return "Season Planner";
    }

    @Override
    public String getDescription() {
        return "Plan crop timing, bed use, expected stems, and harvest windows in one view";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshData();
    }

    @Override
    public void refreshData() {
        if (tableModel != null) {
            startRefresh();
        }
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        if (this.canWrite != canWrite) {
            decisionRevision++;
        }
        this.canWrite = canWrite;
        updateActions();
    }

    /** Warns before session-only planting drafts or an unresolved write can be lost. */
    public String applicationExitWarning() {
        if (retainedDrafts.isEmpty() && !saving) {
            return null;
        }
        List<String> work = new ArrayList<>();
        if (!retainedDrafts.isEmpty()) {
            work.add(countLabel(retainedDrafts.size(), "retained planting draft", "retained planting drafts"));
        }
        if (saving) {
            work.add("a planting change still awaiting its result");
        }
        return "Season Planner has " + String.join(" and ", work) + "."
                + (retainedDrafts.isEmpty() ? "" : " Drafts are kept only for this session.")
                + (saving ? " Leaving does not cancel the submitted change." : "");
    }

    /** Whether closing could hide the result of a submitted planting change. */
    public boolean hasPendingSave() {
        return saving;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("seasonPlanner.page");

        seasonPicker = new JComboBox<>(new Integer[]{LocalDate.now().getYear()});
        FarmUi.initialFocus(seasonPicker);
        seasonPicker.setName("seasonPlanner.season");
        seasonPicker.getAccessibleContext().setAccessibleName("Season year");
        addButton = FarmUi.primaryButton("Add planting");
        addButton.setName("seasonPlanner.add");
        refreshButton = FarmUi.secondaryButton("Refresh");
        refreshButton.setName("seasonPlanner.refresh");
        reviewDraftsButton = FarmUi.secondaryButton("Review drafts (0)");
        reviewDraftsButton.setName("seasonPlanner.reviewDrafts");
        panel.add(FarmUi.pageHeader(
                "Season & Bed Planner",
                "Enter each planting block once, then see timing, bed capacity, and expected stems together.",
                FarmUi.toolbar(new JLabel("Season:"), seasonPicker, refreshButton,
                        reviewDraftsButton, addButton)),
                BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        content.add(buildMetrics(), BorderLayout.NORTH);
        content.add(buildWorkspace(), BorderLayout.CENTER);
        statusBadge = FarmUi.wrappingStatusBadge("Ready to plan the season", FarmUi.Tone.NEUTRAL);
        statusBadge.setName("seasonPlanner.status");
        content.add(statusBadge, BorderLayout.SOUTH);
        panel.add(content, BorderLayout.CENTER);

        seasonPicker.addActionListener(event -> {
            if (!updatingSeasons) {
                startRefresh();
            }
        });
        refreshButton.addActionListener(event -> startRefresh());
        addButton.addActionListener(event -> addPlanting());
        editButton.addActionListener(event -> editSelected());
        successionButton.addActionListener(event -> planSuccession());
        deleteButton.addActionListener(event -> deleteSelected());
        reviewDraftsButton.addActionListener(event -> reviewDrafts());
        updateActions();
    }

    private JPanel buildMetrics() {
        blocksValue = metricValue("0");
        bedsValue = metricValue("0");
        stemsValue = metricValue("0");
        harvestValue = metricValue("—");
        blocksDetail = FarmUi.mutedLabel("No planting blocks yet");
        bedsDetail = FarmUi.mutedLabel("0 crops assigned");
        stemsDetail = FarmUi.mutedLabel("Across 0 bed ft");
        harvestDetail = FarmUi.mutedLabel("Add a block to set timing");

        JPanel metrics = new JPanel(new GridLayout(1, 4, FarmUi.CONTROL_GAP, 0));
        metrics.setName("seasonPlanner.metrics");
        metrics.setOpaque(false);
        metrics.add(FarmUi.metricCard("Planting blocks", blocksValue, blocksDetail,
                FarmUi.Tone.SUCCESS));
        metrics.add(FarmUi.metricCard("Beds in use", bedsValue, bedsDetail,
                FarmUi.Tone.INFO));
        metrics.add(FarmUi.metricCard("Expected stems", stemsValue, stemsDetail,
                FarmUi.Tone.WARNING));
        metrics.add(FarmUi.metricCard("Harvest window", harvestValue, harvestDetail,
                FarmUi.Tone.DANGER));
        return metrics;
    }

    private JComponent buildWorkspace() {
        timeline = new SeasonTimeline();
        timeline.setName("seasonPlanner.timeline");
        JScrollPane timelineScroll = new JScrollPane(timeline);
        timelineScroll.setBorder(BorderFactory.createEmptyBorder());
        timelineScroll.getViewport().setBackground(FarmUi.surfaceColor());
        timelineScroll.getAccessibleContext().setAccessibleName("Season timeline scroll area");

        JPanel legend = FarmUi.toolbar(
                legend("Sow / propagation", FarmUi.Tone.WARNING),
                legend("Growing", FarmUi.Tone.INFO),
                legend("Harvest plan / actual fill", FarmUi.Tone.SUCCESS));
        JPanel timelineContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        timelineContent.setOpaque(false);
        timelineContent.add(legend, BorderLayout.NORTH);
        timelineContent.add(timelineScroll, BorderLayout.CENTER);
        JPanel timelineSection = FarmUi.section(
                "Season timeline",
                "Outlined harvest bars are planned; solid green shows recorded stems. Red beds overlap.",
                timelineContent);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 5 -> Double.class;
                    case 6, 7 -> Integer.class;
                    case 8 -> Double.class;
                    default -> String.class;
                };
            }
        };
        table = new JTable(tableModel);
        table.setName("seasonPlanner.table");
        table.setRowSorter(new TableRowSorter<>(tableModel));
        FarmUi.configureTable(table, "Season planting plan table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectionRevision++;
                updateActions();
            }
        });
        editButton = FarmUi.secondaryButton("Edit selected");
        editButton.setName("seasonPlanner.edit");
        successionButton = FarmUi.secondaryButton("Plan succession");
        successionButton.setName("seasonPlanner.succession");
        successionButton.getAccessibleContext().setAccessibleDescription(
                "Create the next planting from the selected block with dates shifted 14 days");
        deleteButton = FarmUi.dangerButton("Remove selected");
        deleteButton.setName("seasonPlanner.delete");
        JPanel tableContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        tableContent.setOpaque(false);
        tableContent.add(FarmUi.tableScroll(table), BorderLayout.CENTER);
        tableContent.add(FarmUi.toolbar(editButton, successionButton, deleteButton),
                BorderLayout.SOUTH);
        JPanel recordsSection = FarmUi.section(
                "Planting records",
                "Sort, edit, or turn a selected block into the next succession without retyping it.",
                tableContent);

        weeklyChart = new WeeklyPaceChart();
        weeklyChart.setName("seasonPlanner.weeklyChart");
        JScrollPane weeklyScroll = new JScrollPane(weeklyChart);
        weeklyScroll.setBorder(BorderFactory.createEmptyBorder());
        weeklyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        weeklyScroll.getViewport().setBackground(FarmUi.surfaceColor());
        weeklyScroll.getAccessibleContext().setAccessibleName("Weekly production chart scroll area");
        weeklyStatus = FarmUi.statusBadge("Add a planting block to see weekly pace",
                FarmUi.Tone.NEUTRAL);
        weeklyStatus.setName("seasonPlanner.weeklyStatus");
        JPanel paceHeader = FarmUi.toolbar(
                legend("Actual stems", FarmUi.Tone.SUCCESS),
                legend("Planned pace", FarmUi.Tone.WARNING));
        JPanel paceContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        paceContent.setOpaque(false);
        paceContent.add(paceHeader, BorderLayout.NORTH);
        paceContent.add(weeklyScroll, BorderLayout.CENTER);
        paceContent.add(weeklyStatus, BorderLayout.SOUTH);
        JPanel paceSection = FarmUi.section(
                "Weekly harvest pace",
                "Green bars are matched stem harvests; the gold line spreads each block's target across its harvest window.",
                paceContent);

        insightsSummary = FarmUi.statusBadge(
                "Building grounded plan insights…", FarmUi.Tone.INFO);
        insightsSummary.setName("seasonPlanner.insightsSummary");
        insightsList = new JPanel();
        insightsList.setName("seasonPlanner.insightsList");
        insightsList.setOpaque(false);
        insightsList.setLayout(new BoxLayout(insightsList, BoxLayout.Y_AXIS));
        insightsList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JScrollPane insightsScroll = new JScrollPane(insightsList);
        insightsScroll.setName("seasonPlanner.insightsScroll");
        insightsScroll.setBorder(BorderFactory.createEmptyBorder());
        insightsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        insightsScroll.getVerticalScrollBar().setUnitIncrement(16);
        insightsScroll.getViewport().setBackground(FarmUi.surfaceColor());
        insightsScroll.getAccessibleContext().setAccessibleName(
                "Guided season plan insights scroll area");
        JPanel insightsContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        insightsContent.setOpaque(false);
        insightsContent.add(insightsSummary, BorderLayout.NORTH);
        insightsContent.add(insightsScroll, BorderLayout.CENTER);
        JPanel insightsSection = FarmUi.section(
                "Guided plan review",
                "See the exact beds, crops, dates, and pace signals that need attention, then jump to the right record.",
                insightsContent);

        availabilityTableModel = new DefaultTableModel(new String[]{
                "Week of", "Crop", "Variety", "Planned stems", "Blocks", "Signal"}, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 3, 4 -> Integer.class;
                    default -> String.class;
                };
            }
        };
        JTable availabilityTable = new JTable(availabilityTableModel);
        availabilityTable.setName("seasonPlanner.availabilityTable");
        availabilityTable.setRowSorter(new TableRowSorter<>(availabilityTableModel));
        FarmUi.configureTable(availabilityTable,
                "Weekly planning availability by crop and variety");
        availabilityStatus = FarmUi.statusBadge(
                "Add planting blocks to build a weekly availability outlook",
                FarmUi.Tone.NEUTRAL);
        availabilityStatus.setName("seasonPlanner.availabilityStatus");
        copyAvailabilityButton = FarmUi.secondaryButton("Copy planning availability");
        copyAvailabilityButton.setName("seasonPlanner.copyAvailability");
        copyAvailabilityButton.setToolTipText(
                "Copy a tab-separated weekly outlook for buyer conversations; confirm live inventory before promising stems.");
        copyAvailabilityButton.addActionListener(event -> copyAvailability());
        JPanel availabilityContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        availabilityContent.setOpaque(false);
        availabilityContent.add(FarmUi.tableScroll(availabilityTable), BorderLayout.CENTER);
        availabilityContent.add(FarmUi.toolbar(
                copyAvailabilityButton, availabilityStatus), BorderLayout.SOUTH);
        JPanel availabilitySection = FarmUi.section(
                "Planning availability",
                "Expected stems are spread across each crop's harvest window. This is a planning signal, not confirmed inventory.",
                availabilityContent);

        workspace = new JTabbedPane();
        workspace.setName("seasonPlanner.workspace");
        workspace.getAccessibleContext().setAccessibleName("Season planner views");
        workspace.addTab("Visual timeline", timelineSection);
        workspace.addTab("Plan records", recordsSection);
        workspace.addTab("Weekly pace", paceSection);
        workspace.addTab("Plan insights", insightsSection);
        workspace.addTab("Availability outlook", availabilitySection);
        return workspace;
    }

    private JLabel legend(String text, FarmUi.Tone tone) {
        JLabel label = FarmUi.statusBadge(text, tone);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    private static JLabel metricValue(String text) {
        JLabel label = new JLabel(text);
        label.setName("seasonPlanner.metricValue");
        return label;
    }

    private int selectedSeason() {
        Object selected = seasonPicker == null ? null : seasonPicker.getSelectedItem();
        return selected instanceof Integer year ? year : LocalDate.now().getYear();
    }

    private void startRefresh() {
        if (tableModel == null) {
            return;
        }
        decisionRevision++;
        if (loading || saving) {
            refreshQueued = true;
            return;
        }
        refreshQueued = false;
        int seasonYear = selectedSeason();
        Long selectedId = requestedSelectionId != null ? requestedSelectionId
                : selectedEntry() == null ? null : selectedEntry().getId();
        loading = true;
        updateActions();
        FarmUi.updateStatusBadge(statusBadge, "Building the " + seasonYear + " plan…",
                FarmUi.Tone.INFO);

        new SwingWorker<SeasonSnapshot, Void>() {
            @Override protected SeasonSnapshot doInBackground() {
                return new SeasonSnapshot(
                        seasonPlanService.analyze(seasonYear),
                        seasonPlanService.availableSeasons());
            }

            @Override protected void done() {
                try {
                    SeasonSnapshot snapshot = get();
                    if (selectedSeason() == seasonYear && !refreshQueued) {
                        applySnapshot(snapshot, selectedId, seasonYear);
                        refreshFailed = false;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showRefreshFailure(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showRefreshFailure(
                            exception.getCause() == null ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    showRefreshFailure(exception);
                } finally {
                    loading = false;
                    decisionRevision++;
                    if (refreshQueued || selectedSeason() != seasonYear) {
                        startRefresh();
                    }
                    updateActions();
                }
            }
        }.execute();
    }

    private void applySnapshot(SeasonSnapshot snapshot, Long selectedId, int selectedYear) {
        SeasonPlanService.SeasonAnalysis analysis = snapshot.analysis();
        lastView = List.copyOf(analysis.entries());
        lastPerformance = List.copyOf(analysis.performance());
        updateSeasonChoices(snapshot.seasons(), selectedYear);
        tableModel.setRowCount(0);
        for (SeasonPlanEntry entry : lastView) {
            tableModel.addRow(row(entry, performanceFor(entry)));
        }
        Set<SeasonPlanEntry> conflictEntries = conflictEntries(analysis.bedConflicts());
        timeline.setSeason(selectedYear, lastView, lastPerformance, conflictEntries);
        weeklyChart.setSeries(selectedYear, analysis.weeklyProduction());
        updateWeeklyStatus(analysis);
        updateAvailability(lastView);
        updateInsights(analysis);
        updateMetrics(analysis);
        boolean restored = restoreSelection(selectedId);
        requestedSelectionId = null;
        if (!restored && !lastView.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        String message = analysisMessage(analysis, selectedYear);
        String outcome = acknowledgedChange == null ? "" : acknowledgedChange + " ";
        String warning = hostRefreshWarning == null ? "" : " " + hostRefreshWarning;
        setStatus(outcome + message + warning,
                hostRefreshWarning == null ? analysisTone(analysis) : FarmUi.Tone.WARNING);
        acknowledgedChange = null;
        hostRefreshWarning = null;
    }

    private void updateSeasonChoices(List<Integer> years, int selectedYear) {
        updatingSeasons = true;
        try {
            DefaultComboBoxModel<Integer> model = new DefaultComboBoxModel<>();
            model.addElement(selectedYear);
            for (Integer year : years) {
                if (year != null && year != selectedYear) {
                    model.addElement(year);
                }
            }
            seasonPicker.setModel(model);
            seasonPicker.setSelectedItem(selectedYear);
        } finally {
            updatingSeasons = false;
        }
    }

    private void updateMetrics(SeasonPlanService.SeasonAnalysis analysis) {
        SeasonPlanService.SeasonSummary summary = analysis.summary();
        long behind = analysis.performance().stream()
                .filter(item -> item.pace() == SeasonPlanService.PlanPace.BEHIND).count();
        blocksValue.setText(Integer.toString(summary.plantingBlocks()));
        blocksDetail.setText(summary.activeBlocks() + " active · "
                + (behind == 0 ? summary.cropCount() + " crops" : behind + " behind pace"));
        bedsValue.setText(Integer.toString(summary.bedCount()));
        bedsDetail.setText(formatNumber(summary.bedFeet()) + " bed ft · "
                + countLabel(analysis.bedConflicts().size(), "overlap", "overlaps"));
        stemsValue.setText(String.format(Locale.US, "%,d", summary.expectedStems()));
        stemsDetail.setText(summary.plantingBlocks() == 0 ? "No yield estimate yet"
                : formatNumber(analysis.actualStems()) + " actual · "
                        + percent(analysis.actualStems(), summary.expectedStems()) + "% of plan");
        if (summary.firstHarvest() == null) {
            harvestValue.setText("—");
            harvestDetail.setText("Add a block to set timing");
        } else {
            harvestValue.setText(SHORT_DATE.format(summary.firstHarvest()));
            harvestDetail.setText("through " + SHORT_DATE.format(summary.lastHarvest())
                    + " · " + countLabel(analysis.harvestGaps().size(), "gap", "gaps"));
        }
    }

    private void updateWeeklyStatus(SeasonPlanService.SeasonAnalysis analysis) {
        if (analysis.entries().isEmpty()) {
            FarmUi.updateStatusBadge(weeklyStatus,
                    "Add a planting block to see weekly pace", FarmUi.Tone.NEUTRAL);
            return;
        }
        double expectedToDate = analysis.performance().stream()
                .mapToDouble(SeasonPlanService.PlanPerformance::expectedToDate).sum();
        if (expectedToDate <= 0) {
            FarmUi.updateStatusBadge(weeklyStatus,
                    "Harvest has not started · the planned line shows what is coming",
                    FarmUi.Tone.INFO);
            return;
        }
        double variancePercent = (analysis.actualStems() - expectedToDate)
                * 100d / expectedToDate;
        String direction = Math.abs(variancePercent) < 0.5 ? "on planned pace"
                : formatNumber(Math.abs(variancePercent)) + "% "
                        + (variancePercent > 0 ? "ahead of pace" : "behind pace");
        FarmUi.Tone tone = variancePercent >= 10 ? FarmUi.Tone.SUCCESS
                : variancePercent < -20 ? FarmUi.Tone.DANGER : FarmUi.Tone.INFO;
        FarmUi.updateStatusBadge(weeklyStatus,
                formatNumber(analysis.actualStems()) + " actual stems · "
                        + formatNumber(expectedToDate) + " expected by today · " + direction,
                tone);
    }

    private void updateAvailability(List<SeasonPlanEntry> entries) {
        lastAvailability = availabilityRows(entries);
        availabilityTableModel.setRowCount(0);
        for (AvailabilityRow row : lastAvailability) {
            availabilityTableModel.addRow(new Object[]{
                    row.weekOf().toString(), row.crop(), row.variety(),
                    row.plannedStems(), row.blocks(), row.signal()});
        }
        int total = lastAvailability.stream()
                .mapToInt(AvailabilityRow::plannedStems).sum();
        FarmUi.updateStatusBadge(availabilityStatus,
                lastAvailability.isEmpty()
                        ? "Add planting blocks to build a weekly availability outlook"
                        : lastAvailability.size() + " crop-week estimates · about "
                                + String.format(Locale.US, "%,d", total)
                                + " planned stems · confirm live stock before committing",
                lastAvailability.isEmpty() ? FarmUi.Tone.NEUTRAL : FarmUi.Tone.INFO);
        updateActions();
    }

    private void copyAvailability() {
        if (lastAvailability.isEmpty()) {
            FarmUi.updateStatusBadge(availabilityStatus,
                    "Nothing to copy until the season has planting blocks",
                    FarmUi.Tone.WARNING);
            return;
        }
        StringBuilder text = new StringBuilder(
                "Week of\tCrop\tVariety\tPlanned stems\tBlocks\tSignal\n");
        for (AvailabilityRow row : lastAvailability) {
            text.append(row.weekOf()).append('\t')
                    .append(row.crop()).append('\t')
                    .append(row.variety()).append('\t')
                    .append(row.plannedStems()).append('\t')
                    .append(row.blocks()).append('\t')
                    .append(row.signal()).append('\n');
        }
        try {
            clipboard.copyText(text.toString());
            FarmUi.updateStatusBadge(availabilityStatus,
                    "Planning availability copied · confirm live inventory before sharing",
                    FarmUi.Tone.SUCCESS);
        } catch (HeadlessException | IllegalStateException exception) {
            FarmUi.updateStatusBadge(availabilityStatus,
                    "Clipboard unavailable · availability stayed in the table",
                    FarmUi.Tone.WARNING);
        }
    }

    private static List<AvailabilityRow> availabilityRows(List<SeasonPlanEntry> entries) {
        Map<AvailabilityKey, AvailabilityTotal> totals = new LinkedHashMap<>();
        for (SeasonPlanEntry entry : entries == null
                ? List.<SeasonPlanEntry>of() : entries) {
            long windowDays = ChronoUnit.DAYS.between(
                    entry.getHarvestStart(), entry.getHarvestEnd()) + 1;
            for (LocalDate week = weekStart(entry.getHarvestStart());
                 !week.isAfter(weekStart(entry.getHarvestEnd())); week = week.plusWeeks(1)) {
                LocalDate overlapStart = week.isAfter(entry.getHarvestStart())
                        ? week : entry.getHarvestStart();
                LocalDate weekEnd = week.plusDays(6);
                LocalDate overlapEnd = weekEnd.isBefore(entry.getHarvestEnd())
                        ? weekEnd : entry.getHarvestEnd();
                long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
                double stems = entry.getExpectedStems() * overlapDays / (double) windowDays;
                AvailabilityKey key = new AvailabilityKey(
                        week, entry.getCropName(), entry.getVariety());
                totals.merge(key, new AvailabilityTotal(stems, 1),
                        (left, right) -> new AvailabilityTotal(
                                left.stems() + right.stems(),
                                left.blocks() + right.blocks()));
            }
        }
        LocalDate thisWeek = weekStart(LocalDate.now());
        List<AvailabilityRow> rows = new ArrayList<>();
        totals.forEach((key, value) -> rows.add(new AvailabilityRow(
                key.weekOf(), key.crop(), key.variety(),
                (int) Math.round(value.stems()), value.blocks(),
                key.weekOf().isBefore(thisWeek) ? "Past plan week"
                        : key.weekOf().equals(thisWeek) ? "Current plan week"
                        : "Upcoming plan week")));
        rows.sort(Comparator.comparing(AvailabilityRow::weekOf)
                .thenComparing(AvailabilityRow::crop, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AvailabilityRow::variety, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(rows);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void updateInsights(SeasonPlanService.SeasonAnalysis analysis) {
        insightsList.removeAll();
        long behindCount = analysis.performance().stream()
                .filter(item -> item.pace() == SeasonPlanService.PlanPace.BEHIND)
                .count();
        int signalCount = analysis.bedConflicts().size() + analysis.harvestGaps().size()
                + (int) behindCount + (analysis.unmatchedHarvestCount() > 0 ? 1 : 0);
        FarmUi.updateStatusBadge(insightsSummary,
                signalCount == 0
                        ? "Plan looks aligned · no overlaps, harvest gaps, pace warnings, or unmatched stem harvests"
                        : countLabel(signalCount, "planning signal", "planning signals")
                                + " to review · use an action below to reach the relevant records",
                signalCount == 0 ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);

        for (SeasonPlanService.BedConflict conflict : analysis.bedConflicts()) {
            JButton review = insightButton("Review both blocks", "reviewConflict", () ->
                    showPlanRecord(conflict.first(),
                            "Opened the two overlapping " + conflict.first().getBedOrField()
                                    + " planting blocks."));
            review.getAccessibleContext().setAccessibleDescription(
                    "Review " + cropLabel(conflict.first()) + " and "
                            + cropLabel(conflict.second()) + " in Plan records");
            addInsight("Bed overlap · " + conflict.first().getBedOrField(),
                    cropLabel(conflict.first()) + " and " + cropLabel(conflict.second())
                            + " both use this bed from "
                            + INSIGHT_DATE.format(conflict.overlapStart()) + " through "
                            + INSIGHT_DATE.format(conflict.overlapEnd()) + ".",
                    FarmUi.Tone.DANGER, review);
        }

        for (SeasonPlanService.HarvestGap gap : analysis.harvestGaps()) {
            SeasonPlanEntry next = nextBlockAfter(gap);
            JButton review = insightButton(
                    next == null ? "Review plan records" : "Review next block",
                    "reviewGap", () -> showPlanRecord(next,
                            "Opened the planting plan beside the " + gap.days()
                                    + "-day harvest gap."));
            addInsight("Harvest gap · " + gap.days() + " days",
                    "No expected harvest is scheduled from " + INSIGHT_DATE.format(gap.from())
                            + " through " + INSIGHT_DATE.format(gap.to())
                            + ". Review nearby timing or add a succession if the farm needs coverage.",
                    FarmUi.Tone.WARNING, review);
        }

        for (SeasonPlanService.PlanPerformance performance : analysis.performance()) {
            if (performance.pace() != SeasonPlanService.PlanPace.BEHIND) {
                continue;
            }
            SeasonPlanEntry entry = performance.entry();
            JButton review = insightButton("Review planting block", "reviewPace", () ->
                    showPlanRecord(entry, "Opened the planting block that is behind pace."));
            addInsight("Behind planned pace · " + cropLabel(entry),
                    formatNumber(performance.actualStems()) + " actual stems versus "
                            + formatNumber(performance.expectedToDate())
                            + " expected by today · "
                            + formatNumber(Math.abs(performance.varianceToDate()))
                            + " stems behind.",
                    FarmUi.Tone.INFO, review);
        }

        if (analysis.unmatchedHarvestCount() > 0) {
            JButton openHarvests = insightButton("Open Harvest Log", "openHarvests", () -> {
                if (host != null) {
                    host.selectTab("Harvest Log");
                    host.setStatus("Opened Harvest Log to review stem harvests that do not match the season plan.");
                }
            });
            addInsight("Harvest matching · "
                            + countLabel(analysis.unmatchedHarvestCount(), "record", "records"),
                    formatNumber(analysis.unmatchedStems())
                            + " unmatched stems are not included in plan performance because their crop, bed, or date does not match a planting block.",
                    FarmUi.Tone.WARNING, openHarvests);
        }

        if (signalCount == 0) {
            JPanel clear = FarmUi.emptyState(
                    "The season plan is aligned",
                    "Keep recording stem harvests; new overlaps, gaps, and pace changes will appear here.");
            clear.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            clear.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
            insightsList.add(clear);
        }
        insightsList.revalidate();
        insightsList.repaint();
    }

    private JButton insightButton(String text, String name, Runnable action) {
        JButton button = FarmUi.secondaryButton(text);
        button.setName("seasonPlanner.insight." + name);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void addInsight(String heading, String message, FarmUi.Tone tone,
                            JButton... actions) {
        JLabel messageLabel = new JLabel("<html><body style='width: 650px'>"
                + html(message) + "</body></html>");
        messageLabel.getAccessibleContext().setAccessibleName(message);
        JPanel card = FarmUi.insightCard(heading, messageLabel, tone);
        card.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        card.add(FarmUi.toolbar(actions), BorderLayout.EAST);
        Dimension preferred = card.getPreferredSize();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        insightsList.add(card);
        insightsList.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
    }

    private static String html(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private SeasonPlanEntry nextBlockAfter(SeasonPlanService.HarvestGap gap) {
        return lastView.stream()
                .filter(entry -> entry.getHarvestStart().isAfter(gap.to()))
                .min(java.util.Comparator.comparing(SeasonPlanEntry::getHarvestStart))
                .orElse(null);
    }

    private void showPlanRecord(SeasonPlanEntry entry, String status) {
        int recordsIndex = workspace.indexOfTab("Plan records");
        if (recordsIndex >= 0) {
            workspace.setSelectedIndex(recordsIndex);
        }
        if (entry != null) {
            restoreSelection(entry.getId());
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                table.scrollRectToVisible(table.getCellRect(selectedRow, 0, true));
            }
        }
        SwingUtilities.invokeLater(table::requestFocusInWindow);
        if (host != null) {
            host.setStatus(status);
        }
    }

    private Object[] row(SeasonPlanEntry entry, SeasonPlanService.PlanPerformance performance) {
        return new Object[]{
                entry.getBedOrField(), cropLabel(entry), entry.getSowDate().toString(),
                entry.getTransplantDate() == null ? "—" : entry.getTransplantDate().toString(),
                SHORT_DATE.format(entry.getHarvestStart()) + " – "
                        + SHORT_DATE.format(entry.getHarvestEnd()),
                entry.getBedFeet(), entry.getExpectedStems(),
                performance == null ? 0 : (int) Math.round(performance.actualStems()),
                performance == null ? 0d : rounded(performance.completionPercent()),
                performance == null ? "Not started" : performance.pace().getLabel(),
                entry.getStatus().getLabel()
        };
    }

    private SeasonPlanService.PlanPerformance performanceFor(SeasonPlanEntry entry) {
        return lastPerformance.stream()
                .filter(item -> item.entry() == entry)
                .findFirst().orElse(null);
    }

    private static Set<SeasonPlanEntry> conflictEntries(
            List<SeasonPlanService.BedConflict> conflicts) {
        Set<SeasonPlanEntry> entries = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SeasonPlanService.BedConflict conflict : conflicts) {
            entries.add(conflict.first());
            entries.add(conflict.second());
        }
        return entries;
    }

    private static String analysisMessage(SeasonPlanService.SeasonAnalysis analysis, int year) {
        if (analysis.entries().isEmpty()) {
            return "No planting blocks for " + year + " — add the first one";
        }
        String message = formatNumber(analysis.actualStems()) + " actual / "
                + String.format(Locale.US, "%,d", analysis.summary().expectedStems())
                + " planned stems · "
                + countLabel(analysis.bedConflicts().size(), "bed overlap", "bed overlaps")
                + " · "
                + countLabel(analysis.harvestGaps().size(), "harvest gap", "harvest gaps");
        if (analysis.unmatchedHarvestCount() > 0) {
            message += " · " + analysis.unmatchedHarvestCount() + " harvest "
                    + (analysis.unmatchedHarvestCount() == 1 ? "needs" : "need") + " matching";
        }
        return message;
    }

    private static FarmUi.Tone analysisTone(SeasonPlanService.SeasonAnalysis analysis) {
        boolean behind = analysis.performance().stream()
                .anyMatch(item -> item.pace() == SeasonPlanService.PlanPace.BEHIND);
        return analysis.entries().isEmpty() ? FarmUi.Tone.NEUTRAL
                : (!analysis.bedConflicts().isEmpty() || !analysis.harvestGaps().isEmpty()
                || analysis.unmatchedHarvestCount() > 0 || behind)
                ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS;
    }

    private static String countLabel(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private static int percent(double actual, long expected) {
        return expected <= 0 ? 0 : (int) Math.round(actual * 100d / expected);
    }

    private static double rounded(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private boolean canStartChange() {
        return canWrite && (host == null || host.canMutateData())
                && !loading && !saving && !refreshFailed;
    }

    private PlantingDecision beginDecision(SeasonPlanEntry target) {
        decisionRevision++;
        return new PlantingDecision(decisionRevision, selectionRevision,
                selectedSeason(), EntryState.of(target));
    }

    private boolean currentDecision(PlantingDecision decision) {
        return canStartChange() && decision.revision() == decisionRevision
                && decision.seasonYear() == selectedSeason()
                && (decision.target() == null
                || decision.selectionRevision() == selectionRevision
                && decision.target().equals(EntryState.of(selectedEntry())));
    }

    private PendingPlanting newDraft(Long id, PlantingForm form) {
        return new PendingPlanting(++nextDraftKey, id,
                id == null ? null : EntryState.of(selectedEntry()), form);
    }

    private PendingPlanting draftFor(Long id) {
        return retainedDrafts.values().stream()
                .filter(draft -> Objects.equals(id, draft.targetId()))
                .filter(draft -> id != null || draft.form().seasonYear == selectedSeason())
                .findFirst().orElse(null);
    }

    private void addPlanting() {
        if (!canStartChange()) {
            return;
        }
        PendingPlanting draft = draftFor(null);
        if (draft == null) {
            draft = newDraft(null, new PlantingForm((SeasonPlanEntry) null, selectedSeason(), null));
        }
        openDraft(draft, null, retainedDrafts.containsKey(draft.key())
                ? "Retry planting block" : "Add planting block");
    }

    /** Opens a farm-owned draft whose dates, bed space, and yield require review. */
    public boolean preparePlantingFromReference(PnwPlantProfile reference) {
        Objects.requireNonNull(reference, "reference");
        getUIComponent();
        if (!canStartChange()) {
            return false;
        }
        String guidance = "Started from PNW Plant Guide · source retained · "
                + "confirm every farm-specific value";
        PendingPlanting draft = newDraft(null,
                new PlantingForm(reference, selectedSeason(), guidance));
        return openDraft(draft, null, "Plan “" + reference.commonName() + "”");
    }

    private void editSelected() {
        if (!canStartChange()) {
            return;
        }
        SeasonPlanEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        PendingPlanting draft = draftFor(selected.getId());
        if (draft == null) {
            draft = newDraft(selected.getId(), new PlantingForm(selected, selectedSeason(), null));
        }
        openDraft(draft, selected, retainedDrafts.containsKey(draft.key())
                ? "Retry planting block" : "Edit planting block");
    }

    private void planSuccession() {
        if (!canStartChange()) {
            return;
        }
        SeasonPlanEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        SeasonPlanEntry entry = seasonPlanService.draftSuccession(selected, SUCCESSION_SHIFT_DAYS);
        PendingPlanting draft = newDraft(null, new PlantingForm(entry, selectedSeason(),
                "Copied from the selected block · all dates moved 14 days · review before saving"));
        openDraft(draft, selected, "Plan next succession");
    }

    private boolean openDraft(PendingPlanting original, SeasonPlanEntry target, String title) {
        PlantingDecision decision = beginDecision(target);
        if (original.targetId() != null && !Objects.equals(original.baseline(), EntryState.of(target))
                && !approveChangedPlanting(original, target, decision)) {
            return false;
        }
        boolean retainedBefore = retainedDrafts.get(original.key()) == original;
        PlantingForm form = new PlantingForm(original.form());
        PendingPlanting draft = new PendingPlanting(original.key(), original.targetId(),
                original.targetId() == null ? null : EntryState.of(target), form);
        while (true) {
            int choice = dialogs.showConfirm(panel, form.panel(), title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (!currentDecision(decision)) {
                // A nested review may have replaced or saved this exact draft already.
                if (retainedDrafts.get(original.key()) == original
                        || !retainedBefore && !retainedDrafts.containsKey(original.key())) {
                    retainDraft(draft);
                }
                setStatus("The season plan or access changed while this form was open. "
                        + "No change was submitted from this form. Review retained drafts before saving.",
                        FarmUi.Tone.WARNING);
                return false;
            }
            if (choice != JOptionPane.OK_OPTION) {
                if (retainedDrafts.containsKey(original.key())) {
                    retainDraft(draft);
                }
                return false;
            }
            try {
                SeasonPlanEntry entry = form.read();
                startSave(draft, entry);
                return true;
            } catch (IllegalArgumentException exception) {
                dialogs.showMessage(panel, exception.getMessage(),
                        "Check the planting block", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private boolean approveChangedPlanting(PendingPlanting draft, SeasonPlanEntry target,
                                           PlantingDecision decision) {
        JTextArea comparison = new JTextArea("CURRENT SAVED BLOCK\n"
                + new PlantingForm(target, selectedSeason(), null).details()
                + "\nRETAINED DRAFT\n" + draft.form().details(), 20, 62);
        comparison.setEditable(false);
        comparison.setLineWrap(true);
        comparison.setWrapStyleWord(true);
        comparison.setCaretPosition(0);
        comparison.getAccessibleContext().setAccessibleName("Current planting and retained draft details");
        JPanel message = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        message.add(FarmUi.wrappingStatusBadge(
                "This planting changed since the draft was prepared. Compare all values before "
                        + "using the draft to replace the current block. You will review the form next.",
                FarmUi.Tone.WARNING), BorderLayout.NORTH);
        message.add(new JScrollPane(comparison), BorderLayout.CENTER);
        Object[] options = {"Keep draft", "Use draft values"};
        int answer = dialogs.showOption(panel, message, "Review changed planting block",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return answer == 1 && currentDecision(decision);
    }

    private void showDraftDetails(PendingPlanting draft) {
        PlantingForm copy = new PlantingForm(draft.form());
        for (JTextField field : copy.fields()) {
            field.setEditable(false);
        }
        copy.notes.setEditable(false);
        copy.status.setEnabled(false);
        dialogs.showMessage(panel, copy.panel(), "Retained planting draft", JOptionPane.INFORMATION_MESSAGE);
    }

    private void retainDraft(PendingPlanting draft) {
        retainedDrafts.put(draft.key(), draft);
        decisionRevision++;
        updateActions();
    }

    private void reviewDrafts() {
        if (loading || saving || retainedDrafts.isEmpty()) {
            return;
        }
        long revision = ++decisionRevision;
        JComboBox<PendingPlanting> choices = new JComboBox<>(
                retainedDrafts.values().toArray(PendingPlanting[]::new));
        choices.setName("seasonPlanner.draftChoice");
        choices.getAccessibleContext().setAccessibleName("Retained planting draft");
        JPanel message = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        message.add(FarmUi.wrappingStatusBadge(
                "Drafts are kept only for this session. Select one to review or discard.",
                FarmUi.Tone.INFO), BorderLayout.NORTH);
        message.add(choices, BorderLayout.CENTER);
        Object[] options = {"Keep working", "Review draft", "Discard draft"};
        int answer = dialogs.showOption(panel, message, "Retained planting drafts",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        PendingPlanting selected = (PendingPlanting) choices.getSelectedItem();
        if (revision != decisionRevision || loading || saving || selected == null
                || retainedDrafts.get(selected.key()) != selected) {
            return;
        }
        if (answer == 2) {
            discardDraft(selected);
        } else if (answer == 1 && canStartChange()) {
            SeasonPlanEntry target = selected.targetId() == null ? null : selectedEntry();
            if (selected.targetId() != null
                    && (target == null || !selected.targetId().equals(target.getId()))) {
                if (!restoreSelection(selected.targetId())) {
                    showDraftDetails(selected);
                    setStatus("This draft belongs to planting #" + selected.targetId()
                            + " in season " + selected.form().seasonYear
                            + ". Select that season and refresh to review it, or discard the draft.",
                            FarmUi.Tone.WARNING);
                    return;
                }
                target = selectedEntry();
            }
            openDraft(selected, target, "Retry planting block");
        } else if (answer == 1) {
            showDraftDetails(selected);
            setStatus("Refresh the season plan and restore write access before saving a retained draft.",
                    FarmUi.Tone.WARNING);
        }
    }

    private void discardDraft(PendingPlanting draft) {
        long revision = ++decisionRevision;
        Object[] options = {"Keep draft", "Discard draft"};
        int answer = dialogs.showOption(panel, FarmUi.confirmationPanel(
                        "Discard this retained planting draft?",
                        draft + " will be lost. Saved planting blocks are unchanged.", FarmUi.Tone.WARNING),
                "Discard planting draft?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (answer == 1 && revision == decisionRevision && !loading && !saving
                && retainedDrafts.get(draft.key()) == draft) {
            retainedDrafts.remove(draft.key());
            decisionRevision++;
            setStatus("Planting draft discarded. Saved planting blocks are unchanged.", FarmUi.Tone.NEUTRAL);
            updateActions();
        }
    }

    private void startSave(PendingPlanting draft, SeasonPlanEntry entry) {
        if (!canStartChange()) {
            return;
        }
        retainDraft(draft);
        saving = true;
        savingDraft = true;
        updateActions();
        setStatus(draft.targetId() == null ? "Adding planting block…" : "Updating planting block…",
                FarmUi.Tone.INFO);
        new SwingWorker<SeasonPlanEntry, Void>() {
            @Override protected SeasonPlanEntry doInBackground() {
                return draft.targetId() == null ? seasonPlanService.create(entry)
                        : seasonPlanService.update(draft.targetId(), entry);
            }

            @Override protected void done() {
                boolean acknowledged = false;
                try {
                    SeasonPlanEntry saved = get();
                    acknowledged = true;
                    retainedDrafts.remove(draft.key());
                    requestedSelectionId = saved == null ? draft.targetId() : saved.getId();
                    updateSeasonChoices(List.of(), entry.getSeasonYear());
                    acknowledgeChange("Planting block saved: " + cropLabel(entry) + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSaveFailure(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showSaveFailure(exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    finishWrite(acknowledged);
                }
            }
        }.execute();
    }

    private void acknowledgeChange(String message) {
        acknowledgedChange = message;
        // Acknowledged writes are never retried because a view or host callback failed.
        setStatus(message + " Refreshing the season plan…", FarmUi.Tone.SUCCESS);
        if (host != null) {
            try {
                host.refreshAll();
            } catch (RuntimeException exception) {
                hostRefreshWarning = "Other views could not refresh; use Refresh all to reload them.";
            }
        }
    }

    private void finishWrite(boolean acknowledged) {
        saving = false;
        savingDraft = false;
        decisionRevision++;
        if (acknowledged || refreshQueued) {
            startRefresh();
        }
        updateActions();
    }

    private void deleteSelected() {
        if (!canStartChange()) {
            return;
        }
        SeasonPlanEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        PlantingDecision decision = beginDecision(selected);
        int answer = dialogs.showConfirm(panel,
                FarmUi.confirmationPanel("Remove this planting block?",
                        cropLabel(selected) + " in " + selected.getBedOrField()
                                + " will be removed from the season plan. Harvest records are unchanged."
                                + (draftFor(selected.getId()) == null ? ""
                                : " Retained edit drafts for this planting block will also be discarded."),
                        FarmUi.Tone.WARNING), "Remove planting block",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || !currentDecision(decision)) {
            return;
        }
        Long id = selected.getId();
        saving = true;
        decisionRevision++;
        updateActions();
        setStatus("Removing planting block…", FarmUi.Tone.INFO);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                seasonPlanService.delete(id);
                return null;
            }

            @Override protected void done() {
                boolean acknowledged = false;
                try {
                    get();
                    acknowledged = true;
                    retainedDrafts.values().removeIf(draft -> Objects.equals(id, draft.targetId()));
                    acknowledgeChange("Planting block removed; harvest records were unchanged.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Removing the planting block", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showFailure("Removing the planting block",
                            exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    finishWrite(acknowledged);
                }
            }
        }.execute();
    }

    private SeasonPlanEntry selectedEntry() {
        if (table == null || table.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        return modelRow >= 0 && modelRow < lastView.size() ? lastView.get(modelRow) : null;
    }

    private boolean restoreSelection(Long id) {
        if (id == null) {
            return false;
        }
        for (int modelRow = 0; modelRow < lastView.size(); modelRow++) {
            if (id.equals(lastView.get(modelRow).getId())) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return true;
                }
            }
        }
        return false;
    }

    private void showFailure(String action, Throwable failure) {
        String message = FarmUi.actionableError(action, failure);
        setStatus(message, FarmUi.Tone.DANGER);
    }

    private void showRefreshFailure(Throwable failure) {
        refreshFailed = true;
        String outcome = acknowledgedChange == null ? "" : acknowledgedChange + " Do not repeat that change. ";
        setStatus(outcome + FarmUi.actionableError("Loading the season plan", failure)
                + " Displayed rows may be out of date. Choose Refresh before changing planting blocks.",
                FarmUi.Tone.WARNING);
    }

    private void setStatus(String message, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(statusBadge, message, tone);
        statusBadge.setToolTipText(message);
        statusBadge.getAccessibleContext().setAccessibleDescription(message);
        if (host != null) {
            try {
                host.setStatus(message);
            } catch (RuntimeException exception) {
                // The planner's visible status still reports the actual write/read result.
                hostRefreshWarning = "The application status could not update; the season result is shown here.";
            }
        }
    }

    private void showSaveFailure(Throwable failure) {
        String message = FarmUi.actionableError("Saving the planting block", failure)
                + " The complete draft was kept; choose Review drafts to review and retry.";
        setStatus(message, FarmUi.Tone.DANGER);
        FarmUi.updateStatusBadge(statusBadge,
                "Planting block not saved · draft kept", FarmUi.Tone.DANGER);
        statusBadge.setToolTipText(message);
        statusBadge.getAccessibleContext().setAccessibleDescription(message);
    }

    private void updateActions() {
        boolean ready = !loading && !saving;
        boolean writable = canStartChange();
        if (addButton != null) {
            String label = savingDraft ? "Saving planting…"
                    : draftFor(null) != null
                    ? "Retry planting" : "Add planting";
            addButton.setText(label);
            addButton.setToolTipText(draftFor(null) != null
                    ? "Review the planting draft retained after the failed save."
                    : "Add a planting block to the selected season.");
            addButton.getAccessibleContext().setAccessibleDescription(
                    addButton.getToolTipText());
        }
        String disabledTip = refreshFailed ? "Refresh the season plan before changing planting blocks."
                : loading || saving ? "Wait for the current planting operation to finish."
                : GuiPermissions.VIEWER_READONLY_TIP;
        GuiPermissions.setWritable(writable, disabledTip, addButton);
        GuiPermissions.setWritable(writable && selectedEntry() != null, disabledTip,
                editButton, successionButton, deleteButton);
        if (reviewDraftsButton != null) {
            reviewDraftsButton.setText("Review drafts (" + retainedDrafts.size() + ")");
            reviewDraftsButton.setEnabled(ready && !retainedDrafts.isEmpty());
            reviewDraftsButton.setToolTipText("Review or discard planting drafts kept for this session.");
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(ready);
        }
        if (seasonPicker != null) {
            seasonPicker.setEnabled(ready);
        }
        if (copyAvailabilityButton != null) {
            copyAvailabilityButton.setEnabled(ready && !lastAvailability.isEmpty());
        }
    }

    private static String cropLabel(SeasonPlanEntry entry) {
        return entry.getVariety().isBlank() ? entry.getCropName()
                : entry.getCropName() + " · " + entry.getVariety();
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.US, "%,.0f", value)
                : String.format(Locale.US, "%,.1f", value);
    }

    private record SeasonSnapshot(SeasonPlanService.SeasonAnalysis analysis,
                                  List<Integer> seasons) {
    }

    private record PendingPlanting(long key, Long targetId, EntryState baseline, PlantingForm form) {
        @Override public String toString() {
            return form.seasonYear + " · " + (targetId == null ? "New planting" : "Planting #" + targetId)
                    + " · " + form.crop.getText() + " · " + form.bed.getText();
        }
    }

    private record PlantingDecision(long revision, long selectionRevision,
                                    int seasonYear, EntryState target) {
    }

    private record EntryState(Long id, int year, String crop, String variety, String bed,
                              LocalDate sow, LocalDate transplant, LocalDate harvestStart,
                              LocalDate harvestEnd, double bedFeet, int expectedStems,
                              SeasonPlanStatus status, String notes, java.time.Instant updatedAt) {
        private static EntryState of(SeasonPlanEntry entry) {
            return entry == null ? null : new EntryState(entry.getId(), entry.getSeasonYear(),
                    entry.getCropName(), entry.getVariety(), entry.getBedOrField(), entry.getSowDate(),
                    entry.getTransplantDate(), entry.getHarvestStart(), entry.getHarvestEnd(),
                    entry.getBedFeet(), entry.getExpectedStems(), entry.getStatus(), entry.getNotes(),
                    entry.getUpdatedAt());
        }
    }

    private record AvailabilityKey(LocalDate weekOf, String crop, String variety) {
    }

    private record AvailabilityTotal(double stems, int blocks) {
    }

    private record AvailabilityRow(LocalDate weekOf, String crop, String variety,
                                   int plannedStems, int blocks, String signal) {
    }

    private static final class PlantingForm {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JTextField crop = field("Crop", 22);
        private final JTextField variety = field("Variety", 22);
        private final JTextField bed = field("Bed or field", 22);
        private final JTextField sow = field("Sow date", 12);
        private final JTextField transplant = field("Transplant date", 12);
        private final JTextField harvestStart = field("Harvest start", 12);
        private final JTextField harvestEnd = field("Harvest end", 12);
        private final JTextField bedFeet = field("Bed length in feet", 10);
        private final JTextField expectedStems = field("Expected stems", 10);
        private final JComboBox<SeasonPlanStatus> status =
                new JComboBox<>(SeasonPlanStatus.values());
        private final JTextArea notes = new JTextArea(3, 28);
        private final int seasonYear;
        private final String guidance;

        private PlantingForm(SeasonPlanEntry existing, int selectedYear, String guidance) {
            seasonYear = existing == null ? selectedYear : existing.getSeasonYear();
            this.guidance = guidance;
            build(existing, null, guidance);
        }

        private PlantingForm(PnwPlantProfile reference, int selectedYear, String guidance) {
            seasonYear = selectedYear;
            this.guidance = guidance;
            build(null, reference, guidance);
        }

        private PlantingForm(PlantingForm source) {
            this((SeasonPlanEntry) null, source.seasonYear, source.guidance);
            List<JTextField> originals = source.fields();
            List<JTextField> copies = fields();
            for (int index = 0; index < copies.size(); index++) {
                copies.get(index).setText(originals.get(index).getText());
            }
            status.setSelectedItem(source.status.getSelectedItem());
            notes.setText(source.notes.getText());
        }

        private List<JTextField> fields() {
            return List.of(crop, variety, bed, sow, transplant,
                    harvestStart, harvestEnd, bedFeet, expectedStems);
        }

        private String details() {
            StringBuilder text = new StringBuilder("Season: " + seasonYear + "\n");
            for (JTextField field : fields()) {
                text.append(field.getAccessibleContext().getAccessibleName())
                        .append(": ").append(field.getText()).append('\n');
            }
            return text + "Planting status: " + status.getSelectedItem()
                    + "\nPlanning notes: " + notes.getText() + "\n";
        }

        private void build(SeasonPlanEntry existing, PnwPlantProfile reference,
                           String guidance) {
            panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            notes.setLineWrap(true);
            notes.setWrapStyleWord(true);
            notes.getAccessibleContext().setAccessibleName("Planning notes");
            status.getAccessibleContext().setAccessibleName("Planting status");

            LocalDate defaultSow = LocalDate.now().getYear() == seasonYear
                    ? LocalDate.now() : LocalDate.of(seasonYear, Month.MARCH, 1);
            if (existing == null) {
                sow.setText(defaultSow.toString());
                transplant.setText(defaultSow.plusWeeks(6).toString());
                harvestStart.setText(defaultSow.plusWeeks(14).toString());
                harvestEnd.setText(defaultSow.plusWeeks(20).toString());
                bedFeet.setText("50");
                expectedStems.setText("250");
                if (reference != null) {
                    crop.setText(reference.commonName());
                    sow.setText("");
                    transplant.setText("");
                    harvestStart.setText("");
                    harvestEnd.setText("");
                    bedFeet.setText("");
                    expectedStems.setText("");
                    notes.setText(referenceNotes(reference));
                }
            } else {
                crop.setText(existing.getCropName());
                variety.setText(existing.getVariety());
                bed.setText(existing.getBedOrField());
                sow.setText(existing.getSowDate().toString());
                transplant.setText(existing.getTransplantDate() == null ? ""
                        : existing.getTransplantDate().toString());
                harvestStart.setText(existing.getHarvestStart().toString());
                harvestEnd.setText(existing.getHarvestEnd().toString());
                bedFeet.setText(Double.toString(existing.getBedFeet()));
                expectedStems.setText(Integer.toString(existing.getExpectedStems()));
                status.setSelectedItem(existing.getStatus());
                notes.setText(existing.getNotes());
            }

            int row = 0;
            if (guidance != null) {
                JLabel guidanceBadge = FarmUi.statusBadge(guidance, FarmUi.Tone.INFO);
                guidanceBadge.setName("seasonPlanner.successionGuidance");
                GridBagConstraints guidanceConstraints = constraints(0, row++, 4);
                guidanceConstraints.insets = new Insets(0, 0, 10, 0);
                guidanceConstraints.fill = GridBagConstraints.HORIZONTAL;
                panel.add(guidanceBadge, guidanceConstraints);
            }
            addHeading("Crop and placement", row++);
            addRow("Crop *", crop, "Variety", variety, row++);
            addRow("Bed / field *", bed, "Status", status, row++);
            addHeading("Season timing", row++);
            addRow("Sow *", sow, "Transplant", transplant, row++);
            addRow("Harvest start *", harvestStart, "Harvest end *", harvestEnd, row++);
            JLabel dateHelp = FarmUi.mutedLabel(
                    "Use YYYY-MM-DD. Leave transplant blank for direct-sown crops.");
            GridBagConstraints help = constraints(0, row++, 4);
            help.insets = new Insets(0, 0, 10, 0);
            panel.add(dateHelp, help);
            addHeading("Capacity and yield", row++);
            addRow("Bed length (ft) *", bedFeet, "Expected stems *", expectedStems, row++);
            addLabel("Notes", row++);
            GridBagConstraints noteConstraints = constraints(0, row, 4);
            noteConstraints.fill = GridBagConstraints.BOTH;
            noteConstraints.weighty = 1;
            panel.add(new JScrollPane(notes), noteConstraints);
            panel.setPreferredSize(new Dimension(690, guidance == null ? 500 : 530));
        }

        private static String referenceNotes(PnwPlantProfile reference) {
            return "Sourced planning starter — verify against this farm before saving.\n"
                    + "Scientific name: " + reference.scientificName() + "\n"
                    + "Type / origin: " + reference.type() + " · "
                    + reference.originLabel() + "\n"
                    + "Light / moisture: " + reference.light() + " · "
                    + reference.moisture() + "\n"
                    + "Regional season: " + reference.season() + "\n"
                    + "Field note: " + reference.fieldNotes() + "\n"
                    + "Source: " + reference.source().label() + "\n"
                    + reference.source().url() + "\n"
                    + "Farm override / cultivar notes: ";
        }

        private void addHeading(String text, int row) {
            JLabel heading = FarmUi.eyebrowLabel(text, FarmUi.Tone.SUCCESS);
            GridBagConstraints constraints = constraints(0, row, 4);
            constraints.insets = new Insets(row == 0 ? 0 : 12, 0, 6, 0);
            panel.add(heading, constraints);
        }

        private void addRow(String firstLabel, JComponent first, String secondLabel,
                            JComponent second, int row) {
            GridBagConstraints labelOne = constraints(0, row, 1);
            labelOne.insets = new Insets(4, 0, 4, 6);
            panel.add(new JLabel(firstLabel), labelOne);
            GridBagConstraints fieldOne = constraints(1, row, 1);
            fieldOne.fill = GridBagConstraints.HORIZONTAL;
            fieldOne.weightx = 1;
            fieldOne.insets = new Insets(4, 0, 4, 14);
            panel.add(first, fieldOne);
            GridBagConstraints labelTwo = constraints(2, row, 1);
            labelTwo.insets = new Insets(4, 0, 4, 6);
            panel.add(new JLabel(secondLabel), labelTwo);
            GridBagConstraints fieldTwo = constraints(3, row, 1);
            fieldTwo.fill = GridBagConstraints.HORIZONTAL;
            fieldTwo.weightx = 1;
            fieldTwo.insets = new Insets(4, 0, 4, 0);
            panel.add(second, fieldTwo);
        }

        private void addLabel(String text, int row) {
            GridBagConstraints constraints = constraints(0, row, 4);
            constraints.insets = new Insets(7, 0, 4, 0);
            panel.add(new JLabel(text), constraints);
        }

        private static GridBagConstraints constraints(int x, int y, int width) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = x;
            constraints.gridy = y;
            constraints.gridwidth = width;
            constraints.anchor = GridBagConstraints.WEST;
            return constraints;
        }

        private static JTextField field(String accessibleName, int columns) {
            JTextField field = new JTextField(columns);
            field.getAccessibleContext().setAccessibleName(accessibleName);
            return field;
        }

        private JPanel panel() {
            return panel;
        }

        private SeasonPlanEntry read() {
            return new SeasonPlanEntry(
                    seasonYear, crop.getText(), variety.getText(), bed.getText(),
                    date(sow, "Sow date"), optionalDate(transplant, "Transplant date"),
                    date(harvestStart, "Harvest start"), date(harvestEnd, "Harvest end"),
                    decimal(bedFeet, "Bed length"), wholeNumber(expectedStems, "Expected stems"),
                    (SeasonPlanStatus) status.getSelectedItem(), notes.getText());
        }

        private static LocalDate date(JTextField field, String label) {
            if (field.getText().isBlank()) {
                throw new IllegalArgumentException(label + " is required.");
            }
            try {
                return LocalDate.parse(field.getText().trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(label + " must use YYYY-MM-DD.");
            }
        }

        private static LocalDate optionalDate(JTextField field, String label) {
            return field.getText().isBlank() ? null : date(field, label);
        }

        private static double decimal(JTextField field, String label) {
            try {
                return Double.parseDouble(field.getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be a number.");
            }
        }

        private static int wholeNumber(JTextField field, String label) {
            try {
                return Integer.parseInt(field.getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be a whole number.");
            }
        }
    }

    private static final class WeeklyPaceChart extends JComponent {
        private static final int LEFT_INSET = 62;
        private static final int RIGHT_INSET = 24;
        private static final int TOP_INSET = 30;
        private static final int BOTTOM_INSET = 46;
        private static final int CHART_HEIGHT = 278;
        private int seasonYear = LocalDate.now().getYear();
        private List<SeasonPlanService.WeeklyProduction> series = List.of();

        private WeeklyPaceChart() {
            setOpaque(true);
            setBackground(FarmUi.surfaceColor());
            setPreferredSize(new Dimension(920, CHART_HEIGHT));
            setToolTipText("Green bars show matched actual stems; the gold line shows planned weekly stems.");
            getAccessibleContext().setAccessibleName("Weekly planned versus actual harvest chart");
            getAccessibleContext().setAccessibleDescription(
                    "No weekly production is available until a planting block is added.");
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleWeeklyPaceChart();
            }
            return accessibleContext;
        }

        protected class AccessibleWeeklyPaceChart extends AccessibleJComponent {
            private static final long serialVersionUID = 1L;
        }

        private void setSeries(int seasonYear,
                               List<SeasonPlanService.WeeklyProduction> series) {
            this.seasonYear = seasonYear;
            this.series = series == null ? List.of() : List.copyOf(series);
            double planned = this.series.stream()
                    .mapToDouble(SeasonPlanService.WeeklyProduction::plannedStems).sum();
            double actual = this.series.stream()
                    .mapToDouble(SeasonPlanService.WeeklyProduction::actualStems).sum();
            getAccessibleContext().setAccessibleDescription(this.series.isEmpty()
                    ? "No weekly production is available until a planting block is added."
                    : this.series.size() + " weeks of planned and actual production; "
                            + Math.round(planned) + " planned stems and "
                            + Math.round(actual) + " actual stems.");
            int width = Math.max(920, LEFT_INSET + RIGHT_INSET
                    + Math.max(1, this.series.size()) * 38);
            setPreferredSize(new Dimension(width, CHART_HEIGHT));
            revalidate();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(getWidth(), getPreferredSize().width);
                if (series.isEmpty()) {
                    paintEmptyState(copy, width);
                    return;
                }
                int plotWidth = Math.max(200, width - LEFT_INSET - RIGHT_INSET);
                int plotHeight = Math.max(100, getHeight() - TOP_INSET - BOTTOM_INSET);
                double maxValue = series.stream()
                        .mapToDouble(item -> Math.max(item.plannedStems(), item.actualStems()))
                        .max().orElse(1d);
                double scaleMax = niceCeiling(maxValue);
                paintCurrentWeek(copy, plotWidth, plotHeight);
                paintGrid(copy, plotWidth, plotHeight, scaleMax);
                paintActualBars(copy, plotWidth, plotHeight, scaleMax);
                paintPlannedLine(copy, plotWidth, plotHeight, scaleMax);
                paintWeekLabels(copy, plotWidth, plotHeight);
            } finally {
                copy.dispose();
            }
        }

        private void paintGrid(Graphics2D graphics, int plotWidth, int plotHeight,
                               double scaleMax) {
            graphics.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            for (int step = 0; step <= 4; step++) {
                double value = scaleMax * step / 4d;
                int y = TOP_INSET + plotHeight - (int) Math.round(plotHeight * step / 4d);
                graphics.setColor(FarmUi.borderColor());
                graphics.drawLine(LEFT_INSET, y, LEFT_INSET + plotWidth, y);
                graphics.setColor(FarmUi.mutedColor());
                String label = formatNumber(value);
                graphics.drawString(label,
                        LEFT_INSET - graphics.getFontMetrics().stringWidth(label) - 9, y + 4);
            }
            graphics.setColor(FarmUi.mutedColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 9f));
            graphics.drawString("STEMS", 12, TOP_INSET - 9);
        }

        private void paintActualBars(Graphics2D graphics, int plotWidth, int plotHeight,
                                     double scaleMax) {
            double spacing = plotWidth / (double) series.size();
            int barWidth = Math.max(8, Math.min(20, (int) Math.round(spacing * 0.48)));
            Color fill = FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS);
            Color shadow = FarmUi.toneBackgroundColor(FarmUi.Tone.SUCCESS);
            for (int index = 0; index < series.size(); index++) {
                SeasonPlanService.WeeklyProduction item = series.get(index);
                int center = xFor(index, plotWidth);
                int height = (int) Math.round(item.actualStems() / scaleMax * plotHeight);
                if (height <= 0) {
                    continue;
                }
                int y = TOP_INSET + plotHeight - height;
                graphics.setColor(shadow);
                graphics.fillRoundRect(center - barWidth / 2 - 2, y - 2,
                        barWidth + 4, height + 2, 7, 7);
                graphics.setColor(fill);
                graphics.fillRoundRect(center - barWidth / 2, y,
                        barWidth, height, 6, 6);
            }
        }

        private void paintPlannedLine(Graphics2D graphics, int plotWidth, int plotHeight,
                                      double scaleMax) {
            Path2D.Double path = new Path2D.Double();
            for (int index = 0; index < series.size(); index++) {
                SeasonPlanService.WeeklyProduction item = series.get(index);
                int x = xFor(index, plotWidth);
                int y = TOP_INSET + plotHeight
                        - (int) Math.round(item.plannedStems() / scaleMax * plotHeight);
                if (index == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            Color planned = FarmUi.toneForegroundColor(FarmUi.Tone.WARNING);
            graphics.setColor(planned);
            graphics.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics.draw(path);
            for (int index = 0; index < series.size(); index++) {
                SeasonPlanService.WeeklyProduction item = series.get(index);
                int x = xFor(index, plotWidth);
                int y = TOP_INSET + plotHeight
                        - (int) Math.round(item.plannedStems() / scaleMax * plotHeight);
                graphics.fillOval(x - 3, y - 3, 6, 6);
            }
        }

        private void paintWeekLabels(Graphics2D graphics, int plotWidth, int plotHeight) {
            graphics.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            int baseline = TOP_INSET + plotHeight + 22;
            int every = Math.max(1, (int) Math.ceil(series.size() / 10d));
            for (int index = 0; index < series.size(); index++) {
                LocalDate week = series.get(index).weekStart();
                boolean monthChange = index == 0
                        || week.getMonth() != series.get(index - 1).weekStart().getMonth();
                if (index % every != 0 && !monthChange && index != series.size() - 1) {
                    continue;
                }
                int x = xFor(index, plotWidth);
                String label = SHORT_DATE.format(week);
                graphics.setColor(monthChange ? FarmUi.accentColor() : FarmUi.mutedColor());
                graphics.setFont(getFont().deriveFont(
                        monthChange ? Font.BOLD : Font.PLAIN, 10f));
                graphics.drawString(label,
                        x - graphics.getFontMetrics().stringWidth(label) / 2, baseline);
            }
        }

        private void paintCurrentWeek(Graphics2D graphics, int plotWidth, int plotHeight) {
            LocalDate today = LocalDate.now();
            if (today.getYear() != seasonYear) {
                return;
            }
            LocalDate currentWeek = today.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (int index = 0; index < series.size(); index++) {
                if (!series.get(index).weekStart().equals(currentWeek)) {
                    continue;
                }
                double spacing = plotWidth / (double) series.size();
                int x = xFor(index, plotWidth);
                Color color = FarmUi.toneBackgroundColor(FarmUi.Tone.INFO);
                graphics.setColor(new Color(color.getRed(), color.getGreen(),
                        color.getBlue(), 110));
                graphics.fillRoundRect((int) Math.round(x - spacing / 2), TOP_INSET,
                        Math.max(8, (int) Math.round(spacing)), plotHeight, 8, 8);
                graphics.setColor(FarmUi.toneForegroundColor(FarmUi.Tone.INFO));
                graphics.setFont(getFont().deriveFont(Font.BOLD, 9f));
                graphics.drawString("THIS WEEK", Math.max(LEFT_INSET, x - 27), 18);
                return;
            }
        }

        private void paintEmptyState(Graphics2D graphics, int width) {
            graphics.setColor(FarmUi.accentColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 17f));
            String title = "Your weekly harvest pace will appear here";
            int titleWidth = graphics.getFontMetrics().stringWidth(title);
            graphics.drawString(title, Math.max(18, (width - titleWidth) / 2), 126);
            graphics.setColor(FarmUi.mutedColor());
            graphics.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            String help = "Add expected stems and a harvest window to create the plan line.";
            int helpWidth = graphics.getFontMetrics().stringWidth(help);
            graphics.drawString(help, Math.max(18, (width - helpWidth) / 2), 151);
        }

        private int xFor(int index, int plotWidth) {
            double spacing = plotWidth / (double) series.size();
            return LEFT_INSET + (int) Math.round((index + 0.5) * spacing);
        }

        private static double niceCeiling(double value) {
            if (value <= 0) {
                return 100;
            }
            double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
            double normalized = value / magnitude;
            double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2
                    : normalized <= 5 ? 5 : 10;
            return nice * magnitude;
        }
    }

    private static final class SeasonTimeline extends JComponent {
        private static final int LABEL_WIDTH = 230;
        private static final int HEADER_HEIGHT = 54;
        private static final int ROW_HEIGHT = 48;
        private static final int RIGHT_INSET = 18;
        private int seasonYear = LocalDate.now().getYear();
        private List<SeasonPlanEntry> entries = List.of();
        private List<SeasonPlanService.PlanPerformance> performance = List.of();
        private Set<SeasonPlanEntry> conflictEntries = Set.of();

        private SeasonTimeline() {
            setOpaque(true);
            setBackground(FarmUi.surfaceColor());
            setPreferredSize(new Dimension(920, 260));
            getAccessibleContext().setAccessibleName("Visual season timeline");
            getAccessibleContext().setAccessibleDescription(
                    "Planting rows show sowing, growing, and expected harvest periods by month. "
                            + "Solid green inside an outlined harvest bar shows actual progress.");
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleSeasonTimeline();
            }
            return accessibleContext;
        }

        protected class AccessibleSeasonTimeline extends AccessibleJComponent {
            private static final long serialVersionUID = 1L;
        }

        private void setSeason(int seasonYear, List<SeasonPlanEntry> entries,
                               List<SeasonPlanService.PlanPerformance> performance,
                               Set<SeasonPlanEntry> conflictEntries) {
            this.seasonYear = seasonYear;
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            this.performance = performance == null ? List.of() : List.copyOf(performance);
            this.conflictEntries = conflictEntries == null ? Set.of() : conflictEntries;
            long actual = this.performance.stream()
                    .mapToLong(item -> Math.round(item.actualStems())).sum();
            getAccessibleContext().setAccessibleDescription(
                    "Planting rows show sowing, growing, and expected harvest periods by month; "
                            + actual + " actual stems are allocated to this plan.");
            int height = Math.max(235, HEADER_HEIGHT + this.entries.size() * ROW_HEIGHT + 16);
            setPreferredSize(new Dimension(920, height));
            revalidate();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(getWidth(), getPreferredSize().width);
                int timelineWidth = Math.max(300, width - LABEL_WIDTH - RIGHT_INSET);
                paintMonths(copy, timelineWidth);
                if (entries.isEmpty()) {
                    paintEmptyState(copy, width);
                    return;
                }
                for (int index = 0; index < entries.size(); index++) {
                    paintRow(copy, entries.get(index), index, timelineWidth, width);
                }
                paintToday(copy, timelineWidth);
            } finally {
                copy.dispose();
            }
        }

        private void paintMonths(Graphics2D graphics, int timelineWidth) {
            LocalDate yearStart = LocalDate.of(seasonYear, 1, 1);
            int days = yearStart.lengthOfYear();
            graphics.setColor(FarmUi.mutedColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 10f));
            for (Month month : Month.values()) {
                LocalDate first = LocalDate.of(seasonYear, month, 1);
                int x = xFor(first, yearStart, days, timelineWidth);
                int nextX = month == Month.DECEMBER
                        ? LABEL_WIDTH + timelineWidth
                        : xFor(first.plusMonths(1), yearStart, days, timelineWidth);
                if (month.getValue() % 2 == 0) {
                    graphics.setColor(new Color(FarmUi.toneBackgroundColor(
                            FarmUi.Tone.NEUTRAL).getRGB() & 0x00ffffff | 0x1f000000, true));
                    graphics.fillRect(x, HEADER_HEIGHT - 18, nextX - x,
                            Math.max(1, getHeight() - HEADER_HEIGHT + 18));
                }
                graphics.setColor(FarmUi.mutedColor());
                graphics.drawString(month.name().substring(0, 3), x + 4, 22);
                graphics.setColor(FarmUi.borderColor());
                graphics.drawLine(x, 31, x, getHeight() - 8);
            }
            graphics.setColor(FarmUi.accentColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 12f));
            graphics.drawString(Integer.toString(seasonYear), 14, 23);
            graphics.setColor(FarmUi.borderColor());
            graphics.drawLine(0, HEADER_HEIGHT - 1, getWidth(), HEADER_HEIGHT - 1);
        }

        private void paintRow(Graphics2D graphics, SeasonPlanEntry entry, int index,
                              int timelineWidth, int width) {
            int y = HEADER_HEIGHT + index * ROW_HEIGHT;
            if (index % 2 == 0) {
                Color stripe = FarmUi.toneBackgroundColor(FarmUi.Tone.NEUTRAL);
                graphics.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 45));
                graphics.fillRect(0, y, width, ROW_HEIGHT);
            }
            graphics.setColor(FarmUi.accentColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 12f));
            if (conflictEntries.contains(entry)) {
                graphics.setColor(FarmUi.toneForegroundColor(FarmUi.Tone.DANGER));
            }
            String bedLabel = conflictEntries.contains(entry)
                    ? entry.getBedOrField() + " · OVERLAP" : entry.getBedOrField();
            graphics.drawString(trim(bedLabel, 24), 14, y + 19);
            graphics.setColor(FarmUi.mutedColor());
            graphics.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            graphics.drawString(trim(cropLabel(entry), 30), 14, y + 36);

            LocalDate yearStart = LocalDate.of(seasonYear, 1, 1);
            int days = yearStart.lengthOfYear();
            LocalDate transplant = entry.getTransplantDate() == null
                    ? entry.getSowDate() : entry.getTransplantDate();
            drawSegment(graphics, entry.getSowDate(), transplant, y,
                    FarmUi.Tone.WARNING, yearStart, days, timelineWidth, 5);
            drawSegment(graphics, transplant, entry.getHarvestStart(), y,
                    FarmUi.Tone.INFO, yearStart, days, timelineWidth, 5);
            drawHarvestProgress(graphics, entry, performanceFor(entry), y,
                    yearStart, days, timelineWidth);
            graphics.setColor(FarmUi.borderColor());
            graphics.drawLine(0, y + ROW_HEIGHT - 1, width, y + ROW_HEIGHT - 1);
        }

        private void drawSegment(Graphics2D graphics, LocalDate from, LocalDate to, int rowY,
                                 FarmUi.Tone tone, LocalDate yearStart, int days,
                                 int timelineWidth, int height) {
            LocalDate clampedFrom = from.isBefore(yearStart) ? yearStart : from;
            LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
            LocalDate clampedTo = to.isAfter(yearEnd) ? yearEnd : to;
            if (clampedTo.isBefore(clampedFrom)) {
                return;
            }
            int x = xFor(clampedFrom, yearStart, days, timelineWidth);
            int end = xFor(clampedTo.plusDays(1), yearStart, days, timelineWidth);
            int y = rowY + (ROW_HEIGHT - height) / 2;
            graphics.setColor(FarmUi.toneForegroundColor(tone));
            graphics.fillRoundRect(x, y, Math.max(5, end - x), height, height, height);
        }

        private void drawHarvestProgress(Graphics2D graphics, SeasonPlanEntry entry,
                                         SeasonPlanService.PlanPerformance planPerformance,
                                         int rowY, LocalDate yearStart, int days,
                                         int timelineWidth) {
            LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
            LocalDate from = entry.getHarvestStart().isBefore(yearStart)
                    ? yearStart : entry.getHarvestStart();
            LocalDate to = entry.getHarvestEnd().isAfter(yearEnd)
                    ? yearEnd : entry.getHarvestEnd();
            if (to.isBefore(from)) {
                return;
            }
            int x = xFor(from, yearStart, days, timelineWidth);
            int end = xFor(to.plusDays(1), yearStart, days, timelineWidth);
            int width = Math.max(5, end - x);
            int height = 11;
            int y = rowY + (ROW_HEIGHT - height) / 2;
            graphics.setColor(FarmUi.toneBackgroundColor(FarmUi.Tone.SUCCESS));
            graphics.fillRoundRect(x, y, width, height, height, height);
            graphics.setColor(FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
            graphics.drawRoundRect(x, y, width, height, height, height);
            double completion = planPerformance == null ? 0
                    : Math.max(0, Math.min(100, planPerformance.completionPercent()));
            int completedWidth = (int) Math.round(width * completion / 100d);
            if (completedWidth > 0) {
                graphics.fillRoundRect(x, y, completedWidth, height, height, height);
            }
        }

        private SeasonPlanService.PlanPerformance performanceFor(SeasonPlanEntry entry) {
            return performance.stream().filter(item -> item.entry() == entry)
                    .findFirst().orElse(null);
        }

        private void paintToday(Graphics2D graphics, int timelineWidth) {
            LocalDate today = LocalDate.now();
            if (today.getYear() != seasonYear) {
                return;
            }
            LocalDate yearStart = LocalDate.of(seasonYear, 1, 1);
            int x = xFor(today, yearStart, yearStart.lengthOfYear(), timelineWidth);
            graphics.setColor(FarmUi.toneForegroundColor(FarmUi.Tone.DANGER));
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.drawLine(x, 32, x, getHeight() - 8);
            graphics.setFont(getFont().deriveFont(Font.BOLD, 9f));
            graphics.drawString("TODAY", Math.max(LABEL_WIDTH, x - 15), 43);
        }

        private void paintEmptyState(Graphics2D graphics, int width) {
            graphics.setColor(FarmUi.accentColor());
            graphics.setFont(getFont().deriveFont(Font.BOLD, 17f));
            String title = "Build your first planting block";
            int titleWidth = graphics.getFontMetrics().stringWidth(title);
            graphics.drawString(title, Math.max(18, (width - titleWidth) / 2), 150);
            graphics.setColor(FarmUi.mutedColor());
            graphics.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            String help = "Add a crop, bed, dates, bed feet, and expected stems to map the season.";
            int helpWidth = graphics.getFontMetrics().stringWidth(help);
            graphics.drawString(help, Math.max(18, (width - helpWidth) / 2), 174);
        }

        private static int xFor(LocalDate date, LocalDate yearStart, int days,
                                int timelineWidth) {
            long offset = ChronoUnit.DAYS.between(yearStart, date);
            double fraction = Math.max(0, Math.min(days, offset)) / (double) days;
            return LABEL_WIDTH + (int) Math.round(fraction * timelineWidth);
        }

        private static String trim(String value, int max) {
            if (value == null || value.length() <= max) {
                return value == null ? "" : value;
            }
            return value.substring(0, Math.max(1, max - 1)) + "…";
        }
    }
}

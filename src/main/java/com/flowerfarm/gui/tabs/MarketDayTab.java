package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.FulfillBatchResult;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.ProductNeed;
import com.flowerfarm.util.InventoryUnits;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Market morning packing list — pick sheet + per-customer slips from CRM pipeline.
 */
public class MarketDayTab implements FlowerFarmTab {

    private final MarketDayPackingService packingService;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;

    private JPanel panel;
    private JTextField dateField;
    private JSpinner windowSpinner;
    private JCheckBox draftBox;
    private JCheckBox fulfilledBox;
    private JTextArea planArea;
    private JLabel summaryLabel;
    private JLabel accessLabel;
    private JButton fulfillBtn;
    private JLabel fulfillmentStatusLabel;
    private JTextArea fulfillmentResultArea;
    private JScrollPane fulfillmentResultScroll;
    private JButton buildPlanBtn;
    private JButton todayBtn;
    private JButton nextSaturdayBtn;
    private JButton exportBtn;
    private JComboBox<String> exportType;
    private JLabel exportStatusLabel;
    private JPanel packingChecklist;
    private JLabel packingProgressLabel;
    private JLabel checklistRefreshLabel;
    private JPanel handoffBanner;
    private JLabel handoffLabel;
    private JButton returnFromMarketDayBtn;
    private JButton nextUnpackedBtn;
    private JButton clearPackedChecksBtn;
    private JLabel shortfallLabel;
    private JButton resolveShortfallsBtn;
    private final Map<Long, JCheckBox> packedOrders = new LinkedHashMap<>();
    private final Map<Long, String> packedOrderSignatures = new LinkedHashMap<>();
    private final Map<Long, PackingReadiness> packingReadinessByOrder = new LinkedHashMap<>();
    private java.util.List<String> shortfallProducts = java.util.List.of();
    private OrderIssueTarget correctionOrderIssue;
    private String packingSnapshot = "";
    private boolean canWrite = true;
    private boolean fulfilling;
    private boolean refreshingPlan;
    private boolean exporting;
    private boolean planRefreshQueued;
    private boolean planScopeDirty;
    private String planRefreshFailure;
    private int confirmedReady;
    private MarketDayPlan displayedPlan;
    private FulfillmentPresentation pendingFulfillmentPresentation;
    private boolean completionRefreshRequired;
    private int committedCompletionCount;
    private java.util.Set<Long> committedCompletionOrderIds = java.util.Set.of();
    private java.util.Set<Long> packedCheckClearApproval;
    private String handoffReturnTab;
    private String handoffReturnLabel;

    public MarketDayTab(MarketDayPackingService packingService, TabHost host) {
        this(packingService, host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public MarketDayTab(MarketDayPackingService packingService, TabHost host,
                        FarmDialogs dialogs) {
        this(packingService, host, dialogs, FarmFileChooser.swing());
    }

    public MarketDayTab(MarketDayPackingService packingService, TabHost host,
                        FarmDialogs dialogs, FarmFileChooser files) {
        this.packingService = packingService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    @Override
    public String getTabTitle() {
        return "Market Day";
    }

    @Override
    public String getDescription() {
        return "Packing list for market / wholesale load-out";
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
        startPlanRefresh();
    }

    @Override
    public void refreshData() {
        startPlanRefresh();
    }

    /** Returns a close warning when local packing evidence or a completion could be lost. */
    public String applicationExitWarning() {
        long checkedLoads = packedOrders.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .filter(entry -> !committedCompletionOrderIds.contains(entry.getKey()))
                .count();
        java.util.List<String> work = new java.util.ArrayList<>();
        if (checkedLoads > 0) {
            work.add(checkedLoads + " checked packed load" + (checkedLoads == 1 ? "" : "s"));
        }
        if (fulfilling) {
            work.add("a Market Day completion still running");
        }
        if (exporting) {
            work.add("a packing export still writing");
        }
        if (work.isEmpty()) {
            return null;
        }
        return "Market Day still has " + String.join(" and ", work)
                + ". Closing now can discard local packing checks or interrupt the running "
                + "completion or file write. Keep working until the handoff is finished, or "
                + "explicitly discard this packing progress.";
    }

    /** Show the retained packing work with a state-preserving route back. */
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
        String context = "Packing work opened from " + handoffReturnLabel;
        handoffLabel.setText(context);
        handoffLabel.setToolTipText(
                "The current market date, scope, packing list, and checked loads were preserved.");
        handoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromMarketDayBtn.setText("Return to " + handoffReturnLabel);
        returnFromMarketDayBtn.setToolTipText(
                "Return without rebuilding the current Dashboard or daily checklist.");
        returnFromMarketDayBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " from Market Day packing");
        handoffBanner.setVisible(true);
        if (host != null) {
            host.setStatus("Opened retained Market Day packing work from "
                    + handoffReturnLabel + ".");
        }
    }

    private void returnFromMarketDay() {
        if (host == null || handoffReturnTab == null) {
            return;
        }
        String returnTab = handoffReturnTab;
        String returnLabel = handoffReturnLabel;
        handoffReturnTab = null;
        handoffReturnLabel = null;
        handoffBanner.setVisible(false);
        host.selectTab(returnTab);
        host.setStatus("Returned to " + returnLabel + " from Market Day.");
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        for (Map.Entry<Long, JCheckBox> entry : packedOrders.entrySet()) {
            entry.getValue().setEnabled(orderReadyToPack(entry.getKey())
                    && canWrite && !fulfilling && !refreshingPlan && !planScopeDirty);
        }
        updateFulfillAction();
        FarmUi.updateStatusBadge(accessLabel,
                canWrite ? "Order completion enabled" : "View only — exports remain available",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    private void buildUI() {
        panel = FarmUi.page();
        summaryLabel = FarmUi.statusBadge("Packing list not built", FarmUi.Tone.NEUTRAL);
        summaryLabel.setName("marketDay.planStatus");
        summaryLabel.getAccessibleContext().setAccessibleName("Market-day plan refresh status");
        panel.add(FarmUi.pageHeader(
                "Market day packing",
                "Turn confirmed orders into a clear pick list and customer-ready load-out.",
                summaryLabel), BorderLayout.NORTH);

        dateField = new JTextField(LocalDate.now().toString(), 12);
        FarmUi.initialFocus(dateField);
        dateField.setToolTipText("Market / delivery date (YYYY-MM-DD)");
        dateField.getAccessibleContext().setAccessibleName("Market or delivery date");
        dateField.addActionListener(event -> startPlanRefresh());
        windowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
        windowSpinner.setToolTipText("1 = that day only; 7 = trailing week ending on market date");
        windowSpinner.getAccessibleContext().setAccessibleName("Packing window in days");
        draftBox = new JCheckBox("Include unconfirmed drafts");
        draftBox.setMnemonic(java.awt.event.KeyEvent.VK_U);
        draftBox.setToolTipText("Draft orders may still change and are excluded by default.");
        fulfilledBox = new JCheckBox("Include completed orders");
        fulfilledBox.setMnemonic(java.awt.event.KeyEvent.VK_O);
        fulfilledBox.setToolTipText("Completed orders are excluded from the active packing list by default.");
        buildPlanBtn = FarmUi.primaryButton("Build packing list");
        buildPlanBtn.setMnemonic(java.awt.event.KeyEvent.VK_B);
        buildPlanBtn.addActionListener(e -> startPlanRefresh());
        todayBtn = FarmUi.secondaryButton("Today");
        todayBtn.setMnemonic(java.awt.event.KeyEvent.VK_T);
        todayBtn.addActionListener(e -> {
            dateField.setText(LocalDate.now().toString());
            startPlanRefresh();
        });
        nextSaturdayBtn = FarmUi.secondaryButton("Next Saturday");
        nextSaturdayBtn.setMnemonic(java.awt.event.KeyEvent.VK_S);
        nextSaturdayBtn.setToolTipText(
                "Use the next Saturday, including today when today is Saturday.");
        nextSaturdayBtn.addActionListener(e -> {
            dateField.setText(LocalDate.now()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toString());
            startPlanRefresh();
        });
        exportType = new JComboBox<>(new String[]{
                "Packing PDF", "Spreadsheet CSV", "Plain text"
        });
        exportType.setName("marketDay.exportType");
        exportType.getAccessibleContext().setAccessibleName("Packing list export format");
        exportBtn = FarmUi.secondaryButton("Export file…");
        exportBtn.setMnemonic(java.awt.event.KeyEvent.VK_E);
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> runExport((String) exportType.getSelectedItem()));
        exportStatusLabel = FarmUi.statusBadge("Export ready", FarmUi.Tone.NEUTRAL);
        exportStatusLabel.setName("marketDay.exportStatus");
        exportStatusLabel.getAccessibleContext().setAccessibleName(
                "Market-day packing export status");
        fulfillBtn = FarmUi.dangerButton("Complete confirmed orders…");
        fulfillBtn.setMnemonic(java.awt.event.KeyEvent.VK_C);
        fulfillBtn.setName("marketDay.fulfillConfirmed");
        fulfillBtn.setToolTipText(
                "After loading or delivery: complete every confirmed order and deduct inventory.");
        fulfillBtn.addActionListener(e -> fulfillAllConfirmed());
        fulfillmentStatusLabel = FarmUi.statusBadge(
                "Completion locked · finish packing checks", FarmUi.Tone.NEUTRAL);
        fulfillmentStatusLabel.setName("marketDay.fulfillmentStatus");
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleName(
                "Market-day order completion status");
        JButton openCrm = FarmUi.secondaryButton("Review draft orders");
        openCrm.setMnemonic(java.awt.event.KeyEvent.VK_R);
        openCrm.setName("marketDay.reviewDraftOrders");
        openCrm.setToolTipText(
                "Open all draft orders in Customers & Orders so eligible market work can be confirmed.");
        openCrm.addActionListener(e -> {
            if (host != null) {
                host.showOrdersByStatus("DRAFT", "Market Day", "Market Day");
            }
        });
        resolveShortfallsBtn = FarmUi.secondaryButton("Resolve stock shortfalls");
        resolveShortfallsBtn.setMnemonic(java.awt.event.KeyEvent.VK_V);
        resolveShortfallsBtn.setName("marketDay.resolveShortfalls");
        resolveShortfallsBtn.setEnabled(false);
        resolveShortfallsBtn.setToolTipText(
                "Build a packing list to identify products that need a stock adjustment.");
        resolveShortfallsBtn.addActionListener(event -> {
            if (host != null) {
                Long unresolvedOrderId = firstUnspecifiedConfirmedOrderId(displayedPlan);
                Long emptyOrderId = firstEmptyConfirmedOrderId(displayedPlan);
                Long missingDeliveryContextId =
                        firstMissingDeliveryContextOrderId(displayedPlan);
                if (unresolvedOrderId != null) {
                    host.showOrder(unresolvedOrderId, "Market Day", "Market Day");
                } else if (emptyOrderId != null) {
                    host.showOrder(emptyOrderId, "Market Day", "Market Day");
                } else if (missingDeliveryContextId != null) {
                    host.showOrder(missingDeliveryContextId, "Market Day", "Market Day");
                } else if (correctionOrderIssue != null) {
                    host.showOrderLineCorrection(
                            correctionOrderIssue.orderId(), correctionOrderIssue.lineId(),
                            correctionOrderIssue.issue().productName(),
                            "Market Day", "Market Day");
                } else {
                    host.showInventoryProducts(
                            shortfallProducts, "Market Day", "Market Day");
                }
            }
        });
        shortfallLabel = FarmUi.statusBadge("Stock check pending", FarmUi.Tone.NEUTRAL);
        shortfallLabel.setName("marketDay.shortfallSummary");
        shortfallLabel.getAccessibleContext().setAccessibleName(
                "Market-day stock shortfall summary");

        JLabel dateLabel = new JLabel("Market date:");
        dateLabel.setLabelFor(dateField);
        dateLabel.setDisplayedMnemonic('D');
        JLabel windowLabel = new JLabel("Window days:");
        windowLabel.setLabelFor(windowSpinner);
        windowLabel.setDisplayedMnemonic('W');
        JPanel dateScope = FarmUi.toolbar(
                dateLabel, dateField, windowLabel, windowSpinner, todayBtn, nextSaturdayBtn);
        JPanel scopeOptions = FarmUi.toolbar(draftBox, fulfilledBox, buildPlanBtn);
        JPanel exports = FarmUi.toolbar(
                new JLabel("Export"), exportType, exportBtn, exportStatusLabel, openCrm);
        JPanel inventoryHandoff = FarmUi.toolbar(
                shortfallLabel, resolveShortfallsBtn,
                FarmUi.formHint("Resolve stock gaps before checking customer loads as packed."));
        accessLabel = FarmUi.statusBadge("Order completion enabled", FarmUi.Tone.SUCCESS);
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        handoffLabel = FarmUi.statusBadge("Market Day handoff", FarmUi.Tone.INFO);
        handoffLabel.getAccessibleContext().setAccessibleName("Market Day handoff context");
        returnFromMarketDayBtn = FarmUi.secondaryButton("Return to previous workflow");
        returnFromMarketDayBtn.setMnemonic(java.awt.event.KeyEvent.VK_L);
        returnFromMarketDayBtn.setName("marketDay.returnFromHandoff");
        returnFromMarketDayBtn.addActionListener(event -> returnFromMarketDay());
        handoffBanner = FarmUi.toolbar(handoffLabel, returnFromMarketDayBtn);
        handoffBanner.setName("marketDay.handoffBanner");
        handoffBanner.setVisible(false);
        controls.add(handoffBanner);
        controls.add(dateScope);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(scopeOptions);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(exports);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(inventoryHandoff);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(FarmUi.toolbar(accessLabel, fulfillBtn, fulfillmentStatusLabel,
                FarmUi.formHint(
                        "Exports do not change orders. Complete only after packing or delivery.")));
        fulfillmentResultArea = FarmUi.displayArea(5, 68);
        fulfillmentResultArea.setName("marketDay.fulfillmentResultDetails");
        fulfillmentResultArea.setBackground(FarmUi.surfaceColor());
        fulfillmentResultArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        fulfillmentResultArea.getAccessibleContext().setAccessibleName(
                "Persistent market-day completion outcomes and retry instructions");
        fulfillmentResultScroll = new JScrollPane(fulfillmentResultArea);
        fulfillmentResultScroll.setName("marketDay.fulfillmentResultHandoff");
        fulfillmentResultScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        fulfillmentResultScroll.setPreferredSize(new Dimension(680, 112));
        fulfillmentResultScroll.setMinimumSize(new Dimension(320, 90));
        fulfillmentResultScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        fulfillmentResultScroll.getVerticalScrollBar().setUnitIncrement(16);
        fulfillmentResultScroll.getAccessibleContext().setAccessibleName(
                "Scrollable market-day completion result handoff");
        fulfillmentResultScroll.setVisible(false);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(fulfillmentResultScroll);

        planArea = new JTextArea(22, 72);
        planArea.setEditable(false);
        planArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        planArea.setLineWrap(false);
        planArea.getAccessibleContext().setAccessibleName("Market day packing list");
        packingChecklist = new JPanel();
        packingChecklist.setName("marketDay.packingChecklist");
        packingChecklist.setOpaque(false);
        packingChecklist.setLayout(new BoxLayout(packingChecklist, BoxLayout.Y_AXIS));
        packingChecklist.getAccessibleContext().setAccessibleName(
                "Confirmed customer load-out checklist");
        packingProgressLabel = FarmUi.statusBadge("0 orders ready", FarmUi.Tone.NEUTRAL);
        packingProgressLabel.setName("marketDay.packingProgress");
        packingProgressLabel.getAccessibleContext().setAccessibleName("Load-out packing progress");
        checklistRefreshLabel = FarmUi.statusBadge(
                "Packed checks are kept when order details are unchanged", FarmUi.Tone.NEUTRAL);
        checklistRefreshLabel.setName("marketDay.checklistRefresh");
        checklistRefreshLabel.getAccessibleContext().setAccessibleName(
                "Packing checklist refresh status");
        nextUnpackedBtn = FarmUi.secondaryButton("Next unpacked");
        nextUnpackedBtn.setMnemonic(java.awt.event.KeyEvent.VK_N);
        nextUnpackedBtn.setEnabled(false);
        nextUnpackedBtn.setToolTipText("Move to the next customer that still needs packing.");
        nextUnpackedBtn.addActionListener(event -> focusNextUnpacked());
        clearPackedChecksBtn = FarmUi.secondaryButton("Clear checks");
        clearPackedChecksBtn.setName("marketDay.clearPackedChecks");
        clearPackedChecksBtn.setMnemonic(java.awt.event.KeyEvent.VK_K);
        clearPackedChecksBtn.setEnabled(false);
        clearPackedChecksBtn.setToolTipText(
                "Review before discarding unfinished packed-load checks.");
        clearPackedChecksBtn.getAccessibleContext().setAccessibleName(
                "Clear unfinished packed-load checks");
        clearPackedChecksBtn.addActionListener(event -> clearPackedChecks());
        JScrollPane checklistScroll = new JScrollPane(packingChecklist);
        checklistScroll.setBorder(null);
        checklistScroll.setPreferredSize(new Dimension(285, 240));
        JSplitPane execution = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(planArea), checklistScroll);
        execution.setBorder(null);
        execution.setResizeWeight(0.72);
        execution.setContinuousLayout(true);
        execution.setOneTouchExpandable(true);
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Packing scope",
                "Choose the delivery window and which order stages belong on the list.",
                controls), BorderLayout.NORTH);
        content.add(FarmUi.section(
                "Pick list and customer slips",
                "Review shortfalls, then check each confirmed customer as their load is packed.",
                execution), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        installPackingScopeValidation();
        updateFulfillAction();
    }

    private void installPackingScopeValidation() {
        dateField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { packingScopeChanged(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
        windowSpinner.addChangeListener(event -> packingScopeChanged());
        draftBox.addItemListener(event -> packingScopeChanged());
        fulfilledBox.addItemListener(event -> packingScopeChanged());
    }

    private void packingScopeChanged() {
        boolean validDate = validateMarketDate(false);
        if (!validDate) {
            SwingUtilities.invokeLater(() -> {
                if (!validateMarketDate(false)) {
                    FarmUi.markFieldError(dateField,
                            "Use YYYY-MM-DD, such as " + LocalDate.now() + ".");
                }
            });
        }
        if (displayedPlan != null) {
            planScopeDirty = true;
            String message = validDate
                    ? "Packing scope changed · rebuild required"
                    : "Market date invalid · correct and rebuild";
            String guidance = validDate
                    ? "The visible list and packed checks belong to the previous date or scope. "
                            + "Build the packing list before packing, exporting, or completing orders."
                    : "Use YYYY-MM-DD. The previous list is retained for reference, but packing, "
                            + "export, and completion remain locked until a valid scope is rebuilt.";
            showPlanRefreshState(message, guidance, FarmUi.Tone.WARNING);
        }
        if (!refreshingPlan) {
            setPlanRefreshControls(true);
        }
    }

    private boolean validateMarketDate(boolean showError) {
        try {
            LocalDate.parse(dateField.getText().trim());
            FarmUi.clearFieldError(dateField);
            return true;
        } catch (RuntimeException exception) {
            if (showError) {
                FarmUi.markFieldError(dateField,
                        "Use YYYY-MM-DD, such as " + LocalDate.now() + ".");
                FarmUi.revealAndFocus(dateField);
            }
            return false;
        }
    }

    private void runExport(String type) {
        if (exporting) {
            return;
        }
        try {
            MarketDayPlan plan = snapshotPlan(requireDisplayedPlan());
            ExportKind kind = ExportKind.fromLabel(type);
            var selected = files.chooseSave(panel, "Save market-day packing export",
                    new File(kind.defaultName(plan.marketDate())));
            if (selected.isEmpty()) {
                showExportState("Export cancelled · ready",
                        "No file was written; the packing plan and checked loads were kept.",
                        FarmUi.Tone.NEUTRAL);
                if (host != null) {
                    host.setStatus("Market-day export cancelled. No file was changed.");
                }
                return;
            }
            File destination = selected.get();
            if (approveReplacement(destination)) {
                startMarketDayExport(type, destination, plan);
            } else {
                showExportState("Export cancelled · existing file kept",
                        "The selected file was not replaced; the packing plan and checks were kept.",
                        FarmUi.Tone.NEUTRAL);
                if (host != null) {
                    host.setStatus("Market-day export cancelled. Existing file was kept.");
                }
            }
        } catch (RuntimeException exception) {
            failExport(exception);
        }
    }

    private void updateFulfillAction() {
        updatePackingProgress();
        if (fulfillBtn != null) {
            boolean allPacked = allConfirmedOrdersPacked();
            boolean stockBlocked = hasPackingIssues(displayedPlan);
            GuiPermissions.setWritable(
                    canWrite && allPacked && !stockBlocked && !planScopeDirty
                            && !completionRefreshRequired
                            && planRefreshFailure == null
                            && !fulfilling && !refreshingPlan, fulfillBtn);
            if (fulfilling) {
                return;
            }
            fulfillBtn.setToolTipText(completionRefreshRequired
                    ? completionRefreshRecovery()
                    : planRefreshFailure != null
                    ? "The previous packing plan is still shown. Rebuild it successfully before "
                            + "completing orders or deducting inventory."
                    : planScopeDirty
                    ? "Build the packing list again for the changed date or scope before completion."
                    : stockBlocked
                    ? ("stock gap".equals(packingIssueNoun(displayedPlan))
                            ? "Resolve every stock gap in Inventory, then rebuild this packing list "
                                    + "before completing orders."
                            : "Resolve every packing issue in Orders or Inventory, then rebuild "
                                    + "this packing list before completing orders.")
                    : allPacked
                    ? "Complete " + confirmedReady
                    + " checked order(s) and deduct inventory after load-out."
                    : confirmedReady > 0
                    ? "Check every confirmed customer in the load-out checklist first."
                    : "Build a list containing confirmed orders before completing the load-out.");
            if (fulfillmentStatusLabel != null) {
                long packed = packedOrders.values().stream()
                        .filter(JCheckBox::isSelected).count();
                String message;
                FarmUi.Tone tone;
                if (completionRefreshRequired) {
                    message = committedCompletionCount
                            + " completed once · packing refresh required";
                    tone = FarmUi.Tone.WARNING;
                } else if (planRefreshFailure != null) {
                    message = "Refresh failed · previous plan shown · completion locked";
                    tone = FarmUi.Tone.DANGER;
                } else if (planScopeDirty) {
                    message = "Scope changed · rebuild before completion";
                    tone = FarmUi.Tone.WARNING;
                } else if (confirmedReady == 0) {
                    message = "No confirmed orders to complete";
                    tone = FarmUi.Tone.NEUTRAL;
                } else if (stockBlocked) {
                    int gaps = completionPackingIssueCount(displayedPlan);
                    String issue = packingIssueNoun(displayedPlan);
                    message = packed + " of " + confirmedReady + " checked · "
                            + gaps + " " + issue + (gaps == 1 ? "" : "s")
                            + " · completion locked";
                    tone = FarmUi.Tone.DANGER;
                } else if (allPacked) {
                    message = confirmedReady + " checked · ready to complete";
                    tone = FarmUi.Tone.SUCCESS;
                } else {
                    message = packed + " of " + confirmedReady
                            + " checked · completion locked";
                    tone = FarmUi.Tone.WARNING;
                }
                FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
                fulfillmentStatusLabel.setToolTipText(fulfillBtn.getToolTipText());
                fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                        message + ". " + fulfillBtn.getToolTipText());
            }
        }
    }

    private void updatePackingProgress() {
        if (packingProgressLabel == null || nextUnpackedBtn == null) {
            return;
        }
        updatePackedCheckClearAction();
        long packed = packedOrders.values().stream().filter(JCheckBox::isSelected).count();
        if (confirmedReady == 0) {
            FarmUi.updateStatusBadge(
                    packingProgressLabel, "No confirmed orders", FarmUi.Tone.NEUTRAL);
            nextUnpackedBtn.setEnabled(false);
            return;
        }
        if (planScopeDirty) {
            FarmUi.updateStatusBadge(packingProgressLabel,
                    "Scope changed · rebuild to resume packing", FarmUi.Tone.WARNING);
            nextUnpackedBtn.setEnabled(false);
            return;
        }
        long blocked = packingReadinessByOrder.values().stream()
                .filter(readiness -> !readiness.ready()).count();
        long readyRemaining = packedOrders.entrySet().stream()
                .filter(entry -> orderReadyToPack(entry.getKey()))
                .filter(entry -> !entry.getValue().isSelected())
                .count();
        if (blocked > 0) {
            FarmUi.updateStatusBadge(packingProgressLabel,
                    packed + " packed · " + readyRemaining + " ready remaining · "
                            + blocked + " blocked",
                    readyRemaining == 0 ? FarmUi.Tone.WARNING : FarmUi.Tone.DANGER);
            nextUnpackedBtn.setEnabled(!fulfilling && !refreshingPlan
                    && packedOrders.values().stream().anyMatch(
                            checkBox -> checkBox.isEnabled() && !checkBox.isSelected()));
            return;
        }
        boolean done = packed == confirmedReady;
        FarmUi.updateStatusBadge(packingProgressLabel,
                packed + " of " + confirmedReady + " packed · "
                        + (confirmedReady - packed) + " remaining",
                done ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        nextUnpackedBtn.setEnabled(!done && !fulfilling && !refreshingPlan);
    }

    private void focusNextUnpacked() {
        packedOrders.values().stream()
                .filter(checkBox -> checkBox.isEnabled() && !checkBox.isSelected())
                .findFirst()
                .ifPresent(checkBox -> SwingUtilities.invokeLater(() -> {
                    checkBox.requestFocusInWindow();
                    Rectangle target = SwingUtilities.convertRectangle(
                            checkBox.getParent(), checkBox.getBounds(), packingChecklist);
                    packingChecklist.scrollRectToVisible(target);
                }));
    }

    private java.util.Set<Long> currentDiscardablePackedOrderIds() {
        return packedOrders.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .filter(orderId -> !committedCompletionOrderIds.contains(orderId))
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new));
    }

    private void updatePackedCheckClearAction() {
        if (clearPackedChecksBtn == null) {
            return;
        }
        java.util.Set<Long> checked = currentDiscardablePackedOrderIds();
        if (packedCheckClearApproval != null
                && !packedCheckClearApproval.equals(checked)) {
            packedCheckClearApproval = null;
        }
        clearPackedChecksBtn.setEnabled(canWrite && !checked.isEmpty()
                && !fulfilling && !refreshingPlan);
        clearPackedChecksBtn.setText(
                packedCheckClearApproval == null ? "Clear checks" : "Discard checks");
        String guidance = packedCheckClearApproval == null
                ? "Review before discarding " + checked.size()
                        + " unfinished packed-load check"
                        + (checked.size() == 1 ? "" : "s") + "."
                : "Choose again only to discard these " + checked.size()
                        + " unchanged packed-load check"
                        + (checked.size() == 1 ? "" : "s")
                        + ". Completed orders are never cleared.";
        clearPackedChecksBtn.setToolTipText(guidance);
        clearPackedChecksBtn.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void clearPackedChecks() {
        java.util.Set<Long> checked = currentDiscardablePackedOrderIds();
        if (checked.isEmpty() || fulfilling || refreshingPlan) {
            return;
        }
        if (packedCheckClearApproval == null
                || !packedCheckClearApproval.equals(checked)) {
            packedCheckClearApproval = java.util.Set.copyOf(checked);
            updatePackedCheckClearAction();
            String guidance = "Packed checks were kept. Choose Discard checks again only to "
                    + "remove these " + checked.size() + " unchanged unfinished load"
                    + (checked.size() == 1 ? "" : "s") + ".";
            FarmUi.updateStatusBadge(checklistRefreshLabel,
                    "Packed checks kept · confirm discard", FarmUi.Tone.WARNING);
            checklistRefreshLabel.setToolTipText(guidance);
            checklistRefreshLabel.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(clearPackedChecksBtn);
            if (host != null) {
                host.setStatus(guidance);
            }
            return;
        }
        for (Long orderId : checked) {
            JCheckBox packed = packedOrders.get(orderId);
            if (packed != null) {
                packed.setSelected(false);
            }
        }
        packedCheckClearApproval = null;
        updateFulfillAction();
        String result = checked.size() + " unfinished packed-load check"
                + (checked.size() == 1 ? "" : "s")
                + " cleared. No order or inventory value changed.";
        FarmUi.updateStatusBadge(checklistRefreshLabel,
                "Packed checks cleared · orders unchanged", FarmUi.Tone.NEUTRAL);
        checklistRefreshLabel.setToolTipText(result);
        checklistRefreshLabel.getAccessibleContext().setAccessibleDescription(result);
        if (host != null) {
            host.setStatus(result);
        }
    }

    private boolean allConfirmedOrdersPacked() {
        return confirmedReady > 0
                && packedOrders.size() == confirmedReady
                && packedOrders.values().stream().allMatch(JCheckBox::isSelected);
    }

    private static boolean hasPackingIssues(MarketDayPlan plan) {
        return completionPackingIssueCount(plan) > 0;
    }

    private static int stockShortfallCount(MarketDayPlan plan) {
        if (plan == null) {
            return 0;
        }
        long detailedCount = plan.pickList().stream()
                .filter(MarketDayPackingService.ProductNeed::blockingIssue)
                .count();
        return Math.max(plan.shortfallSkuCount(), (int) detailedCount);
    }

    private static int emptyConfirmedOrderCount(MarketDayPlan plan) {
        if (plan == null) {
            return 0;
        }
        return (int) plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(customer -> customer.lines().isEmpty())
                .count();
    }

    private static int packingIssueCount(MarketDayPlan plan) {
        return stockShortfallCount(plan) + emptyConfirmedOrderCount(plan)
                + unspecifiedConfirmedOrderCount(plan)
                + missingDeliveryContextOrderCount(plan);
    }

    private static int confirmedStockShortfallCount(MarketDayPlan plan) {
        if (plan == null) {
            return 0;
        }
        long detailedCount = plan.confirmedPickList().stream()
                .filter(MarketDayPackingService.ProductNeed::blockingIssue)
                .count();
        return Math.max(plan.confirmedShortfallSkuCount(), (int) detailedCount);
    }

    private static int completionPackingIssueCount(MarketDayPlan plan) {
        return confirmedStockShortfallCount(plan) + emptyConfirmedOrderCount(plan)
                + unspecifiedConfirmedOrderCount(plan)
                + missingDeliveryContextOrderCount(plan);
    }

    private static int unspecifiedConfirmedOrderCount(MarketDayPlan plan) {
        if (plan == null) {
            return 0;
        }
        return (int) plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(customer -> "UNSPECIFIED".equalsIgnoreCase(customer.handoffType()))
                .count();
    }

    private static Long firstUnspecifiedConfirmedOrderId(MarketDayPlan plan) {
        if (plan == null) {
            return null;
        }
        return plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(customer -> "UNSPECIFIED".equalsIgnoreCase(customer.handoffType()))
                .map(MarketDayPackingService.CustomerPack::orderId)
                .findFirst()
                .orElse(null);
    }

    private static Long firstEmptyConfirmedOrderId(MarketDayPlan plan) {
        if (plan == null) {
            return null;
        }
        return plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(customer -> customer.lines().isEmpty())
                .map(MarketDayPackingService.CustomerPack::orderId)
                .findFirst()
                .orElse(null);
    }

    private static int missingDeliveryContextOrderCount(MarketDayPlan plan) {
        if (plan == null) {
            return 0;
        }
        return (int) plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(MarketDayPackingService::missingDeliveryContext)
                .count();
    }

    private static Long firstMissingDeliveryContextOrderId(MarketDayPlan plan) {
        if (plan == null) {
            return null;
        }
        return plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .filter(MarketDayPackingService::missingDeliveryContext)
                .map(MarketDayPackingService.CustomerPack::orderId)
                .findFirst().orElse(null);
    }

    private static OrderIssueTarget firstOrderIssueTarget(
            MarketDayPlan plan, java.util.List<ProductNeed> issues, boolean previewOnly) {
        if (plan == null || issues == null || issues.isEmpty()) {
            return null;
        }
        String targetStatus = previewOnly ? "DRAFT" : "CONFIRMED";
        for (ProductNeed issue : issues) {
            if (issue.quantityInvalid()) {
                OrderIssueTarget target = findOrderIssueTarget(
                        plan, issue, targetStatus, true);
                if (target != null) {
                    return target;
                }
            }
            if (issue.unitMismatch()) {
                OrderIssueTarget target = findOrderIssueTarget(
                        plan, issue, targetStatus, false);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private static OrderIssueTarget findOrderIssueTarget(
            MarketDayPlan plan, ProductNeed issue, String targetStatus,
            boolean quantityCorrection) {
        for (MarketDayPackingService.CustomerPack customer : plan.customers()) {
            if (!targetStatus.equalsIgnoreCase(customer.status())) {
                continue;
            }
            for (MarketDayPackingService.PackLine line : customer.lines()) {
                if (!normalizedProduct(line.productName()).equals(
                        normalizedProduct(issue.productName()))) {
                    continue;
                }
                if (quantityCorrection && Math.max(0, (int) Math.round(line.quantity())) < 1) {
                    return new OrderIssueTarget(
                            customer.orderId(), line.lineId(), issue, line.unit(), true);
                }
                if (!quantityCorrection && lineHasUnitMismatch(line, issue)) {
                    return new OrderIssueTarget(
                            customer.orderId(), line.lineId(), issue, line.unit(), false);
                }
            }
        }
        if (quantityCorrection) {
            return null;
        }
        for (MarketDayPackingService.CustomerPack customer : plan.customers()) {
            if (!targetStatus.equalsIgnoreCase(customer.status())) {
                continue;
            }
            for (MarketDayPackingService.PackLine line : customer.lines()) {
                if (normalizedProduct(line.productName()).equals(
                        normalizedProduct(issue.productName()))) {
                    return new OrderIssueTarget(
                            customer.orderId(), line.lineId(), issue, line.unit(), false);
                }
            }
        }
        return null;
    }

    private static boolean lineHasUnitMismatch(
            MarketDayPackingService.PackLine line, ProductNeed issue) {
        if (!issue.inventoryUnit().isBlank()) {
            return !InventoryUnits.equivalent(line.unit(), issue.inventoryUnit());
        }
        return !InventoryUnits.equivalent(line.unit(), issue.unit());
    }

    private boolean orderReadyToPack(Long orderId) {
        PackingReadiness readiness = packingReadinessByOrder.get(orderId);
        return readiness != null && readiness.ready();
    }

    private static String packingIssueNoun(MarketDayPlan plan) {
        boolean nonStockIssue = emptyConfirmedOrderCount(plan) > 0
                || unspecifiedConfirmedOrderCount(plan) > 0
                || missingDeliveryContextOrderCount(plan) > 0
                || plan != null && plan.confirmedPickList().stream().anyMatch(need ->
                        need.unitMismatch() || need.quantityInvalid());
        return nonStockIssue ? "packing issue" : "stock gap";
    }

    private MarketDayPlan requireDisplayedPlan() {
        if (displayedPlan == null) {
            throw new IllegalStateException(
                    "Build the packing list before exporting or completing orders.");
        }
        if (planScopeDirty) {
            throw new IllegalStateException(
                    "Packing scope changed. Build the packing list again before exporting or completing orders.");
        }
        return displayedPlan;
    }

    private LocalDate marketDate() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            FarmUi.clearFieldError(dateField);
            return date;
        } catch (RuntimeException exception) {
            FarmUi.markFieldError(dateField,
                    "Use YYYY-MM-DD, such as " + LocalDate.now() + ".");
            throw new IllegalArgumentException("Market date must use YYYY-MM-DD.", exception);
        }
    }

    private void refreshPlan() {
        startPlanRefresh();
    }

    void startPlanRefresh() {
        if (refreshingPlan) {
            planRefreshQueued = true;
            showPlanRefreshState("Refresh queued · current packing list finishing",
                    "One newer market-day snapshot will load with the latest scope.",
                    FarmUi.Tone.INFO);
            return;
        }
        PlanRequest request;
        try {
            FarmUi.clearFieldError(dateField);
            request = new PlanRequest(marketDate(),
                    ((Number) windowSpinner.getValue()).intValue(),
                    draftBox.isSelected(), fulfilledBox.isSelected());
        } catch (RuntimeException exception) {
            validateMarketDate(true);
            String guidance = "Use YYYY-MM-DD. The previous packing list and checks were kept; "
                    + "correct the date, then build the list again.";
            showPlanRefreshState("Market date invalid · correct and rebuild",
                    guidance, FarmUi.Tone.DANGER);
            if (host != null) {
                host.setStatus("Market-day list not built. Correct the highlighted market date.");
            }
            return;
        }
        refreshingPlan = true;
        planRefreshFailure = null;
        setPlanRefreshControls(false);
        showPlanRefreshState("Building packing list…",
                "Loading orders, pick quantities, and stock shortfalls in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Building market-day packing list…");
        }

        new SwingWorker<MarketDayPlan, Void>() {
            @Override
            protected MarketDayPlan doInBackground() {
                return packingService.buildPlan(request.date(), request.window(),
                        request.includeDrafts(), request.includeFulfilled());
            }

            @Override
            protected void done() {
                try {
                    MarketDayPlan plan = get();
                    if (!planRefreshQueued) {
                        applyPlanSnapshot(plan);
                    } else if (host != null) {
                        host.setStatus("A newer market-day refresh is queued; discarding the older snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!planRefreshQueued) {
                        failPlanRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!planRefreshQueued) {
                        failPlanRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!planRefreshQueued) {
                        failPlanRefresh(exception);
                    }
                } finally {
                    finishPlanRefresh();
                }
            }
        }.execute();
    }

    private void applyPlanSnapshot(MarketDayPlan plan) {
        displayedPlan = plan;
        planScopeDirty = false;
        confirmedReady = (int) plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .count();
        if (completionRefreshRequired
                && completedOrdersLeftConfirmedQueue(committedCompletionOrderIds, plan)) {
            completionRefreshRequired = false;
            committedCompletionCount = 0;
            committedCompletionOrderIds = java.util.Set.of();
        }
        rebuildPackingChecklist(plan);
        updateShortfallHandoff(plan);
        if (plan.orderCount() == 0) {
            planArea.setText("No orders are ready in this date window.\n\n"
                    + "Review draft orders to confirm eligible work, or create an order in "
                    + "Customers & Orders, then rebuild this list.\n"
                    + "You can also include unconfirmed drafts to preview upcoming work.");
        } else {
            planArea.setText(plan.plainText());
        }
        planArea.setCaretPosition(0);
        int packingIssues = packingIssueCount(plan);
        int completionIssues = completionPackingIssueCount(plan);
        boolean emptyPlan = plan.orderCount() == 0;
        String shortMsg = emptyPlan
                ? " · no work in scope"
                : completionIssues > 0
                ? " · ⚠ " + completionIssues + " confirmed packing issue(s) · completion locked"
                : packingIssues > 0
                ? " · ⚠ " + packingIssues + " Draft preview issue(s) · completion available"
                : " · stock OK";
        String planDescription = emptyPlan
                ? "No orders are ready in this date window. Review draft orders, create an order "
                        + "in Customers & Orders, or include unconfirmed drafts to preview upcoming work."
                : "Packing list, customer checklist, and inventory shortfalls are current.";
        showPlanRefreshState(String.format(
                        "Orders: %d  ·  Value $%,.2f  ·  %s%s",
                        plan.orderCount(), plan.pipelineValue(), plan.scope(), shortMsg),
                planDescription,
                emptyPlan ? FarmUi.Tone.NEUTRAL
                        : packingIssues > 0 ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS);
        if (host != null) {
            host.setStatus("Market day " + plan.marketDate() + ": "
                    + plan.orderCount() + " visible order(s), $"
                    + String.format("%,.2f", plan.pipelineValue()) + " active pipeline; "
                    + plan.confirmedOrderCount() + " confirmed, $"
                    + String.format("%,.2f", plan.confirmedValue()) + " ready to complete.");
        }
        if (pendingFulfillmentPresentation != null) {
            applyFulfillmentPresentation(pendingFulfillmentPresentation);
        }
    }

    private void failPlanRefresh(Throwable failure) {
        String detail = FarmUi.actionableError("Market-day packing list", failure);
        planRefreshFailure = detail
                + " Existing plan, packed checks, scope, and shortfall guidance were kept; retry when ready.";
        showPlanRefreshState("Packing list refresh failed · previous plan shown · retry",
                planRefreshFailure, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Market-day refresh failed. Existing packing work was kept; retry when ready.");
        }
    }

    private void finishPlanRefresh() {
        refreshingPlan = false;
        setPlanRefreshControls(true);
        if (planRefreshFailure != null) {
            showPlanRefreshState("Packing list refresh failed · previous plan shown · retry",
                    planRefreshFailure, FarmUi.Tone.DANGER);
        }
        if (pendingFulfillmentPresentation != null && !planRefreshQueued) {
            applyFulfillmentPresentation(pendingFulfillmentPresentation);
            pendingFulfillmentPresentation = null;
        }
        if (completionRefreshRequired && !planRefreshQueued) {
            applyCompletionRefreshLockGuidance();
        }
        if (planRefreshQueued) {
            planRefreshQueued = false;
            startPlanRefresh();
        }
    }

    private void setPlanRefreshControls(boolean enabled) {
        boolean validDate = validateMarketDate(false);
        dateField.setEnabled(enabled && !fulfilling);
        windowSpinner.setEnabled(enabled && !fulfilling);
        draftBox.setEnabled(enabled && !fulfilling);
        fulfilledBox.setEnabled(enabled && !fulfilling);
        todayBtn.setEnabled(enabled && !fulfilling);
        nextSaturdayBtn.setEnabled(enabled && !fulfilling);
        buildPlanBtn.setEnabled(enabled && !fulfilling && validDate);
        exportBtn.setEnabled(enabled && displayedPlan != null && !planScopeDirty
                && !fulfilling && !exporting);
        exportType.setEnabled(enabled && !fulfilling && !exporting);
        for (Map.Entry<Long, JCheckBox> entry : packedOrders.entrySet()) {
            entry.getValue().setEnabled(enabled && orderReadyToPack(entry.getKey())
                    && canWrite && !fulfilling && !planScopeDirty);
        }
        if (!enabled) {
            resolveShortfallsBtn.setEnabled(false);
        } else if (displayedPlan != null) {
            updateShortfallHandoff(displayedPlan);
            if (planScopeDirty) {
                resolveShortfallsBtn.setEnabled(false);
                resolveShortfallsBtn.setToolTipText(
                        "Rebuild the changed packing scope before resolving its current issues.");
            }
        }
        updateFulfillAction();
    }

    private void showPlanRefreshState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(summaryLabel, message, tone);
        summaryLabel.setToolTipText(description);
        summaryLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private void rebuildPackingChecklist(MarketDayPlan plan) {
        Map<Long, String> previouslyPacked = new LinkedHashMap<>();
        for (Map.Entry<Long, JCheckBox> entry : packedOrders.entrySet()) {
            if (entry.getValue().isSelected()) {
                previouslyPacked.put(entry.getKey(), packedOrderSignatures.get(entry.getKey()));
            }
        }
        packedOrders.clear();
        packedCheckClearApproval = null;
        packedOrderSignatures.clear();
        packingReadinessByOrder.clear();
        packingSnapshot = confirmedSnapshot(plan);
        packingChecklist.removeAll();
        JLabel heading = new JLabel("Confirmed load-out");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        packingProgressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel checklistActions = new JPanel(new GridLayout(
                1, 2, FarmUi.CONTROL_GAP, 0));
        checklistActions.setName("marketDay.checklistActions");
        checklistActions.setOpaque(false);
        checklistActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklistActions.add(nextUnpackedBtn);
        checklistActions.add(clearPackedChecksBtn);
        checklistActions.getAccessibleContext().setAccessibleName(
                "Market Day checklist navigation and recovery actions");
        JPanel checklistHeader = new JPanel();
        checklistHeader.setName("marketDay.checklistHeader");
        checklistHeader.setOpaque(false);
        checklistHeader.setLayout(new BoxLayout(checklistHeader, BoxLayout.Y_AXIS));
        checklistHeader.add(heading);
        checklistHeader.add(Box.createVerticalStrut(3));
        checklistHeader.add(packingProgressLabel);
        checklistHeader.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        checklistHeader.add(checklistActions);
        checklistHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklistHeader.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, checklistHeader.getPreferredSize().height));
        packingChecklist.add(checklistHeader);
        packingChecklist.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        Map<String, Long> handoffCounts = plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .collect(java.util.stream.Collectors.groupingBy(
                        customer -> handoffKey(customer.handoffType()),
                        LinkedHashMap::new, java.util.stream.Collectors.counting()));
        String currentHandoffGroup = null;
        int retainedChecks = 0;
        for (MarketDayPackingService.CustomerPack customer : plan.customers()) {
            if (!"CONFIRMED".equalsIgnoreCase(customer.status())) {
                continue;
            }
            String handoffGroup = handoffKey(customer.handoffType());
            if (!handoffGroup.equals(currentHandoffGroup)) {
                currentHandoffGroup = handoffGroup;
                long groupCount = handoffCounts.getOrDefault(handoffGroup, 0L);
                String groupName = "UNSPECIFIED".equals(handoffGroup)
                        ? "Needs handoff review" : friendlyHandoff(handoffGroup);
                JLabel groupHeading = FarmUi.statusBadge(
                        groupName + " · " + groupCount + " load"
                                + (groupCount == 1 ? "" : "s"),
                        "UNSPECIFIED".equals(handoffGroup)
                                ? FarmUi.Tone.WARNING : FarmUi.Tone.NEUTRAL);
                groupHeading.setName("marketDay.handoffGroup." + handoffGroup);
                groupHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
                groupHeading.getAccessibleContext().setAccessibleName(
                        groupName + " handoff group, " + groupCount + " customer load"
                                + (groupCount == 1 ? "" : "s"));
                packingChecklist.add(groupHeading);
                packingChecklist.add(Box.createVerticalStrut(3));
            }
            String label = "#" + customer.orderId() + "  " + customer.customerName()
                    + "  ·  " + orderDateLabel(customer)
                    + "  ·  " + customer.lines().size() + " product"
                    + (customer.lines().size() == 1 ? "" : "s")
                    + "  ·  $" + String.format("%,.2f", customer.orderTotal());
            JCheckBox packed = new JCheckBox(label);
            packed.setName("marketDay.packOrder." + customer.orderId());
            packed.setOpaque(false);
            PackingReadiness readiness = packingReadiness(customer, plan);
            packingReadinessByOrder.put(customer.orderId(), readiness);
            packed.setEnabled(readiness.ready() && canWrite && !fulfilling && !refreshingPlan);
            packed.setToolTipText(readiness.ready()
                    ? "Check only after this customer's products are packed for "
                            + orderDateLabel(customer) + "."
                    : readiness.guidance());
            packed.getAccessibleContext().setAccessibleName(
                    "Order " + customer.orderId() + " packed for " + customer.customerName()
                            + " on " + orderDateLabel(customer));
            packed.getAccessibleContext().setAccessibleDescription(
                    packed.getAccessibleContext().getAccessibleName() + ". "
                            + (readiness.ready()
                            ? "Stock, quantity, unit, product, and handoff checks are clear."
                            : readiness.guidance()));
            String signature = customerSignature(customer) + "|" + readiness.signature();
            boolean retainPacked = readiness.ready()
                    && signature.equals(previouslyPacked.get(customer.orderId()));
            packed.setSelected(retainPacked);
            if (retainPacked) {
                retainedChecks++;
            }
            packed.addActionListener(event -> {
                packedCheckClearApproval = null;
                updateFulfillAction();
            });
            packedOrders.put(customer.orderId(), packed);
            packedOrderSignatures.put(customer.orderId(), signature);
            JLabel details = FarmUi.mutedLabel(orderDetails(customer));
            details.setName("marketDay.packDetails." + customer.orderId());
            details.getAccessibleContext().setAccessibleName(
                    "Products for order " + customer.orderId());
            details.getAccessibleContext().setAccessibleDescription(
                    orderDetailsAccessible(customer));
            JLabel readinessLabel = FarmUi.statusBadge(
                    readiness.label(), readiness.ready()
                            ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
            readinessLabel.setName("marketDay.packReadiness." + customer.orderId());
            readinessLabel.getAccessibleContext().setAccessibleName(
                    "Packing readiness for order " + customer.orderId());
            readinessLabel.getAccessibleContext().setAccessibleDescription(
                    readiness.guidance());
            JButton reviewOrder = FarmUi.secondaryButton(
                    "Open order #" + customer.orderId());
            reviewOrder.setName("marketDay.openOrder." + customer.orderId());
            reviewOrder.setToolTipText(
                    "Open this exact order to review instructions, products, date, or customer details.");
            reviewOrder.getAccessibleContext().setAccessibleName(
                    "Open order " + customer.orderId() + " for " + customer.customerName());
            reviewOrder.addActionListener(event -> {
                if (host != null) {
                    host.showOrder(customer.orderId(), "Market Day", "Market Day");
                }
            });
            JPanel orderCard = new JPanel(new BorderLayout(0, 2));
            orderCard.setName("marketDay.packCard." + customer.orderId());
            orderCard.setOpaque(false);
            orderCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, FarmUi.borderColor()),
                    BorderFactory.createEmptyBorder(3, 0, 7, 0)));
            orderCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            orderCard.add(packed, BorderLayout.NORTH);
            orderCard.add(details, BorderLayout.CENTER);
            JPanel cardActions = new JPanel(new BorderLayout(0, 3));
            cardActions.setOpaque(false);
            cardActions.add(readinessLabel, BorderLayout.NORTH);
            cardActions.add(reviewOrder, BorderLayout.SOUTH);
            orderCard.add(cardActions, BorderLayout.SOUTH);
            int completeCardHeight = Math.max(86, orderCard.getPreferredSize().height);
            orderCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, completeCardHeight));
            packingChecklist.add(orderCard);
            packingChecklist.add(Box.createVerticalStrut(3));
        }
        if (packedOrders.isEmpty()) {
            packingChecklist.add(FarmUi.formHint(
                    "No confirmed orders in this scope. Drafts are previews only."));
        } else {
            packingChecklist.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
            packingChecklist.add(FarmUi.formHint(
                    "Completion unlocks when every confirmed customer is checked."));
        }
        int removedChecks = previouslyPacked.size() - retainedChecks;
        if (previouslyPacked.isEmpty()) {
            FarmUi.updateStatusBadge(checklistRefreshLabel,
                    "Packed checks are kept when order details are unchanged",
                    FarmUi.Tone.NEUTRAL);
        } else if (removedChecks == 0) {
            FarmUi.updateStatusBadge(checklistRefreshLabel,
                    retainedChecks + " packed check" + (retainedChecks == 1 ? "" : "s")
                            + " kept after refresh",
                    FarmUi.Tone.SUCCESS);
        } else {
            FarmUi.updateStatusBadge(checklistRefreshLabel,
                    retainedChecks + " kept · " + removedChecks
                            + " changed or out-of-scope check"
                            + (removedChecks == 1 ? "" : "s") + " reset",
                    FarmUi.Tone.WARNING);
        }
        checklistRefreshLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        packingChecklist.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        packingChecklist.add(checklistRefreshLabel);
        packingChecklist.revalidate();
        packingChecklist.repaint();
        updateFulfillAction();
    }

    private static String orderDetails(MarketDayPackingService.CustomerPack customer) {
        String products = customer.lines().stream()
                .map(line -> formatQuantity(line.quantity()) + " " + escapeHtml(line.unit())
                        + " " + escapeHtml(line.productName()))
                .collect(java.util.stream.Collectors.joining("<br>"));
        if (products.isBlank()) {
            products = "No product lines";
        }
        String notes = customer.notes() == null || customer.notes().isBlank()
                ? "" : "<br><i>Note: " + escapeHtml(customer.notes()) + "</i>";
        String contact = java.util.stream.Stream.of(
                        customer.contactName(), customer.phone(), customer.email())
                .filter(value -> value != null && !value.isBlank())
                .map(MarketDayTab::escapeHtml)
                .collect(java.util.stream.Collectors.joining(" · "));
        String contactLine = contact.isBlank()
                ? "" : "<br><b>Contact:</b> " + contact;
        String accountNote = customer.accountNotes() == null
                || customer.accountNotes().isBlank()
                ? "" : "<br><i>Account: " + escapeHtml(customer.accountNotes()) + "</i>";
        return "<html><div style='width:240px'><b>Products:</b><br>"
                + products + contactLine + accountNote + notes + "</div></html>";
    }

    private static String orderDetailsAccessible(
            MarketDayPackingService.CustomerPack customer) {
        String products = customer.lines().stream()
                .map(line -> formatQuantity(line.quantity()) + " " + line.unit()
                        + " " + line.productName())
                .collect(java.util.stream.Collectors.joining("; "));
        String contact = java.util.stream.Stream.of(
                        customer.contactName(), customer.phone(), customer.email())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
        String notes = customer.notes() == null || customer.notes().isBlank()
                ? "none saved" : customer.notes().trim();
        String accountNotes = customer.accountNotes() == null
                || customer.accountNotes().isBlank()
                ? "none saved" : customer.accountNotes().trim();
        return "Order " + customer.orderId() + " products: "
                + (products.isBlank() ? "none saved" : products)
                + ". Contact: " + (contact.isBlank() ? "none saved" : contact)
                + ". Account delivery note: " + accountNotes
                + ". Instructions: " + notes + ".";
    }

    private static PackingReadiness packingReadiness(
            MarketDayPackingService.CustomerPack customer, MarketDayPlan plan) {
        if (customer.lines().isEmpty()) {
            return new PackingReadiness(false, "Blocked · no saved products",
                    "Order #" + customer.orderId()
                            + " has no saved products. Open the order, add a product, then rebuild the packing list.",
                    "NO_PRODUCTS");
        }
        if ("UNSPECIFIED".equals(handoffKey(customer.handoffType()))) {
            return new PackingReadiness(false, "Blocked · handoff needs review",
                    "Order #" + customer.orderId()
                            + " needs Pickup, Delivery, or Market before its load can be marked packed.",
                    "UNSPECIFIED_HANDOFF");
        }
        if (MarketDayPackingService.missingDeliveryContext(customer)) {
            return new PackingReadiness(false, "Blocked · delivery context missing",
                    "Order #" + customer.orderId()
                            + " is a Delivery with no contact, account delivery note, or order "
                            + "instruction. Open the exact order or customer, add usable handoff "
                            + "context, then rebuild the packing list.",
                    "MISSING_DELIVERY_CONTEXT");
        }
        java.util.List<MarketDayPackingService.ProductNeed> issues =
                plan.confirmedPickList().stream()
                        .filter(MarketDayPackingService.ProductNeed::blockingIssue)
                        .filter(need -> customer.lines().stream().anyMatch(line ->
                                normalizedProduct(line.productName()).equals(
                                        normalizedProduct(need.productName()))))
                        .toList();
        if (!issues.isEmpty()) {
            String details = issues.stream()
                    .map(MarketDayTab::packingIssueDescription)
                    .collect(java.util.stream.Collectors.joining("; "));
            String signature = issues.stream()
                    .map(need -> normalizedProduct(need.productName()) + "|"
                            + need.unit() + "|" + need.stockOnHand() + "|"
                            + need.shortfallQty() + "|" + need.unitMismatch() + "|"
                            + need.quantityInvalid())
                    .collect(java.util.stream.Collectors.joining(","));
            return new PackingReadiness(false,
                    "Blocked · " + issues.size() + " product issue"
                            + (issues.size() == 1 ? "" : "s"),
                    "Order #" + customer.orderId() + " cannot be marked packed: " + details
                            + ". Resolve the order or Inventory issue, then rebuild the packing list.",
                    "PRODUCT_ISSUES:" + signature);
        }
        return new PackingReadiness(true, "Ready to pack · checks clear",
                "Order #" + customer.orderId()
                        + " has a specific handoff, saved products, valid quantities and units, and current aggregate stock coverage.",
                "READY");
    }

    private static String normalizedProduct(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String packingIssueDescription(
            MarketDayPackingService.ProductNeed need) {
        if (need.quantityInvalid()) {
            return need.productName() + " has a quantity below one rounded inventory unit";
        }
        if (need.unitMismatch()) {
            String inventoryUnit = need.inventoryUnit().isBlank()
                    ? "unknown" : need.inventoryUnit();
            return need.productName() + " uses " + need.unit()
                    + " but Inventory uses " + inventoryUnit;
        }
        return need.productName() + " is short " + formatQuantity(need.shortfallQty())
                + " " + need.unit() + " across confirmed loads";
    }

    private record PackingReadiness(
            boolean ready, String label, String guidance, String signature) {}

    private static String orderDateLabel(MarketDayPackingService.CustomerPack customer) {
        String handoff = "UNSPECIFIED".equals(handoffKey(customer.handoffType()))
                ? "Unspecified handoff" : friendlyHandoff(customer.handoffType());
        return customer.orderDate() == null
                ? handoff + " · date not set"
                : handoff + " · " + customer.orderDate();
    }

    private static String handoffKey(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "PICKUP", "DELIVERY", "MARKET" -> normalized;
            default -> "UNSPECIFIED";
        };
    }

    private static String friendlyHandoff(String value) {
        return switch (handoffKey(value)) {
            case "PICKUP" -> "Pickup";
            case "DELIVERY" -> "Delivery";
            case "MARKET" -> "Market";
            default -> "Unspecified";
        };
    }

    private static String formatQuantity(double quantity) {
        return quantity == Math.rint(quantity)
                ? String.format("%.0f", quantity) : String.format("%.1f", quantity);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String confirmedSnapshot(MarketDayPlan plan) {
        return plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .map(MarketDayTab::customerSignature)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String customerSignature(MarketDayPackingService.CustomerPack customer) {
        return customer.orderId() + "|" + customer.customerName() + "|"
                + customer.contactName() + "|" + customer.phone() + "|" + customer.email() + "|"
                + customer.accountNotes() + "|"
                + customer.status() + "|" + customer.orderDate() + "|"
                + handoffKey(customer.handoffType()) + "|" + customer.notes() + "|"
                + customer.orderTotal() + "|" + customer.lines();
    }

    private void updateShortfallHandoff(MarketDayPlan plan) {
        if (shortfallLabel == null || resolveShortfallsBtn == null) {
            return;
        }
        correctionOrderIssue = null;
        if (plan == null) {
            shortfallProducts = java.util.List.of();
            FarmUi.updateStatusBadge(shortfallLabel,
                    "Stock check unavailable", FarmUi.Tone.DANGER);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(
                    "Stock could not be checked. Completion is unavailable until a current "
                            + "packing list is built.");
            resolveShortfallsBtn.setEnabled(false);
            resolveShortfallsBtn.setToolTipText(
                    "Fix the packing scope, then rebuild the list to check stock.");
            return;
        }
        var planningShortfalls = plan.pickList().stream()
                .filter(MarketDayPackingService.ProductNeed::blockingIssue)
                .toList();
        var confirmedShortfalls = plan.confirmedPickList().stream()
                .filter(MarketDayPackingService.ProductNeed::blockingIssue)
                .toList();
        int unspecifiedOrders = unspecifiedConfirmedOrderCount(plan);
        if (unspecifiedOrders > 0) {
            Long firstOrder = firstUnspecifiedConfirmedOrderId(plan);
            shortfallProducts = java.util.List.of();
            resolveShortfallsBtn.setText("Review handoff #" + firstOrder);
            String guidance = unspecifiedOrders + " confirmed order"
                    + (unspecifiedOrders == 1 ? " has" : "s have")
                    + " no Pickup, Delivery, or Market handoff type. Open order #" + firstOrder
                    + ", save the exact handoff type, then rebuild this packing list.";
            FarmUi.updateStatusBadge(shortfallLabel,
                    unspecifiedOrders + " unspecified handoff"
                            + (unspecifiedOrders == 1 ? "" : "s")
                            + " · completion locked",
                    FarmUi.Tone.WARNING);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(guidance);
            resolveShortfallsBtn.setToolTipText(guidance);
            resolveShortfallsBtn.setEnabled(true);
            return;
        }
        int emptyOrders = emptyConfirmedOrderCount(plan);
        if (emptyOrders > 0) {
            Long firstOrder = firstEmptyConfirmedOrderId(plan);
            shortfallProducts = java.util.List.of();
            resolveShortfallsBtn.setText("Open empty order #" + firstOrder);
            String guidance = emptyOrders + " confirmed order"
                    + (emptyOrders == 1 ? " has" : "s have")
                    + " no saved products. Open order #" + firstOrder
                    + ", add at least one product, then rebuild this packing list.";
            FarmUi.updateStatusBadge(shortfallLabel,
                    emptyOrders + " empty confirmed order" + (emptyOrders == 1 ? "" : "s")
                            + " · completion locked",
                    FarmUi.Tone.WARNING);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(guidance);
            resolveShortfallsBtn.setToolTipText(guidance);
            resolveShortfallsBtn.setEnabled(true);
            return;
        }
        int missingDeliveryContext = missingDeliveryContextOrderCount(plan);
        if (missingDeliveryContext > 0) {
            Long firstOrder = firstMissingDeliveryContextOrderId(plan);
            shortfallProducts = java.util.List.of();
            resolveShortfallsBtn.setText("Add delivery context #" + firstOrder);
            String guidance = missingDeliveryContext + " confirmed Delivery order"
                    + (missingDeliveryContext == 1 ? " has" : "s have")
                    + " no contact, account delivery note, or saved order instruction. Open order #"
                    + firstOrder + ", add usable delivery context, then rebuild this packing list.";
            FarmUi.updateStatusBadge(shortfallLabel,
                    missingDeliveryContext + " Delivery context issue"
                            + (missingDeliveryContext == 1 ? "" : "s")
                            + " · completion locked",
                    FarmUi.Tone.WARNING);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(guidance);
            resolveShortfallsBtn.setToolTipText(guidance);
            resolveShortfallsBtn.setEnabled(true);
            return;
        }
        resolveShortfallsBtn.setText("Resolve stock shortfalls");
        if (planningShortfalls.isEmpty()) {
            shortfallProducts = java.util.List.of();
            FarmUi.updateStatusBadge(shortfallLabel,
                    plan.orderCount() == 0 ? "No order stock to check" : "Stock covers this list",
                    plan.orderCount() == 0 ? FarmUi.Tone.NEUTRAL : FarmUi.Tone.SUCCESS);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(
                    plan.orderCount() == 0
                            ? "No orders in this packing list require a stock check."
                            : "Current inventory covers every product in this packing list.");
            resolveShortfallsBtn.setEnabled(false);
            resolveShortfallsBtn.setToolTipText("No stock shortfalls were found in this packing list.");
            return;
        }
        boolean previewOnly = confirmedShortfalls.isEmpty();
        var shortfalls = previewOnly ? planningShortfalls : confirmedShortfalls;
        String products = shortfalls.stream()
                .limit(2)
                .map(MarketDayTab::packingIssueText)
                .reduce((left, right) -> left + " · " + right)
                .orElse("");
        shortfallProducts = shortfalls.stream()
                .map(MarketDayPackingService.ProductNeed::productName)
                .distinct()
                .toList();
        if (shortfalls.size() > 2) {
            products += " · +" + (shortfalls.size() - 2) + " more";
        }
        boolean nonStockIssue = shortfalls.stream().anyMatch(need ->
                need.unitMismatch() || need.quantityInvalid());
        String issueName = nonStockIssue ? "packing issue" : "stock gap";
        FarmUi.updateStatusBadge(shortfallLabel,
                (previewOnly ? "Draft preview · " : "")
                        + shortfalls.size() + " " + issueName
                        + (shortfalls.size() == 1 ? "" : "s")
                        + " · " + products
                        + (previewOnly ? " · completion not blocked" : ""),
                FarmUi.Tone.WARNING);
        shortfallLabel.getAccessibleContext().setAccessibleDescription(previewOnly
                ? "Draft preview demand has " + shortfalls.size() + " " + issueName
                        + (shortfalls.size() == 1 ? "" : "s") + " for "
                        + String.join(", ", shortfallProducts)
                        + ". These preview-only issues do not block confirmed completion; open "
                        + "Inventory to plan replenishment, then rebuild when ready."
                : shortfalls.size() + " " + issueName
                        + (shortfalls.size() == 1 ? "" : "s")
                        + " detected in confirmed demand; these issues block order completion. "
                        + "Open Inventory to correct the unit or stock for "
                        + String.join(", ", shortfallProducts)
                        + ", then rebuild this packing list to verify stock.");
        OrderIssueTarget orderIssue = firstOrderIssueTarget(plan, shortfalls, previewOnly);
        if (orderIssue != null) {
            correctionOrderIssue = orderIssue;
            resolveShortfallsBtn.setText(orderIssue.buttonLabel());
            String guidance = orderIssue.guidance(previewOnly);
            shortfallLabel.getAccessibleContext().setAccessibleDescription(guidance);
            resolveShortfallsBtn.setToolTipText(guidance);
            resolveShortfallsBtn.setEnabled(true);
            return;
        }
        resolveShortfallsBtn.setEnabled(true);
        resolveShortfallsBtn.setToolTipText(previewOnly
                ? "Open Inventory to plan for Draft preview demand. Confirmed completion remains available."
                : "Open Inventory to correct units, count, or replenish these products; rebuild "
                        + "the packing list afterward to unlock completion.");
    }

    private record OrderIssueTarget(long orderId, Long lineId, ProductNeed issue,
                                    String orderUnit, boolean quantityCorrection) {
        private String buttonLabel() {
            return (quantityCorrection ? "Correct quantity #" : "Review unit #") + orderId;
        }

        private String guidance(boolean previewOnly) {
            String completion = previewOnly
                    ? "This Draft preview issue does not block confirmed completion."
                    : "This confirmed-order issue blocks that load and order completion.";
            if (quantityCorrection) {
                return issue.productName() + " on order #" + orderId
                        + " is below one rounded inventory unit. Open that exact order with the "
                        + "affected saved product selected, correct its quantity to at least one inventory unit, "
                        + "and save the product. Saving starts a fresh Market Day snapshot. "
                        + completion + " Return and wait for the packing-list status to say it is "
                        + "current; build the list only if that refresh failed.";
            }
            String inventoryUnit = issue.inventoryUnit().isBlank()
                    ? "not recorded" : issue.inventoryUnit();
            return issue.productName() + " on order #" + orderId + " uses " + orderUnit
                    + " while Inventory uses " + inventoryUnit + ". Open that exact order and "
                    + "review the saved product unit. Correct the order if its unit is wrong; "
                    + "if the catalog unit is wrong, return and correct Inventory instead. "
                    + completion + " A saved correction starts a fresh Market Day snapshot. "
                    + "Return and wait for the packing-list status to say it is current; build "
                    + "the list only if that refresh failed.";
        }
    }

    private static String packingIssueText(MarketDayPackingService.ProductNeed need) {
        if (need.quantityInvalid()) {
            return need.productName() + " quantity must round to at least 1 inventory unit";
        }
        if (need.unitMismatch()) {
            String inventoryUnit = need.inventoryUnit().isBlank()
                    ? "unknown" : need.inventoryUnit();
            return need.productName() + " unit mismatch: order " + need.unit()
                    + ", inventory " + inventoryUnit;
        }
        return need.productName() + " short "
                + formatQuantity(need.shortfallQty()) + " " + need.unit();
    }

    void startMarketDayExport(String type, File destination, MarketDayPlan plan) {
        if (exporting) {
            return;
        }
        ExportKind kind = ExportKind.fromLabel(type);
        MarketDayPlan snapshot = snapshotPlan(plan);
        exporting = true;
        exportBtn.setEnabled(false);
        exportType.setEnabled(false);
        showExportState("Saving " + kind.shortName() + "…",
                "Writing the reviewed packing-list snapshot in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Saving market-day " + kind.shortName() + "…");
        }

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                byte[] content = switch (kind) {
                    case PDF -> packingService.generatePackingPdf(snapshot);
                    case CSV -> packingService.exportCsv(snapshot)
                            .getBytes(StandardCharsets.UTF_8);
                    case TEXT -> snapshot.plainText().getBytes(StandardCharsets.UTF_8);
                };
                return writeExportAtomically(destination.toPath(), content);
            }

            @Override
            protected void done() {
                try {
                    Path saved = get();
                    completeExport(kind, saved);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failExport(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failExport(exception.getCause() == null
                            ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failExport(exception);
                }
            }
        }.execute();
    }

    private void completeExport(ExportKind kind, Path saved) {
        finishExportControls();
        String message = kind.shortName() + " saved · " + saved.getFileName();
        showExportState(message,
                "Saved the captured packing-list snapshot to " + saved + ".",
                FarmUi.Tone.SUCCESS);
        if (host != null) {
            host.setStatus("Market-day " + kind.shortName() + " saved → " + saved);
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel,
                    "Packing list saved to:\n" + saved,
                    "Packing list saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void failExport(Throwable failure) {
        finishExportControls();
        String guidance = FarmUi.actionableError("Market-day packing export", failure);
        showExportState("Export failed · retry",
                guidance + " The packing list, scope, and packed checks were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(guidance + " Packing work was kept; choose Export file to retry.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, guidance, "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finishExportControls() {
        exporting = false;
        exportType.setEnabled(!refreshingPlan && !fulfilling);
        exportBtn.setEnabled(!refreshingPlan && !fulfilling && displayedPlan != null
                && !planScopeDirty);
    }

    private void showExportState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(exportStatusLabel, message, tone);
        exportStatusLabel.setToolTipText(description);
        exportStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private boolean approveReplacement(File file) {
        if (!file.exists()) {
            return true;
        }
        return dialogs.showConfirm(panel,
                file.getName() + " already exists. Replace it?",
                "Replace existing export?", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static Path writeExportAtomically(Path destination, byte[] content) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("The generated export was empty.");
        }
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("The selected export folder does not exist.");
        }
        Path temporary = Files.createTempFile(parent, ".market-day-export-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                return Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                return Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static MarketDayPlan snapshotPlan(MarketDayPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("A packing list is required for export.");
        }
        var customers = plan.customers().stream()
                .map(customer -> new MarketDayPackingService.CustomerPack(
                        customer.orderId(), customer.customerName(), customer.customerType(),
                        customer.contactName(), customer.phone(), customer.email(),
                        customer.accountNotes(), customer.status(), customer.orderDate(),
                        customer.notes(),
                        java.util.List.copyOf(customer.lines()), customer.orderTotal(),
                        customer.handoffType()))
                .toList();
        return new MarketDayPlan(plan.marketDate(), plan.from(), plan.to(), plan.scope(),
                plan.orderCount(), plan.pipelineValue(), customers,
                java.util.List.copyOf(plan.pickList()), plan.shortfallSkuCount(),
                java.util.List.copyOf(plan.confirmedPickList()),
                plan.confirmedShortfallSkuCount(),
                plan.plainText());
    }

    private void fulfillAllConfirmed() {
        if (fulfilling) {
            return;
        }
        if (blockCommittedCompletionRetry()) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "fulfill market-day orders")) {
            return;
        }
        if (!allConfirmedOrdersPacked()) {
            JOptionPane.showMessageDialog(panel,
                    "Check every confirmed customer after packing their load, then try again.",
                    "Packing checklist incomplete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            // Always CONFIRMED-only for safety
            MarketDayPlan plan = requireDisplayedPlan();
            if (hasPackingIssues(plan)) {
                int gaps = completionPackingIssueCount(plan);
                String issue = packingIssueNoun(plan);
                JOptionPane.showMessageDialog(panel,
                        gaps + " " + issue + (gaps == 1 ? " is" : "s are")
                                + " still present in this packing list.\n"
                                + "Open the affected order or Inventory, correct quantities, units, "
                                + "or counts, "
                                + "then rebuild the list.",
                        "Resolve packing issues before completion", JOptionPane.WARNING_MESSAGE);
                resolveShortfallsBtn.requestFocusInWindow();
                return;
            }
            long confirmed = plan.customers().stream()
                    .filter(c -> "CONFIRMED".equalsIgnoreCase(c.status()))
                    .count();
            if (confirmed == 0) {
                JOptionPane.showMessageDialog(panel,
                        "No confirmed orders are ready in this window.\n"
                                + "Open Customers & Orders to confirm an order, then rebuild the list.",
                        "Nothing to complete", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (!packingSnapshot.equals(confirmedSnapshot(plan))) {
                JOptionPane.showMessageDialog(panel,
                        "Confirmed orders changed after this checklist was built.\n"
                                + "The list will refresh so the new work can be reviewed and packed.",
                        "Packing list changed", JOptionPane.WARNING_MESSAGE);
                refreshPlan();
                return;
            }
            MarketDayCompletionReview review = marketDayCompletionReview(plan);
            Object[] options = new Object[]{"Cancel", "Complete checked orders"};
            int ok = dialogs.showOption(panel,
                    marketDayCompletionPanel(review),
                    "Complete confirmed orders",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (ok != 1) {
                return;
            }
            startFulfillment(plan);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel,
                    FarmUi.actionableError("Market-day batch fulfillment", ex),
                    "Batch fulfill failed", JOptionPane.ERROR_MESSAGE);
            if (host != null) {
                host.setStatus("Batch fulfill failed: " + ex.getMessage());
            }
        }
    }

    record MarketDayCompletionReview(
            String heading, String details, int confirmedCount, double confirmedValue) {}

    static MarketDayCompletionReview marketDayCompletionReview(MarketDayPlan plan) {
        if (plan == null) {
            return new MarketDayCompletionReview(
                    "Complete checked confirmed orders?",
                    "The reviewed packing list is unavailable. Build it again before completing orders.",
                    0, 0);
        }
        java.util.List<MarketDayPackingService.CustomerPack> confirmed = plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .toList();
        double confirmedValue = confirmed.stream()
                .mapToDouble(MarketDayPackingService.CustomerPack::orderTotal)
                .sum();
        int productCount = confirmed.stream()
                .mapToInt(customer -> customer.lines().size())
                .sum();
        String heading = "Complete " + confirmed.size() + " checked confirmed order"
                + (confirmed.size() == 1 ? "" : "s") + " · $"
                + String.format(java.util.Locale.ROOT, "%,.2f", confirmedValue) + "?";
        StringBuilder details = new StringBuilder()
                .append("Market window: ").append(plan.from()).append(" → ").append(plan.to())
                .append("\nOnly the confirmed orders below will be completed. Draft and already-completed ")
                .append("reference orders are excluded.\n\n");
        for (int orderIndex = 0; orderIndex < confirmed.size(); orderIndex++) {
            MarketDayPackingService.CustomerPack customer = confirmed.get(orderIndex);
            details.append(orderIndex + 1).append(". Order #").append(customer.orderId())
                    .append(" · ").append(customer.customerName())
                    .append(" · ").append(orderDateLabel(customer))
                    .append(" · ").append(customer.lines().size()).append(" product")
                    .append(customer.lines().size() == 1 ? "" : "s")
                    .append(" · $")
                    .append(String.format(java.util.Locale.ROOT, "%,.2f", customer.orderTotal()))
                    .append('\n');
            String contact = java.util.stream.Stream.of(
                            customer.contactName(), customer.phone(), customer.email())
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(" · "));
            details.append("   Contact: ")
                    .append(contact.isBlank() ? "none saved" : contact)
                    .append('\n');
            details.append("   Account delivery note: ")
                    .append(customer.accountNotes() == null
                            || customer.accountNotes().isBlank()
                            ? "none saved" : customer.accountNotes().trim())
                    .append('\n');
            for (int lineIndex = 0; lineIndex < customer.lines().size(); lineIndex++) {
                MarketDayPackingService.PackLine line = customer.lines().get(lineIndex);
                details.append("   ").append(lineIndex + 1).append(") ")
                        .append(formatQuantity(line.quantity())).append(' ')
                        .append(line.unit()).append(' ').append(line.productName())
                        .append(" · $")
                        .append(String.format(java.util.Locale.ROOT, "%,.2f", line.unitPrice()))
                        .append(" each · $")
                        .append(String.format(java.util.Locale.ROOT, "%,.2f", line.lineTotal()))
                        .append('\n');
            }
            details.append("   Instructions: ")
                    .append(customer.notes() == null || customer.notes().isBlank()
                            ? "none saved" : customer.notes().trim())
                    .append("\n\n");
        }
        details.append("Total: ").append(confirmed.size()).append(" confirmed order")
                .append(confirmed.size() == 1 ? "" : "s")
                .append(" · ").append(productCount).append(" product")
                .append(productCount == 1 ? "" : "s")
                .append(" · $")
                .append(String.format(java.util.Locale.ROOT, "%,.2f", confirmedValue))
                .append("\nCompleting deducts matching inventory. Cancel if any customer load, product, ")
                .append("quantity, date, or instruction is not exact.");
        return new MarketDayCompletionReview(
                heading, details.toString(), confirmed.size(), confirmedValue);
    }

    static JComponent marketDayCompletionPanel(MarketDayCompletionReview review) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setName("marketDay.completionReview");
        content.setBackground(FarmUi.surfaceColor());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel heading = FarmUi.statusBadge(review.heading(), FarmUi.Tone.WARNING);
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(heading, BorderLayout.NORTH);

        JTextArea details = FarmUi.displayArea(12, 64);
        details.setName("marketDay.completionDetails");
        details.setText(review.details());
        details.setCaretPosition(0);
        details.setBackground(FarmUi.surfaceColor());
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        details.getAccessibleContext().setAccessibleName(
                "Checked customer loads and inventory completion consequences");
        details.getAccessibleContext().setAccessibleDescription(review.details());
        JScrollPane scroll = new JScrollPane(details);
        scroll.setName("marketDay.completionScroll");
        scroll.setPreferredSize(new Dimension(680, 300));
        scroll.setMinimumSize(new Dimension(340, 180));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getAccessibleContext().setAccessibleName(
                "Scrollable Market Day completion review");
        content.add(scroll, BorderLayout.CENTER);
        content.getAccessibleContext().setAccessibleName(
                review.heading() + " exact checked-load review");
        return content;
    }

    void startFulfillment(MarketDayPlan plan) {
        if (fulfilling) {
            return;
        }
        if (blockCommittedCompletionRetry()) {
            return;
        }
        if (hasPackingIssues(plan)) {
            int gaps = completionPackingIssueCount(plan);
            String issue = packingIssueNoun(plan);
            String message = gaps + " " + issue + (gaps == 1 ? "" : "s")
                    + " · completion locked";
            boolean stockOnly = "stock gap".equals(issue);
            String guidance = stockOnly
                    ? "Resolve the listed products in Inventory, then rebuild the packing list "
                            + "to verify stock before completing orders."
                    : "Resolve the listed quantities, units, or stock in Orders or Inventory, "
                            + "then rebuild the packing list before completing orders.";
            FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.DANGER);
            fulfillmentStatusLabel.setToolTipText(guidance);
            fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                    message + ". " + guidance);
            if (host != null) {
                host.setStatus(stockOnly
                        ? "Market-day completion blocked by " + gaps
                                + " stock gap" + (gaps == 1 ? "" : "s")
                                + ". Resolve stock and rebuild the packing list."
                        : "Market-day completion blocked by " + gaps
                                + " packing issue" + (gaps == 1 ? "" : "s")
                                + ". Correct the order or inventory unit and rebuild the list.");
            }
            return;
        }
        long confirmed = plan == null ? 0 : plan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .count();
        if (confirmed == 0) {
            throw new IllegalArgumentException("At least one confirmed order is required.");
        }
        fulfilling = true;
        setFulfillmentControlsEnabled(false);
        showFulfillmentProgressHandoff(confirmed);
        fulfillBtn.setText("Completing orders…");
        fulfillBtn.setToolTipText(
                "Order completion is running; duplicate submission is disabled.");
        String progress = "Completing " + confirmed + (confirmed == 1 ? " order" : " orders")
                + " · inventory updating";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, progress, FarmUi.Tone.INFO);
        fulfillmentStatusLabel.setToolTipText(
                "The checked order snapshot is completing in the background.");
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                progress + ". Scope and packing checks are locked until it finishes.");
        if (host != null) {
            host.setStatus("⏳ Completing " + confirmed
                    + (confirmed == 1 ? " market-day order…" : " market-day orders…"));
        }

        SwingWorker<FulfillBatchResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FulfillBatchResult doInBackground() {
                return packingService.fulfillConfirmedOrders(plan);
            }

            @Override
            protected void done() {
                try {
                    FulfillBatchResult result = get();
                    if (result == null) {
                        throw new IllegalStateException(
                                "The completion service returned no result.");
                    }
                    completeFulfillment(result);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failFulfillment(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failFulfillment(exception.getCause() == null
                            ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failFulfillment(exception);
                }
            }
        };
        worker.execute();
    }

    private void completeFulfillment(FulfillBatchResult result) {
        finishFulfillmentControls();
        showFulfillmentResultHandoff(result);
        if (result.fulfilled() > 0) {
            completionRefreshRequired = true;
            committedCompletionCount = result.fulfilled();
            committedCompletionOrderIds = result.fulfilledOrderIds();
        }
        String status = result.fulfilled() + " completed · " + result.failed() + " failed"
                + (result.failed() > 0 ? " · retry remaining" : "");
        FarmUi.Tone tone = result.failed() > 0 ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS;
        String failureDetail = failedFulfillmentDetail(result);
        String refreshGuidance = "";
        if (host != null) {
            try {
                host.refreshAll();
            } catch (RuntimeException refreshFailure) {
                refreshGuidance = " Refresh the farm overview manually. "
                        + FarmUi.actionableError(
                        "Refreshing completed market-day work", refreshFailure);
            }
        }
        String tooltip =
                result.failed() > 0
                        ? "Completed orders were saved. " + failureDetail
                        + " Retry only orders still confirmed."
                        : "All attempted confirmed orders completed successfully.";
        String description = status + ". Fulfilled " + result.fulfilled() + ", skipped "
                        + result.skipped() + ", failed " + result.failed() + "."
                        + (failureDetail.isBlank() ? "" : " " + failureDetail + ".")
                        + refreshGuidance;
        pendingFulfillmentPresentation = new FulfillmentPresentation(
                status, tone, tooltip, description,
                "Market-day completion: " + result.fulfilled()
                        + " done, " + result.failed() + " failed.");
        refreshPlan();
        applyFulfillmentPresentation(pendingFulfillmentPresentation);
        if (panel.isShowing()) {
            showFulfillmentResult(result);
        }
    }

    private boolean blockCommittedCompletionRetry() {
        if (!completionRefreshRequired) {
            return false;
        }
        applyCompletionRefreshLockGuidance();
        FarmUi.revealAndFocus(buildPlanBtn);
        return true;
    }

    private void applyCompletionRefreshLockGuidance() {
        String guidance = completionRefreshRecovery();
        String status = committedCompletionCount + " completed once · packing refresh required";
        fulfillBtn.setEnabled(false);
        fulfillBtn.setToolTipText(guidance);
        fulfillBtn.getAccessibleContext().setAccessibleDescription(guidance);
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, status, FarmUi.Tone.WARNING);
        fulfillmentStatusLabel.setToolTipText(guidance);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                status + ". " + guidance);
        buildPlanBtn.setToolTipText(guidance + " Build packing list is the only safe retry action.");
        if (host != null) {
            host.setStatus("Market-day completion was saved once. Build the packing list to verify "
                    + "the current confirmed queue; do not complete the retained checklist again.");
        }
    }

    private String completionRefreshRecovery() {
        return committedCompletionCount + " market-day order(s) completed once and inventory was "
                + "deducted once. The current checked list is retained pre-completion data. Use "
                + "Build packing list only; do not complete this checklist again until the refreshed "
                + "confirmed queue proves those exact completed orders are no longer active.";
    }

    static boolean completedOrdersLeftConfirmedQueue(
            java.util.Set<Long> completedOrderIds, MarketDayPlan refreshedPlan) {
        if (completedOrderIds == null || completedOrderIds.isEmpty() || refreshedPlan == null) {
            return false;
        }
        return refreshedPlan.customers().stream()
                .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                .map(MarketDayPackingService.CustomerPack::orderId)
                .noneMatch(completedOrderIds::contains);
    }

    private void applyFulfillmentPresentation(FulfillmentPresentation presentation) {
        FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                presentation.status(), presentation.tone());
        fulfillmentStatusLabel.setToolTipText(presentation.tooltip());
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                presentation.description());
        if (host != null) {
            host.setStatus(presentation.hostStatus());
        }
    }

    private void failFulfillment(Throwable failure) {
        finishFulfillmentControls();
        updateFulfillAction();
        String detail = safeMessage(failure);
        boolean rebuildRequired = detail.toLowerCase(java.util.Locale.ROOT)
                .contains("rebuild the packing list");
        showFulfillmentFailureHandoff(detail, rebuildRequired);
        String status = rebuildRequired
                ? "Packing review changed · rebuild required"
                : "Completion failed · " + compactMessage(detail) + " · try again";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, status, FarmUi.Tone.DANGER);
        String guidance = rebuildRequired
                ? detail + " No order was completed; use Build packing list to review the current "
                        + "customer loads and stock."
                : FarmUi.actionableError("Completing market-day orders", failure);
        fulfillmentStatusLabel.setToolTipText(guidance);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                status + ". Packing checks were kept and no completion result was received. "
                        + guidance);
        if (rebuildRequired) {
            buildPlanBtn.setToolTipText(
                    "Rebuild now to review changed customer loads or current stock before completion.");
            buildPlanBtn.requestFocusInWindow();
        }
        if (host != null) {
            host.setStatus(rebuildRequired
                    ? "Market-day completion stopped before any order changed. Rebuild the packing "
                            + "list and review it again."
                    : guidance + " Packing checks were kept.");
        }
    }

    private void finishFulfillmentControls() {
        fulfilling = false;
        fulfillBtn.setText("Complete confirmed orders…");
        setFulfillmentControlsEnabled(true);
        updateFulfillAction();
    }

    private void setFulfillmentControlsEnabled(boolean enabled) {
        dateField.setEnabled(enabled);
        windowSpinner.setEnabled(enabled);
        draftBox.setEnabled(enabled);
        fulfilledBox.setEnabled(enabled);
        buildPlanBtn.setEnabled(enabled && !refreshingPlan);
        exportBtn.setEnabled(enabled && !refreshingPlan && !exporting && displayedPlan != null);
        exportType.setEnabled(enabled && !refreshingPlan && !exporting);
        todayBtn.setEnabled(enabled);
        nextSaturdayBtn.setEnabled(enabled);
        for (Map.Entry<Long, JCheckBox> entry : packedOrders.entrySet()) {
            entry.getValue().setEnabled(enabled && orderReadyToPack(entry.getKey())
                    && canWrite && !refreshingPlan);
        }
        nextUnpackedBtn.setEnabled(enabled && !refreshingPlan && !allConfirmedOrdersPacked());
        if (!enabled) {
            fulfillBtn.setEnabled(false);
        }
    }

    private void showFulfillmentResult(FulfillBatchResult result) {
        StringBuilder message = new StringBuilder();
        message.append("Fulfilled: ").append(result.fulfilled())
                .append("  ·  Skipped: ").append(result.skipped())
                .append("  ·  Failed: ").append(result.failed()).append("\n\n");
        for (String line : result.messages()) {
            message.append("• ").append(line).append('\n');
        }
        JTextArea area = new JTextArea(message.toString(), 14, 52);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(panel, new JScrollPane(area),
                "Batch completion result", result.failed() > 0
                        ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private void showFulfillmentProgressHandoff(long confirmed) {
        fulfillmentResultArea.setText(
                "Completion in progress for " + confirmed + " checked confirmed order"
                        + (confirmed == 1 ? "" : "s") + ".\n"
                        + "The previous outcome is cleared; wait for the exact result before retrying.");
        fulfillmentResultArea.setCaretPosition(0);
        fulfillmentResultArea.getAccessibleContext().setAccessibleDescription(
                fulfillmentResultArea.getText());
        fulfillmentResultScroll.setVisible(true);
        fulfillmentResultScroll.revalidate();
    }

    private void showFulfillmentResultHandoff(FulfillBatchResult result) {
        String details = fulfillmentHandoffText(result);
        fulfillmentResultArea.setText(details);
        fulfillmentResultArea.setCaretPosition(0);
        fulfillmentResultArea.getAccessibleContext().setAccessibleDescription(details);
        fulfillmentResultScroll.setVisible(true);
        fulfillmentResultScroll.revalidate();
        fulfillmentResultScroll.repaint();
    }

    private void showFulfillmentFailureHandoff(String detail, boolean rebuildRequired) {
        String guidance = rebuildRequired
                ? "No order was completed. Build the packing list again and recheck every changed load."
                : "No completion result was received. The packed checks were kept; review the error "
                        + "before retrying.";
        String text = "Completion stopped before a result\n" + detail + "\nNext action: " + guidance;
        fulfillmentResultArea.setText(text);
        fulfillmentResultArea.setCaretPosition(0);
        fulfillmentResultArea.getAccessibleContext().setAccessibleDescription(text);
        fulfillmentResultScroll.setVisible(true);
        fulfillmentResultScroll.revalidate();
        fulfillmentResultScroll.repaint();
    }

    static String fulfillmentHandoffText(FulfillBatchResult result) {
        if (result == null) {
            return "No completion result was received. Do not retry until the packing list is refreshed.";
        }
        StringBuilder text = new StringBuilder("Completion result · ")
                .append(result.fulfilled()).append(" completed · ")
                .append(result.failed()).append(" failed · ")
                .append(result.skipped()).append(" skipped\n");
        if (result.messages() == null || result.messages().isEmpty()) {
            text.append("No per-order outcomes were returned.\n");
        } else {
            for (String message : result.messages()) {
                if (message != null && !message.isBlank()) {
                    text.append("- ").append(message.trim()).append('\n');
                }
            }
        }
        if (result.failed() > 0) {
            text.append("Next action: completed orders are already saved. Failed confirmed orders ")
                    .append("remain checked below. Correct each reason, rebuild if order or stock ")
                    .append("details changed, then complete only the remaining confirmed orders.");
        } else if (result.skipped() > 0) {
            text.append("Next action: completed orders are saved. Review every skipped order; it was ")
                    .append("not changed by this completion.");
        } else {
            text.append("Next action: all attempted confirmed orders completed successfully; no retry is needed.");
        }
        return text.toString();
    }

    private static String failedFulfillmentDetail(FulfillBatchResult result) {
        if (result.messages() == null) {
            return "";
        }
        return result.messages().stream()
                .filter(message -> message != null && message.contains("FAILED:"))
                .limit(2)
                .reduce((left, right) -> left + " · " + right)
                .orElse("");
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? "The application did not provide additional details" : message.trim();
    }

    private static String compactMessage(String message) {
        return message.length() <= 64 ? message : message.substring(0, 61) + "…";
    }

    private record PlanRequest(LocalDate date, int window,
                               boolean includeDrafts, boolean includeFulfilled) {
    }

    private record FulfillmentPresentation(String status, FarmUi.Tone tone,
                                           String tooltip, String description,
                                           String hostStatus) {
    }

    private enum ExportKind {
        PDF("Packing PDF", "PDF"), CSV("Spreadsheet CSV", "CSV"), TEXT("Plain text", "text file");

        private final String label;
        private final String shortName;

        ExportKind(String label, String shortName) {
            this.label = label;
            this.shortName = shortName;
        }

        static ExportKind fromLabel(String label) {
            for (ExportKind kind : values()) {
                if (kind.label.equals(label)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Choose a supported market-day export format.");
        }

        String shortName() {
            return shortName;
        }

        String defaultName(LocalDate date) {
            return switch (this) {
                case PDF -> "market-day-packing-" + date + ".pdf";
                case CSV -> "market-day-" + date + ".csv";
                case TEXT -> "market-day-" + date + ".txt";
            };
        }
    }
}

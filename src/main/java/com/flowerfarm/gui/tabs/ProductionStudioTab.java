package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.service.ProductionConflictException;
import com.flowerfarm.service.ProductionStudioService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.function.Function;

/** Desktop production workspace spanning crops, tasks, recipes, economics, and handoffs. */
public final class ProductionStudioTab implements FlowerFarmTab {

    private final ProductionStudioService service;
    private final FarmDialogs dialogs;

    private JPanel panel;
    private boolean canWrite = true;
    private List<GrowingTemplate> templates = List.of();
    private List<FloralRecipe> recipes = List.of();
    private List<ProductionTask> tasks = List.of();
    private List<ProductionTask> visibleTasks = List.of();
    private JLabel studioStatus;
    private int statusRevision;

    private JTextField seasonYear;
    private JTextField springFrost;
    private JTextField fallFrost;
    private JTextField frostSource;
    private JTextField loadedLaborRate;
    private JButton saveFrost;

    private JTextField templateName;
    private JTextField cropName;
    private JTextField variety;
    private JTextField spacing;
    private JTextField rows;
    private JTextField sowOffset;
    private JTextField transplantOffset;
    private JTextField daysToHarvest;
    private JTextField harvestWindow;
    private JTextField successionDays;
    private JTextField stemsPerPlant;
    private JTextField germination;
    private JTextField soilLead;
    private JTextField supportOffset;
    private JTextArea templateNotes;
    private JButton saveTemplate;
    private JButton loadTemplate;
    private JButton resetTemplate;
    private JButton deleteTemplate;
    private JTable templateTable;
    private DefaultTableModel templateModel;

    private JComboBox<GrowingTemplate> plantingTemplate;
    private JTextField plantingYear;
    private JTextField plantingBed;
    private JTextField plantingFeet;
    private JTextField plantingSuccessions;
    private JButton draftPlantings;
    private JButton commitPlantings;
    private JTable draftTable;
    private DefaultTableModel draftModel;
    private JTextField taskFrom;
    private JTextField taskTo;
    private JButton refreshTasks;
    private JButton markTaskDone;
    private JButton skipTask;
    private JButton reopenTask;
    private JButton regenerateTasks;
    private JComboBox<String> taskStatusFilter;
    private JTextField taskActualMinutes;
    private JTextField taskInputCost;
    private JTextField taskFieldObservation;
    private JTable taskTable;
    private DefaultTableModel taskModel;

    private JTextField recipeName;
    private JTextField recipeType;
    private JTextField salePrice;
    private JTextField laborMinutes;
    private JTextField laborRate;
    private JTextField overheadPercent;
    private JTextField wastePercent;
    private JTextArea ingredientLines;
    private JTextArea recipeNotes;
    private JButton saveRecipe;
    private JButton loadRecipe;
    private JButton resetRecipe;
    private JButton deleteRecipe;
    private JTable recipeTable;
    private DefaultTableModel recipeModel;

    private JComboBox<FloralRecipe> estimateRecipe;
    private JTextField designCount;
    private JTextField eventDate;
    private JButton calculate;
    private JTextArea estimateOutput;
    private JTextField availabilityWeek;
    private JButton refreshAvailability;
    private JTable availabilityTable;
    private DefaultTableModel availabilityModel;
    private JTextField productionCostYear;
    private JButton refreshProductionCosts;
    private JTextArea productionCostSummary;
    private JTable productionCostTable;
    private DefaultTableModel productionCostModel;
    private JComboBox<String> handoffType;
    private JTextField handoffProvider;
    private JComboBox<String> handoffStatus;
    private JTextField handoffReference;
    private JTextField handoffOrder;
    private JTextField handoffRecipe;
    private JTextField handoffAmount;
    private JTextField handoffCurrency;
    private JTextArea handoffSummary;
    private JButton createHandoff;
    private JTable handoffTable;
    private DefaultTableModel handoffModel;
    private Long editingTemplateId;
    private Long editingRecipeId;

    public ProductionStudioTab(ProductionStudioService service, FarmDialogs dialogs) {
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Production Studio";
    }

    @Override
    public String getDescription() {
        return "Farm templates, frost schedules, task chains, design recipes, profit, and handoffs";
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
        if (panel == null) {
            return;
        }
        loadTemplatesAndRecipes();
        loadFrost();
        loadTasks();
        loadAvailability();
        loadProductionCosts();
        loadHandoffs();
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        for (JButton button : new JButton[]{saveFrost, saveTemplate, commitPlantings,
                deleteTemplate, markTaskDone, skipTask, reopenTask, regenerateTasks, saveRecipe,
                deleteRecipe, createHandoff}) {
            if (button != null) {
                button.setEnabled(canWrite);
            }
        }
        updateTemplateActionState();
        updatePlantingActionState();
        updateTaskActionState();
        updateRecipeActionState();
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("productionStudio.page");
        panel.add(FarmUi.pageHeader("Production Studio",
                "Turn farm-owned crop assumptions into field work, saleable designs, and "
                        + "reviewable external handoffs.", null), BorderLayout.NORTH);
        JTabbedPane workspace = new JTabbedPane();
        workspace.setName("productionStudio.workspace");
        workspace.getAccessibleContext().setAccessibleName("Production studio workspaces");
        workspace.addTab("Frost & crop templates", buildTemplateWorkspace());
        workspace.addTab("Plantings & work", buildPlantingWorkspace());
        workspace.addTab("Design recipes", buildRecipeWorkspace());
        workspace.addTab("Economics & handoffs", buildEconomicsWorkspace());
        panel.add(workspace, BorderLayout.CENTER);
        studioStatus = FarmUi.statusBadge("Production Studio ready", FarmUi.Tone.NEUTRAL);
        studioStatus.setName("production.status");
        studioStatus.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        panel.add(studioStatus, BorderLayout.SOUTH);
        wireActions();
        applyRolePermissions(canWrite);
    }

    private JComponent buildTemplateWorkspace() {
        JPanel frost = formPanel();
        seasonYear = field("production.seasonYear", Integer.toString(Year.now().getValue()), 6);
        springFrost = field("production.springFrost", Year.now() + "-04-15", 10);
        fallFrost = field("production.fallFrost", Year.now() + "-10-15", 10);
        frostSource = field("production.frostSource", "Farm estimate", 18);
        loadedLaborRate = field("production.loadedLaborRate", "25.00", 8);
        saveFrost = named(FarmUi.primaryButton("Save frost anchors"), "production.saveFrost");
        addRow(frost, 0, "Season year", seasonYear, "Last spring frost", springFrost);
        addRow(frost, 1, "First fall frost", fallFrost, "Source / station", frostSource);
        addRow(frost, 2, "Loaded labor $ / hour", loadedLaborRate, "", saveFrost);

        JPanel form = formPanel();
        templateName = field("production.templateName", "", 16);
        cropName = field("production.cropName", "", 16);
        variety = field("production.variety", "", 16);
        spacing = field("production.spacing", "9", 8);
        rows = field("production.rows", "4", 8);
        sowOffset = field("production.sowOffset", "-42", 8);
        transplantOffset = field("production.transplantOffset", "7", 8);
        daysToHarvest = field("production.daysToHarvest", "90", 8);
        harvestWindow = field("production.harvestWindow", "21", 8);
        successionDays = field("production.successionDays", "14", 8);
        stemsPerPlant = field("production.stemsPerPlant", "3", 8);
        germination = field("production.germination", "85", 8);
        soilLead = field("production.soilLead", "14", 8);
        supportOffset = field("production.supportOffset", "10", 8);
        templateNotes = area("production.templateNotes", 3, 28);
        saveTemplate = named(FarmUi.primaryButton("Save template"),
                "production.saveTemplate");
        loadTemplate = named(FarmUi.secondaryButton("Load selected"),
                "production.loadTemplate");
        resetTemplate = named(FarmUi.secondaryButton("New template"),
                "production.resetTemplate");
        deleteTemplate = named(FarmUi.secondaryButton("Delete selected"),
                "production.deleteTemplate");
        loadTemplate.setToolTipText("Load the selected library row into the editor.");
        deleteTemplate.setToolTipText(
                "Delete the selected template only when it has no generated work history.");
        addRow(form, 0, "Template name", templateName, "Crop", cropName);
        addRow(form, 1, "Variety", variety, "Spacing (in)", spacing);
        addRow(form, 2, "Rows per bed", rows, "Sow days from frost", sowOffset);
        addRow(form, 3, "Transplant days from frost (blank=direct)", transplantOffset,
                "Days sow-to-harvest", daysToHarvest);
        addRow(form, 4, "Harvest window (days)", harvestWindow,
                "Succession interval", successionDays);
        addRow(form, 5, "Stems per viable plant", stemsPerPlant,
                "Germination %", germination);
        addRow(form, 6, "Soil-prep lead", soilLead,
                "Support after planting", supportOffset);
        addWide(form, 7, scroll(templateNotes));
        addWide(form, 8, buttonRow(saveTemplate, loadTemplate,
                resetTemplate, deleteTemplate));

        templateModel = model("ID", "Template", "Crop / variety", "Spacing", "Frost offsets",
                "Maturity", "Yield");
        templateTable = table("production.templateTable", templateModel);
        JPanel top = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        top.setOpaque(false);
        top.add(FarmUi.section("Annual frost anchors",
                "Owned by the farm; verify against your microclimate.", frost), BorderLayout.NORTH);
        top.add(FarmUi.section("Reusable growing template",
                "Spacing, maturity, succession, yield, and dependent task offsets.", form),
                BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(top),
                FarmUi.section("Farm template library", "Saved assumptions used by every plan.",
                        scroll(templateTable)));
        split.setResizeWeight(0.68);
        split.setDividerLocation(0.68);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JComponent buildPlantingWorkspace() {
        JPanel controls = formPanel();
        plantingTemplate = combo("production.plantingTemplate");
        plantingYear = field("production.plantingYear", Integer.toString(Year.now().getValue()), 6);
        plantingBed = field("production.plantingBed", "Bed A", 12);
        plantingFeet = field("production.plantingFeet", "50", 8);
        plantingSuccessions = field("production.plantingSuccessions", "1", 6);
        draftPlantings = named(FarmUi.secondaryButton("Preview schedule"),
                "production.draftPlantings");
        commitPlantings = named(FarmUi.primaryButton("Commit plan & tasks"),
                "production.commitPlantings");
        draftPlantings.setToolTipText("Calculate a schedule without changing farm records.");
        commitPlantings.setToolTipText(
                "Review and commit the current schedule plus its dependent work.");
        addRow(controls, 0, "Growing template", plantingTemplate, "Season year", plantingYear);
        addRow(controls, 1, "Bed / field", plantingBed, "Bed feet", plantingFeet);
        addRow(controls, 2, "Successions", plantingSuccessions, "", buttonRow(draftPlantings,
                commitPlantings));
        draftModel = model("Succession", "Sow", "Transplant", "Harvest window",
                "Plant sites", "Viable", "Expected stems", "Frost check");
        draftTable = table("production.draftTable", draftModel);
        JPanel plans = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        plans.setOpaque(false);
        plans.add(FarmUi.section("Frost-aware schedule builder",
                "Preview before committing; successions move the full dependent chain.", controls),
                BorderLayout.NORTH);
        plans.add(scroll(draftTable), BorderLayout.CENTER);

        JPanel taskControls = new JPanel(new GridLayout(0, 1, 0, 6));
        taskControls.setOpaque(false);
        taskFrom = field("production.taskFrom", LocalDate.now().minusDays(14).toString(), 10);
        taskTo = field("production.taskTo", LocalDate.now().plusDays(90).toString(), 10);
        refreshTasks = named(FarmUi.secondaryButton("Refresh work"), "production.refreshTasks");
        taskStatusFilter = stringCombo("production.taskStatusFilter",
                "Open", "All", "Done", "Skipped");
        markTaskDone = named(FarmUi.primaryButton("Mark selected done"),
                "production.markTaskDone");
        skipTask = named(FarmUi.secondaryButton("Skip selected"), "production.skipTask");
        reopenTask = named(FarmUi.secondaryButton("Reopen selected"), "production.reopenTask");
        regenerateTasks = named(FarmUi.secondaryButton("Regenerate open work"),
                "production.regenerateTasks");
        taskActualMinutes = field("production.taskActualMinutes", "0", 6);
        taskInputCost = field("production.taskInputCost", "0.00", 7);
        taskFieldObservation = field("production.taskFieldObservation", "", 22);
        markTaskDone.setText("Save actuals + done");
        markTaskDone.setToolTipText(
                "Complete the task with its actual labor, input cost, and field result.");
        taskActualMinutes.setToolTipText(
                "Total crew minutes spent on this task, from 0 to 10,080.");
        taskInputCost.setToolTipText(
                "Task-specific seed, amendment, support, treatment, or other input cost.");
        taskFieldObservation.setToolTipText(
                "What the crew saw or learned while completing this field task.");
        skipTask.setToolTipText("Skip the selected open task.");
        reopenTask.setToolTipText("Reopen the selected completed or skipped task.");
        regenerateTasks.setToolTipText(
                "Rebuild only open generated work for the selected task's planting plan.");
        taskControls.add(FarmUi.toolbar(new JLabel("From"), taskFrom, new JLabel("To"), taskTo,
                refreshTasks, new JLabel("Show"), taskStatusFilter,
                skipTask, reopenTask, regenerateTasks));
        taskControls.add(FarmUi.toolbar(new JLabel("Labor min"), taskActualMinutes,
                new JLabel("Input cost"), taskInputCost,
                new JLabel("Field result"), taskFieldObservation, markTaskDone));
        taskModel = model("ID", "Due", "Timing", "Status", "Task", "Crop", "Bed / field",
                "Labor", "Inputs", "Field result", "Plan");
        taskTable = table("production.taskTable", taskModel);
        JPanel work = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        work.setOpaque(false);
        work.add(taskControls, BorderLayout.NORTH);
        work.add(scroll(taskTable), BorderLayout.CENTER);
        JPanel workspace = new JPanel(new GridLayout(2, 1, 0, FarmUi.SECTION_GAP));
        workspace.setOpaque(false);
        workspace.add(FarmUi.section("Planting blocks",
                "Template math becomes the season plan.", plans));
        workspace.add(FarmUi.section("Dependent work",
                "Completed work is preserved on regeneration.", work));
        return workspace;
    }

    private JComponent buildRecipeWorkspace() {
        JPanel form = formPanel();
        recipeName = field("production.recipeName", "", 16);
        recipeType = field("production.recipeType", "Bouquet", 12);
        salePrice = field("production.salePrice", "35", 8);
        laborMinutes = field("production.laborMinutes", "12", 8);
        laborRate = field("production.laborRate", "25", 8);
        overheadPercent = field("production.overheadPercent", "15", 8);
        wastePercent = field("production.wastePercent", "10", 8);
        ingredientLines = area("production.ingredientLines", 7, 32);
        ingredientLines.setText("Sunflower | 3 | 10 | 12.00\nZinnia | 5 | 10 | 9.00");
        recipeNotes = area("production.recipeNotes", 3, 32);
        saveRecipe = named(FarmUi.primaryButton("Save recipe"), "production.saveRecipe");
        loadRecipe = named(FarmUi.secondaryButton("Load selected"), "production.loadRecipe");
        resetRecipe = named(FarmUi.secondaryButton("New recipe"), "production.resetRecipe");
        deleteRecipe = named(FarmUi.secondaryButton("Delete selected"),
                "production.deleteRecipe");
        loadRecipe.setToolTipText("Load the selected recipe into the editor.");
        deleteRecipe.setToolTipText(
                "Delete the selected recipe; handoff history remains but its recipe link clears.");
        addRow(form, 0, "Recipe name", recipeName, "Design type", recipeType);
        addRow(form, 1, "Sale price / design", salePrice,
                "Labor minutes / design", laborMinutes);
        addRow(form, 2, "Labor rate / hour", laborRate, "Overhead %", overheadPercent);
        addRow(form, 3, "Waste %", wastePercent, "",
                buttonRow(saveRecipe, loadRecipe, resetRecipe, deleteRecipe));
        addWide(form, 4, new JLabel("Ingredients: one per line as Item | stems/design | pack size | pack cost"));
        addWide(form, 5, scroll(ingredientLines));
        addWide(form, 6, scroll(recipeNotes));
        recipeModel = model("ID", "Recipe", "Type", "Price", "Labor", "Waste",
                "Ingredients");
        recipeTable = table("production.recipeTable", recipeModel);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                FarmUi.section("Reusable design recipe",
                        "Bouquets, events, subscriptions, and wholesale packs use the same math.",
                        scroll(form)),
                FarmUi.section("Recipe library",
                        "Stored recipes can be estimated against any event week.",
                        scroll(recipeTable)));
        split.setResizeWeight(0.56);
        split.setDividerLocation(0.56);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JComponent buildEconomicsWorkspace() {
        JPanel estimate = formPanel();
        estimateRecipe = combo("production.estimateRecipe");
        designCount = field("production.designCount", "25", 8);
        eventDate = field("production.eventDate", LocalDate.now().plusDays(30).toString(), 10);
        calculate = named(FarmUi.primaryButton("Calculate profit & purchasing"),
                "production.calculate");
        estimateOutput = area("production.estimateOutput", 9, 42);
        estimateOutput.setEditable(false);
        addRow(estimate, 0, "Recipe", estimateRecipe, "Design count", designCount);
        addRow(estimate, 1, "Event / fulfillment date", eventDate, "", calculate);
        addWide(estimate, 2, scroll(estimateOutput));

        JPanel availability = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        availability.setOpaque(false);
        JPanel availabilityControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        availabilityControls.setOpaque(false);
        availabilityWeek = field("production.availabilityWeek", LocalDate.now().toString(), 10);
        refreshAvailability = named(FarmUi.secondaryButton("Refresh availability"),
                "production.refreshAvailability");
        availabilityControls.add(new JLabel("Week of"));
        availabilityControls.add(availabilityWeek);
        availabilityControls.add(refreshAvailability);
        availabilityModel = model("Crop / SKU", "Week", "Planned", "Live", "Total", "Windows");
        availabilityTable = table("production.availabilityTable", availabilityModel);
        availability.add(availabilityControls, BorderLayout.NORTH);
        availability.add(scroll(availabilityTable), BorderLayout.CENTER);

        JTabbedPane economics = new JTabbedPane();
        economics.setName("production.economicsWorkspace");
        economics.getAccessibleContext().setAccessibleName("Economics and handoff workspaces");
        economics.addTab("Recipe profitability", FarmUi.section("True recipe economics",
                "Wholesale pack rounding, waste, labor, overhead, markup, margin, and shortage.",
                estimate));
        economics.addTab("Planned + live availability", FarmUi.section("Sellable availability",
                "The event week's crop plan reconciled with live inventory.", availability));
        economics.addTab("Production costs", FarmUi.section("Planting and season costs",
                "Task actuals plus matched harvests; unlinked work and unmatched harvests stay visible.",
                buildProductionCostsPanel()));
        economics.addTab("External handoffs", FarmUi.section("External handoffs",
                "Reviewable references for contract, payment, and marketplace providers.",
                buildHandoffPanel()));
        return economics;
    }

    private JComponent buildProductionCostsPanel() {
        JPanel costs = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        costs.setOpaque(false);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        productionCostYear = field("production.costYear",
                Integer.toString(Year.now().getValue()), 6);
        refreshProductionCosts = named(FarmUi.secondaryButton("Refresh costs"),
                "production.refreshCosts");
        controls.add(new JLabel("Season"));
        controls.add(productionCostYear);
        controls.add(refreshProductionCosts);
        productionCostSummary = area("production.costSummary", 4, 52);
        productionCostSummary.setEditable(false);
        productionCostModel = model("Plan", "Crop / variety", "Bed / field",
                "Harvested / expected", "Actualized tasks", "Labor", "Inputs",
                "Total", "Cost / harvested stem");
        productionCostTable = table("production.costTable", productionCostModel);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(controls, BorderLayout.NORTH);
        top.add(scroll(productionCostSummary), BorderLayout.CENTER);
        costs.add(top, BorderLayout.NORTH);
        costs.add(scroll(productionCostTable), BorderLayout.CENTER);
        return costs;
    }

    private JPanel buildHandoffPanel() {
        JPanel form = formPanel();
        handoffType = stringCombo("production.handoffType",
                "CONTRACT", "PAYMENT", "MARKETPLACE");
        handoffProvider = field("production.handoffProvider", "External provider", 14);
        handoffStatus = stringCombo("production.handoffStatus", "DRAFT", "READY", "SENT");
        handoffReference = field("production.handoffReference", "", 14);
        handoffOrder = field("production.handoffOrder", "", 8);
        handoffRecipe = field("production.handoffRecipe", "", 8);
        handoffAmount = field("production.handoffAmount", "", 8);
        handoffCurrency = field("production.handoffCurrency", "USD", 5);
        handoffSummary = area("production.handoffSummary", 3, 32);
        createHandoff = named(FarmUi.primaryButton("Record reviewed handoff"),
                "production.createHandoff");
        handoffReference.setToolTipText(
                "Required after sending, accepting, or paying; must be unique per provider and type.");
        handoffOrder.setToolTipText("Contract and payment handoffs require an existing order id.");
        handoffRecipe.setToolTipText("Optional existing design recipe id for traceability.");
        handoffAmount.setToolTipText(
                "Payment amount; leave blank to use the linked order total.");
        addRow(form, 0, "Type", handoffType, "Provider", handoffProvider);
        addRow(form, 1, "Status", handoffStatus, "External reference", handoffReference);
        addRow(form, 2, "Related order id", handoffOrder, "Related recipe id", handoffRecipe);
        addRow(form, 3, "Amount (blank=order total)", handoffAmount,
                "Currency", handoffCurrency);
        addWide(form, 4, scroll(handoffSummary));
        addWide(form, 5, createHandoff);
        handoffModel = model("Created", "Type", "Provider", "Status", "Order", "Recipe",
                "Amount", "External reference");
        handoffTable = table("production.handoffTable", handoffModel);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll(form),
                scroll(handoffTable));
        split.setResizeWeight(0.58);
        split.setDividerLocation(0.58);
        split.setBorder(BorderFactory.createEmptyBorder());
        JPanel result = new JPanel(new BorderLayout());
        result.setOpaque(false);
        result.add(split, BorderLayout.CENTER);
        return result;
    }

    private void wireActions() {
        saveFrost.addActionListener(event -> runAction("Save frost anchors", this::saveFrost));
        saveTemplate.addActionListener(event -> runAction("Save crop template", this::saveTemplate));
        loadTemplate.addActionListener(event -> runAction("Load crop template", this::loadTemplate));
        resetTemplate.addActionListener(event -> {
            clearTemplate();
            updateStatus("New growing-template form ready.", FarmUi.Tone.NEUTRAL);
        });
        deleteTemplate.addActionListener(event -> runAction("Delete crop template", this::deleteTemplate));
        draftPlantings.addActionListener(event -> runAction("Preview schedule", () -> loadDrafts(false)));
        commitPlantings.addActionListener(event -> runAction("Commit schedule", () -> loadDrafts(true)));
        refreshTasks.addActionListener(event -> runAction("Refresh work", this::loadTasks));
        markTaskDone.addActionListener(event -> runAction("Complete work", this::markTaskDone));
        skipTask.addActionListener(event -> runAction("Skip work",
                () -> updateSelectedTaskStatus(ProductionTaskStatus.SKIPPED)));
        reopenTask.addActionListener(event -> runAction("Reopen work",
                () -> updateSelectedTaskStatus(ProductionTaskStatus.TODO)));
        regenerateTasks.addActionListener(event ->
                runAction("Regenerate open work", this::regenerateSelectedTasks));
        taskStatusFilter.addActionListener(event -> renderTasks());
        saveRecipe.addActionListener(event -> runAction("Save recipe", this::saveRecipe));
        loadRecipe.addActionListener(event -> runAction("Load recipe", this::loadRecipe));
        resetRecipe.addActionListener(event -> {
            clearRecipe();
            updateStatus("New design-recipe form ready.", FarmUi.Tone.NEUTRAL);
        });
        deleteRecipe.addActionListener(event -> runAction("Delete recipe", this::deleteRecipe));
        calculate.addActionListener(event -> runAction("Calculate recipe", this::calculate));
        refreshAvailability.addActionListener(event ->
                runAction("Refresh availability", this::loadAvailability));
        refreshProductionCosts.addActionListener(event ->
                runAction("Refresh production costs", this::loadProductionCosts));
        createHandoff.addActionListener(event -> runAction("Create handoff", this::createHandoff));
        handoffType.addActionListener(event -> configureHandoffStatuses());
        seasonYear.addActionListener(event -> runAction("Load frost anchors", this::loadFrost));
        templateTable.addMouseListener(loadOnDoubleClick("Load crop template", this::loadTemplate));
        recipeTable.addMouseListener(loadOnDoubleClick("Load recipe", this::loadRecipe));
        templateTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateTemplateActionState();
            }
        });
        recipeTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateRecipeActionState();
            }
        });
        taskTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedTaskActuals();
                updateTaskActionState();
            }
        });
        plantingTemplate.addActionListener(event -> {
            invalidatePlantingPreview();
            updatePlantingActionState();
        });
        for (JTextField input : List.of(
                plantingYear, plantingBed, plantingFeet, plantingSuccessions)) {
            watchPlantingInput(input);
        }
        configureHandoffStatuses();
    }

    private void saveFrost() {
        int year = integer(seasonYear);
        service.saveSeasonSettings(new FarmSeasonSettings(year,
                date(springFrost), date(fallFrost), frostSource.getText(),
                decimal(loadedLaborRate)));
        plantingYear.setText(Integer.toString(year));
        productionCostYear.setText(Integer.toString(year));
        loadProductionCosts();
        updateStatus("Saved frost anchors and loaded labor rate for " + year
                        + "; selected that season in planning and cost reporting.",
                FarmUi.Tone.SUCCESS);
    }

    private void loadFrost() {
        int year = integer(seasonYear);
        service.findSeasonSettings(year).ifPresentOrElse(settings -> {
            springFrost.setText(settings.getLastSpringFrost().toString());
            fallFrost.setText(settings.getFirstFallFrost().toString());
            frostSource.setText(settings.getSource());
            loadedLaborRate.setText(String.format(Locale.ROOT, "%.2f",
                    settings.getLoadedLaborRatePerHour()));
        }, () -> {
            springFrost.setText(year + "-04-15");
            fallFrost.setText(year + "-10-15");
            frostSource.setText("Farm estimate");
            loadedLaborRate.setText("25.00");
            updateStatus("No saved frost anchors for " + year
                    + "; review the displayed defaults before saving.", FarmUi.Tone.WARNING);
        });
    }

    private void saveTemplate() {
        boolean updating = editingTemplateId != null;
        Integer transplant = transplantOffset.getText().isBlank()
                ? null : integer(transplantOffset);
        GrowingTemplate template = new GrowingTemplate(templateName.getText(), cropName.getText(),
                variety.getText(), decimal(spacing), integer(rows), integer(sowOffset), transplant,
                integer(daysToHarvest), integer(harvestWindow), integer(successionDays),
                decimal(stemsPerPlant), decimal(germination), integer(soilLead),
                integer(supportOffset), true, templateNotes.getText());
        if (editingTemplateId == null) {
            service.createTemplate(template);
        } else {
            service.updateTemplate(editingTemplateId, template);
        }
        clearTemplate();
        loadTemplatesAndRecipes();
        updateStatus(updating ? "Growing template updated." : "Growing template saved.",
                FarmUi.Tone.SUCCESS);
    }

    private void loadTemplate() {
        GrowingTemplate template = selectedTemplateRow();
        editingTemplateId = template.getId();
        templateName.setText(template.getName());
        cropName.setText(template.getCropName());
        variety.setText(template.getVariety());
        spacing.setText(Double.toString(template.getSpacingInches()));
        rows.setText(Integer.toString(template.getRowsPerBed()));
        sowOffset.setText(Integer.toString(template.getSowDaysFromFrost()));
        transplantOffset.setText(template.getTransplantDaysFromFrost() == null ? ""
                : template.getTransplantDaysFromFrost().toString());
        daysToHarvest.setText(Integer.toString(template.getDaysToHarvest()));
        harvestWindow.setText(Integer.toString(template.getHarvestWindowDays()));
        successionDays.setText(Integer.toString(template.getSuccessionDays()));
        stemsPerPlant.setText(Double.toString(template.getStemsPerPlant()));
        germination.setText(Double.toString(template.getGerminationPercent()));
        soilLead.setText(Integer.toString(template.getSoilPrepLeadDays()));
        supportOffset.setText(Integer.toString(template.getSupportDaysAfterPlanting()));
        templateNotes.setText(template.getNotes());
        saveTemplate.setText("Update template");
        updateStatus("Editing template “" + template.getName() + "”.",
                FarmUi.Tone.INFO);
    }

    private void clearTemplate() {
        editingTemplateId = null;
        templateTable.clearSelection();
        for (JTextField field : List.of(templateName, cropName, variety)) {
            field.setText("");
        }
        spacing.setText("9");
        rows.setText("4");
        sowOffset.setText("-42");
        transplantOffset.setText("7");
        daysToHarvest.setText("90");
        harvestWindow.setText("21");
        successionDays.setText("14");
        stemsPerPlant.setText("3");
        germination.setText("85");
        soilLead.setText("14");
        supportOffset.setText("10");
        templateNotes.setText("");
        saveTemplate.setText("Save template");
    }

    private void deleteTemplate() {
        GrowingTemplate template = selectedTemplateRow();
        int choice = dialogs.showConfirm(panel,
                "Delete crop template ‘" + template.getName()
                        + "’? Only unused templates can be deleted; generated work protects "
                        + "the assumptions that created it.",
                "Delete crop template", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            service.deleteTemplate(template.getId());
            clearTemplate();
            loadTemplatesAndRecipes();
        }
    }

    private GrowingTemplate selectedTemplateRow() {
        int row = templateTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a growing template first.");
        }
        return templates.get(templateTable.convertRowIndexToModel(row));
    }

    private void loadTemplatesAndRecipes() {
        Long selectedTemplateId = selectedId(plantingTemplate, GrowingTemplate::getId);
        Long selectedRecipeId = selectedId(estimateRecipe, FloralRecipe::getId);
        templates = safe(service.listTemplates());
        templateModel.setRowCount(0);
        for (GrowingTemplate template : templates) {
            templateModel.addRow(new Object[]{template.getId(), template.getName(),
                    template.getCropName() + varietySuffix(template.getVariety()),
                    template.getSpacingInches() + " in × " + template.getRowsPerBed(),
                    template.getSowDaysFromFrost() + " / "
                            + value(template.getTransplantDaysFromFrost()),
                    template.getDaysToHarvest() + "d + " + template.getHarvestWindowDays() + "d",
                    template.getStemsPerPlant() + " stems @ "
                            + template.getGerminationPercent() + "%"});
        }
        setObjects(plantingTemplate, templates, GrowingTemplate::getName);
        selectId(plantingTemplate, selectedTemplateId, GrowingTemplate::getId);
        recipes = safe(service.listRecipes());
        recipeModel.setRowCount(0);
        for (FloralRecipe recipe : recipes) {
            recipeModel.addRow(new Object[]{recipe.getId(), recipe.getName(), recipe.getDesignType(),
                    money(recipe.getSalePrice()), recipe.getLaborMinutes() + " min @ "
                            + money(recipe.getLaborRatePerHour()), recipe.getWastePercent() + "%",
                    recipe.getIngredients().size()});
        }
        setObjects(estimateRecipe, recipes, FloralRecipe::getName);
        selectId(estimateRecipe, selectedRecipeId, FloralRecipe::getId);
        updateTemplateActionState();
        updatePlantingActionState();
        updateRecipeActionState();
    }

    private void loadDrafts(boolean commit) {
        GrowingTemplate template = selected(plantingTemplate, "Select a growing template.");
        int year = integer(plantingYear);
        int successions = integer(plantingSuccessions);
        List<ProductionStudioService.PlantingDraft> drafts = service.draftPlantings(
                template.getId(), year, plantingBed.getText(), decimal(plantingFeet), successions);
        String previewFingerprint = service.plantingFingerprint(drafts);
        draftModel.setRowCount(0);
        for (ProductionStudioService.PlantingDraft draft : drafts) {
            draftModel.addRow(new Object[]{draft.successionNumber(), draft.sowDate(),
                    value(draft.transplantDate()), draft.harvestStart() + "–" + draft.harvestEnd(),
                    draft.plantSites(), draft.viablePlants(), draft.expectedStems(),
                    draft.fallFrostRisk() ? "Review" : "Inside window"});
        }
        if (commit && canWrite) {
            int stems = drafts.stream().mapToInt(
                    ProductionStudioService.PlantingDraft::expectedStems).sum();
            long frostRisks = drafts.stream().filter(
                    ProductionStudioService.PlantingDraft::fallFrostRisk).count();
            FarmUi.Tone tone = frostRisks == 0 ? FarmUi.Tone.INFO : FarmUi.Tone.WARNING;
            JPanel confirmation = FarmUi.confirmationPanel(
                    "Commit " + drafts.size() + " planting block(s)",
                    "This creates " + drafts.size() + " season-plan block(s) and their dependent "
                            + "work for " + plantingBed.getText().trim() + ". Expected stems: "
                            + stems + ". Frost warnings: " + frostRisks
                            + ". Exact duplicates are blocked; changed inputs create a separate "
                            + "set of blocks.", tone);
            int choice = dialogs.showConfirm(panel, confirmation, "Commit production plan",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                updateStatus("Plan preview kept; no planting blocks were committed.",
                        FarmUi.Tone.NEUTRAL);
                return;
            }
            List<ProductionStudioService.CommittedPlanting> committed =
                    service.commitPlantings(template.getId(), year, plantingBed.getText(),
                            decimal(plantingFeet), successions, previewFingerprint);
            loadTasks();
            int generatedTasks = committed.stream().mapToInt(row -> row.tasks().size()).sum();
            updateStatus("Committed " + committed.size() + " planting block(s) and "
                    + generatedTasks + " production task(s).", FarmUi.Tone.SUCCESS);
        } else {
            long frostRisks = drafts.stream().filter(
                    ProductionStudioService.PlantingDraft::fallFrostRisk).count();
            updateStatus("Previewed " + drafts.size() + " planting block(s); " + frostRisks
                    + " frost warning(s).", frostRisks == 0
                    ? FarmUi.Tone.INFO : FarmUi.Tone.WARNING);
        }
    }

    private void loadTasks() {
        tasks = safe(service.listTasks(date(taskFrom), date(taskTo)));
        renderTasks();
    }

    private void renderTasks() {
        if (taskModel == null || taskStatusFilter == null) {
            return;
        }
        String filter = selectedString(taskStatusFilter);
        visibleTasks = tasks.stream().filter(task -> switch (filter) {
            case "Open" -> task.getStatus() == ProductionTaskStatus.TODO;
            case "Done" -> task.getStatus() == ProductionTaskStatus.DONE;
            case "Skipped" -> task.getStatus() == ProductionTaskStatus.SKIPPED;
            default -> true;
        }).toList();
        taskModel.setRowCount(0);
        for (ProductionTask task : visibleTasks) {
            boolean hasActuals = task.hasActuals();
            taskModel.addRow(new Object[]{task.getId(), task.getDueDate(), taskTiming(task),
                    task.getStatus(), task.getTaskType(), task.getCropName(),
                    task.getBedOrField(), !hasActuals || task.getActualMinutes() == null
                            ? "" : task.getActualMinutes() + " min",
                    hasActuals ? money(task.getInputCost()) : "",
                    hasActuals ? task.getFieldObservation() : "",
                    task.getSeasonPlanId()});
        }
        loadSelectedTaskActuals();
        updateTaskActionState();
    }

    private void markTaskDone() {
        ProductionTask task = selectedTask();
        if (task == null) {
            throw new IllegalArgumentException("Select a production task first.");
        }
        service.recordTaskActuals(task.getId(),
                new ProductionStudioService.TaskActuals(
                        integer(taskActualMinutes), decimal(taskInputCost),
                        taskFieldObservation.getText()));
        loadTasks();
        updateStatus("Task “" + task.getTaskType()
                + "” completed with labor, input cost, and field result saved.",
                FarmUi.Tone.SUCCESS);
    }

    private void loadSelectedTaskActuals() {
        if (taskActualMinutes == null) {
            return;
        }
        ProductionTask task = selectedTask();
        taskActualMinutes.setText(task == null || task.getActualMinutes() == null
                ? "0" : task.getActualMinutes().toString());
        taskInputCost.setText(task == null
                ? "0.00" : String.format(Locale.ROOT, "%.2f", task.getInputCost()));
        taskFieldObservation.setText(task == null || task.getFieldObservation() == null
                ? "" : task.getFieldObservation());
    }

    private void updateSelectedTaskStatus(ProductionTaskStatus status) {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a production task first.");
        }
        ProductionTask task = visibleTasks.get(taskTable.convertRowIndexToModel(row));
        service.updateTaskStatus(task.getId(), status);
        loadTasks();
        updateStatus("Task “" + task.getTaskType() + "” is now "
                + status.toString().toLowerCase(Locale.ROOT) + ".", FarmUi.Tone.SUCCESS);
    }

    private void regenerateSelectedTasks() {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a generated production task first.");
        }
        ProductionTask task = visibleTasks.get(taskTable.convertRowIndexToModel(row));
        if (task.getSeasonPlanId() == null || task.getGrowingTemplateId() == null) {
            throw new IllegalArgumentException(
                    "Only plan-linked generated work can be regenerated.");
        }
        JPanel confirmation = FarmUi.confirmationPanel(
                "Regenerate open work for plan #" + task.getSeasonPlanId(),
                "Open generated tasks will be rebuilt from the linked crop template. "
                        + "Completed, skipped, and custom tasks remain unchanged.",
                FarmUi.Tone.WARNING);
        int choice = dialogs.showConfirm(panel, confirmation, "Regenerate production work",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            updateStatus("Regeneration cancelled; production work was not changed.",
                    FarmUi.Tone.NEUTRAL);
            return;
        }
        service.regenerateTasks(task.getSeasonPlanId(), task.getGrowingTemplateId());
        loadTasks();
        updateStatus("Open generated work was rebuilt; completed and skipped work was preserved.",
                FarmUi.Tone.SUCCESS);
    }

    private void saveRecipe() {
        boolean updating = editingRecipeId != null;
        FloralRecipe recipe = new FloralRecipe(recipeName.getText(), recipeType.getText(),
                decimal(salePrice), integer(laborMinutes), decimal(laborRate),
                decimal(overheadPercent), decimal(wastePercent), recipeNotes.getText());
        String[] lines = ingredientLines.getText().split("\\R");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException(
                        "Ingredient line " + (lineIndex + 1)
                                + " needs Item | stems/design | pack size | pack cost.");
            }
            try {
                recipe.addIngredient(new FloralRecipeIngredient(parts[0], number(parts[1]),
                        whole(parts[2]), number(parts[3]), ""));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Ingredient line " + (lineIndex + 1) + ": "
                                + cleanMessage(exception), exception);
            }
        }
        if (editingRecipeId == null) {
            service.createRecipe(recipe);
        } else {
            service.updateRecipe(editingRecipeId, recipe);
        }
        clearRecipe();
        loadTemplatesAndRecipes();
        updateStatus(updating ? "Design recipe updated." : "Design recipe saved.",
                FarmUi.Tone.SUCCESS);
    }

    private void loadRecipe() {
        FloralRecipe recipe = selectedRecipeRow();
        editingRecipeId = recipe.getId();
        recipeName.setText(recipe.getName());
        recipeType.setText(recipe.getDesignType());
        salePrice.setText(Double.toString(recipe.getSalePrice()));
        laborMinutes.setText(Integer.toString(recipe.getLaborMinutes()));
        laborRate.setText(Double.toString(recipe.getLaborRatePerHour()));
        overheadPercent.setText(Double.toString(recipe.getOverheadPercent()));
        wastePercent.setText(Double.toString(recipe.getWastePercent()));
        ingredientLines.setText(recipe.getIngredients().stream()
                .map(ingredient -> ingredient.getItemName() + " | "
                        + ingredient.getStemsPerDesign() + " | "
                        + ingredient.getPackSize() + " | " + ingredient.getPackCost())
                .collect(java.util.stream.Collectors.joining("\n")));
        recipeNotes.setText(recipe.getNotes());
        saveRecipe.setText("Update recipe");
        updateStatus("Editing recipe “" + recipe.getName() + "”.", FarmUi.Tone.INFO);
    }

    private void clearRecipe() {
        editingRecipeId = null;
        recipeTable.clearSelection();
        recipeName.setText("");
        recipeType.setText("Bouquet");
        salePrice.setText("35");
        laborMinutes.setText("12");
        laborRate.setText("25");
        overheadPercent.setText("15");
        wastePercent.setText("10");
        ingredientLines.setText("");
        recipeNotes.setText("");
        saveRecipe.setText("Save recipe");
    }

    private void deleteRecipe() {
        FloralRecipe recipe = selectedRecipeRow();
        int choice = dialogs.showConfirm(panel,
                "Delete recipe ‘" + recipe.getName()
                        + "’? Existing handoff records remain, but their recipe link clears.",
                "Delete recipe", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            service.deleteRecipe(recipe.getId());
            clearRecipe();
            loadTemplatesAndRecipes();
        }
    }

    private FloralRecipe selectedRecipeRow() {
        int row = recipeTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a floral recipe first.");
        }
        return recipes.get(recipeTable.convertRowIndexToModel(row));
    }

    private void calculate() {
        FloralRecipe recipe = selected(estimateRecipe, "Select a recipe.");
        ProductionStudioService.RecipeEstimate result = service.estimateRecipe(
                recipe.getId(), integer(designCount), date(eventDate));
        StringBuilder text = new StringBuilder();
        text.append(result.recipeName()).append(" × ").append(result.designCount()).append('\n');
        for (ProductionStudioService.IngredientEstimate ingredient : result.ingredients()) {
            text.append("• ").append(ingredient.itemName()).append(": ")
                    .append(ingredient.requiredWithWaste()).append(" needed; ")
                    .append(ingredient.packsToBuy()).append(" pack(s); ")
                    .append(ingredient.packLeftovers()).append(" leftover; live ")
                    .append(ingredient.liveInventory()).append(" + planned ")
                    .append(ingredient.plannedThisWeek()).append("; shortage ")
                    .append(ingredient.remainingShortage()).append('\n');
        }
        text.append("Flowers ").append(money(result.flowerCost()))
                .append(" · Labor ").append(money(result.laborCost()))
                .append(" · Overhead ").append(money(result.overheadCost())).append('\n')
                .append("Cost ").append(money(result.totalCost()))
                .append(" · Revenue ").append(money(result.revenue()))
                .append(" · Profit ").append(money(result.profit())).append('\n')
                .append("Per design: cost ")
                .append(money(result.totalCost() / result.designCount()))
                .append(" · profit ").append(money(result.profit() / result.designCount()))
                .append(" · break-even price ")
                .append(money(result.totalCost() / result.designCount())).append('\n')
                .append(String.format(Locale.ROOT, "Margin %.1f%% · Markup %.1f%%",
                        result.marginPercent(), result.markupPercent()));
        estimateOutput.setText(text.toString());
        estimateOutput.setCaretPosition(0);
        updateStatus(result.profit() < 0
                        ? "Recipe estimate is below break-even; review price, recipe, or sourcing."
                        : "Recipe estimate is profitable at the current assumptions.",
                result.profit() < 0 ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS);
    }

    private void loadAvailability() {
        List<ProductionStudioService.AvailabilityLine> rows =
                safe(service.availability(date(availabilityWeek)));
        availabilityModel.setRowCount(0);
        for (ProductionStudioService.AvailabilityLine row : rows) {
            availabilityModel.addRow(new Object[]{row.cropName(),
                    row.weekStart() + "–" + row.weekEnd(), row.plannedStems(),
                    row.liveInventory(), row.totalAvailable(), row.harvestWindows()});
        }
    }

    private void loadProductionCosts() {
        if (productionCostModel == null) {
            return;
        }
        Optional<ProductionStudioService.ProductionCostReport> loaded =
                Optional.ofNullable(service.productionCosts(integer(productionCostYear), null));
        productionCostModel.setRowCount(0);
        if (loaded.isEmpty()) {
            productionCostSummary.setText("");
            return;
        }
        ProductionStudioService.ProductionCostReport report = loaded.orElseThrow();
        for (ProductionStudioService.PlantingCostLine row : report.plantings()) {
            productionCostModel.addRow(new Object[]{row.seasonPlanId(),
                    row.cropName() + varietySuffix(row.variety()), row.bedOrField(),
                    row.harvestedStems() + " / " + row.expectedStems(),
                    row.actualizedTaskCount() + " / " + row.taskCount(),
                    money(row.laborCost()), money(row.inputCost()),
                    money(row.totalProductionCost()),
                    row.costPerHarvestedStem() == null
                            ? "—" : money(row.costPerHarvestedStem())});
        }
        String warnings = report.warnings().isEmpty()
                ? "No allocation warnings."
                : String.join(" ", report.warnings());
        productionCostSummary.setText("Season " + report.seasonYear() + " · labor rate "
                + money(report.loadedLaborRatePerHour()) + "/hour ("
                + report.laborRateSource().toLowerCase(Locale.ROOT).replace('_', ' ') + ")\n"
                + "Actuals " + report.actualizedTaskCount() + "/" + report.taskCount()
                + " tasks · " + report.actualMinutes() + " crew min · labor "
                + money(report.laborCost()) + " · inputs " + money(report.inputCost())
                + " · total " + money(report.totalProductionCost()) + "\n"
                + "Matched harvest " + report.harvestedStems() + "/"
                + report.expectedStems() + " expected stems · allocated cost/harvested stem "
                + (report.costPerHarvestedStem() == null
                ? "—" : money(report.costPerHarvestedStem())) + "\n" + warnings);
        productionCostSummary.setCaretPosition(0);
    }

    private void createHandoff() {
        ProductionStudioService.HandoffRequest request =
                new ProductionStudioService.HandoffRequest(
                        selectedString(handoffType), handoffProvider.getText(),
                        selectedString(handoffStatus), handoffReference.getText(),
                        optionalLong(handoffOrder), optionalLong(handoffRecipe),
                        optionalDouble(handoffAmount), handoffCurrency.getText(),
                        handoffSummary.getText());
        service.createHandoff(request);
        handoffReference.setText("");
        handoffOrder.setText("");
        handoffRecipe.setText("");
        handoffAmount.setText("");
        handoffSummary.setText("");
        handoffStatus.setSelectedItem("DRAFT");
        loadHandoffs();
        updateStatus("Reviewed " + request.handoffType().toLowerCase(Locale.ROOT)
                + " handoff recorded; no external transaction was performed.",
                FarmUi.Tone.SUCCESS);
    }

    private void loadHandoffs() {
        List<IntegrationHandoff> rows = safe(service.listHandoffs());
        handoffModel.setRowCount(0);
        for (IntegrationHandoff row : rows) {
            handoffModel.addRow(new Object[]{row.getCreatedAt(), row.getHandoffType(),
                    row.getProvider(), row.getStatus(), row.getRelatedOrderId(),
                    row.getRelatedRecipeId(), money(row.getAmount()), row.getExternalReference()});
        }
    }

    private void configureHandoffStatuses() {
        if (handoffType == null || handoffStatus == null) {
            return;
        }
        String selected = selectedString(handoffStatus);
        List<String> statuses = switch (selectedString(handoffType)) {
            case "CONTRACT" -> List.of(
                    "DRAFT", "READY", "SENT", "ACCEPTED", "FAILED", "CANCELLED");
            case "PAYMENT" -> List.of(
                    "DRAFT", "READY", "SENT", "PAID", "FAILED", "CANCELLED");
            default -> List.of("DRAFT", "READY", "SENT", "FAILED", "CANCELLED");
        };
        handoffStatus.removeAllItems();
        statuses.forEach(handoffStatus::addItem);
        handoffStatus.setSelectedItem(statuses.contains(selected) ? selected : "DRAFT");
    }

    private void updateTemplateActionState() {
        if (templateTable == null) {
            return;
        }
        boolean selected = templateTable.getSelectedRow() >= 0;
        loadTemplate.setEnabled(selected);
        deleteTemplate.setEnabled(canWrite && selected);
    }

    private void updatePlantingActionState() {
        if (plantingTemplate == null) {
            return;
        }
        boolean hasTemplate = plantingTemplate.getSelectedItem() != null;
        draftPlantings.setEnabled(hasTemplate);
        commitPlantings.setEnabled(canWrite && hasTemplate);
    }

    private void updateRecipeActionState() {
        if (recipeTable == null) {
            return;
        }
        boolean selected = recipeTable.getSelectedRow() >= 0;
        loadRecipe.setEnabled(selected);
        deleteRecipe.setEnabled(canWrite && selected);
    }

    private void updateTaskActionState() {
        if (taskTable == null) {
            return;
        }
        ProductionTask task = selectedTask();
        boolean open = task != null && task.getStatus() == ProductionTaskStatus.TODO;
        boolean finalState = task != null && task.getStatus() != ProductionTaskStatus.TODO;
        boolean actualsEditable = canWrite && task != null
                && task.getStatus() != ProductionTaskStatus.SKIPPED;
        markTaskDone.setEnabled(actualsEditable);
        skipTask.setEnabled(canWrite && open);
        reopenTask.setEnabled(canWrite && finalState);
        regenerateTasks.setEnabled(canWrite && task != null
                && task.getSeasonPlanId() != null && task.getGrowingTemplateId() != null);
        taskActualMinutes.setEditable(actualsEditable);
        taskInputCost.setEditable(actualsEditable);
        taskFieldObservation.setEditable(actualsEditable);
    }

    private ProductionTask selectedTask() {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = taskTable.convertRowIndexToModel(row);
        return modelRow < visibleTasks.size() ? visibleTasks.get(modelRow) : null;
    }

    private void watchPlantingInput(JTextField input) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                invalidatePlantingPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                invalidatePlantingPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                invalidatePlantingPreview();
            }
        });
    }

    private void invalidatePlantingPreview() {
        if (draftModel != null && draftModel.getRowCount() > 0) {
            draftModel.setRowCount(0);
            updateStatus("Planting inputs changed; preview the schedule again before committing.",
                    FarmUi.Tone.INFO);
        }
    }

    private void runAction(String title, Runnable action) {
        int revisionBefore = statusRevision;
        try {
            action.run();
            if (revisionBefore == statusRevision) {
                updateStatus(title + " complete.", FarmUi.Tone.SUCCESS);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            updateStatus(exception.getMessage(), FarmUi.Tone.WARNING);
            dialogs.showMessage(panel, exception.getMessage(), title, JOptionPane.WARNING_MESSAGE);
        } catch (ProductionConflictException exception) {
            updateStatus(exception.getMessage(), FarmUi.Tone.WARNING);
            dialogs.showMessage(panel, exception.getMessage(), title, JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException exception) {
            updateStatus("Could not complete " + title.toLowerCase(Locale.ROOT) + ": "
                    + cleanMessage(exception), FarmUi.Tone.DANGER);
            dialogs.showMessage(panel, "The operation could not be completed. "
                    + cleanMessage(exception), title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatus(String message, FarmUi.Tone tone) {
        statusRevision++;
        FarmUi.updateStatusBadge(studioStatus, message, tone);
    }

    private static String cleanMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private MouseAdapter loadOnDoubleClick(String title, Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    runAction(title, action);
                }
            }
        };
    }

    private static String taskTiming(ProductionTask task) {
        if (task.getStatus() == ProductionTaskStatus.DONE) {
            return "Complete";
        }
        if (task.getStatus() == ProductionTaskStatus.SKIPPED) {
            return "Skipped";
        }
        LocalDate today = LocalDate.now();
        if (task.getDueDate().isBefore(today)) {
            return "Overdue";
        }
        if (task.getDueDate().equals(today)) {
            return "Today";
        }
        if (!task.getDueDate().isAfter(today.plusDays(7))) {
            return "Next 7 days";
        }
        return "Upcoming";
    }

    private static JPanel formPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private static void addRow(JPanel panel, int row, String firstLabel, Component first,
                               String secondLabel, Component second) {
        GridBagConstraints constraints = constraints(row);
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(firstLabel), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(first, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(new JLabel(secondLabel), constraints);
        constraints.gridx = 3;
        constraints.weightx = 1;
        panel.add(second, constraints);
    }

    private static void addWide(JPanel panel, int row, Component component) {
        GridBagConstraints constraints = constraints(row);
        constraints.gridx = 0;
        constraints.gridwidth = 4;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, constraints);
    }

    private static GridBagConstraints constraints(int row) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridy = row;
        result.insets = new Insets(4, 5, 4, 5);
        result.anchor = GridBagConstraints.WEST;
        result.fill = GridBagConstraints.HORIZONTAL;
        return result;
    }

    private static JTextField field(String name, String value, int columns) {
        JTextField field = new JTextField(value, columns);
        field.setName(name);
        field.getAccessibleContext().setAccessibleName(name);
        return field;
    }

    private static JTextArea area(String name, int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setName(name);
        area.getAccessibleContext().setAccessibleName(name);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static <T> JComboBox<T> combo(String name) {
        JComboBox<T> combo = new JComboBox<>();
        combo.setName(name);
        combo.getAccessibleContext().setAccessibleName(name);
        return combo;
    }

    private static JComboBox<String> stringCombo(String name, String... values) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setName(name);
        combo.getAccessibleContext().setAccessibleName(name);
        return combo;
    }

    private static JButton named(JButton button, String name) {
        button.setName(name);
        return button;
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable table(String name, DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setName(name);
        table.getAccessibleContext().setAccessibleName(name);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static JScrollPane scroll(Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private static JPanel buttonRow(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static int integer(JTextField field) {
        return whole(field.getText());
    }

    private static int whole(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number in " + value + ".");
        }
    }

    private static double decimal(JTextField field) {
        return number(field.getText());
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a number in " + value + ".");
        }
    }

    private static LocalDate date(JTextField field) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Use YYYY-MM-DD for " + field.getText() + ".");
        }
    }

    private static Long optionalLong(JTextField field) {
        return field.getText().isBlank() ? null : (long) whole(field.getText());
    }

    private static Double optionalDouble(JTextField field) {
        return field.getText().isBlank() ? null : number(field.getText());
    }

    private static String selectedString(JComboBox<String> combo) {
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private static <T> T selected(JComboBox<T> combo, String message) {
        @SuppressWarnings("unchecked")
        T selected = (T) combo.getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException(message);
        }
        return selected;
    }

    private static <T> void setObjects(JComboBox<T> combo, List<T> values,
                                       java.util.function.Function<T, String> label) {
        combo.removeAllItems();
        values.forEach(combo::addItem);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                @SuppressWarnings("unchecked")
                T item = (T) value;
                setText(item == null ? "Select…" : label.apply(item));
                return this;
            }
        });
    }

    private static <T> Long selectedId(JComboBox<T> combo, Function<T, Long> id) {
        if (combo == null || combo.getSelectedItem() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T selected = (T) combo.getSelectedItem();
        return id.apply(selected);
    }

    private static <T> void selectId(JComboBox<T> combo, Long selectedId,
                                     Function<T, Long> id) {
        if (selectedId == null) {
            return;
        }
        for (int index = 0; index < combo.getItemCount(); index++) {
            T item = combo.getItemAt(index);
            if (selectedId.equals(id.apply(item))) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private static String varietySuffix(String value) {
        return value == null || value.isBlank() ? "" : " · " + value;
    }

    private static String value(Object value) {
        return value == null ? "—" : value.toString();
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "$%,.2f", value);
    }
}

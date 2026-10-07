package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.reference.FieldScienceCatalog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Profile-specific starting point that links only to installed workflows. */
public final class WorkspaceHomeTab implements FlowerFarmTab {

    private final FarmWorkspace workspace;
    private final Set<FarmWorkspace> enabledWorkspaces;
    private final List<String> destinationTitles;
    private final TabHost host;
    private String customFocus;
    private JPanel panel;
    private JTextArea scopeNote;

    public WorkspaceHomeTab(FarmWorkspace workspace, List<String> destinationTitles,
                            TabHost host) {
        this(workspace, destinationTitles, host, "");
    }

    public WorkspaceHomeTab(FarmWorkspace workspace, List<String> destinationTitles,
                            TabHost host, String customFocus) {
        this(workspace, destinationTitles, host, customFocus, Set.of(workspace));
    }

    public WorkspaceHomeTab(FarmWorkspace workspace, List<String> destinationTitles,
                            TabHost host, Set<FarmWorkspace> enabledWorkspaces) {
        this(workspace, destinationTitles, host, "", enabledWorkspaces);
    }

    public WorkspaceHomeTab(FarmWorkspace workspace, List<String> destinationTitles,
                            TabHost host, String customFocus,
                            Set<FarmWorkspace> enabledWorkspaces) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.enabledWorkspaces = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(enabledWorkspaces, "enabledWorkspaces")));
        if (!this.enabledWorkspaces.contains(workspace)) {
            throw new IllegalArgumentException("The primary farm type must be selected.");
        }
        Objects.requireNonNull(destinationTitles, "destinationTitles");
        this.host = Objects.requireNonNull(host, "host");
        this.customFocus = Objects.requireNonNull(customFocus, "customFocus").trim();
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (String title : destinationTitles) {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Workspace destinations need a tab title.");
            }
            if (!"Workspace".equals(title)) {
                titles.add(title);
            }
        }
        if (FieldScienceCatalog.hasTools(this.enabledWorkspaces)) {
            titles.add("Field science");
        }
        this.destinationTitles = List.copyOf(titles);
    }

    /** Reflect a saved custom-farm specialty edit without rebuilding open drafts. */
    public void setCustomFocus(String customFocus) {
        if (!enabledWorkspaces.contains(FarmWorkspace.CUSTOM_FARM)) {
            return;
        }
        this.customFocus = Objects.requireNonNull(customFocus, "customFocus").trim();
        if (scopeNote != null) {
            String detail = copy().limit();
            scopeNote.setText(detail);
            scopeNote.setRows(noteRows(detail));
            scopeNote.revalidate();
        }
    }

    @Override
    public String getTabTitle() {
        return "Workspace";
    }

    @Override
    public String getDescription() {
        return copy().heading() + " and available workflows";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    private void buildUi() {
        Copy copy = copy();
        panel = FarmUi.page();
        panel.setName("workspace.page");
        panel.getAccessibleContext().setAccessibleName(copy.heading() + " workspace");
        panel.add(FarmUi.pageHeader(copy.heading(), copy.scope(), null), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.NORTH;
        row.insets = new Insets(0, 0, 16, 0);
        scopeNote = note(copy.limit());
        body.add(FarmUi.section("How to use this workspace", copy.summary(),
                scopeNote), row);
        row.gridy = 1;
        row.insets = new Insets(0, 0, 0, 0);
        body.add(FarmUi.section("Open a workflow",
                "These links match the tools shown in this workspace.",
                destinations()), row);
        row.gridy = 2;
        row.weighty = 1;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        body.add(spacer, row);

        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("workspace.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        panel.add(scroll, BorderLayout.CENTER);
    }

    private JComponent destinations() {
        if (destinationTitles.isEmpty()) {
            JLabel empty = FarmUi.mutedLabel("No workflows are available for this workspace yet.");
            empty.setName("workspace.empty");
            return empty;
        }
        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        grid.setName("workspace.destinations");
        for (String title : destinationTitles) {
            JButton button = FarmUi.secondaryButton(title);
            button.setName("workspace.open." + title.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-"));
            button.getAccessibleContext().setAccessibleName("Open " + title);
            button.getAccessibleContext().setAccessibleDescription(
                    "Open the " + title + " workflow in this workspace.");
            button.addActionListener(event -> host.selectTab(title));
            if (grid.getComponentCount() == 0) {
                FarmUi.initialFocus(button);
            }
            grid.add(button);
        }
        return grid;
    }

    private static JTextArea note(String text) {
        JTextArea note = new JTextArea(text, noteRows(text), 56);
        note.setName("workspace.scopeNote");
        note.setEditable(false);
        note.setFocusable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        note.getAccessibleContext().setAccessibleName("Workspace scope note");
        return note;
    }

    private static int noteRows(String text) {
        return Math.max(2, (text.length() + 89) / 90);
    }

    private Copy copy() {
        if (enabledWorkspaces.size() > 1) {
            String chosen = enabledWorkspaces.stream().map(WorkspaceHomeTab::workspaceName)
                    .collect(java.util.stream.Collectors.joining(" · "));
            String limits = "Named selected types have their own specialty tools. "
                    + (destinationTitles.contains("Harvest Log")
                            ? "Record harvests in the shared Harvest Log. " : "")
                    + "Use Inventory and Customers & Orders as shared tools; "
                    + "specialty plans and collection logs do not create stock "
                    + "or orders automatically.";
            if (enabledWorkspaces.contains(FarmWorkspace.CANNABIS_FARM)) {
                limits += " Cannabis compliance reporting is not included.";
            }
            if (enabledWorkspaces.contains(FarmWorkspace.CUSTOM_FARM)) {
                limits += " Custom farm uses these shared records for other lines.";
                if (!customFocus.isBlank()) {
                    limits += " Farm setup specialties: " + customFocus + ".";
                }
                limits += " It adds no separate production plan for those lines.";
            }
            return new Copy("Your combined farm", chosen,
                    "Start with any selected type, then use the shared records for the whole operation. "
                            + "Main farm type: " + workspaceName(workspace) + ".",
                    limits);
        }
        return switch (workspace) {
            case ROSE_AND_EVENTS -> new Copy(
                    "Rose & event farm",
                    "Grow roses, sell stems and bouquets, and plan customer events.",
                    "Keep growing, stock, and order records together.",
                    "Event briefs and floral design records are available through the flower tools.");
            case WILDFLOWER_FARM -> new Copy(
                    "Wildflower farm",
                    "Grow cut wildflowers and mixed bunches for local buyers.",
                    "Track field harvests, available stock, and customer orders.",
                    "Use your own species and product names; this workspace does not assume roses.");
            case FLOWER_SHOP -> new Copy(
                    "Flower shop",
                    "Manage flower products, customer orders, and design work.",
                    "Start with stock and orders, then open the other tools you use.",
                    "Growing and field harvest tools are separate from this shop workspace.");
            case CANNABIS_FARM -> new Copy(
                    "Cannabis farm",
                    "Plan cultivation batches and record inventory and customer orders.",
                    "Start with a cultivation area and batch, then use stock and sales records.",
                    "This planning view does not provide regulated tracking or state reporting.");
            case POTATO_FARM -> new Copy(
                    "Potato farm",
                    "Plan fields and varieties for the season.",
                    "Start with a field plan, then use general harvest, stock, and order records.",
                    "Field plans do not grade potatoes, manage bulk storage, or create packout lots.");
            case APPLE_ORCHARD -> new Copy(
                    "Apple orchard",
                    "Plan orchard blocks and target harvests.",
                    "Start with a block, then use general harvest, stock, and order records.",
                    "Block plans do not track bins, cold storage, grading, or packout.");
            case BERRY_FARM -> new Copy(
                    "Berry farm",
                    "Plan berry blocks, crops, and picking windows.",
                    "Start with a berry block, then use general harvest, stock, and order records.",
                    "Block plans do not create harvest lots or stock, packout, or U-pick bookings.");
            case NURSERY_FARM -> new Copy(
                    "Nursery farm",
                    "Plan batches of live plants from propagation to ready for sale.",
                    "Start with a growing batch, then use shared stock and customer orders.",
                    "Batch plans do not create stock, reconcile plant loss, or handle nursery licensing.");
            case LIVESTOCK_FARM -> new Copy(
                    "Livestock farm",
                    "Plan cattle, sheep, or goat groups with editable headcounts and locations.",
                    "Start with a herd group, then use shared stock and customer records as needed.",
                    "Group plans do not track individual animals, health, breeding, sales, or traceability.");
            case POULTRY_FARM -> new Copy(
                    "Poultry & eggs",
                    "Log daily egg collections by flock or coop.",
                    "Record collected and unusable eggs; usable count is calculated for each entry.",
                    "Collection logs do not create stock or orders, grade eggs, or provide regulatory reporting.");
            case DAIRY_FARM -> new Copy(
                    "Dairy farm",
                    "Log milking sessions by group and measured unit.",
                    "Record each dated collection, then use shared stock and customer tools separately.",
                    "Collection logs do not track milk quality, processor delivery, food safety, or regulatory reporting.");
            case VINEYARD_FARM -> new Copy(
                    "Vineyard farm",
                    "Plan grape blocks, varieties, and expected harvest windows.",
                    "Start with a vineyard block, then use shared stock and customer tools separately.",
                    "Block plans do not create harvest lots, crush records, winery stock, or deliveries.");
            case HAY_FARM -> new Copy(
                    "Hay & forage",
                    "Plan repeat cuttings from field to bale.",
                    "Start with a cutting, then plan the next pass for the same field and crop.",
                    "Cutting plans do not create bale stock, feed rations, quality tests, or sales.");
            case HOPS_FARM -> new Copy(
                    "Hops farm",
                    "Plan hop yards by variety, stringing, and harvest window.",
                    "Start with a yard plan, then draft next season for the same yard and variety.",
                    "Yard plans do not record actual yields, drying, pelletizing, contracts, stock, or sales.");
            case GRAIN_FARM -> new Copy(
                    "Grains farm",
                    "Plan grain fields from seeding to harvest window.",
                    "Start with a field plan, then draft the next rotation for that field.",
                    "Field plans do not record yields, grade, moisture, storage, contracts, or sales.");
            case SEED_FARM -> new Copy(
                    "Seed farm",
                    "Plan seed fields, flowering, and harvest windows.",
                    "Start with a seed field, record isolation notes, then draft the next season.",
                    "Plans do not calculate isolation, certify seed, or create seed lots, stock, or sales.");
            case NUT_ORCHARD -> new Copy(
                    "Tree nut orchard",
                    "Plan nut blocks, pollinizers, and harvest windows.",
                    "Start with a nut block, then draft the next season for the same trees.",
                    "Plans do not check pollinizers or record actual yields, processing, stock, or sales.");
            case VEGETABLE_FARM -> new Copy(
                    "Vegetable farm",
                    "Plan beds, vegetable crops, and harvest windows.",
                    "Start with a planting, then draft the next succession for that crop.",
                    "Planting dates are entered by the grower; plans do not create harvests, stock, or sales.");
            case AQUACULTURE_FARM -> new Copy(
                    "Aquaculture farm",
                    "Plan aquatic cohorts by species, growing site, and harvest window.",
                    "Start with a cohort, then draft the next cycle for that growing site.",
                    "Plans do not verify harvest clearance, licensing, food safety, or create stock or sales.");
            case CHRISTMAS_TREE_FARM -> new Copy(
                    "Christmas tree farm",
                    "Plan tree blocks across planting, shearing, and the target selling season.",
                    "Start with a block, then draft a replacement planting for that site.",
                    "Plans do not record actual cuts, bookings, stock, sales, licensing, inspections, or export clearance.");
            case APIARY_FARM -> new Copy(
                    "Apiary farm",
                    "Record hive checks and plan the next visit for each colony.",
                    "Start with a dated check, then draft the next check for that hive.",
                    "Checks do not diagnose colony health, verify registration, manage pollination contracts, or post honey stock and sales.");
            case MUSHROOM_FARM -> new Copy(
                    "Mushroom farm",
                    "Plan mushroom batches from inoculation to a fruiting window.",
                    "Start with a batch, then plan the next batch using its species, method, substrate, and site.",
                    "Plans do not forecast fruiting, record actual yield, control the growing environment, or post stock and sales.");
            case CRANBERRY_FARM -> new Copy(
                    "Cranberry farm",
                    "Plan cranberry beds and their wet or dry harvest windows.",
                    "Start with a bed, then draft next season for the same vines and harvest method.",
                    "Bed plans do not schedule flooding, record actual harvests or yield, or post stock and sales.");
            case PUMPKIN_PATCH -> new Copy(
                    "Pumpkin patch",
                    "Plan patches, planting, and seasonal picking windows.",
                    "Start with a patch, then draft next season with its variety and sales mode.",
                    "Plans do not record completed picks, admission, bookings, payments, or stock and sales.");
            case CUSTOM_FARM -> new Copy(
                    "Custom farm",
                    "Use a workspace for your own mix of farm enterprises.",
                    "Open the general records that fit your operation.",
                    (customFocus.isEmpty() ? "" : "Your focus: " + customFocus + ". ")
                            + "Crop-specific planning, grading, and reporting need dedicated workflows.");
        };
    }

    private static String workspaceName(FarmWorkspace selected) {
        return switch (selected) {
            case ROSE_AND_EVENTS -> "Rose & events";
            case WILDFLOWER_FARM -> "Wildflower farm";
            case FLOWER_SHOP -> "Flower shop";
            case CANNABIS_FARM -> "Cannabis farm";
            case POTATO_FARM -> "Potato farm";
            case APPLE_ORCHARD -> "Apple orchard";
            case BERRY_FARM -> "Berry farm";
            case NURSERY_FARM -> "Nursery farm";
            case LIVESTOCK_FARM -> "Livestock farm";
            case POULTRY_FARM -> "Poultry & eggs";
            case DAIRY_FARM -> "Dairy farm";
            case VINEYARD_FARM -> "Vineyard farm";
            case HAY_FARM -> "Hay & forage";
            case HOPS_FARM -> "Hops farm";
            case GRAIN_FARM -> "Grains farm";
            case SEED_FARM -> "Seed farm";
            case NUT_ORCHARD -> "Tree nut orchard";
            case VEGETABLE_FARM -> "Vegetable farm";
            case AQUACULTURE_FARM -> "Aquaculture farm";
            case CHRISTMAS_TREE_FARM -> "Christmas tree farm";
            case APIARY_FARM -> "Apiary farm";
            case MUSHROOM_FARM -> "Mushroom farm";
            case CRANBERRY_FARM -> "Cranberry farm";
            case PUMPKIN_PATCH -> "Pumpkin patch";
            case CUSTOM_FARM -> "Custom farm";
        };
    }

    private record Copy(String heading, String scope, String summary, String limit) {
    }
}

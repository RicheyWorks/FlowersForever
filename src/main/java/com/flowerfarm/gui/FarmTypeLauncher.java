package com.flowerfarm.gui;

import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** The first desktop screen: pick a farm type, then enter its workflow. */
final class FarmTypeLauncher extends JPanel {

    private final FarmWorkspace currentWorkspace;
    private final Set<FarmWorkspace> enabledWorkspaces;
    private final java.util.Map<FarmWorkspace, JButton> enterButtons =
            new java.util.EnumMap<>(FarmWorkspace.class);
    private final java.util.Map<FarmWorkspace, JPanel> farmCards =
            new EnumMap<>(FarmWorkspace.class);

    FarmTypeLauncher(FarmWorkspace currentWorkspace, boolean canChange,
                     Consumer<FarmWorkspace> enter, Runnable setup) {
        this(currentWorkspace, Set.of(currentWorkspace), canChange, enter, setup, null);
    }

    FarmTypeLauncher(FarmWorkspace currentWorkspace,
                     Set<FarmWorkspace> enabledWorkspaces, boolean canChange,
                     Consumer<FarmWorkspace> enter, Runnable setup, Runnable combine) {
        super(new BorderLayout(0, FarmUi.SECTION_GAP));
        this.currentWorkspace = Objects.requireNonNull(currentWorkspace, "currentWorkspace");
        this.enabledWorkspaces = java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(enabledWorkspaces,
                        "enabledWorkspaces")));
        if (!this.enabledWorkspaces.contains(currentWorkspace)) {
            throw new IllegalArgumentException("The primary farm type must be selected.");
        }
        Objects.requireNonNull(enter, "enter");
        Objects.requireNonNull(setup, "setup");
        setName("farm.launcher");
        getAccessibleContext().setAccessibleName("Choose your farm type");
        FarmUi.pageSurface(this);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JButton setupButton = FarmUi.secondaryButton("Farm setup…");
        setupButton.setName("farmLauncher.setup");
        setupButton.setToolTipText(canChange
                ? "Edit state, enterprises, specialties, and flower offerings."
                : "View state, enterprises, specialties, and flower offerings.");
        setupButton.getAccessibleContext().setAccessibleDescription(setupButton.getToolTipText());
        setupButton.addActionListener(event -> setup.run());
        JButton combineButton = FarmUi.primaryButton(enabledWorkspaces.size() > 1
                ? "Edit combined farm…" : "Combine farm types…");
        combineButton.setName("farmLauncher.combine");
        combineButton.setEnabled(canChange && combine != null);
        combineButton.setToolTipText("Drag farm cards into a mix, remove types, "
                + "and choose the main farm type.");
        combineButton.getAccessibleContext().setAccessibleDescription(
                combineButton.getToolTipText());
        if (combine != null) {
            combineButton.addActionListener(event -> combine.run());
        }
        JPanel headerActions = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        headerActions.add(combineButton);
        headerActions.add(setupButton);
        JPanel header = FarmUi.pageHeader("Choose your farm type",
                "Pick one farm type or combine several for the same operation.",
                headerActions);
        JLabel mixSummary = FarmUi.mutedLabel(enabledWorkspaces.size() > 1
                ? "Current mix: " + enabledWorkspaces.stream()
                        .map(FarmSetupDialog::workspaceLabel)
                        .collect(java.util.stream.Collectors.joining(" · "))
                : "One farm can run several lines, such as potatoes, cannabis, and apples.");
        mixSummary.setName("farmLauncher.mixSummary");
        JTextField search = new JTextField(28);
        search.setName("farmLauncher.search");
        search.putClientProperty("JTextField.placeholderText",
                "Try potatoes, wedding, or livestock");
        search.getAccessibleContext().setAccessibleName("Find a farm type");
        JLabel searchLabel = new JLabel("Find a farm type");
        searchLabel.setLabelFor(search);
        JLabel matchCount = FarmUi.mutedLabel("");
        matchCount.setName("farmLauncher.matchCount");
        JPanel searchRow = new JPanel(new BorderLayout(12, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(matchCount, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(mixSummary, BorderLayout.CENTER);
        top.add(searchRow, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(0, 3, 14, 14));
        cards.setOpaque(false);
        cards.setName("farmLauncher.cards");
        int rows = (FarmWorkspace.values().length + 2) / 3;
        cards.setPreferredSize(new Dimension(980, rows * 204 + (rows - 1) * 14));
        int number = 1;
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            JPanel farmCard = card(workspace, number++, canChange, enter);
            farmCards.put(workspace, farmCard);
            cards.add(farmCard);
        }
        JPanel cardList = new JPanel(new BorderLayout());
        cardList.setOpaque(false);
        cardList.add(cards, BorderLayout.NORTH);
        JScrollPane cardScroll = new JScrollPane(cardList);
        cardScroll.setName("farmLauncher.cardScroll");
        cardScroll.setBorder(null);
        cardScroll.setOpaque(false);
        cardScroll.getViewport().setOpaque(false);
        cardScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardScroll.getVerticalScrollBar().setUnitIncrement(24);
        cardScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                resizeCardGrid(cards, cardScroll);
            }
        });
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.setName("farmLauncher.empty");
        JPanel emptyContent = new JPanel(new BorderLayout(0, 12));
        emptyContent.setOpaque(false);
        emptyContent.add(FarmUi.mutedLabel(
                "No farm types match. Try a crop, product, or farm type."), BorderLayout.NORTH);
        JButton clearSearch = FarmUi.secondaryButton("Clear search");
        clearSearch.setName("farmLauncher.clearSearch");
        clearSearch.addActionListener(event -> search.setText(""));
        emptyContent.add(clearSearch, BorderLayout.CENTER);
        empty.add(emptyContent);
        CardLayout resultLayout = new CardLayout();
        JPanel results = new JPanel(resultLayout);
        results.setOpaque(false);
        results.add(cardScroll, "matches");
        results.add(empty, "empty");
        add(results, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) {
                filterCards(search.getText(), cards, cardScroll, results, resultLayout,
                        matchCount);
            }

            @Override public void removeUpdate(DocumentEvent event) {
                filterCards(search.getText(), cards, cardScroll, results, resultLayout,
                        matchCount);
            }

            @Override public void changedUpdate(DocumentEvent event) {
                filterCards(search.getText(), cards, cardScroll, results, resultLayout,
                        matchCount);
            }
        });
        updateCount(matchCount, FarmWorkspace.values().length);

        JLabel guidance = FarmUi.wrappingHint(canChange
                ? enabledWorkspaces.size() > 1
                        ? "Cards in your mix open their tools. Add another type through its card, "
                                + "or edit the mix to remove types and return to one farm."
                        : "Choose a farm card, use Combine to mix types, or open Farm setup to describe your operation."
                : "Enter the current type to use its tools. Changing the type requires owner access and a working data store.");
        guidance.setName("farmLauncher.guidance");
        guidance.setHorizontalAlignment(SwingConstants.LEFT);
        add(guidance, BorderLayout.SOUTH);
    }

    private JPanel card(FarmWorkspace workspace, int number, boolean canChange,
                        Consumer<FarmWorkspace> enter) {
        boolean active = workspace == currentWorkspace;
        boolean included = enabledWorkspaces.contains(workspace);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);

        JLabel index = FarmUi.mutedLabel(String.format("%02d  /  %s", number, category(workspace)));
        index.setFont(index.getFont().deriveFont(Font.BOLD, 11f));
        body.add(index, BorderLayout.NORTH);

        JTextArea description = new JTextArea(description(workspace));
        description.setName("farmLauncher.description." + workspace.name());
        description.setEditable(false);
        description.setFocusable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(4);
        description.setFont(description.getFont().deriveFont(13f));
        description.getAccessibleContext().setAccessibleName(
                FarmSetupDialog.workspaceLabel(workspace) + " description");
        body.add(description, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.add(FarmUi.statusBadge(active
                        ? enabledWorkspaces.size() > 1 ? "Primary · in mix" : "Current type"
                        : included ? "In your mix" : scope(workspace),
                included ? FarmUi.Tone.SUCCESS : FarmUi.Tone.NEUTRAL), BorderLayout.WEST);
        boolean outsideMix = enabledWorkspaces.size() > 1 && !included;
        JButton button = active ? FarmUi.primaryButton(enabledWorkspaces.size() > 1
                        ? "Enter combined farm" : "Enter farm")
                : FarmUi.secondaryButton(included ? "Open in mix"
                        : outsideMix ? "Add to mix…"
                        : "Select & enter");
        button.setName("farmLauncher.enter." + workspace.name());
        String actionHint = active ? "Enter the current farm workspace."
                : included ? "Open this farm tool without changing your mix."
                : !canChange ? "Only an owner can change the farm mix or farm type."
                : outsideMix
                        ? "Open the mix editor with this type included. Save to keep the change."
                : "Use only this farm type and open its workspace.";
        button.setToolTipText(actionHint + " " + description(workspace));
        button.getAccessibleContext().setAccessibleName(
                (active ? "Enter " : included ? "Open "
                        : !canChange ? "Owner can select "
                        : outsideMix ? "Add to mix "
                        : "Select and enter ")
                        + FarmSetupDialog.workspaceLabel(workspace));
        button.getAccessibleContext().setAccessibleDescription(button.getToolTipText());
        button.setEnabled(included || canChange);
        button.addActionListener(event -> enter.accept(workspace));
        enterButtons.put(workspace, button);
        footer.add(button, BorderLayout.EAST);
        body.add(footer, BorderLayout.SOUTH);

        JPanel card = FarmUi.section(FarmSetupDialog.workspaceLabel(workspace), null, body);
        card.setName("farmLauncher.card." + workspace.name());
        card.getAccessibleContext().setAccessibleDescription(description.getText());
        return card;
    }

    JButton currentEnterButton() {
        return enterButtons.get(currentWorkspace);
    }

    private void filterCards(String query, JPanel cards, JScrollPane cardScroll,
                             JPanel results, CardLayout resultLayout, JLabel matchCount) {
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        cards.removeAll();
        int matches = 0;
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            String searchable = (FarmSetupDialog.workspaceLabel(workspace) + " "
                    + category(workspace) + " " + description(workspace) + " "
                    + searchAliases(workspace)).toLowerCase(Locale.ROOT);
            boolean visible = true;
            for (String term : terms) {
                if (!searchable.contains(term)) {
                    visible = false;
                    break;
                }
            }
            if (visible) {
                cards.add(farmCards.get(workspace));
                matches++;
            }
        }
        resizeCardGrid(cards, cardScroll);
        cards.revalidate();
        cards.repaint();
        cardScroll.getVerticalScrollBar().setValue(0);
        resultLayout.show(results, matches == 0 ? "empty" : "matches");
        updateCount(matchCount, matches);
    }

    private static void resizeCardGrid(JPanel cards, JScrollPane cardScroll) {
        int width = cardScroll.getViewport().getWidth();
        if (width <= 0) {
            width = 980;
        }
        int columns = width >= 900 ? 3 : width >= 620 ? 2 : 1;
        int matches = 0;
        for (Component component : cards.getComponents()) {
            if ("farmLauncher.spacer".equals(component.getName())) {
                cards.remove(component);
            } else {
                matches++;
            }
        }
        cards.setLayout(new GridLayout(0, columns, 14, 14));
        int blankSlots = (columns - matches % columns) % columns;
        for (int slot = 0; slot < blankSlots && matches > 0; slot++) {
            JPanel spacer = new JPanel();
            spacer.setName("farmLauncher.spacer");
            spacer.setOpaque(false);
            spacer.setFocusable(false);
            cards.add(spacer);
        }
        int rows = (matches + columns - 1) / columns;
        cards.setPreferredSize(new Dimension(width,
                rows * 204 + Math.max(0, rows - 1) * 14));
        cards.revalidate();
        cards.repaint();
    }

    private static void updateCount(JLabel matchCount, int count) {
        matchCount.setText(count + (count == 1 ? " farm type" : " farm types"));
    }

    static String searchAliases(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "florist floral bouquet wedding stems";
            case WILDFLOWER_FARM -> "wild flower flowers flower farm";
            case FLOWER_SHOP -> "florist retail";
            case CANNABIS_FARM -> "marijuana marihuana weed subcool super soil grower recipes greenhouse pheno";
            case POTATO_FARM -> "spuds tubers row crop";
            case APPLE_ORCHARD -> "fruit tree";
            case BERRY_FARM -> "blueberry blackberry raspberry strawberry u pick";
            case NURSERY_FARM -> "propagation seedlings starts live plants";
            case LIVESTOCK_FARM -> "cattle sheep goats herd grazing ranch";
            case POULTRY_FARM -> "eggs hens chickens ducks poultry flock coop";
            case DAIRY_FARM -> "dairy milk milking cream cows herd tank";
            case VINEYARD_FARM -> "vineyard wine grapes vines viticulture winery block";
            case HAY_FARM -> "hay forage alfalfa pasture cutting bale mowing field";
            case HOPS_FARM -> "hops hop yard bines cones brewing brewer stringing trellis";
            case GRAIN_FARM -> "grains grain wheat barley oats rye triticale corn cereal dryland";
            case SEED_FARM -> "seed crops grass seed vegetable seed flower seed isolation pollination";
            case NUT_ORCHARD -> "tree nuts hazelnuts filberts walnuts almonds nut orchard pollinizer";
            case VEGETABLE_FARM -> "vegetables onions carrots greens brassicas tomatoes sweet corn market garden succession planting";
            case AQUACULTURE_FARM -> "aquaculture aquatic shellfish oysters clams mussels fish seaweed kelp pond tank coastal grow out";
            case CHRISTMAS_TREE_FARM -> "christmas holiday tree noble douglas fir choose and cut u cut wholesale evergreen shearing";
            case APIARY_FARM -> "apiary beekeeper beekeeping bees honey hives hive pollination queen colony";
            case MUSHROOM_FARM -> "mushroom fungi shiitake oyster lion's mane log sawdust grow room fruiting";
            case CRANBERRY_FARM -> "cranberry cranberries bog beds coastal wet harvest dry harvest fresh processing";
            case PUMPKIN_PATCH -> "pumpkin pumpkins patch pick your own u pick farm stand autumn fall squash visitor";
            case CUSTOM_FARM -> "other custom";
        };
    }

    /** Changes the workspace while retaining the installation's configured farm details. */
    static FarmOperationSettings settingsFor(FarmOperationSettings source,
                                             FarmWorkspace chosen) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(chosen, "chosen");
        FarmOperationSettings result = source.snapshot();
        LinkedHashSet<FarmEnterprise> enterprises =
                new LinkedHashSet<>(result.getEnterprises());
        // Replace only the untouched rose starter, never an intentional flower line.
        boolean onlyDefaultFlowers = result.getWorkspace()
                == FarmWorkspace.ROSE_AND_EVENTS
                && result.getState() == FarmState.WA
                && result.getPrimaryEnterprise() == FarmEnterprise.CUT_FLOWERS
                && enterprises.size() == 1
                && enterprises.contains(FarmEnterprise.CUT_FLOWERS)
                && result.getSpecialtyCrops().isEmpty()
                && result.getFlowerOfferings().equals(java.util.Set.of(
                        FlowerOffering.STEMS, FlowerOffering.BOUQUETS,
                        FlowerOffering.WEDDINGS));
        if (chosen == FarmWorkspace.CUSTOM_FARM && onlyDefaultFlowers) {
            enterprises.remove(FarmEnterprise.CUT_FLOWERS);
            enterprises.add(FarmEnterprise.OTHER);
            result.setPrimaryEnterprise(FarmEnterprise.OTHER);
        }
        chosen.requiredEnterprise().ifPresent(required -> {
            if (onlyDefaultFlowers && required != FarmEnterprise.CUT_FLOWERS) {
                enterprises.remove(FarmEnterprise.CUT_FLOWERS);
            }
            enterprises.add(required);
            result.setPrimaryEnterprise(required);
        });
        result.setEnterprises(enterprises);
        if (!enterprises.contains(FarmEnterprise.CUT_FLOWERS)
                && !enterprises.contains(FarmEnterprise.FLORAL_RETAIL)) {
            result.setFlowerOfferings(java.util.Set.of());
        }
        result.setWorkspace(chosen);
        result.setEnabledWorkspaces(Set.of(chosen));
        return result.snapshot();
    }

    /** Selects a mix of specialized toolsets while keeping existing farm records. */
    static FarmOperationSettings settingsForCombination(FarmOperationSettings source,
            FarmWorkspace primary, Set<FarmWorkspace> included) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(included, "included");
        if (included.isEmpty() || included.stream().anyMatch(Objects::isNull)
                || !included.contains(primary)) {
            throw new IllegalArgumentException(
                    "Choose farm types including the primary.");
        }
        FarmOperationSettings result = settingsFor(source, primary);
        LinkedHashSet<FarmEnterprise> enterprises =
                new LinkedHashSet<>(result.getEnterprises());
        for (FarmWorkspace selected : included) {
            selected.requiredEnterprise().ifPresent(enterprises::add);
        }
        result.setEnterprises(enterprises);
        result.setEnabledWorkspaces(included);
        if (included.stream().anyMatch(selected -> selected.requiredEnterprise()
                .filter(required -> required == FarmEnterprise.CUT_FLOWERS
                        || required == FarmEnterprise.FLORAL_RETAIL).isPresent())) {
            result.setFlowerOfferings(source.getFlowerOfferings());
        }
        return result.snapshot();
    }

    private static String category(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "GROW + DESIGN";
            case WILDFLOWER_FARM -> "FIELD FLOWERS";
            case FLOWER_SHOP -> "FLORAL RETAIL";
            case CANNABIS_FARM -> "CULTIVATION";
            case POTATO_FARM -> "FIELD CROPS";
            case APPLE_ORCHARD -> "ORCHARDS";
            case BERRY_FARM -> "BERRIES";
            case NURSERY_FARM -> "LIVE PLANTS";
            case LIVESTOCK_FARM -> "HERD GROUPS";
            case POULTRY_FARM -> "EGGS + FLOCKS";
            case DAIRY_FARM -> "MILK COLLECTIONS";
            case VINEYARD_FARM -> "VINEYARD BLOCKS";
            case HAY_FARM -> "HAY CUTTINGS";
            case HOPS_FARM -> "HOP YARDS";
            case GRAIN_FARM -> "GRAIN FIELDS";
            case SEED_FARM -> "SEED FIELDS";
            case NUT_ORCHARD -> "NUT BLOCKS";
            case VEGETABLE_FARM -> "VEGETABLE PLANTINGS";
            case AQUACULTURE_FARM -> "AQUATIC COHORTS";
            case CHRISTMAS_TREE_FARM -> "TREE BLOCKS";
            case APIARY_FARM -> "HIVE CHECKS";
            case MUSHROOM_FARM -> "MUSHROOM BATCHES";
            case CRANBERRY_FARM -> "CRANBERRY BEDS";
            case PUMPKIN_PATCH -> "PUMPKIN PATCHES";
            case CUSTOM_FARM -> "OTHER FARMS";
        };
    }

    private static String scope(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS, WILDFLOWER_FARM -> "Grower tools";
            case FLOWER_SHOP -> "Shop tools";
            case CANNABIS_FARM -> "Pilot";
            case POTATO_FARM, APPLE_ORCHARD, BERRY_FARM, NURSERY_FARM,
                    LIVESTOCK_FARM, VINEYARD_FARM, HAY_FARM, HOPS_FARM,
                    GRAIN_FARM, SEED_FARM, NUT_ORCHARD, VEGETABLE_FARM,
                    AQUACULTURE_FARM, CHRISTMAS_TREE_FARM,
                    CRANBERRY_FARM, PUMPKIN_PATCH -> "Planning";
            case POULTRY_FARM, DAIRY_FARM -> "Daily log";
            case APIARY_FARM -> "Visit log";
            case MUSHROOM_FARM -> "Planning";
            case CUSTOM_FARM -> "General tools";
        };
    }

    private static String description(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS ->
                    "Grow roses, sell stems and bouquets, and plan weddings and events.";
            case WILDFLOWER_FARM ->
                    "Grow cut flowers, record harvests, prepare market days, and manage orders.";
            case FLOWER_SHOP ->
                    "Manage shop stock, bouquets, customer orders, and event services.";
            case CANNABIS_FARM ->
                    "Plan cultivation batches; record generic harvests, stock, and orders separately. No compliance reporting.";
            case POTATO_FARM ->
                    "Plan potato fields by variety, acreage, and harvest target. Use shared stock and orders; no grading or packout.";
            case APPLE_ORCHARD ->
                    "Track orchard blocks, varieties, and harvest targets. Use shared stock and orders; no bin or cold-storage ledger.";
            case BERRY_FARM ->
                    "Plan berry blocks and picking windows. Choose a fresh, processing, or U-pick channel; stock stays separate.";
            case NURSERY_FARM ->
                    "Plan live plant batches from propagation to ready for sale. Stock and orders stay in shared tools.";
            case LIVESTOCK_FARM ->
                    "Plan cattle, sheep, or goat groups by headcount, location, and next check. No individual animal or health records.";
            case POULTRY_FARM ->
                    "Record egg collections by flock and date. Usable count is calculated; stock and orders stay separate.";
            case DAIRY_FARM ->
                    "Log milking sessions by group and measured unit. Collections stay separate from stock and orders.";
            case VINEYARD_FARM ->
                    "Plan grape blocks, varieties, harvest windows, and intended destinations. Stock and orders stay separate.";
            case HAY_FARM ->
                    "Plan each hay cutting by field, forage crop, and target dates. Move its stage; stock and orders stay separate.";
            case HOPS_FARM ->
                    "Plan hop yards by variety, stringing, and harvest window. Move stages; stock and orders stay separate.";
            case GRAIN_FARM ->
                    "Plan grain fields by crop, rotation, seeding, and harvest window. Move stages; stock and orders stay separate.";
            case SEED_FARM ->
                    "Plan seed fields, flowering windows, and isolation notes. Move stages; seed lots and sales stay separate.";
            case NUT_ORCHARD ->
                    "Plan nut orchard blocks, pollinizers, floor prep, and harvest windows. Move stages; stock and sales stay separate.";
            case VEGETABLE_FARM ->
                    "Plan vegetable plantings by bed, crop, and harvest window. Repeat successions; stock and sales stay separate.";
            case AQUACULTURE_FARM ->
                    "Plan aquatic cohorts by species, growing site, and harvest window. Move stages; harvest clearance and sales stay separate.";
            case CHRISTMAS_TREE_FARM ->
                    "Plan Christmas tree blocks by species, shearing, and target sale season. Cut counts, bookings, and sales stay separate.";
            case APIARY_FARM ->
                    "Log dated hive checks, observations, and next visits. Honey stock, pollination contracts, and registration stay separate.";
            case MUSHROOM_FARM ->
                    "Plan mushroom batches by species, method, substrate, and fruiting window. Harvests, stock, and orders stay separate.";
            case CRANBERRY_FARM ->
                    "Plan cranberry beds by variety, wet or dry harvest method, and target window. Actual harvest, stock, and sales stay separate.";
            case PUMPKIN_PATCH ->
                    "Plan pumpkin patches by planting and picking windows, with pick-your-own or farm stand options. Sales stay in shared tools.";
            case CUSTOM_FARM ->
                    "Other farms: use shared records and custom names while more specialized tools are developed.";
        };
    }
}

package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;
import com.flowerfarm.service.FarmOperationSettingsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Owner-edited farm context; choosing an enterprise does not enable a new workflow. */
final class FarmSetupDialog {
    private final FarmOperationSettingsService service;
    private final FarmDialogs dialogs;

    FarmSetupDialog(FarmOperationSettingsService service, FarmDialogs dialogs) {
        this.service = service;
        this.dialogs = dialogs;
    }

    FarmOperationSettings show(Component owner, FarmProfile profile, FarmRole role) {
        return show(owner, profile, role, null);
    }

    FarmOperationSettings show(Component owner, FarmProfile profile, FarmRole role,
                               Runnable openFarmPicker) {
        FarmOperationSettings current;
        try {
            current = service.getSettings();
        } catch (RuntimeException exception) {
            dialogs.showMessage(owner, "Farm setup could not be loaded. Try again after checking "
                            + "the local farm data store.", "Farm setup unavailable",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        boolean editable = role == FarmRole.OWNER;
        Form form = new Form(profile, current, editable, openFarmPicker != null);
        JScrollPane scroll = FarmUi.dialogScroll(owner, form, new Dimension(740, 610));
        Object[] options = editable
                ? openFarmPicker == null
                        ? new Object[]{"Save farm setup", "Cancel"}
                        : new Object[]{"Save farm setup", "Save & edit farm mix", "Cancel"}
                : new Object[]{"Close"};
        while (true) {
            int choice = dialogs.showOption(owner, scroll, "Farm setup",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            boolean editFarmMix = editable && openFarmPicker != null && choice == 1;
            if (!editable || (choice != 0 && !editFarmMix)) {
                return null;
            }
            try {
                FarmOperationSettings saved = service.saveSettings(form.draft());
                if (editFarmMix) {
                    SwingUtilities.invokeLater(openFarmPicker);
                }
                return saved;
            } catch (IllegalArgumentException exception) {
                form.showError(exception.getMessage());
            } catch (RuntimeException exception) {
                form.showError("Farm setup was not saved. Check the local data store and try again. "
                        + "Your choices remain here.");
            }
        }
    }

    static String enterpriseLabel(FarmEnterprise enterprise) {
        return switch (enterprise) {
            case CUT_FLOWERS -> "Cut flowers";
            case FLORAL_RETAIL -> "Flower shop / floral retail";
            case ROW_CROPS -> "Row crops · potatoes";
            case VEGETABLES -> "Vegetables";
            case BERRIES -> "Berries";
            case ORCHARDS -> "Orchards · apples";
            case TREE_NUTS -> "Tree nuts";
            case VINEYARDS -> "Vineyards";
            case NURSERY -> "Nursery plants";
            case HOPS -> "Hops";
            case GRAINS -> "Grains";
            case HAY_AND_FORAGE -> "Hay and forage";
            case SEED_CROPS -> "Seed crops";
            case LIVESTOCK -> "Livestock";
            case DAIRY -> "Dairy";
            case POULTRY_AND_EGGS -> "Poultry and eggs";
            case AQUACULTURE -> "Aquaculture";
            case CHRISTMAS_TREES -> "Christmas trees";
            case APIARY -> "Apiary / beekeeping";
            case MUSHROOMS -> "Mushrooms";
            case CRANBERRIES -> "Cranberries";
            case PUMPKINS -> "Pumpkins";
            case CANNABIS -> "Cannabis";
            case INDUSTRIAL_HEMP -> "Industrial hemp";
            case OTHER -> "Other";
        };
    }

    static String stateLabel(FarmState state) {
        return switch (state) {
            case WA -> "Washington (WA)";
            case OR -> "Oregon (OR)";
            case CA -> "California (CA) · later pilot planning";
        };
    }

    static String workspaceLabel(FarmWorkspace workspace) {
        return switch (workspace) {
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

    static final class Form extends JPanel {
        private final FarmOperationSettings current;
        private final boolean editable;
        private final JComboBox<FarmWorkspace> workspace =
                new JComboBox<>(FarmWorkspace.values());
        private final JComboBox<FarmState> state = new JComboBox<>(FarmState.values());
        private final JComboBox<FarmEnterprise> primary =
                new JComboBox<>(FarmEnterprise.values());
        private final EnumMap<FarmEnterprise, JCheckBox> enterprises =
                new EnumMap<>(FarmEnterprise.class);
        private final EnumMap<FlowerOffering, JCheckBox> offerings =
                new EnumMap<>(FlowerOffering.class);
        private final JTextArea specialties = new JTextArea(2, 48);
        private final JTextArea readiness = new JTextArea(5, 48);
        private final JTextArea selectedTypes = new JTextArea(5, 48);
        private final JLabel error = FarmUi.wrappingStatusBadge("", FarmUi.Tone.DANGER);

        Form(FarmProfile profile, FarmOperationSettings current, boolean editable) {
            this(profile, current, editable, false);
        }

        Form(FarmProfile profile, FarmOperationSettings current,
             boolean editable, boolean canOpenFarmPicker) {
            this.current = current.snapshot();
            this.editable = editable;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            error.setName("farmSetup.error");
            error.setVisible(false);
            add(error);
            add(FarmUi.section("Farm identity", "Identity on customer documents is configured separately.",
                    text("Farm: " + profile.getName() + "\nLocation: " + profile.locationLabel()
                            + "\nTime zone: " + profile.getTimeZone(), 3)));
            add(Box.createVerticalStrut(10));

            workspace.setName("farmSetup.workspace");
            workspace.getAccessibleContext().setAccessibleName("Farm workspace");
            workspace.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                        Object value, int index, boolean selected, boolean focus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            list, value, index, selected, focus);
                    label.setText(value instanceof FarmWorkspace chosen
                            ? workspaceLabel(chosen) : "");
                    return label;
                }
            });
            workspace.setSelectedItem(current.getWorkspace());
            state.setName("farmSetup.state");
            state.getAccessibleContext().setAccessibleName("Farm state");
            state.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                        Object value, int index, boolean selected, boolean focus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            list, value, index, selected, focus);
                    label.setText(value instanceof FarmState farmState ? stateLabel(farmState) : "");
                    return label;
                }
            });
            state.setSelectedItem(current.getState());
            primary.setName("farmSetup.primaryEnterprise");
            primary.getAccessibleContext().setAccessibleName("Primary farm enterprise");
            primary.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                        Object value, int index, boolean selected, boolean focus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            list, value, index, selected, focus);
                    label.setText(value instanceof FarmEnterprise enterprise
                            ? enterpriseLabel(enterprise) : "");
                    return label;
                }
            });
            primary.setSelectedItem(current.getPrimaryEnterprise());
            JPanel context = new JPanel(new GridLayout(3, 2, 10, 6));
            context.setOpaque(false);
            context.add(new JLabel("Workspace", SwingConstants.LEFT));
            context.add(workspace);
            context.add(new JLabel("State", SwingConstants.LEFT));
            context.add(state);
            context.add(new JLabel("Primary enterprise", SwingConstants.LEFT));
            context.add(primary);
            add(FarmUi.section("Operating context",
                    "Workspace changes desktop navigation; state does not change weather guidance.",
                    context));
            add(Box.createVerticalStrut(10));

            selectedTypes.setName("farmSetup.selectedTypes");
            selectedTypes.getAccessibleContext().setAccessibleName("Selected farm types");
            selectedTypes.setEditable(false);
            selectedTypes.setOpaque(false);
            selectedTypes.setLineWrap(true);
            selectedTypes.setWrapStyleWord(true);
            selectedTypes.setFocusable(false);
            add(FarmUi.section("Selected farm types",
                    canOpenFarmPicker
                            ? "Choose Save & edit farm mix to add or remove types on the farm picker."
                            : "Use the farm picker to add or remove types.",
                    selectedTypes));
            add(Box.createVerticalStrut(10));

            JPanel enterpriseGrid = new JPanel(new GridLayout(0, 3, 8, 4));
            enterpriseGrid.setOpaque(false);
            Set<FarmEnterprise> selected = current.getEnterprises();
            for (FarmEnterprise enterprise : FarmEnterprise.values()) {
                JCheckBox box = new JCheckBox(enterpriseLabel(enterprise),
                        selected != null && selected.contains(enterprise));
                box.setName("farmSetup.enterprise." + enterprise.name());
                box.getAccessibleContext().setAccessibleName(enterpriseLabel(enterprise));
                box.setOpaque(false);
                box.addItemListener(event -> updateReadiness());
                enterprises.put(enterprise, box);
                enterpriseGrid.add(box);
            }
            primary.addActionListener(event -> {
                FarmEnterprise chosen = (FarmEnterprise) primary.getSelectedItem();
                if (editable && chosen != null) {
                    enterprises.get(chosen).setSelected(true);
                }
                updateReadiness();
            });
            workspace.addActionListener(event -> {
                FarmWorkspace chosen = (FarmWorkspace) workspace.getSelectedItem();
                if (editable && chosen != null) {
                    chosen.requiredEnterprise().ifPresent(required -> {
                        boolean onlyDefaultFlowers = current.getWorkspace()
                                == FarmWorkspace.ROSE_AND_EVENTS
                                && current.getEnabledWorkspaces().size() == 1
                                && current.getState() == FarmState.WA
                                && primary.getSelectedItem() == FarmEnterprise.CUT_FLOWERS
                                && enterprises.values().stream().filter(JCheckBox::isSelected)
                                        .count() == 1
                                && enterprises.get(FarmEnterprise.CUT_FLOWERS).isSelected()
                                && current.getSpecialtyCrops().isEmpty()
                                && current.getFlowerOfferings().equals(Set.of(
                                        FlowerOffering.STEMS, FlowerOffering.BOUQUETS,
                                        FlowerOffering.WEDDINGS));
                        if (onlyDefaultFlowers && required != FarmEnterprise.CUT_FLOWERS) {
                            enterprises.get(FarmEnterprise.CUT_FLOWERS).setSelected(false);
                        }
                        enterprises.get(required).setSelected(true);
                        primary.setSelectedItem(required);
                    });
                }
                updateReadiness();
            });
            add(FarmUi.section("Enterprise mix",
                    "Select all business lines. Clear Cut flowers if this shop or farm does not grow them.",
                    enterpriseGrid));
            add(Box.createVerticalStrut(10));

            specialties.setName("farmSetup.specialtyCrops");
            specialties.getAccessibleContext().setAccessibleName("Farm specialties");
            specialties.setLineWrap(true);
            specialties.setWrapStyleWord(true);
            specialties.setText(String.join(", ", current.getSpecialtyCrops()));
            JPanel specialtyPanel = new JPanel(new BorderLayout(0, 4));
            specialtyPanel.setOpaque(false);
            specialtyPanel.add(new JLabel("Crops, breeds, or products, separated by commas"),
                    BorderLayout.NORTH);
            specialtyPanel.add(new JScrollPane(specialties), BorderLayout.CENTER);
            specialtyPanel.add(FarmUi.mutedLabel(
                    "Examples: roses, potatoes, Honeycrisp apples, dairy cattle."),
                    BorderLayout.SOUTH);
            add(FarmUi.section("Specialties", "For farm context and future workflow planning.",
                    specialtyPanel));
            add(Box.createVerticalStrut(10));

            JPanel offeringRow = new JPanel(new GridLayout(1, 3, 8, 4));
            offeringRow.setOpaque(false);
            Set<FlowerOffering> selectedOfferings = current.getFlowerOfferings();
            for (FlowerOffering offering : FlowerOffering.values()) {
                String label = switch (offering) {
                    case STEMS -> "Stems";
                    case BOUQUETS -> "Bouquets";
                    case WEDDINGS -> "Weddings & events";
                };
                JCheckBox box = new JCheckBox(label,
                        selectedOfferings != null && selectedOfferings.contains(offering));
                box.setName("farmSetup.offering." + offering.name());
                box.getAccessibleContext().setAccessibleName(label);
                box.setOpaque(false);
                offerings.put(offering, box);
                offeringRow.add(box);
            }
            add(FarmUi.section("Flower offerings",
                    "Used by cut-flower growers and flower shops; choices stay selected while you edit.",
                    offeringRow));
            add(Box.createVerticalStrut(10));

            readiness.setName("farmSetup.readiness");
            readiness.getAccessibleContext().setAccessibleName("Workflow availability for this farm");
            readiness.setEditable(false);
            readiness.setOpaque(false);
            readiness.setLineWrap(true);
            readiness.setWrapStyleWord(true);
            readiness.setFocusable(false);
            readiness.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) {
                    sizeReadinessToText();
                }
            });
            add(FarmUi.section("What works today",
                    "The workspace chooses visible tools; enterprise checkboxes describe the operation.",
                    readiness));

            state.addActionListener(event -> updateReadiness());
            updateReadiness();
            if (!editable) {
                workspace.setEnabled(false);
                state.setEnabled(false);
                primary.setEnabled(false);
                enterprises.values().forEach(box -> box.setEnabled(false));
                offerings.values().forEach(box -> box.setEnabled(false));
                specialties.setEditable(false);
            }
        }

        FarmOperationSettings draft() {
            Set<FarmEnterprise> selected = enterprises.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(java.util.Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            FarmEnterprise chosen = (FarmEnterprise) primary.getSelectedItem();
            if (selected.isEmpty() || chosen == null || !selected.contains(chosen)) {
                throw new IllegalArgumentException("Select the primary enterprise in the enterprise mix.");
            }
            FarmWorkspace chosenWorkspace = (FarmWorkspace) workspace.getSelectedItem();
            if (chosenWorkspace == null) {
                throw new IllegalArgumentException("Choose a farm workspace.");
            }
            chosenWorkspace.requiredEnterprise().ifPresent(required -> {
                if (!selected.contains(required)) {
                    throw new IllegalArgumentException(workspaceLabel(chosenWorkspace)
                            + " needs " + enterpriseLabel(required) + " selected.");
                }
            });
            Set<String> crops = Arrays.stream(specialties.getText().split("[,\\n]"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<FlowerOffering> selectedOfferings = offerings.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(java.util.Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!selected.contains(FarmEnterprise.CUT_FLOWERS)
                    && !selected.contains(FarmEnterprise.FLORAL_RETAIL)) {
                selectedOfferings.clear();
            }
            FarmOperationSettings result = current.snapshot();
            result.setWorkspace(chosenWorkspace);
            result.setState((FarmState) state.getSelectedItem());
            result.setPrimaryEnterprise(chosen);
            result.setEnterprises(selected);
            result.setSpecialtyCrops(crops);
            result.setFlowerOfferings(selectedOfferings);
            result.validate();
            return result;
        }

        void showError(String message) {
            error.setText(message == null || message.isBlank()
                    ? "Review the farm setup choices and try again." : message);
            error.getAccessibleContext().setAccessibleDescription(error.getText());
            error.setVisible(true);
        }

        String readinessText() {
            return readiness.getText();
        }

        private void updateReadiness() {
            FarmOperationSettings preview = current.snapshot();
            FarmWorkspace chosenWorkspace = (FarmWorkspace) workspace.getSelectedItem();
            if (chosenWorkspace != null) {
                preview.setWorkspace(chosenWorkspace);
            }
            selectedTypes.setText("Primary: " + workspaceLabel(preview.getWorkspace())
                    + "\nEnabled: " + preview.getEnabledWorkspaces().stream()
                            .map(FarmSetupDialog::workspaceLabel)
                            .collect(Collectors.joining(" · "))
                    + "\nUse the farm picker to add or remove types.");
            selectedTypes.setCaretPosition(0);
            if (enterprises.isEmpty()) {
                return;
            }
            Set<FarmEnterprise> selected = enterprises.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(java.util.Map.Entry::getKey)
                    .collect(Collectors.toSet());
            StringBuilder text = new StringBuilder();
            FarmWorkspace selectedWorkspace = (FarmWorkspace) workspace.getSelectedItem();
            if (selectedWorkspace != null) {
                text.append(workspaceLabel(selectedWorkspace)).append(" workspace: ");
                switch (selectedWorkspace) {
                    case ROSE_AND_EVENTS -> text.append(
                            "rose growing, floral design, weddings, and orders appear together.\n\n");
                    case WILDFLOWER_FARM -> text.append(
                            "cut-flower growing tools appear without rose-only references.\n\n");
                    case FLOWER_SHOP -> text.append(
                            "stock, customer orders, and floral design are foregrounded; field tools are hidden.\n\n");
                    case CANNABIS_FARM -> text.append(
                            "cultivation batch planning, harvest notes, inventory, and customer orders "
                                    + "are shown with a clear pilot scope.\n\n");
                    case POTATO_FARM -> text.append(
                            "field planning, general harvest and stock records, and customer "
                                    + "orders are shown; grading and packout are not included.\n\n");
                    case APPLE_ORCHARD -> text.append(
                            "orchard block planning, general harvest and stock records, and "
                                    + "customer orders are shown; bins and cold storage are not included.\n\n");
                    case BERRY_FARM -> text.append(
                            "berry block and picking-window planning is shown with general "
                                    + "harvest, stock, and order records; U-pick booking and "
                                    + "packout are not included.\n\n");
                    case NURSERY_FARM -> text.append(
                            "live-plant growing batches are shown with general stock and "
                                    + "order records; batches do not create stock or handle "
                                    + "nursery licensing and inspections.\n\n");
                    case LIVESTOCK_FARM -> text.append(
                            "editable herd group plans are shown with shared records; "
                                    + "individual animals, health, breeding, and sales links "
                                    + "are not included.\n\n");
                    case POULTRY_FARM -> text.append(
                            "egg collections are logged by flock and day. Usable counts are calculated; "
                                    + "stock and orders remain separate. No grading or regulatory reporting.\n\n");
                    case DAIRY_FARM -> text.append(
                            "milking sessions are logged by group, date, measured amount, and unit. "
                                    + "Collections do not post to stock or orders or record quality, "
                                    + "processor delivery, or food-safety compliance.\n\n");
                    case VINEYARD_FARM -> text.append(
                            "grape blocks, varieties, and planned harvest windows are editable. "
                                    + "Plans do not create harvest lots, crush records, stock, "
                                    + "deliveries, or winery production.\n\n");
                    case HAY_FARM -> text.append(
                            "hay cuttings can be planned by field, forage crop, number, and "
                                    + "target dates. Stages are editable, but entries do not "
                                    + "create bale inventory, feed rations, quality tests, "
                                    + "stock, or orders.\n\n");
                    case HOPS_FARM -> text.append(
                            "hop yards can be planned by variety, stringing date, and "
                                    + "harvest window. Stages are editable, but entries do not "
                                    + "record actual yield, drying, pelletizing, contracts, "
                                    + "stock, or orders.\n\n");
                    case GRAIN_FARM -> text.append(
                            "grain fields can be planned by crop, previous crop, seeding "
                                    + "date, and harvest window. Stages are editable, but "
                                    + "plans do not record actual yield, grade, moisture, "
                                    + "storage, contracts, stock, or orders.\n\n");
                    case SEED_FARM -> text.append(
                            "seed fields can be planned by crop, flowering window, "
                                    + "harvest window, and manual isolation notes. Stages "
                                    + "are editable, but plans do not calculate isolation, "
                                    + "certify seed, or create lots, stock, or orders.\n\n");
                    case NUT_ORCHARD -> text.append(
                            "nut orchard blocks can be planned by crop, variety, pollinizer, "
                                    + "floor preparation, and harvest window. Stages are "
                                    + "editable, but pollinizer compatibility is not checked "
                                    + "and plans do not create yield, stock, or orders.\n\n");
                    case VEGETABLE_FARM -> text.append(
                            "vegetable plantings can be planned by bed, crop, succession, "
                                    + "and harvest window. Dates and stages are grower-entered; "
                                    + "plans do not predict harvests or create yield, stock, "
                                    + "or orders.\n\n");
                    case AQUACULTURE_FARM -> text.append(
                            "aquatic cohorts can be planned by species, growing site, "
                                    + "stocking date, and harvest window. Stages are editable, "
                                    + "but plans do not confirm harvest-area status, licensing, "
                                    + "food safety, traceability, stock, or orders.\n\n");
                    case CHRISTMAS_TREE_FARM -> text.append(
                            "Christmas tree blocks can be planned by species, planting year, "
                                    + "shearing date, target sale season, and intended channel. "
                                    + "Plans do not record actual cut counts or verify grower "
                                    + "licensing, inspections, export clearance, stock, or orders.\n\n");
                    case APIARY_FARM -> text.append(
                            "Hive checks record dated beekeeper observations by apiary and "
                                    + "hive, with a self-entered status and next check date. "
                                    + "They do not diagnose colony health, verify registration, "
                                    + "manage pollination contracts, or create honey stock and sales.\n\n");
                    case MUSHROOM_FARM -> text.append(
                            "Mushroom batches can be planned by species, growing method, "
                                    + "substrate, site, and a grower-entered fruiting window. "
                                    + "Plans do not forecast fruiting, record actual yield, "
                                    + "control the growing environment, or create stock and sales.\n\n");
                    case CRANBERRY_FARM -> text.append(
                            "Cranberry beds can be planned by variety, acreage, wet or dry "
                                    + "harvest method, and a grower-entered harvest window. "
                                    + "Plans do not schedule flooding, record actual harvest "
                                    + "or yield, or create stock and sales.\n\n");
                    case PUMPKIN_PATCH -> text.append(
                            "Pumpkin patches can be planned by planting and picking windows, "
                                    + "with a pick-your-own, farm stand, or wholesale mode and "
                                    + "visitor-area notes. Plans do not record actual harvests, "
                                    + "admission, bookings, payments, or stock and sales.\n\n");
                    case CUSTOM_FARM -> text.append(
                            "general farm records are shown without flower-specific planning.\n\n");
                }
            }
            if (selected.isEmpty()) {
                text.append("Choose at least one farm enterprise above.\n\n");
            }
            if (selectedWorkspace == FarmWorkspace.CUSTOM_FARM
                    && selected.size() == 1
                    && selected.contains(FarmEnterprise.CUT_FLOWERS)) {
                text.append("Custom farm still has the existing Cut flowers default. "
                        + "Choose the enterprise you run and clear Cut flowers if needed.\n\n");
            }
            if (selected.contains(FarmEnterprise.CUT_FLOWERS)) {
                text.append("Cut flowers: stem-based season planning, harvest, inventory, "
                        + "orders, Market Day, and farm lessons are available now.\n\n");
            }
            if (selected.contains(FarmEnterprise.FLORAL_RETAIL)) {
                text.append("Flower shop: inventory, customer orders, event services, and design "
                        + "references are available. Bouquet recipes do not assemble stock automatically.\n\n");
            }
            if (selected.stream().anyMatch(type -> type != FarmEnterprise.CUT_FLOWERS
                    && type != FarmEnterprise.FLORAL_RETAIL)) {
                text.append("Other enterprises: general inventory, ");
                if (selectedWorkspace != FarmWorkspace.NURSERY_FARM
                        && selectedWorkspace != FarmWorkspace.LIVESTOCK_FARM
                        && selectedWorkspace != FarmWorkspace.POULTRY_FARM
                        && selectedWorkspace != FarmWorkspace.DAIRY_FARM
                        && selectedWorkspace != FarmWorkspace.VINEYARD_FARM
                        && selectedWorkspace != FarmWorkspace.HAY_FARM
                        && selectedWorkspace != FarmWorkspace.HOPS_FARM
                        && selectedWorkspace != FarmWorkspace.GRAIN_FARM
                        && selectedWorkspace != FarmWorkspace.SEED_FARM
                        && selectedWorkspace != FarmWorkspace.NUT_ORCHARD
                        && selectedWorkspace != FarmWorkspace.VEGETABLE_FARM
                        && selectedWorkspace != FarmWorkspace.AQUACULTURE_FARM
                        && selectedWorkspace != FarmWorkspace.CHRISTMAS_TREE_FARM
                        && selectedWorkspace != FarmWorkspace.APIARY_FARM
                        && selectedWorkspace != FarmWorkspace.MUSHROOM_FARM
                        && selectedWorkspace != FarmWorkspace.CRANBERRY_FARM
                        && selectedWorkspace != FarmWorkspace.PUMPKIN_PATCH) {
                    text.append("harvest, ");
                }
                text.append("orders, and notes "
                        + "accept custom names and units. Potato field, apple block, berry "
                        + "picking-window, vineyard block, hay cutting, hop yard, grain field, seed field, nut block, vegetable planting, aquatic cohort, Christmas tree block, hive check, mushroom batch, cranberry bed, pumpkin patch, nursery batch, herd group planning, "
                        + "egg collections, and milk collections "
                        + "are available in their workspaces. Plans do not automatically "
                        + "create stock or orders; crop grading, packout, and storage need "
                        + "separate workflows.\n\n");
            }
            if (selected.contains(FarmEnterprise.CANNABIS)) {
                text.append("Cannabis cultivation can track planning batches, areas, stages, "
                        + "plant counts, and target harvest dates. Stock and customer orders "
                        + "use the shared records. There is no cannabis-specific compliance "
                        + "or regulated reporting workflow.\n\n");
            }
            if (selected.contains(FarmEnterprise.INDUSTRIAL_HEMP)) {
                text.append("Industrial hemp: current records are generic only; "
                        + "specialized workflows are planned.\n\n");
            }
            if (selected.contains(FarmEnterprise.ROW_CROPS)) {
                text.append("Row crops: Potato farm has a field planning notebook. "
                        + "General harvest, stock, and order records remain separate; "
                        + "weight and pack conversion and grading are planned.\n\n");
            }
            if (selected.contains(FarmEnterprise.ORCHARDS)) {
                text.append("Orchards: Apple orchard has a block planning notebook. "
                        + "General harvest, stock, and order records remain separate; "
                        + "bin, grading, and cold-storage workflows are planned.\n\n");
            }
            if (selected.contains(FarmEnterprise.BERRIES)) {
                text.append("Berries: Berry farm has a block and picking-window notebook. "
                        + "Harvest, stock, and order records remain separate; weight conversion, "
                        + "packout, and U-pick booking are not included.\n\n");
            }
            if (selected.contains(FarmEnterprise.NURSERY)) {
                text.append("Nursery: Nursery farm has a live-plant growing-batch notebook. "
                        + "Stock and orders remain separate; plant loss reconciliation, "
                        + "licensing, inspections, and shipment certification are not included.\n\n");
            }
            text.append("State is profile context. Irrigation advice remains tailored to "
                    + "Port Orchard / Kitsap; it is hidden outside the rose workspace, and "
                    + "selecting another state does not change it.");
            if (state.getSelectedItem() == FarmState.CA) {
                text.append(" California is for later pilot planning.");
            }
            readiness.setText(text.toString());
            readiness.setCaretPosition(0);
            sizeReadinessToText();
            boolean flowerSelected = selected.contains(FarmEnterprise.CUT_FLOWERS)
                    || selected.contains(FarmEnterprise.FLORAL_RETAIL);
            offerings.values().forEach(box -> box.setEnabled(editable && flowerSelected));
        }

        private void sizeReadinessToText() {
            Insets insets = readiness.getInsets();
            int contentWidth = readiness.getWidth() > 0
                    ? readiness.getWidth() - insets.left - insets.right : 660;
            contentWidth = Math.max(120, contentWidth);
            FontMetrics metrics = readiness.getFontMetrics(readiness.getFont());
            int lines = 0;
            for (String paragraph : readiness.getText().split("\\n", -1)) {
                lines++;
                int usedWidth = 0;
                for (String word : paragraph.split("\\s+")) {
                    int wordWidth = metrics.stringWidth(word);
                    int spaceWidth = usedWidth == 0 ? 0 : metrics.charWidth(' ');
                    if (usedWidth > 0 && usedWidth + spaceWidth + wordWidth > contentWidth) {
                        lines++;
                        usedWidth = wordWidth;
                    } else {
                        usedWidth += spaceWidth + wordWidth;
                    }
                }
            }
            int rows = Math.max(5, lines);
            if (readiness.getRows() != rows) {
                readiness.setRows(rows);
                revalidate();
            }
        }

        private static JTextArea text(String value, int rows) {
            JTextArea area = new JTextArea(value, rows, 48);
            area.setEditable(false);
            area.setOpaque(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFocusable(false);
            return area;
        }
    }
}

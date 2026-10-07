package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.gui.view.InventoryItemFormView;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

/** Add-inventory workflow using the same item form and validation as editing. */
public class AddItemTab implements FlowerFarmTab {

    private final InventoryItemPresenter presenter;
    private final TabHost host;
    private final FarmDialogs dialogs;

    private JPanel panel;
    private InventoryItemFormView formView;
    private JButton addBtn;
    private JButton resetBtn;
    private JButton openExistingBtn;
    private JButton returnToStockQueueBtn;
    private JButton applyPresetBtn;
    private JComboBox<String> presetBox;
    private JCheckBox keepSetupBox;
    private JLabel accessBadge;
    private JLabel feedbackBadge;
    private JLabel unitProfitLabel;
    private JLabel marginRateLabel;
    private JLabel openingValueLabel;
    private boolean canWrite = true;
    private boolean savingItem;
    private InventoryItemPresenter.ItemDraft initialDraft;
    private FarmWorkspace workspace = FarmWorkspace.ROSE_AND_EVENTS;
    private java.util.Set<FarmWorkspace> enabledWorkspaces =
            java.util.Set.of(FarmWorkspace.ROSE_AND_EVENTS);
    private String duplicateProductName;
    private String missingQueueProduct;

    public AddItemTab(InventoryService inventoryService, TabHost host) {
        this(inventoryService, host, FarmDialogs.swing());
    }

    public AddItemTab(InventoryService inventoryService, TabHost host,
                      FarmDialogs dialogs) {
        this.presenter = new InventoryItemPresenter(inventoryService);
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
    }

    /** Choose the inventory entry presentation before building its Swing form. */
    public void setWorkspace(FarmWorkspace workspace) {
        if (panel != null) {
            throw new IllegalStateException("Set the farm workspace before opening Add Item.");
        }
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.enabledWorkspaces = java.util.Set.of(workspace);
    }

    /** Include starters for every selected farm type in a combined operation. */
    public void setEnabledWorkspaces(java.util.Set<FarmWorkspace> enabledWorkspaces) {
        if (panel != null) {
            throw new IllegalStateException("Choose farm types before opening Add Item.");
        }
        java.util.Objects.requireNonNull(enabledWorkspaces, "enabledWorkspaces");
        if (!enabledWorkspaces.contains(workspace)) {
            throw new IllegalArgumentException("Selected types must include the primary farm.");
        }
        this.enabledWorkspaces = java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(enabledWorkspaces));
    }

    @Override
    public String getTabTitle() {
        return "Add Item";
    }

    @Override
    public String getDescription() {
        return "Add a new item to the inventory";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildForm();
        }
        return panel;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        boolean writable = canWrite && !savingItem;
        if (formView != null) {
            formView.setWritable(writable);
        }
        GuiPermissions.setWritable(
                writable, addBtn, resetBtn, applyPresetBtn, presetBox, keepSetupBox);
        if (openExistingBtn != null) {
            openExistingBtn.setEnabled(!savingItem && duplicateProductName != null);
        }
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · editing available" : "VIEWER · read only",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    /** Protects a partially entered item and a save that has not finished. */
    public String applicationExitWarning() {
        if (savingItem) {
            return "Add Item has an inventory save in progress.";
        }
        if (formView != null && !formView.draft().equals(initialDraft)) {
            return "Add Item has an unsaved inventory item draft.";
        }
        return null;
    }

    public boolean hasPendingSave() {
        return savingItem;
    }

    private void buildForm() {
        panel = FarmUi.page();
        accessBadge = FarmUi.statusBadge(
                "HAND / OWNER · editing available", FarmUi.Tone.SUCCESS);
        panel.add(FarmUi.pageHeader(
                "Add inventory item",
                "Start with a crop or product. Add prices and the stock you have today.",
                accessBadge), BorderLayout.NORTH);

        formView = new InventoryItemFormView();
        configureWorkspaceForm();
        presetBox = new JComboBox<>(starterOptions());
        presetBox.setName("inventory.itemPreset");
        presetBox.getAccessibleContext().setAccessibleName(
                workspace == FarmWorkspace.NURSERY_FARM
                        ? "Nursery product starter library"
                        : isGeneralWorkspace() ? "Farm item starter library"
                                : "Flower product starter library");
        presetBox.getAccessibleContext().setAccessibleDescription(
                workspace == FarmWorkspace.NURSERY_FARM
                        ? "Choose a live plant product or general farm item setup."
                        : isGeneralWorkspace() ? "Choose a general farm item setup."
                                : "Choose a common flower product or general farm item setup.");
        applyPresetBtn = FarmUi.secondaryButton("Use starter");
        applyPresetBtn.addActionListener(event -> {
            String preset = String.valueOf(presetBox.getSelectedItem());
            formView.applyPreset(preset);
            FarmUi.updateStatusBadge(feedbackBadge,
                    preset + " defaults applied", FarmUi.Tone.INFO);
        });
        addBtn = FarmUi.primaryButton("Add item to inventory");
        addBtn.setMnemonic('A');
        addBtn.addActionListener(event -> submitItem());
        resetBtn = FarmUi.secondaryButton("Reset form");
        resetBtn.addActionListener(event -> {
            formView.reset();
            applyWorkspaceDefaults();
            clearDuplicateHandoff();
            FarmUi.updateStatusBadge(feedbackBadge, "Ready for a new item", FarmUi.Tone.NEUTRAL);
        });
        openExistingBtn = FarmUi.secondaryButton("Open existing item");
        openExistingBtn.setName("inventory.openExistingItem");
        openExistingBtn.setEnabled(false);
        openExistingBtn.setToolTipText(
                "Open Inventory with the matching product selected; no duplicate was added.");
        openExistingBtn.getAccessibleContext().setAccessibleName(
                "Open the existing inventory item");
        openExistingBtn.addActionListener(event -> openExistingItem());
        returnToStockQueueBtn = FarmUi.secondaryButton("Return to stock queue");
        returnToStockQueueBtn.setName("inventory.returnToStockQueue");
        returnToStockQueueBtn.setVisible(false);
        returnToStockQueueBtn.setToolTipText(
                "Return to the focused Inventory queue that identified this missing product.");
        returnToStockQueueBtn.getAccessibleContext().setAccessibleName(
                "Return to focused inventory stock queue");
        returnToStockQueueBtn.addActionListener(event -> returnToStockQueue());
        keepSetupBox = new JCheckBox("Keep setup for next item");
        keepSetupBox.setName("inventory.keepSetup");
        keepSetupBox.setOpaque(false);
        keepSetupBox.setToolTipText(
                "After saving, keep category, selling unit, price, and cost only.");
        keepSetupBox.getAccessibleContext().setAccessibleName(
                "Keep shared inventory setup after saving");
        keepSetupBox.getAccessibleContext().setAccessibleDescription(
                "Keeps category, selling unit, price, and cost. Clears name, variety, quantity, and notes.");
        feedbackBadge = FarmUi.wrappingStatusBadge("Ready for a new item", FarmUi.Tone.NEUTRAL);
        feedbackBadge.getAccessibleContext().setAccessibleName("Add inventory item status");
        formView.setValidationCorrectionListener(() -> {
            clearDuplicateHandoff();
            showSaveState("Changed · ready to retry",
                    "The highlighted value changed. Submit the item again to validate and save it.",
                    FarmUi.Tone.INFO);
        });
        formView.setDraftChangeListener(this::updateEconomicsPreview);
        formView.quantityField().addActionListener(event -> submitItem());

        unitProfitLabel = economicsValue("inventory.unitProfit", "$0.00");
        marginRateLabel = economicsValue("inventory.marginRate", "—");
        openingValueLabel = economicsValue("inventory.openingValue", "$0.00");
        unitProfitLabel.getAccessibleContext().setAccessibleDescription(
                "Selling price minus unit cost");
        marginRateLabel.getAccessibleContext().setAccessibleDescription(
                "Gross margin before labor and overhead");
        openingValueLabel.getAccessibleContext().setAccessibleDescription(
                "Opening quantity multiplied by selling price");
        JPanel economics = new JPanel(new GridLayout(3, 1, FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        economics.setName("inventory.economicsPreview");
        economics.setOpaque(false);
        economics.add(FarmUi.metricCard(
                "Gross profit / unit", unitProfitLabel, null, FarmUi.Tone.SUCCESS));
        economics.add(FarmUi.metricCard(
                "Gross margin", marginRateLabel, null, FarmUi.Tone.INFO));
        economics.add(FarmUi.metricCard(
                "Opening sell value", openingValueLabel, null, FarmUi.Tone.NEUTRAL));

        JPanel actions = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actions.setName("inventory.itemActions");
        actions.setOpaque(false);
        JPanel repeatMode = new JPanel(new BorderLayout(0, 4));
        repeatMode.setOpaque(false);
        repeatMode.add(FarmUi.toolbar(keepSetupBox), BorderLayout.NORTH);
        repeatMode.add(FarmUi.wrappingHint(
                "Adding a few similar products? Keep their shared setup."), BorderLayout.CENTER);
        actions.add(repeatMode, BorderLayout.NORTH);
        JPanel actionRows = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actionRows.setOpaque(false);
        actionRows.add(FarmUi.toolbar(addBtn, resetBtn), BorderLayout.NORTH);
        actionRows.add(FarmUi.toolbar(openExistingBtn, returnToStockQueueBtn), BorderLayout.SOUTH);
        actions.add(actionRows, BorderLayout.CENTER);
        JPanel feedbackRow = new JPanel(new BorderLayout());
        feedbackRow.setName("inventory.itemFeedback");
        feedbackRow.setOpaque(false);
        feedbackRow.add(feedbackBadge, BorderLayout.CENTER);
        actions.add(feedbackRow, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        JPanel itemWorkspace = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        itemWorkspace.setOpaque(false);
        JPanel starter = new JPanel(new BorderLayout(0, 4));
        starter.setName("inventory.itemStarter");
        starter.setOpaque(false);
        starter.add(FarmUi.toolbar(new JLabel("Product starter"), presetBox, applyPresetBtn),
                BorderLayout.NORTH);
        starter.add(FarmUi.wrappingHint("A head start; your entered prices stay intact."),
                BorderLayout.CENTER);
        itemWorkspace.add(starter, BorderLayout.NORTH);
        itemWorkspace.add(new ItemDetails(formView, economics), BorderLayout.CENTER);
        content.add(FarmUi.section(
                "1 · Product & stock",
                isGeneralWorkspace()
                        ? "Enter your own product or supply. Fields marked * are required."
                        : "Choose a starter, or make it your own. Fields marked * are required.",
                itemWorkspace), BorderLayout.CENTER);
        content.add(FarmUi.section(
                "2 · Save to your inventory",
                "Zero price or stock is fine while planning. Press Enter from Quantity to save.",
                actions), BorderLayout.SOUTH);
        JPanel body = new ItemBody(content);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("inventory.itemScroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(body);
        updateEconomicsPreview();
        initialDraft = formView.draft();
    }

    private String[] starterOptions() {
        if (enabledWorkspaces.size() > 1) {
            java.util.LinkedHashSet<String> starters = new java.util.LinkedHashSet<>();
            for (FarmWorkspace selected : enabledWorkspaces) {
                java.util.Collections.addAll(starters, starterOptionsFor(selected));
            }
            return starters.toArray(String[]::new);
        }
        return starterOptionsFor(workspace);
    }

    private static String[] starterOptionsFor(FarmWorkspace workspace) {
        if (workspace == FarmWorkspace.NURSERY_FARM) {
            return new String[]{"Potted plant", "Seedling tray", "Bare-root plant",
                    "Farm supply", "Tool or equipment"};
        }
        if (isGeneralWorkspace(workspace)) {
            return new String[]{"Farm supply", "Tool or equipment"};
        }
        if (workspace == FarmWorkspace.WILDFLOWER_FARM) {
            return new String[]{"Cut flower / stem", "Market bouquet",
                    "Farm supply", "Tool or equipment"};
        }
        return new String[]{
                "Cut flower / stem", "Dahlia stem", "Nootka rose stem", "Peony stem",
                "Sunflower stem", "Market bouquet", "Wedding flower bucket",
                "Flower subscription", "Farm supply", "Tool or equipment"
        };
    }

    private boolean isGeneralWorkspace() {
        return isGeneralWorkspace(workspace);
    }

    private static boolean isGeneralWorkspace(FarmWorkspace workspace) {
        return workspace == FarmWorkspace.CANNABIS_FARM
                || workspace == FarmWorkspace.POTATO_FARM
                || workspace == FarmWorkspace.APPLE_ORCHARD
                || workspace == FarmWorkspace.BERRY_FARM
                || workspace == FarmWorkspace.NURSERY_FARM
                || workspace == FarmWorkspace.LIVESTOCK_FARM
                || workspace == FarmWorkspace.POULTRY_FARM
                || workspace == FarmWorkspace.DAIRY_FARM
                || workspace == FarmWorkspace.VINEYARD_FARM
                || workspace == FarmWorkspace.HAY_FARM
                || workspace == FarmWorkspace.HOPS_FARM
                || workspace == FarmWorkspace.GRAIN_FARM
                || workspace == FarmWorkspace.SEED_FARM
                || workspace == FarmWorkspace.NUT_ORCHARD
                || workspace == FarmWorkspace.VEGETABLE_FARM
                || workspace == FarmWorkspace.AQUACULTURE_FARM
                || workspace == FarmWorkspace.CHRISTMAS_TREE_FARM
                || workspace == FarmWorkspace.APIARY_FARM
                || workspace == FarmWorkspace.MUSHROOM_FARM
                || workspace == FarmWorkspace.CRANBERRY_FARM
                || workspace == FarmWorkspace.PUMPKIN_PATCH
                || workspace == FarmWorkspace.CUSTOM_FARM;
    }

    private void configureWorkspaceForm() {
        if (!enabledWorkspaces.contains(FarmWorkspace.ROSE_AND_EVENTS)
                && !enabledWorkspaces.contains(FarmWorkspace.FLOWER_SHOP)
                && (workspace == FarmWorkspace.WILDFLOWER_FARM || isGeneralWorkspace())) {
            hideRoseVariety();
        }
        if (isGeneralWorkspace()) {
            applyWorkspaceDefaults();
            JTextField name = textField(formView, "Item name");
            JTextField notes = textField(formView, "Item notes");
            if (name != null) {
                FarmUi.entryControl(name, "e.g. Product or farm supply",
                        "Use the name your crew and customers recognize.");
            }
            if (notes != null) {
                FarmUi.entryControl(notes, "e.g. Storage area · batch note",
                        "Optional storage, source, or packing notes.");
            }
        }
    }

    private void applyWorkspaceDefaults() {
        if (!isGeneralWorkspace() || formView == null) {
            return;
        }
        InventoryItemPresenter.ItemDraft draft = formView.draft();
        formView.setDraft(new InventoryItemPresenter.ItemDraft(
                draft.name(), "Other", "", draft.price(), "Per Unit",
                draft.cost(), draft.quantity(), draft.notes()));
    }

    private void hideRoseVariety() {
        JPanel notesField = null;
        for (Component component : formView.getComponents()) {
            if (!(component instanceof JPanel field)) {
                continue;
            }
            for (Component fieldPart : field.getComponents()) {
                if (fieldPart instanceof JLabel label) {
                    if (label.getText().startsWith("Rose variety")) {
                        field.setVisible(false);
                    } else if (label.getText().startsWith("Notes")) {
                        notesField = field;
                    }
                }
            }
        }
        if (notesField != null && formView.getLayout() instanceof GridBagLayout layout) {
            GridBagConstraints constraints = layout.getConstraints(notesField);
            constraints.gridwidth = 2;
            layout.setConstraints(notesField, constraints);
        }
    }

    private static JTextField textField(Container parent, String accessibleName) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTextField field
                    && accessibleName.equals(field.getAccessibleContext().getAccessibleName())) {
                return field;
            }
            if (child instanceof Container nested) {
                JTextField found = textField(nested, accessibleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Keep paired fields readable, moving the preview beneath them on a compact page. */
    private static final class ItemDetails extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int SIDE_WIDTH = 225;
        private static final int STACK_WIDTH = 900;
        private final JComponent form;
        private final JPanel economics;

        private ItemDetails(JComponent form, JPanel economics) {
            super(null);
            this.form = form;
            this.economics = economics;
            setName("inventory.itemDetails");
            setOpaque(false);
            add(form);
            add(economics);
        }

        private boolean stacked() { return getWidth() > 0 && getWidth() < STACK_WIDTH; }

        private int cardHeight() {
            int height = 0;
            for (Component card : economics.getComponents()) {
                height = Math.max(height, card.getPreferredSize().height);
            }
            return height;
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = form.getPreferredSize();
            int previewHeight = cardHeight();
            int height = stacked() ? preferred.height + FarmUi.SECTION_GAP + previewHeight
                    : Math.max(preferred.height, previewHeight * 3 + FarmUi.CONTROL_GAP * 2);
            return new Dimension(preferred.width + FarmUi.SECTION_GAP + SIDE_WIDTH, height);
        }

        @Override public void doLayout() {
            GridLayout cards = (GridLayout) economics.getLayout();
            if (stacked()) {
                cards.setRows(1);
                cards.setColumns(3);
                int previewHeight = cardHeight();
                int formHeight = Math.max(0, getHeight() - FarmUi.SECTION_GAP - previewHeight);
                form.setBounds(0, 0, getWidth(), formHeight);
                economics.setBounds(0, formHeight + FarmUi.SECTION_GAP, getWidth(), previewHeight);
            } else {
                cards.setRows(3);
                cards.setColumns(1);
                int formWidth = Math.max(0, getWidth() - FarmUi.SECTION_GAP - SIDE_WIDTH);
                form.setBounds(0, 0, formWidth, getHeight());
                economics.setBounds(formWidth + FarmUi.SECTION_GAP, 0, SIDE_WIDTH, getHeight());
            }
        }

        @Override public void setBounds(int x, int y, int width, int height) {
            boolean wasStacked = stacked();
            super.setBounds(x, y, width, height);
            if (wasStacked != stacked()) revalidate();
        }
    }

    private static final class ItemBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        private ItemBody(JComponent content) {
            super(new BorderLayout());
            setName("inventory.itemBody");
            setOpaque(false);
            add(content, BorderLayout.NORTH);
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

    private JLabel economicsValue(String name, String text) {
        JLabel label = new JLabel(text);
        label.setName(name);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        label.getAccessibleContext().setAccessibleName(text);
        return label;
    }

    private void updateEconomicsPreview() {
        if (formView == null || unitProfitLabel == null) {
            return;
        }
        InventoryItemPresenter.ItemDraft draft = formView.draft();
        Double price = parseNonNegative(draft.price());
        Double cost = parseNonNegative(draft.cost());
        Integer quantity = parseNonNegativeWholeNumber(draft.quantity());
        if (price == null || cost == null || quantity == null) {
            setEconomicsValue(unitProfitLabel, "Check values");
            setEconomicsValue(marginRateLabel, "—");
            setEconomicsValue(openingValueLabel, "—");
            return;
        }
        double profit = price - cost;
        String profitText = profit < 0
                ? String.format(Locale.US, "−$%,.2f", Math.abs(profit))
                : String.format(Locale.US, "$%,.2f", profit);
        setEconomicsValue(unitProfitLabel, profitText);
        setEconomicsValue(marginRateLabel, price == 0
                ? "—" : String.format(Locale.US, "%.0f%%", profit / price * 100));
        setEconomicsValue(openingValueLabel,
                String.format(Locale.US, "$%,.2f", price * quantity));
        unitProfitLabel.setForeground(profit < 0
                ? FarmUi.toneForegroundColor(FarmUi.Tone.DANGER)
                : FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
    }

    private void setEconomicsValue(JLabel label, String value) {
        label.setText(value);
        label.getAccessibleContext().setAccessibleName(value);
        if (label == unitProfitLabel && !value.startsWith("−")) {
            label.setForeground(FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        }
    }

    private static Double parseNonNegative(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) && value >= 0 ? value : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Integer parseNonNegativeWholeNumber(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 ? value : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void submitItem() {
        if (savingItem) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "add inventory items")) {
            return;
        }
        clearDuplicateHandoff();
        formView.clearValidation();
        try {
            InventoryItemPresenter.ItemDraft draft = formView.draft();
            presenter.toItem(draft);
            startItemSave(draft, keepSetupBox.isSelected());
        } catch (InventoryItemPresenter.FieldValidationException exception) {
            showValidationFailure(exception);
        }
    }

    void startItemSave(InventoryItemPresenter.ItemDraft draft, boolean keepSetup) {
        if (savingItem) {
            return;
        }
        clearDuplicateHandoff();
        savingItem = true;
        addBtn.setText("Saving item…");
        applyRolePermissions(canWrite);
        showSaveState("Saving item…",
                "The captured item details are being saved in the background. "
                        + "Editing is locked until this one save finishes.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Saving “" + draft.name().trim() + "” to Inventory…");
        }

        new javax.swing.SwingWorker<Item, Void>() {
            @Override
            protected Item doInBackground() {
                return presenter.add(draft);
            }

            @Override
            protected void done() {
                try {
                    completeItemSave(get(), keepSetup);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failItemSave(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    if (cause instanceof InventoryService.DuplicateItemNameException duplicate) {
                        finishSaveControls();
                        showDuplicateFailure(duplicate);
                    } else if (cause instanceof InventoryItemPresenter.FieldValidationException validation) {
                        finishSaveControls();
                        showValidationFailure(validation);
                    } else {
                        failItemSave(cause);
                    }
                } catch (RuntimeException exception) {
                    failItemSave(exception);
                }
            }
        }.execute();
    }

    private void completeItemSave(Item saved, boolean keepSetup) {
        finishSaveControls();
        clearDuplicateHandoff();
        if (keepSetup) {
            formView.resetForSimilarItem();
        } else {
            formView.reset();
            applyWorkspaceDefaults();
        }
        String message = "Added “" + saved.getName() + "”"
                + (keepSetup ? " · setup kept" : "");
        showSaveState(message,
                "The item was saved once. " + (keepSetup
                        ? "Shared category, unit, price, and cost are ready for the next item."
                        : "The form is ready for a new item."), FarmUi.Tone.SUCCESS);
        String refreshGuidance = "";
        if (host != null) {
            try {
                host.refreshAll();
            } catch (RuntimeException refreshFailure) {
                refreshGuidance = " Refresh Inventory manually. "
                        + FarmUi.actionableError("Refreshing the saved inventory item", refreshFailure);
            }
            host.setStatus("Added “" + saved.getName()
                    + "” to inventory. " + (keepSetup
                    ? "Shared setup is ready for the next item."
                    : "The form is ready for another item.") + refreshGuidance);
        }
    }

    private void failItemSave(Throwable failure) {
        finishSaveControls();
        String guidance = FarmUi.actionableError("Adding this inventory item", failure);
        showSaveState("Item was not saved · retry",
                guidance + " Every entered field and the repeat-entry choice were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(guidance + " Item details were kept; retry when ready.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, guidance,
                    "Item not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showValidationFailure(InventoryItemPresenter.FieldValidationException exception) {
        formView.markError(exception.field(), exception.getMessage());
        showSaveState("Check the highlighted field",
                exception.getMessage() + " Every entered item detail and the repeat-entry choice "
                        + "were kept. Correct the highlighted value, then submit again.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Item not saved. " + exception.getMessage()
                    + " Correct the highlighted value; every other detail was kept.");
        }
    }

    private void showDuplicateFailure(InventoryService.DuplicateItemNameException duplicate) {
        duplicateProductName = duplicate.itemName();
        formView.markError("name", duplicate.getMessage());
        showSaveState("“" + duplicate.itemName() + "” already exists · no duplicate added",
                duplicate.getMessage() + " Every entered field and the repeat-entry choice "
                        + "were kept. Choose Open existing item to continue safely.",
                FarmUi.Tone.WARNING);
        openExistingBtn.setEnabled(true);
        if (host != null) {
            host.setStatus("“" + duplicate.itemName()
                    + "” is already in Inventory; no duplicate was added. Open the existing item "
                    + "to count, replenish, or edit it.");
        }
    }

    private void openExistingItem() {
        if (host == null || duplicateProductName == null) {
            return;
        }
        String product = duplicateProductName;
        host.showInventoryProducts(List.of(product));
        host.setStatus("Opened the existing Inventory item “" + product
                + "”. Count, replenish, or edit this row instead of creating a duplicate.");
    }

    /** Prepare one missing queue product without overwriting an unfinished item draft. */
    public void prepareMissingItem(String productName) {
        getUIComponent();
        String safeName = productName == null ? "" : productName.trim();
        if (safeName.isEmpty()) {
            showSaveState("Enter the missing product name",
                    "The existing item draft was kept. Enter a product name before saving.",
                    FarmUi.Tone.WARNING);
            return;
        }
        missingQueueProduct = safeName;
        returnToStockQueueBtn.setVisible(true);
        boolean prepared = formView.prepareNameIfBlank(safeName);
        String currentName = formView.draft().name().trim();
        if (prepared) {
            showSaveState("Missing product prepared · “" + safeName + "”",
                    "Complete its category, unit, price, cost, and starting quantity, then save once.",
                    FarmUi.Tone.INFO);
        } else {
            showSaveState("Existing item draft kept",
                    "“" + currentName + "” was already being entered, so “" + safeName
                            + "” did not replace it. Finish or reset that draft before adding the missing product.",
                    FarmUi.Tone.WARNING);
        }
    }

    private void returnToStockQueue() {
        if (host == null) {
            return;
        }
        host.selectTab("Inventory");
        host.setStatus(missingQueueProduct == null
                ? "Returned to Inventory stock and counts."
                : "Returned to the focused stock queue for “" + missingQueueProduct
                + "”. Refresh Inventory if the saved product is not visible yet.");
    }

    private void clearDuplicateHandoff() {
        duplicateProductName = null;
        if (openExistingBtn != null) {
            openExistingBtn.setEnabled(false);
        }
    }

    private void finishSaveControls() {
        savingItem = false;
        addBtn.setText("Add item to inventory");
        applyRolePermissions(canWrite);
    }

    private void showSaveState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(feedbackBadge, message, tone);
        feedbackBadge.setToolTipText(description);
        feedbackBadge.getAccessibleContext().setAccessibleDescription(description);
    }
}

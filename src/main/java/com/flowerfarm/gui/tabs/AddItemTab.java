package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.gui.view.InventoryItemFormView;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

/** Add-inventory workflow using the same item form and validation as editing. */
public class AddItemTab implements FlowerFarmTab {

    private final InventoryItemPresenter presenter;
    private final TabHost host;

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
    private boolean canWrite = true;
    private boolean savingItem;
    private String duplicateProductName;
    private String missingQueueProduct;

    public AddItemTab(InventoryService inventoryService, TabHost host) {
        this.presenter = new InventoryItemPresenter(inventoryService);
        this.host = host;
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

    private void buildForm() {
        panel = FarmUi.page();
        accessBadge = FarmUi.statusBadge(
                "HAND / OWNER · editing available", FarmUi.Tone.SUCCESS);
        panel.add(FarmUi.pageHeader(
                "Add inventory item",
                "Create a flower, supply, equipment, or service item with a starting quantity.",
                accessBadge), BorderLayout.NORTH);

        formView = new InventoryItemFormView();
        presetBox = new JComboBox<>(new String[]{
                "Cut flower / stem", "Market bouquet", "Farm supply", "Tool or equipment"
        });
        presetBox.setName("inventory.itemPreset");
        presetBox.getAccessibleContext().setAccessibleName("Common farm item preset");
        applyPresetBtn = FarmUi.secondaryButton("Apply preset");
        applyPresetBtn.addActionListener(event -> {
            String preset = String.valueOf(presetBox.getSelectedItem());
            formView.applyPreset(preset);
            FarmUi.updateStatusBadge(feedbackBadge,
                    preset + " defaults applied", FarmUi.Tone.INFO);
        });
        addBtn = FarmUi.primaryButton("Add item to inventory");
        addBtn.addActionListener(event -> submitItem());
        resetBtn = FarmUi.secondaryButton("Reset form");
        resetBtn.addActionListener(event -> {
            formView.reset();
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
        feedbackBadge = FarmUi.statusBadge("Ready for a new item", FarmUi.Tone.NEUTRAL);
        feedbackBadge.getAccessibleContext().setAccessibleName("Add inventory item status");
        formView.setValidationCorrectionListener(() -> {
            clearDuplicateHandoff();
            showSaveState("Changed · ready to retry",
                    "The highlighted value changed. Submit the item again to validate and save it.",
                    FarmUi.Tone.INFO);
        });
        formView.quantityField().addActionListener(event -> submitItem());

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JLabel guidance = FarmUi.formHint(
                "Required fields are marked *. Prices and quantities must be zero or greater.");
        guidance.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(guidance);
        actions.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        JPanel repeatMode = FarmUi.toolbar(
                keepSetupBox,
                FarmUi.formHint("Keeps category, unit, price, and cost; clears product-specific fields."));
        repeatMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(repeatMode);
        actions.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        JPanel actionRow = FarmUi.toolbar(
                addBtn, resetBtn, openExistingBtn, returnToStockQueueBtn, feedbackBadge);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(actionRow);

        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        JPanel itemWorkspace = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        itemWorkspace.setOpaque(false);
        itemWorkspace.add(FarmUi.toolbar(
                new JLabel("Quick setup"), presetBox, applyPresetBtn,
                FarmUi.formHint("Presets set category and selling unit; entered prices stay intact.")),
                BorderLayout.NORTH);
        itemWorkspace.add(formView, BorderLayout.CENTER);
        content.add(FarmUi.section(
                "Item details",
                "Choose Flowers/Plants to make the rose-variety list available.",
                itemWorkspace), BorderLayout.CENTER);
        content.add(FarmUi.section(
                "Save item",
                "Press Enter from Quantity or use the button below.",
                actions), BorderLayout.SOUTH);
        panel.add(content, BorderLayout.CENTER);
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
            JOptionPane.showMessageDialog(panel, guidance,
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

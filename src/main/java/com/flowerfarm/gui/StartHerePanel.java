package com.flowerfarm.gui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dashboard banner for an empty cut-flower farm: three concrete next steps.
 */
public final class StartHerePanel extends JPanel {

    private final JButton logHarvest;
    private final JButton importSpreadsheet;
    private final JButton addProduct;
    private final JButton dismiss;

    public StartHerePanel(Consumer<StartHereGuide.Choice> onChoice) {
        Objects.requireNonNull(onChoice, "onChoice");
        setName("dashboard.startHere");
        setOpaque(false);
        setLayout(new BorderLayout(0, FarmUi.CONTROL_GAP));
        getAccessibleContext().setAccessibleName("Start here for a new farm");

        logHarvest = FarmUi.primaryButton("Log today’s first cut");
        logHarvest.setName("startHere.logHarvest");
        logHarvest.setToolTipText("Open Harvest Log. Crop and quantity are enough for the first save.");
        logHarvest.addActionListener(e -> onChoice.accept(StartHereGuide.Choice.LOG_HARVEST));

        importSpreadsheet = FarmUi.secondaryButton("Import spreadsheet…");
        importSpreadsheet.setName("startHere.importSpreadsheet");
        importSpreadsheet.setToolTipText("Import products from a CSV or Excel file you already use.");
        importSpreadsheet.addActionListener(e -> onChoice.accept(StartHereGuide.Choice.IMPORT_SPREADSHEET));

        addProduct = FarmUi.secondaryButton("Add a product");
        addProduct.setName("startHere.addProduct");
        addProduct.setToolTipText("Open Add Item to enter one flower or farm product by hand.");
        addProduct.addActionListener(e -> onChoice.accept(StartHereGuide.Choice.ADD_PRODUCT));

        dismiss = FarmUi.secondaryButton("Not now");
        dismiss.setName("startHere.dismiss");
        dismiss.setToolTipText("Hide this banner. Help > Start here… still opens the same choices.");
        dismiss.addActionListener(e -> onChoice.accept(StartHereGuide.Choice.DISMISS));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setName("startHere.actions");
        row.add(logHarvest);
        row.add(importSpreadsheet);
        row.add(addProduct);
        row.add(dismiss);

        JLabel hint = FarmUi.mutedLabel("Help > Start here… keeps these choices available.");
        hint.setName("startHere.hint");
        JPanel actions = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actions.setOpaque(false);
        actions.add(row, BorderLayout.NORTH);
        actions.add(hint, BorderLayout.SOUTH);

        add(FarmUi.section(StartHereGuide.dashboardTitle(), StartHereGuide.dashboardGuidance(), actions),
                BorderLayout.CENTER);
    }

    JButton logHarvestButton() {
        return logHarvest;
    }

    JButton importSpreadsheetButton() {
        return importSpreadsheet;
    }

    JButton addProductButton() {
        return addProduct;
    }

    JButton dismissButton() {
        return dismiss;
    }
}

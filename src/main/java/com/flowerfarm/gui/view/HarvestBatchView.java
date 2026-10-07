package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;

/**
 * Self-contained batch-harvest input component.
 */
public final class HarvestBatchView extends JPanel {

    private static final String SAMPLE = "# Crop,Quantity[,Unit][,Bed]\n"
            + "Nootka Rose,40\nDamask Rose,25,bunches\nDahlia mix,15,stems,Bed C\n";

    private final JTextArea batchArea;
    private final JTextField dateField;
    private final JTextField unitField;
    private final JTextField bedField;
    private final JTextField notesField;
    private final JButton logButton;
    private final JButton sampleButton;
    private final JLabel statusLabel;
    private final String sampleLines;

    public HarvestBatchView(Runnable logAction) {
        this(logAction, () -> true);
    }

    public HarvestBatchView(Runnable logAction,
                            java.util.function.BooleanSupplier approveSampleReplacement) {
        this(logAction, approveSampleReplacement, SAMPLE);
    }

    public HarvestBatchView(Runnable logAction,
                            java.util.function.BooleanSupplier approveSampleReplacement,
                            String sampleLines) {
        super(new BorderLayout(4, 4));
        this.sampleLines = java.util.Objects.requireNonNull(sampleLines, "sampleLines");
        setOpaque(false);
        dateField = field(LocalDate.now().toString(), 10, "Batch harvest date",
                "Date applied to every row in this batch; use YYYY-MM-DD.");
        unitField = field("stems", 8, "Batch default unit",
                "Used when a batch line does not include its own unit.");
        bedField = field("", 10, "Batch default bed or field",
                "Used when a batch line does not include its own bed or field.");
        notesField = field("", 18, "Batch harvest notes",
                "Notes applied to every row in this batch.");
        add(buildContextPanel(), BorderLayout.NORTH);

        batchArea = new JTextArea(6, 28);
        batchArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        batchArea.getAccessibleContext().setAccessibleName("Batch harvest lines");
        batchArea.getAccessibleContext().setAccessibleDescription(
                "One harvest per line: Crop, Qty, optional Unit, optional Bed");
        batchArea.setToolTipText(
                "Enter your harvests, one per line: Crop, Quantity, optional Unit, optional Bed.");
        add(new JScrollPane(batchArea), BorderLayout.CENTER);

        logButton = FarmUi.primaryButton("Log batch harvest");
        logButton.setName("harvest.batchSubmit");
        logButton.setToolTipText(
                "Creates one harvest entry per non-empty line and updates inventory.");
        logButton.addActionListener(event -> logAction.run());
        sampleButton = FarmUi.secondaryButton("Use sample lines");
        sampleButton.addActionListener(event -> {
            if (approveSampleReplacement.getAsBoolean()) {
                batchArea.setText(this.sampleLines);
            }
        });
        statusLabel = FarmUi.wrappingStatusBadge("Checking batch…", FarmUi.Tone.NEUTRAL);
        statusLabel.setName("harvest.batchStatus");
        statusLabel.getAccessibleContext().setAccessibleName("Batch harvest readiness");
        statusLabel.getAccessibleContext().setAccessibleDescription(
                "Live validation and submission status for the batch harvest lines.");
        JPanel footer = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(logButton, sampleButton), BorderLayout.NORTH);
        footer.add(statusLabel, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    public JTextArea batchArea() {
        return batchArea;
    }

    public JTextField dateField() {
        return dateField;
    }

    public JTextField unitField() {
        return unitField;
    }

    public JTextField bedField() {
        return bedField;
    }

    public JTextField notesField() {
        return notesField;
    }

    public JButton logButton() {
        return logButton;
    }

    public JButton sampleButton() {
        return sampleButton;
    }

    public JLabel statusLabel() {
        return statusLabel;
    }

    private JPanel buildContextPanel() {
        JPanel context = new JPanel(new GridBagLayout());
        context.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        addField(context, constraints, 0, 0, "Date", dateField, 'D');
        addField(context, constraints, 0, 1, "Unit", unitField, 'U');
        addField(context, constraints, 1, 0, "Bed / field", bedField, 'B');
        addField(context, constraints, 1, 1, "Notes", notesField, 'N');
        return context;
    }

    private static void addField(JPanel panel, GridBagConstraints constraints,
                                 int row, int pair, String text,
                                 JTextField field, char mnemonic) {
        JLabel label = new JLabel(text + ":");
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        constraints.gridy = row;
        constraints.gridx = pair * 2;
        constraints.weightx = 0;
        panel.add(label, constraints);
        constraints.gridx = pair * 2 + 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
    }

    private static JTextField field(String value, int columns, String accessibleName,
                                    String description) {
        JTextField field = new JTextField(value, columns);
        field.getAccessibleContext().setAccessibleName(accessibleName);
        field.getAccessibleContext().setAccessibleDescription(description);
        field.setToolTipText(description);
        FarmUi.entryControl(field, "", description);
        return field;
    }
}

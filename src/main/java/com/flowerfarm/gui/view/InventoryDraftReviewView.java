package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/** Read-only review of retained inventory values, including products outside the current list. */
public final class InventoryDraftReviewView extends JPanel {
    private final List<DraftDetails> drafts;
    private final JComboBox<String> draftChoice;
    private final JTextArea details;

    public InventoryDraftReviewView(List<DraftDetails> drafts) {
        super(new BorderLayout(0, FarmUi.CONTROL_GAP));
        this.drafts = List.copyOf(drafts);
        setName("inventory.draftReview");
        setOpaque(false);
        setPreferredSize(new Dimension(580, 300));
        setMinimumSize(new Dimension(320, 220));
        getAccessibleContext().setAccessibleName("Review retained inventory drafts");

        draftChoice = FarmUi.initialFocus(new JComboBox<>(this.drafts.stream()
                .map(DraftDetails::label).toArray(String[]::new)));
        draftChoice.setName("inventory.draftReviewChoice");
        draftChoice.setPrototypeDisplayValue("Item edit · Product #0000");
        draftChoice.setMaximumRowCount(8);
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        draftChoice.setRenderer(renderer);
        FarmUi.entryControl(draftChoice, "", "Choose the retained count or item edit to review.");
        draftChoice.getAccessibleContext().setAccessibleName("Retained inventory draft");
        JLabel choiceLabel = new JLabel("Retained draft");
        choiceLabel.setLabelFor(draftChoice);
        choiceLabel.setDisplayedMnemonic('D');
        JPanel chooser = new JPanel(new BorderLayout(0, 4));
        chooser.setOpaque(false);
        chooser.add(choiceLabel, BorderLayout.NORTH);
        chooser.add(draftChoice, BorderLayout.CENTER);
        add(chooser, BorderLayout.NORTH);

        details = FarmUi.displayArea(8, 40);
        details.setName("inventory.draftReviewDetails");
        details.getAccessibleContext().setAccessibleName("Retained inventory draft details");
        details.getAccessibleContext().setAccessibleDescription(
                "Read-only retained values. Select text to copy it before closing or discarding a draft.");
        JScrollPane scroll = new JScrollPane(details,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("inventory.draftReviewScroll");
        scroll.getAccessibleContext().setAccessibleName("Scrollable retained inventory draft details");
        scroll.setMinimumSize(new Dimension(100, 100));
        add(scroll, BorderLayout.CENTER);
        add(FarmUi.formHint("Select text to copy retained values."), BorderLayout.SOUTH);

        draftChoice.addActionListener(event -> showSelectedDraft());
        draftChoice.setEnabled(!this.drafts.isEmpty());
        showSelectedDraft();
    }

    public int selectedIndex() {
        return draftChoice.getSelectedIndex();
    }

    private void showSelectedDraft() {
        int index = selectedIndex();
        DraftDetails selected = index < 0 ? null : drafts.get(index);
        details.setText(selected == null ? "No retained inventory drafts." : selected.details());
        details.setCaretPosition(0);
        draftChoice.getAccessibleContext().setAccessibleDescription(selected == null
                ? "No retained inventory drafts."
                : selected.label() + ". Full retained values are available in the details below.");
    }

    public record DraftDetails(String label, String details) {
    }
}

package com.flowerfarm.gui;

import com.flowerfarm.service.GrowerReminder;
import com.flowerfarm.service.GrowerReminders;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Dashboard / dialog checklist of today’s grower reminders. */
public final class RemindersPanel extends JPanel {

    public static final String TITLE = "Today’s reminders";

    private final JPanel list = new JPanel();
    private final JLabel empty = FarmUi.mutedLabel("No reminders right now.");
    private final Consumer<GrowerReminder> onOpen;
    private final Runnable onDismiss;

    public RemindersPanel(Consumer<GrowerReminder> onOpen, Runnable onDismiss) {
        this.onOpen = Objects.requireNonNull(onOpen, "onOpen");
        this.onDismiss = onDismiss == null ? () -> { } : onDismiss;
        setName("reminders.panel");
        setOpaque(false);
        setLayout(new BorderLayout(0, FarmUi.CONTROL_GAP));
        getAccessibleContext().setAccessibleName(TITLE);

        list.setName("reminders.list");
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        empty.setName("reminders.empty");

        JButton dismiss = FarmUi.secondaryButton("Hide for now");
        dismiss.setName("reminders.dismiss");
        dismiss.setToolTipText("Hide this banner. Help > Today’s reminders… still opens the list.");
        dismiss.addActionListener(event -> this.onDismiss.run());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        footer.setOpaque(false);
        footer.add(dismiss);

        JPanel body = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        body.setOpaque(false);
        body.add(list, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);

        add(FarmUi.section(TITLE,
                "Due field work, packing, harvest catch-up, low stock, and Knowledge follow-ups.",
                body), BorderLayout.CENTER);
        showDigest(GrowerReminders.assemble(
                java.time.LocalDate.of(2000, 1, 1), List.of(), List.of(), null, false, List.of(),
                GrowerReminders.DEFAULT_LOW_STOCK));
    }

    public void showDigest(GrowerReminders.Digest digest) {
        GrowerReminders.Digest safe = digest == null
                ? GrowerReminders.assemble(java.time.LocalDate.now(), List.of(), List.of(), null,
                false, List.of(), GrowerReminders.DEFAULT_LOW_STOCK)
                : digest;
        list.removeAll();
        if (safe.isEmpty()) {
            list.add(empty);
        } else {
            JLabel headline = FarmUi.mutedLabel(safe.headline());
            headline.setName("reminders.headline");
            list.add(headline);
            list.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
            int index = 0;
            for (GrowerReminder reminder : safe.items()) {
                list.add(row(reminder, index++));
                list.add(Box.createVerticalStrut(6));
            }
            if (!safe.guidance().isBlank()) {
                JLabel guidance = FarmUi.mutedLabel(safe.guidance());
                guidance.setName("reminders.guidance");
                list.add(guidance);
            }
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel row(GrowerReminder reminder, int index) {
        JPanel row = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setName("reminders.item." + index);
        JLabel text = new JLabel("<html><b>" + escape(reminder.title()) + "</b><br>"
                + escape(reminder.detail()) + "</html>");
        text.setName("reminders.itemText." + index);
        JButton open = FarmUi.secondaryButton("Open");
        open.setName("reminders.open." + index);
        open.setToolTipText("Open " + reminder.targetTab());
        open.addActionListener(event -> onOpen.accept(reminder));
        row.add(text, BorderLayout.CENTER);
        row.add(open, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        return row;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

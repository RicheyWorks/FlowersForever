package com.flowerfarm.gui;

import com.flowerfarm.safety.SafeFiles;
import com.flowerfarm.scouting.Scouting;
import com.flowerfarm.scouting.ScoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Help → Scouting &amp; lab samples: scouting log, lab sample tracking, guides, and lab directory. */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopScoutingController {

    public record Host(JFrame frame, FarmDialogs dialogs, FarmFileChooser files, Consumer<String> status) {
    }

    private static final Dimension FORM_VIEWPORT = new Dimension(620, 520);

    private final ScoutingService scouting;
    private Host host;

    @Autowired
    public DesktopScoutingController(ScoutingService scouting) {
        this.scouting = Objects.requireNonNull(scouting, "scouting");
    }

    public void attach(Host frameHost) {
        this.host = Objects.requireNonNull(frameHost, "frameHost");
    }

    public void open() {
        if (host == null) {
            return;
        }
        ScoutingPanel panel = new ScoutingPanel(host.status());
        Runnable refresh = () -> refresh(panel);
        refresh.run();

        JButton newFinding = button(true, "New finding…", "scouting.newFinding",
                e -> editFinding(panel, refresh, true));
        JButton editFinding = button(false, "Edit finding…", "scouting.editFinding",
                e -> editFinding(panel, refresh, false));
        JButton deleteFinding = button(false, "Delete finding…", "scouting.deleteFinding",
                e -> deleteFinding(panel, refresh));
        JButton newSample = button(false, "New lab sample…", "scouting.newSample",
                e -> editSample(panel, refresh, true));
        JButton editSample = button(false, "Update lab sample…", "scouting.editSample",
                e -> editSample(panel, refresh, false));
        JButton sheet = button(false, "Save submission sheet…", "scouting.saveSheet", e -> saveSheet(panel));
        JButton overdue = button(false, "Overdue after…", "scouting.overdueDays", e -> editOverdue(panel, refresh));
        JButton refreshButton = button(false, "Refresh", "scouting.refresh", e -> refresh.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        for (JButton button : List.of(newFinding, editFinding, deleteFinding, newSample, editSample, sheet, overdue,
                refreshButton)) {
            actions.add(button);
        }
        JPanel wrap = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        wrap.setName("scouting.dialog");
        wrap.add(panel, BorderLayout.CENTER);
        wrap.add(actions, BorderLayout.SOUTH);
        Object[] options = {"Close"};
        host.dialogs().showOption(host.frame(), wrap, Scouting.TITLE, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }

    private static JButton button(boolean primary, String text, String name,
                                  java.awt.event.ActionListener listener) {
        JButton button = primary ? FarmUi.primaryButton(text) : FarmUi.secondaryButton(text);
        button.setName(name);
        button.addActionListener(listener);
        return button;
    }

    private void refresh(ScoutingPanel panel) {
        try {
            panel.showBoard(scouting.board());
            String notice = scouting.takeNotice();
            if (!notice.isEmpty()) {
                warn(notice);
                panel.announce(notice);
            }
        } catch (IOException | RuntimeException failed) {
            String message = FarmUi.actionableError("Opening the scouting log", failed);
            warn(message);
            panel.announce("The scouting log could not be opened.");
        }
    }

    private void editFinding(ScoutingPanel panel, Runnable refresh, boolean create) {
        Scouting.Finding existing = create ? null : panel.selectedFinding().orElse(null);
        if (!create && existing == null) {
            panel.showTab(ScoutingPanel.FINDINGS_TAB);
            panel.announce("Select a finding on the Findings tab first.");
            return;
        }
        ScoutingForms.FindingForm form = new ScoutingForms.FindingForm(existing, scouting.plantingChoices(),
                scouting.today());
        while (true) {
            Object[] options = {"Save finding", "Cancel"};
            int choice = host.dialogs().showOption(host.frame(),
                    FarmUi.dialogScroll(host.frame(), form.panel, FORM_VIEWPORT),
                    create ? "New scouting finding" : "Edit scouting finding", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice != 0) {
                return;
            }
            try {
                Scouting.Finding saved = scouting.saveFinding(form.read());
                refresh.run();
                panel.showTab(ScoutingPanel.FINDINGS_TAB);
                panel.announce("Saved finding " + saved.id() + ".");
                return;
            } catch (IllegalArgumentException invalid) {
                warn(invalid.getMessage());
            } catch (IOException | RuntimeException failed) {
                warn(FarmUi.actionableError("Saving the finding", failed));
                return;
            }
        }
    }

    private void deleteFinding(ScoutingPanel panel, Runnable refresh) {
        Scouting.Finding existing = panel.selectedFinding().orElse(null);
        if (existing == null) {
            panel.showTab(ScoutingPanel.FINDINGS_TAB);
            panel.announce("Select a finding on the Findings tab first.");
            return;
        }
        Object[] options = {"Delete finding", "Keep it"};
        int choice = host.dialogs().showOption(host.frame(), "Delete " + existing.id() + " (" + existing.label()
                        + ")? This cannot be undone except by restoring a backup.", "Delete scouting finding",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (choice != 0) {
            return;
        }
        try {
            scouting.deleteFinding(existing.id());
            refresh.run();
            panel.announce("Deleted finding " + existing.id() + ".");
        } catch (IllegalArgumentException blocked) {
            warn(blocked.getMessage());
        } catch (IOException | RuntimeException failed) {
            warn(FarmUi.actionableError("Deleting the finding", failed));
        }
    }

    private void editSample(ScoutingPanel panel, Runnable refresh, boolean create) {
        Scouting.LabSample existing = create ? null : panel.selectedSample().orElse(null);
        if (!create && existing == null) {
            panel.showTab(ScoutingPanel.SAMPLES_TAB);
            panel.announce("Select a sample on the Lab samples tab first.");
            return;
        }
        ScoutingService.Board board;
        try {
            board = scouting.board();
        } catch (IOException | RuntimeException failed) {
            warn(FarmUi.actionableError("Opening the scouting log", failed));
            return;
        }
        Scouting.Finding linkTo = create ? panel.selectedFinding().orElse(null) : null;
        ScoutingForms.SampleForm form = new ScoutingForms.SampleForm(existing, linkTo, board.findings(),
                scouting.today());
        while (true) {
            Object[] options = {"Save sample", "Cancel"};
            int choice = host.dialogs().showOption(host.frame(),
                    FarmUi.dialogScroll(host.frame(), form.panel, FORM_VIEWPORT),
                    create ? "New lab sample" : "Update lab sample", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice != 0) {
                return;
            }
            try {
                Scouting.LabSample saved = scouting.saveSample(form.read());
                refresh.run();
                panel.showTab(ScoutingPanel.SAMPLES_TAB);
                panel.announce("Saved lab sample " + saved.id() + ". Write this ID on the bag and the lab form.");
                return;
            } catch (IllegalArgumentException invalid) {
                warn(invalid.getMessage());
            } catch (IOException | RuntimeException failed) {
                warn(FarmUi.actionableError("Saving the lab sample", failed));
                return;
            }
        }
    }

    private void saveSheet(ScoutingPanel panel) {
        Scouting.LabSample sample = panel.selectedSample().orElse(null);
        if (sample == null) {
            panel.showTab(ScoutingPanel.SAMPLES_TAB);
            panel.announce("Select a sample on the Lab samples tab first.");
            return;
        }
        host.files().chooseSave(host.frame(), "Save printable sample sheet",
                new File("sample-" + sample.id() + ".html")).ifPresent(file -> {
                    try {
                        SafeFiles.writeString(file.toPath(), scouting.submissionSheet(sample.id()));
                        panel.announce("Saved the sample sheet for " + sample.id()
                                + ". Open it in a browser to print.");
                    } catch (IOException | RuntimeException failed) {
                        warn(FarmUi.actionableError("Saving the sample sheet", failed));
                        panel.announce("Could not save the sample sheet.");
                    }
                });
    }

    private void editOverdue(ScoutingPanel panel, Runnable refresh) {
        int current;
        try {
            current = scouting.board().overdueDays();
        } catch (IOException | RuntimeException failed) {
            warn(FarmUi.actionableError("Opening the scouting log", failed));
            return;
        }
        JSpinner days = new JSpinner(new SpinnerNumberModel(current, Scouting.MIN_OVERDUE_DAYS,
                Scouting.MAX_OVERDUE_DAYS, 1));
        days.setName("scouting.overdueSpinner");
        days.getAccessibleContext().setAccessibleName("Days before a lab result counts as overdue");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.add(FarmUi.mutedLabel("<html>Flag a sample as overdue when no results have come back this many days "
                + "after it was shipped (or collected, if not shipped yet).<br>Labs do not publish turnaround "
                + "times, so this is your own reminder.</html>"), BorderLayout.NORTH);
        form.add(days, BorderLayout.CENTER);
        Object[] options = {"Save", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(), form, "Overdue lab results",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            return;
        }
        try {
            scouting.saveOverdueDays(((Number) days.getValue()).intValue());
            refresh.run();
            panel.announce("Lab results now count as overdue after " + days.getValue() + " days.");
        } catch (IOException | RuntimeException failed) {
            warn(FarmUi.actionableError("Saving the overdue setting", failed));
        }
    }

    private void warn(String message) {
        host.dialogs().showMessage(host.frame(), message, Scouting.TITLE, JOptionPane.WARNING_MESSAGE);
    }
}

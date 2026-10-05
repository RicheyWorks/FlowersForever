package com.flowerfarm.gui;

import com.flowerfarm.backup.BackupPreferences;
import com.flowerfarm.backup.BackupSchedule;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** The "Automatic backups" choices, built as a plain panel for a confirm dialog. */
final class BackupSettingsForm {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JCheckBox automatic = new JCheckBox("Back up my farm records automatically");
    private final JTextField folder = new JTextField(32);
    private final JButton chooseFolder = new JButton("Choose folder…");
    private final JComboBox<BackupSchedule> schedule = new JComboBox<>(BackupSchedule.values());
    private final JSpinner keep = new JSpinner(new SpinnerNumberModel(BackupPreferences.DEFAULT_KEEP, 1,
            BackupPreferences.MAX_KEEP, 1));

    BackupSettingsForm(BackupPreferences current, Path suggestedFolder, FarmFileChooser chooser) {
        panel.setName("backup.settings");
        automatic.setName("backup.automatic");
        automatic.setSelected(current.automatic());
        folder.setName("backup.folder");
        folder.setEditable(false);
        folder.setText(current.folder().orElse(suggestedFolder).toString());
        folder.getAccessibleContext().setAccessibleName("Backup folder");
        chooseFolder.setName("backup.chooseFolder");
        chooseFolder.setMnemonic('C');
        chooseFolder.addActionListener(event -> chooser.chooseDirectory(panel,
                        "Choose a folder for automatic backups", new File(folder.getText()))
                .ifPresent(chosen -> setFolder(chosen.toPath())));
        schedule.setName("backup.schedule");
        schedule.setSelectedItem(current.schedule());
        schedule.getAccessibleContext().setAccessibleName("How often");
        keep.setName("backup.keep");
        keep.setValue(current.keep());
        keep.getAccessibleContext().setAccessibleName("Automatic backups to keep");

        GridBagConstraints at = new GridBagConstraints();
        at.insets = new Insets(4, 4, 4, 4);
        at.anchor = GridBagConstraints.WEST;
        at.gridx = 0;
        at.gridy = 0;
        at.gridwidth = 3;
        panel.add(automatic, at);
        at.gridy++;
        at.gridwidth = 1;
        panel.add(new JLabel("Save backups in:"), at);
        at.gridx = 1;
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        panel.add(folder, at);
        at.gridx = 2;
        at.fill = GridBagConstraints.NONE;
        at.weightx = 0;
        panel.add(chooseFolder, at);
        at.gridx = 0;
        at.gridy++;
        panel.add(new JLabel("How often:"), at);
        at.gridx = 1;
        panel.add(schedule, at);
        at.gridx = 0;
        at.gridy++;
        panel.add(new JLabel("Keep the newest:"), at);
        at.gridx = 1;
        panel.add(keep, at);
        at.gridx = 0;
        at.gridy++;
        at.gridwidth = 3;
        JLabel hint = new JLabel("<html>A USB drive or a OneDrive folder keeps a copy away from this computer.<br>"
                + "Older automatic backups beyond this number are removed; other backups are never removed.</html>");
        hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        panel.add(hint, at);
    }

    JPanel panel() {
        return panel;
    }

    void setAutomatic(boolean on) {
        automatic.setSelected(on);
    }

    void setFolder(Path chosen) {
        folder.setText(chosen.toAbsolutePath().normalize().toString());
    }

    void setSchedule(BackupSchedule chosen) {
        schedule.setSelectedItem(chosen);
    }

    void setKeep(int count) {
        keep.setValue(count);
    }

    boolean automatic() {
        return automatic.isSelected();
    }

    Optional<Path> folder() {
        String text = folder.getText();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(Path.of(text.trim()));
    }

    BackupSchedule schedule() {
        Object selected = schedule.getSelectedItem();
        return selected instanceof BackupSchedule chosen ? chosen : BackupSchedule.DAILY;
    }

    int keep() {
        return ((Number) keep.getValue()).intValue();
    }
}

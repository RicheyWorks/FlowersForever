package com.flowerfarm.gui;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.util.Optional;

/**
 * Boundary for native save-file selection so user journeys can cover export
 * cancellation and completion without automating an operating-system window.
 */
public interface FarmFileChooser {

    Optional<File> chooseSave(Component parent, String title, File suggestedFile);

    /** Picks an existing folder (backups). Choosers that cannot pick folders answer "cancelled". */
    default Optional<File> chooseDirectory(Component parent, String title, File current) {
        return Optional.empty();
    }

    /** Picks an existing backup ZIP to restore. Choosers that cannot open files answer "cancelled". */
    default Optional<File> chooseBackup(Component parent, String title, File folder) {
        return Optional.empty();
    }

    /** Uses the normal installed-app Swing file chooser. */
    static FarmFileChooser swing() {
        return SwingFileChooser.INSTANCE;
    }

    enum SwingFileChooser implements FarmFileChooser {
        INSTANCE;

        @Override
        public Optional<File> chooseSave(Component parent, String title, File suggestedFile) {
            JFileChooser chooser = new JFileChooser();
            chooser.setName("farm.exportFileChooser");
            chooser.getAccessibleContext().setAccessibleName("Farm export file chooser");
            chooser.setDialogTitle(title);
            chooser.setSelectedFile(suggestedFile);
            return chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION
                    ? Optional.ofNullable(chooser.getSelectedFile()) : Optional.empty();
        }

        @Override
        public Optional<File> chooseDirectory(Component parent, String title, File current) {
            JFileChooser chooser = new JFileChooser(current != null && current.isDirectory() ? current : null);
            chooser.setName("farm.backupFolderChooser");
            chooser.getAccessibleContext().setAccessibleName("Backup folder chooser");
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            return chooser.showDialog(parent, "Use this folder") == JFileChooser.APPROVE_OPTION
                    ? Optional.ofNullable(chooser.getSelectedFile()) : Optional.empty();
        }

        @Override
        public Optional<File> chooseBackup(Component parent, String title, File folder) {
            JFileChooser chooser = new JFileChooser(folder != null && folder.isDirectory() ? folder : null);
            chooser.setName("farm.backupFileChooser");
            chooser.getAccessibleContext().setAccessibleName("Backup file chooser");
            chooser.setDialogTitle(title);
            chooser.setFileFilter(new FileNameExtensionFilter("FlowersForever backups (*.zip)", "zip"));
            return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                    ? Optional.ofNullable(chooser.getSelectedFile()) : Optional.empty();
        }
    }
}

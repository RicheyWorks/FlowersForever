package com.flowerfarm.gui;

import javax.swing.JFileChooser;
import java.awt.Component;
import java.io.File;
import java.util.Optional;

/**
 * Boundary for native save-file selection so user journeys can cover export
 * cancellation and completion without automating an operating-system window.
 */
public interface FarmFileChooser {

    Optional<File> chooseSave(Component parent, String title, File suggestedFile);

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
    }
}

package com.flowerfarm.gui;

import com.flowerfarm.backup.BackupPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class BackupSettingsFormTest {
    @TempDir Path temp;

    @Test
    void displayTextCannotChangeTheTypedFolderAndCancellationPreservesIt() {
        AtomicReference<File> startingFolder = new AtomicReference<>();
        AtomicReference<Optional<File>> selection = new AtomicReference<>(Optional.empty());
        FarmFileChooser chooser = new FarmFileChooser() {
            @Override public Optional<File> chooseSave(Component parent, String title, File suggested) {
                return Optional.empty();
            }
            @Override public Optional<File> chooseDirectory(Component parent, String title, File folder) {
                startingFolder.set(folder);
                return selection.get();
            }
        };
        BackupSettingsForm form = new BackupSettingsForm(BackupPreferences.defaults(), temp, chooser);
        JTextField display = null;
        JButton choose = null;
        for (Component component : form.panel().getComponents()) {
            if (component instanceof JTextField field) display = field;
            if (component instanceof JButton button) choose = button;
        }
        assertThat(display).isNotNull();
        assertThat(choose).isNotNull();
        assertThat(display.isEditable()).isFalse();
        display.setText("../../unselected");
        choose.doClick();
        assertThat(startingFolder).hasValue(temp.toAbsolutePath().normalize().toFile());
        assertThat(form.folder()).contains(temp.toAbsolutePath().normalize());
        Path selected = temp.resolve("chosen");
        selection.set(Optional.of(selected.toFile()));
        choose.doClick();
        assertThat(form.folder()).contains(selected.toAbsolutePath().normalize());
        assertThat(display.getText()).isEqualTo(selected.toAbsolutePath().normalize().toString());
        selection.set(Optional.empty());
        choose.doClick();
        assertThat(form.folder()).contains(selected.toAbsolutePath().normalize());
    }
}

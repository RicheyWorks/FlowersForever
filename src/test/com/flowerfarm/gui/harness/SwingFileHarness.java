package com.flowerfarm.gui.harness;

import com.flowerfarm.gui.FarmFileChooser;

import java.awt.Component;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Scripted save-file choices with title and suggested-name verification. */
public final class SwingFileHarness implements FarmFileChooser {

    private final Deque<SaveScript> scripts = new ArrayDeque<>();
    private final List<String> transcript = new ArrayList<>();
    private boolean cancelUnscripted;

    /** Cancels any unscripted save boundary during broad exploratory sweeps. */
    public SwingFileHarness cancelUnscripted() {
        cancelUnscripted = true;
        return this;
    }

    public SwingFileHarness cancelSave(String title, String suggestedName) {
        scripts.addLast(new SaveScript(title, suggestedName, null));
        return this;
    }

    public SwingFileHarness saveAs(String title, String suggestedName, File selectedFile) {
        if (selectedFile == null) {
            throw new IllegalArgumentException("selectedFile is required");
        }
        scripts.addLast(new SaveScript(title, suggestedName, selectedFile));
        return this;
    }

    @Override
    public Optional<File> chooseSave(Component parent, String title, File suggestedFile) {
        SaveScript script = scripts.pollFirst();
        if (script == null) {
            if (cancelUnscripted) {
                String suggestedName = suggestedFile == null ? null : suggestedFile.getName();
                transcript.add("SAFE CANCEL SAVE " + title + " · suggested " + suggestedName);
                return Optional.empty();
            }
            throw new AssertionError("Unexpected save-file chooser '" + title + "'");
        }
        if (!script.title().equals(title)) {
            throw new AssertionError("Expected save-file chooser '" + script.title()
                    + "' but saw '" + title + "'");
        }
        String suggestedName = suggestedFile == null ? null : suggestedFile.getName();
        if (!java.util.Objects.equals(script.suggestedName(), suggestedName)) {
            throw new AssertionError("Save-file chooser '" + title + "' suggested '"
                    + suggestedName + "' instead of '" + script.suggestedName() + "'");
        }
        if (script.selectedFile() == null) {
            transcript.add("CANCEL SAVE " + title + " · suggested " + suggestedName);
            return Optional.empty();
        }
        transcript.add("SAVE " + title + " = " + script.selectedFile().getAbsolutePath());
        return Optional.of(script.selectedFile());
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    public void assertComplete() {
        if (!scripts.isEmpty()) {
            throw new AssertionError("Unused save-file scripts: " + scripts);
        }
    }

    private record SaveScript(String title, String suggestedName, File selectedFile) {
    }
}

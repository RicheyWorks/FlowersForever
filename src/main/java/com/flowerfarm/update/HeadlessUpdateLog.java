package com.flowerfarm.update;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The result file for the headless release check: one timestamped line per step,
 * ending with {@code RESULT exit=<code> <name>}. Lines are also echoed to the
 * console. Key material is never written here, only key file paths and key ids.
 */
public final class HeadlessUpdateLog implements UpdateActivityLog {

    private final Optional<Path> file;
    private final PrintStream echo;
    private final Clock clock;
    private final String mode;

    public HeadlessUpdateLog(Optional<Path> file, PrintStream echo, Clock clock, String mode) {
        this.file = file.map(path -> path.toAbsolutePath().normalize());
        this.echo = echo;
        this.clock = clock;
        this.mode = mode;
    }

    public Optional<Path> file() {
        return file;
    }

    public void line(String step, String message) {
        String text = Instant.now(clock) + "  " + mode + "  " + step + "  " + oneLine(message);
        echo.println(text);
        if (file.isEmpty()) {
            return;
        }
        try {
            Path parent = file.get().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file.get(), text + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            echo.println("WARNING: could not write the update check log " + file.get() + ": "
                    + unwritable.getMessage());
        }
    }

    /** Writes the final line and returns the exit code to use. */
    public int finish(HeadlessUpdateCommand.Exit exit, String message) {
        line("RESULT", "exit=" + exit.code() + " " + exit.name() + " " + message);
        return exit.code();
    }

    /** Coordinator activity (downloaded, stopped, ...) is recorded here because no database is open. */
    @Override
    public void record(String operation, boolean success, String message) {
        line(operation, (success ? "ok: " : "problem: ") + message);
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.replace('\r', ' ').replace('\n', ' ');
    }
}

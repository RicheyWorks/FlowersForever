package com.flowerfarm.backup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

/** Saves {@link BackupPreferences} as a small, human-readable properties file. */
public final class BackupPreferencesStore {

    public static final String FILE = "backup-preferences.properties";

    private final Path file;

    public BackupPreferencesStore(Path stateDirectory) {
        this.file = stateDirectory.resolve(FILE);
    }

    public synchronized BackupPreferences load() {
        if (!Files.isRegularFile(file)) {
            return BackupPreferences.defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException unreadable) {
            return BackupPreferences.defaults();
        }
        int keep;
        try {
            keep = Integer.parseInt(properties.getProperty("automatic.keep",
                    String.valueOf(BackupPreferences.DEFAULT_KEEP)).trim());
        } catch (NumberFormatException invalid) {
            keep = BackupPreferences.DEFAULT_KEEP;
        }
        return new BackupPreferences(
                Boolean.parseBoolean(properties.getProperty("automatic.enabled", "false")),
                text(properties, "automatic.folder").map(Path::of),
                BackupSchedule.fromName(properties.getProperty("automatic.schedule")).orElse(BackupSchedule.DAILY),
                keep,
                instant(properties, "last.automatic"),
                instant(properties, "last.backup"),
                text(properties, "last.problem"));
    }

    public synchronized void save(BackupPreferences preferences) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("automatic.enabled", String.valueOf(preferences.automatic()));
        preferences.folder().ifPresent(folder -> properties.setProperty("automatic.folder", folder.toString()));
        properties.setProperty("automatic.schedule", preferences.schedule().key());
        properties.setProperty("automatic.keep", String.valueOf(preferences.keep()));
        preferences.lastAutomatic().ifPresent(when -> properties.setProperty("last.automatic", when.toString()));
        preferences.lastBackup().ifPresent(when -> properties.setProperty("last.backup", when.toString()));
        preferences.lastProblem().ifPresent(problem -> properties.setProperty("last.problem", problem));
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "FlowersForever backup choices (File > Automatic backups)");
        }
        try {
            com.flowerfarm.safety.SafeFiles.force(temporary);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Optional<String> text(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<Instant> instant(Properties properties, String key) {
        try {
            return text(properties, key).map(Instant::parse);
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}

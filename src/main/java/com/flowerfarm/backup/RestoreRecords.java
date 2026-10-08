package com.flowerfarm.backup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The three small files that carry a restore across the app's restart, all in
 * the backup settings folder beside (never inside) the records:
 * <ul>
 *   <li>{@code restore-request.properties}: the checked backup to restore on the next start;</li>
 *   <li>{@code restore-in-progress.properties}: written once the swap happened, removed after a good start;</li>
 *   <li>{@code restore-notice.properties}: one message to show (and log) once the window is open.</li>
 * </ul>
 */
public final class RestoreRecords {

    public static final String REQUEST = "restore-request.properties";
    public static final String IN_PROGRESS = "restore-in-progress.properties";
    public static final String NOTICE = "restore-notice.properties";

    /** What to restore; {@code sha256} pins the exact file that was checked. */
    public record Request(Path archive, String sha256, Optional<Path> safetyBackup, String madeAtText) {
    }

    /** A restore whose records are in place but whose first start has not finished yet. */
    public record InProgress(Path archive, Optional<Path> keptAside, Optional<Path> safetyBackup,
                             String madeAtText) {
    }

    /** One message for the grower and the matching Activity History entry. */
    public record Notice(String message, boolean success, String operation) {
    }

    private final Path directory;

    public RestoreRecords(Path stateDirectory) {
        this.directory = stateDirectory;
    }

    public synchronized void saveRequest(Request request) throws IOException {
        Map<String, String> values = new TreeMap<>();
        values.put("archive", request.archive().toString());
        values.put("sha256", request.sha256());
        request.safetyBackup().ifPresent(path -> values.put("safety-backup", path.toString()));
        values.put("made-at", request.madeAtText());
        write(REQUEST, values);
    }

    public synchronized Optional<Request> request() {
        return read(REQUEST).flatMap(values -> Optional.ofNullable(values.getProperty("archive"))
                .map(archive -> new Request(Path.of(archive), values.getProperty("sha256", ""),
                        path(values, "safety-backup"), values.getProperty("made-at", ""))));
    }

    public synchronized void saveInProgress(InProgress progress) throws IOException {
        Map<String, String> values = new TreeMap<>();
        values.put("archive", progress.archive().toString());
        progress.keptAside().ifPresent(path -> values.put("kept-aside", path.toString()));
        progress.safetyBackup().ifPresent(path -> values.put("safety-backup", path.toString()));
        values.put("made-at", progress.madeAtText());
        write(IN_PROGRESS, values);
    }

    public synchronized Optional<InProgress> inProgress() {
        return read(IN_PROGRESS).flatMap(values -> Optional.ofNullable(values.getProperty("archive"))
                .map(archive -> new InProgress(Path.of(archive), path(values, "kept-aside"),
                        path(values, "safety-backup"), values.getProperty("made-at", ""))));
    }

    public synchronized void saveNotice(Notice notice) throws IOException {
        Map<String, String> values = new TreeMap<>();
        values.put("message", notice.message());
        values.put("success", String.valueOf(notice.success()));
        values.put("operation", notice.operation());
        write(NOTICE, values);
    }

    /** Returns the pending notice once and removes it. */
    public synchronized Optional<Notice> takeNotice() {
        Optional<Notice> notice = read(NOTICE).map(values -> new Notice(values.getProperty("message", ""),
                Boolean.parseBoolean(values.getProperty("success", "false")),
                values.getProperty("operation", "RESTORE")));
        clear(NOTICE);
        return notice.filter(value -> !value.message().isBlank());
    }

    public synchronized void clear(String name) {
        try {
            Files.deleteIfExists(directory.resolve(name));
        } catch (IOException stuck) {
            // A stale file is re-read next start; every step is safe to repeat.
        }
    }

    private Optional<Properties> read(String name) {
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return Optional.of(properties);
        } catch (IOException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    private void write(String name, Map<String, String> values) throws IOException {
        Files.createDirectories(directory);
        Properties properties = new Properties();
        properties.putAll(values);
        Path file = directory.resolve(name);
        Path temporary = directory.resolve(name + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "FlowersForever restore (see docs/BACKUP_RESTORE.md)");
        }
        com.flowerfarm.safety.SafeFiles.force(temporary);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Optional<Path> path(Properties values, String key) {
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
    }
}

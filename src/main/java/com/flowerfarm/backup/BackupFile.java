package com.flowerfarm.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A backup ZIP named like {@code FlowersForever-[<label>-]yyyyMMdd-HHmmss[-n].zip}: the
 * app's names carry a label ({@code backup}, {@code auto}, {@code before-1.2.1}, ...), and
 * {@code scripts/backup-data.ps1} writes them without one.
 */
public record BackupFile(Path archive, String label, LocalDateTime madeAt, int counter, boolean hasChecksum) {

    public static final String AUTOMATIC_LABEL = "auto";
    private static final Pattern NAME = Pattern.compile(
            "FlowersForever-(?:(.+)-)?(\\d{8}-\\d{6})(?:-(\\d+))?\\.zip", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Comparator<BackupFile> NEWEST_FIRST = Comparator.comparing(BackupFile::madeAt)
            .thenComparingInt(BackupFile::counter).reversed();

    public static Optional<BackupFile> parse(Path archive) {
        Matcher matcher = NAME.matcher(String.valueOf(archive.getFileName()));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            LocalDateTime madeAt = LocalDateTime.parse(matcher.group(2), STAMP);
            int counter = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
            String label = matcher.group(1) == null ? "" : matcher.group(1);
            return Optional.of(new BackupFile(archive, label, madeAt, counter,
                    Files.isRegularFile(checksumFor(archive))));
        } catch (DateTimeParseException | NumberFormatException notOurs) {
            return Optional.empty();
        }
    }

    /** Backups in {@code folder}, newest first. A missing folder simply has none. */
    public static List<BackupFile> list(Path folder) {
        List<BackupFile> found = new ArrayList<>();
        if (folder == null || !Files.isDirectory(folder)) {
            return found;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).map(BackupFile::parse).flatMap(Optional::stream).forEach(found::add);
        } catch (IOException unreadable) {
            return found;
        }
        found.sort(NEWEST_FIRST);
        return found;
    }

    /**
     * Deletes the oldest automatic backups beyond {@code keep}. Manual, pre-update
     * and pre-restore backups are never touched. Returns the deleted archives.
     */
    public static List<Path> prune(Path folder, int keep) throws IOException {
        int limit = Math.max(1, keep);
        List<BackupFile> automatic = list(folder).stream()
                .filter(file -> AUTOMATIC_LABEL.equalsIgnoreCase(file.label())).toList();
        List<Path> deleted = new ArrayList<>();
        for (BackupFile old : automatic.subList(Math.min(limit, automatic.size()), automatic.size())) {
            Files.deleteIfExists(checksumFor(old.archive()));
            Files.deleteIfExists(old.archive());
            deleted.add(old.archive());
        }
        return deleted;
    }

    public static Path checksumFor(Path archive) {
        return Path.of(archive + ".sha256");
    }

    public boolean automatic() {
        return AUTOMATIC_LABEL.equalsIgnoreCase(label);
    }
}

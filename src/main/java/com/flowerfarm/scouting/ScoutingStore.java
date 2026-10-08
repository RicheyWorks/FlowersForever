package com.flowerfarm.scouting;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowerfarm.safety.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps the scouting log in {@code <records folder>/scouting/scouting-log.json}.
 * <ul>
 *   <li>Every save goes through {@link SafeFiles}: written to a temporary file, flushed to
 *       disk, then swapped in, so a crash or power cut leaves the old or the new log.</li>
 *   <li>The previous good version is kept as {@code scouting-log.json.bak}.</li>
 *   <li>If the log cannot be read, the last good version is used, the damaged file is
 *       kept aside (never deleted), and the grower is told. If neither can be read, saving
 *       is refused so nothing is overwritten.</li>
 * </ul>
 * Backups and restores include this folder because they copy the whole records folder.
 */
public final class ScoutingStore {

    public static final String FOLDER = "scouting";
    public static final String FILE = "scouting-log.json";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /** Everything in the log. Lists are copied so callers cannot change stored data by accident. */
    public record Log(int version, int overdueDays, List<Scouting.Finding> findings,
                      List<Scouting.LabSample> samples) {

        public Log {
            version = version <= 0 ? 1 : version;
            overdueDays = overdueDays <= 0 ? Scouting.DEFAULT_OVERDUE_DAYS : Scouting.clampDays(overdueDays);
            findings = List.copyOf(Objects.requireNonNullElse(findings, List.of()));
            samples = List.copyOf(Objects.requireNonNullElse(samples, List.of()));
        }

        public static Log empty() {
            return new Log(1, Scouting.DEFAULT_OVERDUE_DAYS, List.of(), List.of());
        }

        public Optional<Scouting.Finding> finding(String id) {
            return findings.stream().filter(finding -> finding.id().equals(id)).findFirst();
        }

        public Optional<Scouting.LabSample> sample(String id) {
            return samples.stream().filter(sample -> sample.id().equals(id)).findFirst();
        }
    }

    /** The log cannot be read and no good copy exists; saving is refused. */
    public static final class DamagedLogException extends IOException {
        private static final long serialVersionUID = 1L;

        public DamagedLogException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Path folder;
    private final Path file;
    private final Path previous;
    private final Clock clock;
    private final ObjectMapper json;
    private String notice = "";
    private boolean blocked;

    public ScoutingStore(Path recordsFolder, Clock clock) {
        this.folder = recordsFolder.toAbsolutePath().normalize().resolve(FOLDER);
        this.file = folder.resolve(FILE);
        this.previous = folder.resolve(FILE + ".bak");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Path file() {
        return file;
    }

    /** A plain message about a recovery during the last load, or empty. Cleared when read. */
    public synchronized String takeNotice() {
        String message = notice;
        notice = "";
        return message;
    }

    public synchronized Log load() throws IOException {
        if (!Files.exists(file) && !Files.exists(previous)) {
            blocked = false;
            return Log.empty();
        }
        IOException mainFailure;
        try {
            Log log = read(file);
            blocked = false;
            return log;
        } catch (IOException unreadable) {
            mainFailure = unreadable;
        }
        if (Files.exists(previous)) {
            try {
                Log log = read(previous);
                Path kept = keepAside(file);
                notice = "The scouting log was damaged (for example by a power cut while saving). FlowersForever "
                        + "opened the last good copy instead; the most recent change may be missing. The damaged "
                        + "file was kept at " + kept + ".";
                blocked = false;
                return log;
            } catch (IOException alsoUnreadable) {
                mainFailure.addSuppressed(alsoUnreadable);
            }
        }
        blocked = true;
        throw new DamagedLogException("The scouting log at " + file + " could not be read and no good copy was "
                + "found. Nothing has been changed or deleted. Close FlowersForever and restore a backup, or copy "
                + "the file somewhere safe and contact support.", mainFailure);
    }

    /** Saves the whole log. The old version becomes the {@code .bak} copy. */
    public synchronized void save(Log log) throws IOException {
        if (blocked) {
            throw new DamagedLogException("Saving is paused because the scouting log could not be read. "
                    + "Nothing was overwritten.", null);
        }
        byte[] bytes = json.writeValueAsBytes(Objects.requireNonNull(log, "log"));
        json.readValue(bytes, Log.class); // never write something that cannot be read back
        Files.createDirectories(folder);
        if (Files.exists(file)) {
            byte[] current = Files.readAllBytes(file);
            if (readable(current)) {
                SafeFiles.write(previous, current);
            }
        }
        SafeFiles.write(file, bytes);
    }

    private Log read(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("missing: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        try {
            Log log = json.readValue(bytes, Log.class);
            if (log == null) {
                throw new IOException("empty log: " + path);
            }
            return log;
        } catch (IOException | RuntimeException unreadable) {
            throw new IOException("The scouting log could not be read: " + path, unreadable);
        }
    }

    private boolean readable(byte[] bytes) {
        try {
            return json.readValue(bytes, Log.class) != null;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private Path keepAside(Path damaged) throws IOException {
        if (!Files.exists(damaged)) {
            return damaged;
        }
        Path target = folder.resolve("scouting-log.damaged-" + LocalDateTime.now(clock).format(STAMP)
                + ".json");
        Files.move(damaged, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Helper for callers that rebuild a list with one item replaced or added. */
    static <T> List<T> replace(List<T> items, java.util.function.Predicate<T> match, T replacement) {
        List<T> copy = new ArrayList<>();
        boolean replaced = false;
        for (T item : items) {
            if (!replaced && match.test(item)) {
                copy.add(replacement);
                replaced = true;
            } else {
                copy.add(item);
            }
        }
        if (!replaced) {
            copy.add(replacement);
        }
        return copy;
    }
}

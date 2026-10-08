package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Checks the farm records before FlowersForever opens them. If the records file is
 * damaged (for example by a power cut in the middle of a save), the damaged folder is
 * kept aside, never deleted, and the newest backup that passes its checksum
 * <em>and</em> opens cleanly is put back. The grower is told what happened in plain words.
 */
public final class DatabaseIntegrityGuard {

    /** What the check found. */
    public enum Health { MISSING, HEALTHY, IN_USE, DAMAGED, UNREADABLE }

    /** What the guard did about it. */
    public record Outcome(Health health, Optional<BackupFile> restored, Optional<Path> keptAside, String message) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseIntegrityGuard.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US);

    private final BackupSettings settings;
    private final RestoreRecords records;
    private final DataBackupService archives;

    public DatabaseIntegrityGuard(BackupSettings settings, RestoreRecords records, DataBackupService archives,
                                  Clock clock) {
        this.settings = settings;
        this.records = records;
        this.archives = archives;
    }

    /** Opens the records once and reads every table. Never changes or deletes anything. */
    public static Health check(Path dataDirectory) {
        Path database = dataDirectory.resolve(DataBackupService.DATABASE_FILE);
        if (!Files.isRegularFile(database)) {
            return Health.MISSING;
        }
        try {
            DataBackupService.requireDatabaseClosed(dataDirectory);
        } catch (UpdateVerificationException inUse) {
            return Health.IN_USE;
        }
        String url = "jdbc:h2:file:" + dataDirectory.toAbsolutePath().resolve("flowerfarm") + ";IFEXISTS=TRUE";
        // Same account the app uses (spring.datasource.username/password, blank by default).
        String user = System.getProperty("spring.datasource.username", "sa");
        String password = System.getProperty("spring.datasource.password", System.getenv().getOrDefault(
                "SPRING_DATASOURCE_PASSWORD", ""));
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (Statement statement = connection.createStatement()) {
                List<String> tables = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'")) {
                    while (rows.next()) {
                        tables.add(rows.getString(1));
                    }
                }
                for (String table : tables) {
                    try (ResultSet rows = statement.executeQuery("SELECT * FROM \"" + table.replace("\"", "\"\"")
                            + "\"")) {
                        while (rows.next()) {
                            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                                rows.getObject(column);
                            }
                        }
                    }
                }
                return Health.HEALTHY;
            } finally {
                // A failed table scan must release DB_CLOSE_DELAY=-1 too, or this
                // process prevents its own recovery from moving the damaged file.
                try (Statement shutdown = connection.createStatement()) {
                    shutdown.execute("SHUTDOWN");
                } catch (SQLException | RuntimeException alreadyClosed) {
                    // Preserve the original scan failure if H2 already closed the store.
                }
            }
        } catch (SQLException | RuntimeException failure) {
            return damaged(failure) ? Health.DAMAGED : Health.UNREADABLE;
        }
    }

    static boolean damaged(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 90030 || sql.getErrorCode() == 90048
                    || sql.getErrorCode() == 50000 && String.valueOf(sql.getMessage()).contains("MVStore"))) {
                return true;
            }
            String type = cause.getClass().getName();
            String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (type.contains("MVStoreException") || type.startsWith("org.h2.mvstore")
                    || text.contains("file corrupted") || text.contains("corrupt")) {
                return true;
            }
        }
        return false;
    }

    /** Checks the live records and, only if they are damaged, restores the newest healthy backup. */
    public Outcome ensureHealthy() throws IOException {
        Path data = settings.dataDirectory().toAbsolutePath().normalize();
        Health health = check(data);
        if (health != Health.DAMAGED) {
            return new Outcome(health, Optional.empty(), Optional.empty(), "");
        }
        LOG.error("The farm records in {} are damaged; looking for a verified backup", data);
        for (BackupFile candidate : candidates()) {
            if (!candidate.hasChecksum() || !BackupReceipts.recognizes(settings, candidate.archive())
                    || !opensCleanly(candidate.archive(), data)) {
                continue;
            }
            Path keptAside;
            try {
                keptAside = archives.restore(candidate.archive(), data, previous ->
                        records.saveInProgress(new RestoreRecords.InProgress(candidate.archive(),
                                Optional.ofNullable(previous), Optional.empty(), candidate.madeAt().format(WHEN))));
            } catch (UpdateVerificationException notRestored) {
                if (!Files.isRegularFile(data.resolve(DataBackupService.DATABASE_FILE))) {
                    throw new IOException("Recovery was interrupted. The previous records and restore instructions "
                            + "have been kept; reopen FlowersForever to finish recovery.", notRestored);
                }
                records.clear(RestoreRecords.IN_PROGRESS);
                LOG.warn("Backup {} could not be restored", candidate.archive(), notRestored);
                continue;
            }
            String message = "Your farm records file was damaged, most likely by a power cut or the computer "
                    + "being switched off while FlowersForever was saving. FlowersForever put back your latest "
                    + "verified backup, made " + candidate.madeAt().format(WHEN) + ". Anything entered after "
                    + "that backup is not included. The damaged copy was kept (not deleted) at " + keptAside + ".";
            records.saveNotice(new RestoreRecords.Notice(message, false, BackupActivityLog.RESTORE_DONE));
            return new Outcome(health, Optional.of(candidate), Optional.ofNullable(keptAside), message);
        }
        String message = "Your farm records file is damaged and no verified backup for this records store "
                + "was found to put back automatically. Nothing has been deleted. Older or unfamiliar backups "
                + "may still be usable through manual restore; contact support to review them. "
                + "Please do not enter new records yet; copy the folder " + data + " somewhere safe.";
        records.saveNotice(new RestoreRecords.Notice(message, false, BackupActivityLog.RESTORE_SKIPPED));
        return new Outcome(health, Optional.empty(), Optional.empty(), message);
    }

    private List<BackupFile> candidates() {
        Set<Path> folders = new LinkedHashSet<>();
        new BackupPreferencesStore(settings.stateDirectory()).load().folder().ifPresent(folders::add);
        folders.add(settings.defaultFolder());
        List<BackupFile> found = new ArrayList<>();
        for (Path folder : folders) {
            found.addAll(BackupFile.list(folder));
        }
        found.sort(Comparator.comparing(BackupFile::madeAt).thenComparing(BackupFile::counter).reversed());
        return found;
    }

    private boolean opensCleanly(Path archive, Path data) {
        Path parent = data.getParent();
        if (parent == null) {
            return false;
        }
        Path staging = parent.resolve(".integrity-" + Long.toHexString(System.nanoTime()));
        try {
            String user = System.getProperty("spring.datasource.username", "sa");
            String password = System.getProperty("spring.datasource.password", System.getenv().getOrDefault(
                    "SPRING_DATASOURCE_PASSWORD", ""));
            new RestoreService(settings, archives, user, password).inspect(archive);
            return check(archives.unpack(archive, staging)) == Health.HEALTHY;
        } catch (UpdateVerificationException | BackupException damagedBackup) {
            LOG.warn("Skipping backup {}: {}", archive, damagedBackup.getMessage());
            return false;
        } finally {
            deleteQuietly(staging);
        }
    }

    private static void deleteQuietly(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // a temporary copy; leaving it behind is harmless
                }
            });
        } catch (IOException ignored) {
            // a temporary copy; leaving it behind is harmless
        }
    }
}

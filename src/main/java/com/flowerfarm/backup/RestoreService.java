package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateSignatures;
import com.flowerfarm.update.UpdateVerificationException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Checks a backup before anything is restored: the {@code .sha256} file must
 * match, the ZIP must hold FlowersForever records, and those records must open
 * in this version (not damaged, not made by a newer version). The check works
 * on a scratch copy; the farm's live records are never touched here.
 */
public final class RestoreService {

    private final BackupSettings settings;
    private final DataBackupService archives;
    private final String user;
    private final String password;
    private final String[] migrationLocations;

    public RestoreService(BackupSettings settings, DataBackupService archives, String user, String password,
                          String... migrationLocations) {
        this.settings = settings;
        this.archives = archives;
        this.user = user;
        this.password = password;
        this.migrationLocations = migrationLocations.length == 0
                ? new String[] {"classpath:db/migration/h2"} : migrationLocations.clone();
    }

    public BackupDetails inspect(Path archive) throws BackupException {
        Path file = archive.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new BackupException(BackupMessages.backupMissing(file));
        }
        if (!Files.isRegularFile(BackupFile.checksumFor(file))) {
            throw new BackupException(BackupMessages.checksumMissing(file));
        }
        if (settings.insideRecords(file)) {
            throw new BackupException(BackupMessages.FOLDER_INSIDE_RECORDS);
        }
        Path work = settings.stateDirectory().resolve("check-" + Long.toHexString(System.nanoTime()));
        try {
            Path records = archives.unpack(file, work);
            String sha256 = UpdateSignatures.sha256Hex(file);
            long[] totals = totals(records);
            RecordsVersion version = checkRecordsOpen(records);
            return new BackupDetails(file, sha256, madeAt(file), totals[1], (int) totals[0], version.version(),
                    version.upgradesOnOpen());
        } catch (UpdateVerificationException damaged) {
            throw new BackupException(BackupMessages.notRestorable(damaged.getMessage()), damaged);
        } catch (IOException unreadable) {
            throw new BackupException(BackupMessages.notRestorable(unreadable.getMessage()), unreadable);
        } finally {
            LiveBackupService.deleteQuietly(work);
        }
    }

    private record RecordsVersion(String version, boolean upgradesOnOpen) {
    }

    private RecordsVersion checkRecordsOpen(Path records) throws BackupException {
        String url = "jdbc:h2:file:" + records.resolve("flowerfarm").toAbsolutePath() + ";IFEXISTS=TRUE";
        try {
            Flyway flyway = Flyway.configure().dataSource(url, user, password)
                    .locations(migrationLocations).baselineOnMigrate(true).baselineVersion("1")
                    .ignoreMigrationPatterns("*:pending").load();
            MigrationInfoService info = flyway.info();
            for (MigrationInfo step : info.all()) {
                MigrationState state = step.getState();
                if (state == MigrationState.FUTURE_SUCCESS || state == MigrationState.FUTURE_FAILED) {
                    throw new BackupException(BackupMessages.NEWER_VERSION);
                }
                if (state.isFailed()) {
                    throw new BackupException(BackupMessages.DAMAGED_RECORDS);
                }
            }
            if (!flyway.validateWithResult().validationSuccessful) {
                throw new BackupException(BackupMessages.DAMAGED_RECORDS);
            }
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                RecordsReadProbe.readAll(connection);
            }
            MigrationInfo current = info.current();
            MigrationVersion version = current == null ? null : current.getVersion();
            return new RecordsVersion(version == null ? "1" : version.getVersion(), info.pending().length > 0);
        } catch (FlywayException | SQLException unreadable) {
            throw new BackupException(BackupMessages.notRestorable(firstLine(unreadable.getMessage())), unreadable);
        } finally {
            shutdown(url);
        }
    }

    private void shutdown(String url) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException alreadyClosed) {
            // Nothing holds the scratch copy open.
        }
    }

    private static long[] totals(Path records) throws IOException {
        long files = 0;
        long bytes = 0;
        try (Stream<Path> walk = Files.walk(records)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                files++;
                bytes += Files.size(path);
            }
        }
        return new long[] {files, bytes};
    }

    static Optional<LocalDateTime> madeAt(Path archive) {
        Optional<LocalDateTime> named = BackupFile.parse(archive).map(BackupFile::madeAt);
        if (named.isPresent()) {
            return named;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(archive, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            return Optional.of(LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(),
                    ZoneId.systemDefault()));
        } catch (IOException unknown) {
            return Optional.empty();
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "the records could not be opened";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}

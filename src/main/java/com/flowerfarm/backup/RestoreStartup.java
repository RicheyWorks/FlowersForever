package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateSignatures;
import com.flowerfarm.update.UpdateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Finishes a restore the grower asked for, before the records are opened.
 * <ol>
 *   <li>Wait (briefly) for the previous window to let go of the records.</li>
 *   <li>Check the backup again, then swap it in. The records that were there are
 *       kept as {@code data.before-restore-*}; nothing is deleted.</li>
 *   <li>Start normally (Flyway + Hibernate validation are the health check). If the
 *       restored records do not open, put the previous records back and start again.</li>
 * </ol>
 * A start that crashed half-way through a restore is treated the same as a failed one.
 */
public final class RestoreStartup {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreStartup.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final BackupSettings settings;
    private final RestoreRecords records;
    private final DataBackupService archives;
    private final Clock clock;
    private final Duration waitForRelease;
    private final DatabaseIntegrityGuard integrity;

    public RestoreStartup(BackupSettings settings, RestoreRecords records, DataBackupService archives, Clock clock,
                          Duration waitForRelease) {
        this(settings, records, archives, clock, waitForRelease, null);
    }

    /** @param integrity checks the records before a normal start; {@code null} skips the check */
    public RestoreStartup(BackupSettings settings, RestoreRecords records, DataBackupService archives, Clock clock,
                          Duration waitForRelease, DatabaseIntegrityGuard integrity) {
        this.integrity = integrity;
        this.settings = settings;
        this.records = records;
        this.archives = archives;
        this.clock = clock;
        this.waitForRelease = waitForRelease;
    }

    /** Built from JVM properties; {@code AppDataBootstrap} must already have run. */
    public static RestoreStartup forThisProcess() {
        BackupSettings settings = BackupSettings.fromSystemProperties();
        RestoreRecords records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        DataBackupService archives = new DataBackupService(Clock.systemDefaultZone());
        return new RestoreStartup(settings, records, archives, Clock.systemDefaultZone(), Duration.ofSeconds(60),
                new DatabaseIntegrityGuard(settings, records, archives, Clock.systemDefaultZone()));
    }

    /**
     * @param allowed false while an app update is being finished: a waiting restore is then
     *                cancelled (with a notice) rather than mixed into the update's own recovery
     */
    public <T> T start(Callable<T> startSpring, Runnable closeDatabase, boolean allowed) throws Exception {
        records.requireStore(settings.dataDirectory());
        Optional<RestoreRecords.InProgress> unfinished = records.inProgress();
        if (unfinished.isPresent()) {
            putPreviousRecordsBack(unfinished.get(), "the restored records did not finish opening last time");
        }
        Optional<RestoreRecords.Request> request = records.request();
        if (request.isEmpty()) {
            boolean recovered = false;
            if (allowed) {
                recovered = checkIntegrity();
            }
            try {
                T started = startSpring.call();
                if (recovered) {
                    records.clear(RestoreRecords.IN_PROGRESS);
                }
                return started;
            } catch (Exception startFailure) {
                if (recovered) {
                    closeDatabase.run();
                    Optional<RestoreRecords.InProgress> progress = records.inProgress();
                    if (progress.isPresent()) {
                        putPreviousRecordsBack(progress.get(), reason(startFailure));
                    }
                }
                throw startFailure;
            }
        }
        records.clear(RestoreRecords.REQUEST);
        if (!allowed) {
            notice(new RestoreRecords.Notice(BackupMessages.RESTORE_NOT_NOW, false, BackupActivityLog.RESTORE_SKIPPED));
            return startSpring.call();
        }
        Optional<RestoreRecords.InProgress> swapped = swapIn(request.get());
        if (swapped.isEmpty()) {
            return startSpring.call();
        }
        try {
            T started = startSpring.call();
            records.clear(RestoreRecords.IN_PROGRESS);
            RestoreRecords.InProgress done = swapped.get();
            notice(new RestoreRecords.Notice(BackupMessages.restoreDone(done.madeAtText(), done.safetyBackup(),
                    done.keptAside()), true, BackupActivityLog.RESTORE_DONE));
            return started;
        } catch (Exception | Error startFailure) {
            LOG.warn("Restored records did not open; putting the previous records back", startFailure);
            closeDatabase.run();
            putPreviousRecordsBack(swapped.get(), reason(startFailure));
            return startSpring.call();
        }
    }

    private Optional<RestoreRecords.InProgress> swapIn(RestoreRecords.Request request) throws IOException {
        Path data = settings.dataDirectory();
        if (!waitUntilReleased(data)) {
            notice(new RestoreRecords.Notice(BackupMessages.RESTORE_LOCKED, false, BackupActivityLog.RESTORE_SKIPPED));
            return Optional.empty();
        }
        try {
            archives.verifyChecksum(request.archive());
            if (!request.sha256().equalsIgnoreCase(UpdateSignatures.sha256Hex(request.archive()))) {
                throw new UpdateVerificationException(BackupMessages.RESTORE_CHANGED);
            }
        } catch (UpdateVerificationException | IOException changed) {
            notice(new RestoreRecords.Notice(BackupMessages.restoreSkipped(changed.getMessage()), false,
                    BackupActivityLog.RESTORE_SKIPPED));
            return Optional.empty();
        }
        // Record intent first, so a crash during the swap is recovered on the next start.
        RestoreRecords.InProgress pending = new RestoreRecords.InProgress(request.archive(), Optional.empty(),
                request.safetyBackup(), request.madeAtText());
        records.saveInProgress(pending);
        try {
            Path keptAside = archives.restore(request.archive(), data, previous ->
                    records.saveInProgress(new RestoreRecords.InProgress(request.archive(),
                            Optional.ofNullable(previous), request.safetyBackup(), request.madeAtText())));
            RestoreRecords.InProgress swapped = new RestoreRecords.InProgress(request.archive(),
                    Optional.ofNullable(keptAside), request.safetyBackup(), request.madeAtText());
            records.saveInProgress(swapped);
            return Optional.of(swapped);
        } catch (UpdateVerificationException notRestored) {
            // DataBackupService.restore puts the previous folder back if its own swap fails.
            if (!Files.isRegularFile(data.resolve(DataBackupService.DATABASE_FILE))
                    && records.inProgress().flatMap(RestoreRecords.InProgress::keptAside)
                    .filter(Files::isDirectory).isPresent()) {
                throw new IOException("Restore was interrupted. The previous records and restore instructions "
                        + "have been kept; reopen FlowersForever to finish recovery.", notRestored);
            }
            records.clear(RestoreRecords.IN_PROGRESS);
            notice(new RestoreRecords.Notice(BackupMessages.restoreSkipped(notRestored.getMessage()), false,
                    BackupActivityLog.RESTORE_SKIPPED));
            return Optional.empty();
        }
    }

    private void putPreviousRecordsBack(RestoreRecords.InProgress progress, String reason) throws IOException {
        Path data = settings.dataDirectory();
        Optional<Path> previous = progress.keptAside().filter(Files::isDirectory);
        boolean databaseMissing = !Files.isRegularFile(data.resolve(DataBackupService.DATABASE_FILE));
        if (previous.isEmpty()) {
            if (databaseMissing) {
                throw new DamagedRecordsException("The restore could not identify the previous farm records"
                        + progress.keptAside().map(path -> " at " + path).orElse("")
                        + ". Restore instructions were kept; contact support and do not enter new records "
                        + "until that folder is available again.");
            }
            // The swap never happened (or there were no records before): the current folder is the farm's.
            records.clear(RestoreRecords.IN_PROGRESS);
            notice(new RestoreRecords.Notice(BackupMessages.restoreSkipped(reason), false,
                    BackupActivityLog.RESTORE_UNDONE));
            return;
        }
        Path parent = data.getParent();
        String name = String.valueOf(data.getFileName());
        try {
            if (Files.exists(data)) {
                Path failed = (parent == null ? data.resolveSibling(name) : parent)
                        .resolve(name + ".failed-restore-" + LocalDateTime.now(clock).format(STAMP));
                Files.move(data, failed, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(previous.get(), data, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException stuck) {
            String help = BackupMessages.restoreNeedsHelp(previous.get(), stuck.getMessage());
            notice(new RestoreRecords.Notice(help, false, BackupActivityLog.RESTORE_UNDONE));
            throw new IOException(help, stuck);
        }
        records.clear(RestoreRecords.IN_PROGRESS);
        notice(new RestoreRecords.Notice(BackupMessages.restoreUndone(reason), false, BackupActivityLog.RESTORE_UNDONE));
    }

    private boolean waitUntilReleased(Path data) {
        long deadline = System.nanoTime() + waitForRelease.toNanos();
        while (true) {
            try {
                DataBackupService.requireDatabaseClosed(data);
                return true;
            } catch (UpdateVerificationException stillOpen) {
                if (System.nanoTime() >= deadline) {
                    return false;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /** Damaged records are swapped for the newest healthy backup; with none, start-up stops plainly. */
    private boolean checkIntegrity() throws IOException {
        if (integrity == null) {
            return false;
        }
        DatabaseIntegrityGuard.Outcome outcome = integrity.ensureHealthy();
        if (outcome.health() == DatabaseIntegrityGuard.Health.DAMAGED && outcome.restored().isEmpty()) {
            throw new DamagedRecordsException(outcome.message());
        }
        return outcome.restored().isPresent();
    }

    private void notice(RestoreRecords.Notice notice) throws IOException {
        records.saveNotice(notice);
    }

    static String reason(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; depth < 10 && cause.getCause() != null && cause.getCause() != cause; depth++) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        int newline = message.indexOf('\n');
        String line = newline < 0 ? message : message.substring(0, newline);
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }
}

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

    public RestoreStartup(BackupSettings settings, RestoreRecords records, DataBackupService archives, Clock clock,
                          Duration waitForRelease) {
        this.settings = settings;
        this.records = records;
        this.archives = archives;
        this.clock = clock;
        this.waitForRelease = waitForRelease;
    }

    /** Built from JVM properties; {@code AppDataBootstrap} must already have run. */
    public static RestoreStartup forThisProcess() {
        BackupSettings settings = BackupSettings.fromSystemProperties();
        return new RestoreStartup(settings, new RestoreRecords(settings.stateDirectory()),
                new DataBackupService(Clock.systemDefaultZone()), Clock.systemDefaultZone(), Duration.ofSeconds(60));
    }

    /**
     * @param allowed false while an app update is being finished: a waiting restore is then
     *                cancelled (with a notice) rather than mixed into the update's own recovery
     */
    public <T> T start(Callable<T> startSpring, Runnable closeDatabase, boolean allowed) throws Exception {
        Optional<RestoreRecords.InProgress> unfinished = records.inProgress();
        if (unfinished.isPresent()) {
            putPreviousRecordsBack(unfinished.get(), "the restored records did not finish opening last time");
        }
        Optional<RestoreRecords.Request> request = records.request();
        if (request.isEmpty()) {
            return startSpring.call();
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
            Path keptAside = archives.restore(request.archive(), data);
            RestoreRecords.InProgress swapped = new RestoreRecords.InProgress(request.archive(),
                    Optional.ofNullable(keptAside), request.safetyBackup(), request.madeAtText());
            records.saveInProgress(swapped);
            return Optional.of(swapped);
        } catch (UpdateVerificationException notRestored) {
            // DataBackupService.restore puts the previous folder back if its own swap fails.
            records.clear(RestoreRecords.IN_PROGRESS);
            notice(new RestoreRecords.Notice(BackupMessages.restoreSkipped(notRestored.getMessage()), false,
                    BackupActivityLog.RESTORE_SKIPPED));
            return Optional.empty();
        }
    }

    private void putPreviousRecordsBack(RestoreRecords.InProgress progress, String reason) throws IOException {
        Path data = settings.dataDirectory();
        Optional<Path> previous = progress.keptAside().filter(Files::isDirectory);
        if (previous.isEmpty() && !Files.exists(data)) {
            // Stopped between the two folder moves: the newest kept-aside folder is the farm's.
            previous = newestKeptAside(data);
        }
        if (previous.isEmpty()) {
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

    private static Optional<Path> newestKeptAside(Path data) throws IOException {
        Path parent = data.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return Optional.empty();
        }
        String prefix = data.getFileName() + ".before-restore-";
        try (var siblings = Files.list(parent)) {
            return siblings.filter(Files::isDirectory)
                    .filter(path -> String.valueOf(path.getFileName()).startsWith(prefix))
                    .max(java.util.Comparator.comparing(path -> String.valueOf(path.getFileName())));
        }
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

package com.flowerfarm.backup;

import com.flowerfarm.update.VerifiedBackup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Decides when an automatic backup is due and runs it. Missed backups (computer
 * off, USB drive unplugged) simply run at the next chance; a missing folder is
 * never created, because a different drive may now have that letter.
 */
public final class AutomaticBackupScheduler {

    /** Backups are a little early-tolerant so "every day" does not drift later each day. */
    static final Duration EARLY_TOLERANCE = Duration.ofMinutes(30);
    static final Duration REMIND_AFTER = Duration.ofDays(14);

    public enum Status { NOT_ENABLED, NOT_DUE, DONE, FOLDER_UNAVAILABLE, FAILED }

    public record Result(Status status, String message, Optional<VerifiedBackup> backup, List<Path> removed) {
    }

    private final LiveBackupService backups;
    private final BackupPreferencesStore preferences;
    private final BackupActivityLog activity;
    private final Clock clock;

    public AutomaticBackupScheduler(LiveBackupService backups, BackupPreferencesStore preferences,
                                    BackupActivityLog activity, Clock clock) {
        this.backups = backups;
        this.preferences = preferences;
        this.activity = activity;
        this.clock = clock;
    }

    public boolean due(BackupPreferences current, Instant now) {
        if (!current.automatic() || current.folder().isEmpty()) {
            return false;
        }
        return current.lastAutomatic()
                .map(last -> !now.isBefore(last.plus(current.schedule().interval()).minus(EARLY_TOLERANCE)))
                .orElse(true);
    }

    public Result runIfDue() {
        BackupPreferences current = preferences.load();
        Instant now = clock.instant();
        if (!current.automatic() || current.folder().isEmpty()) {
            return new Result(Status.NOT_ENABLED, "", Optional.empty(), List.of());
        }
        if (!due(current, now)) {
            return new Result(Status.NOT_DUE, "", Optional.empty(), List.of());
        }
        Path folder = current.folder().get();
        if (!Files.isDirectory(folder)) {
            String message = BackupMessages.folderUnavailable(folder);
            remember(current.withProblem(message));
            return new Result(Status.FOLDER_UNAVAILABLE, message, Optional.empty(), List.of());
        }
        try {
            VerifiedBackup backup = backups.backUp(folder, BackupFile.AUTOMATIC_LABEL);
            List<Path> removed = BackupFile.prune(folder, current.keep());
            remember(preferences.load().withBackupAt(now, true));
            String message = BackupMessages.automaticSaved(backup, removed.size());
            activity.record(BackupActivityLog.AUTO_BACKUP, true, message);
            return new Result(Status.DONE, message, Optional.of(backup), removed);
        } catch (BackupException | IOException failed) {
            String message = "Automatic backup failed: " + BackupMessages.sentence(failed.getMessage())
                    + " FlowersForever will try again later.";
            remember(current.withProblem(message));
            activity.record(BackupActivityLog.AUTO_BACKUP_FAILED, false, message);
            return new Result(Status.FAILED, message, Optional.empty(), List.of());
        }
    }

    /** A one-time warning when automatic backups have been failing for a while. */
    public Optional<String> overdueWarning() {
        BackupPreferences current = preferences.load();
        if (!current.automatic() || current.lastProblem().isEmpty() || current.lastAutomatic().isEmpty()) {
            return Optional.empty();
        }
        Instant last = current.lastAutomatic().get();
        Duration late = Duration.between(last, clock.instant());
        if (late.compareTo(current.schedule().interval().multipliedBy(3)) < 0) {
            return Optional.empty();
        }
        return Optional.of(BackupMessages.overdue(LocalDateTime.ofInstant(last, ZoneId.systemDefault()),
                current.lastProblem().get()));
    }

    /** A gentle status-bar tip when nothing has been backed up for two weeks and automatic backups are off. */
    public Optional<String> reminder() {
        BackupPreferences current = preferences.load();
        if (current.automatic()) {
            return Optional.empty();
        }
        boolean stale = current.lastBackup()
                .map(last -> Duration.between(last, clock.instant()).compareTo(REMIND_AFTER) >= 0)
                .orElse(true);
        return stale ? Optional.of(BackupMessages.REMINDER) : Optional.empty();
    }

    private void remember(BackupPreferences updated) {
        try {
            preferences.save(updated);
        } catch (IOException cannotSave) {
            // The next check simply runs again; backups themselves are unaffected.
        }
    }
}

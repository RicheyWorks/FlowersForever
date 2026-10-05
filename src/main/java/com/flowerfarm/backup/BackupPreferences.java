package com.flowerfarm.backup;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * The grower's backup choices plus a little history. {@code lastBackup} counts
 * any checked backup (manual, automatic, or before a restore).
 */
public record BackupPreferences(boolean automatic, Optional<Path> folder, BackupSchedule schedule, int keep,
                                Optional<Instant> lastAutomatic, Optional<Instant> lastBackup,
                                Optional<String> lastProblem) {

    public static final int DEFAULT_KEEP = 10;
    public static final int MAX_KEEP = 100;

    public BackupPreferences {
        folder = folder == null ? Optional.empty() : folder;
        schedule = schedule == null ? BackupSchedule.DAILY : schedule;
        keep = Math.max(1, Math.min(MAX_KEEP, keep));
        lastAutomatic = lastAutomatic == null ? Optional.empty() : lastAutomatic;
        lastBackup = lastBackup == null ? Optional.empty() : lastBackup;
        lastProblem = lastProblem == null ? Optional.empty() : lastProblem.filter(text -> !text.isBlank());
    }

    public static BackupPreferences defaults() {
        return new BackupPreferences(false, Optional.empty(), BackupSchedule.DAILY, DEFAULT_KEEP,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public BackupPreferences withChoices(boolean automaticOn, Optional<Path> chosenFolder,
                                         BackupSchedule chosenSchedule, int chosenKeep) {
        return new BackupPreferences(automaticOn, chosenFolder, chosenSchedule, chosenKeep, lastAutomatic,
                lastBackup, lastProblem);
    }

    public BackupPreferences withBackupAt(Instant when, boolean wasAutomatic) {
        return new BackupPreferences(automatic, folder, schedule, keep,
                wasAutomatic ? Optional.of(when) : lastAutomatic, Optional.of(when),
                wasAutomatic ? Optional.empty() : lastProblem);
    }

    public BackupPreferences withProblem(String problem) {
        return new BackupPreferences(automatic, folder, schedule, keep, lastAutomatic, lastBackup,
                Optional.ofNullable(problem));
    }
}

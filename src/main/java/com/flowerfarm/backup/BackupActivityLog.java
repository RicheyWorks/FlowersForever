package com.flowerfarm.backup;

/** Records backups and restores in the farm's Activity History. */
@FunctionalInterface
public interface BackupActivityLog {

    String SOURCE = "Backups";
    String BACKUP_CREATED = "BACKUP_CREATED";
    String BACKUP_FAILED = "BACKUP_FAILED";
    String AUTO_BACKUP = "AUTO_BACKUP";
    String AUTO_BACKUP_FAILED = "AUTO_BACKUP_FAILED";
    String RESTORE_REQUESTED = "RESTORE_REQUESTED";
    String RESTORE_DONE = "RESTORE_DONE";
    String RESTORE_UNDONE = "RESTORE_UNDONE";
    String RESTORE_SKIPPED = "RESTORE_SKIPPED";

    void record(String operation, boolean success, String message);

    static BackupActivityLog none() {
        return (operation, success, message) -> { };
    }
}

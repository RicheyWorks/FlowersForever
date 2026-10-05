package com.flowerfarm.backup;

import com.flowerfarm.update.VerifiedBackup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Every backup and restore message the grower can see, in plain words. */
public final class BackupMessages {

    public static final String TITLE = "FlowersForever backups";
    public static final String FOLDER_INSIDE_RECORDS =
            "Please choose a folder outside the FlowersForever records folder, such as Documents, a USB drive, "
                    + "or a OneDrive folder.";
    public static final String NEWER_VERSION =
            "This backup was made by a newer version of FlowersForever. Update FlowersForever first, then "
                    + "restore it. Nothing was changed.";
    public static final String DAMAGED_RECORDS =
            "The records in this backup are damaged (an upgrade step had failed when it was made), so it "
                    + "cannot be restored. Please choose an older backup. Nothing was changed.";
    public static final String RESTORE_NOT_NOW =
            "The restore did not happen because FlowersForever was finishing an update. Nothing was changed. "
                    + "Choose File > Restore from a backup… again.";
    public static final String RESTORE_LOCKED =
            "The restore did not happen because another FlowersForever window or the AI helper still had the "
                    + "farm records open. Nothing was changed. Close them, then choose File > Restore from a "
                    + "backup… again.";
    public static final String RESTORE_CHANGED =
            "the backup file changed after it was checked";
    public static final String NO_BACKUPS_FOUND =
            "No backups were found in the usual folders. Choose the backup file (.zip) to restore.";
    public static final String UPDATE_IN_PROGRESS =
            "FlowersForever is finishing an update. Please restore after it has opened normally again.";
    public static final String BUSY = "A backup or restore is already running. Please wait for it to finish.";
    public static final String REMINDER =
            "Tip: your farm records have not been backed up for a while. Use File > Back up now, or turn on "
                    + "automatic backups.";
    public static final String CHOOSE_FOLDER_FIRST =
            "Please choose a folder for automatic backups, or turn automatic backups off.";

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US);

    private BackupMessages() {
    }

    public static String when(LocalDateTime time) {
        return time.format(WHEN);
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    public static String backupSaved(VerifiedBackup backup) {
        return "Your farm records are backed up, and the copy was checked.\n\n"
                + "Saved: " + backup.archive() + "\n"
                + backup.fileCount() + " files, " + size(backup.bytes()) + ".\n\n"
                + "Tip: keep a copy away from this computer too, such as on a USB drive or in a OneDrive folder.";
    }

    public static String automaticSaved(VerifiedBackup backup, int removed) {
        return "Automatic backup saved and checked: " + backup.archive().getFileName()
                + (removed > 0 ? " (removed " + removed + " older automatic " + (removed == 1 ? "backup" : "backups")
                + ")" : "") + ".";
    }

    public static String backupFailed(String reason) {
        return "The backup did not finish, so nothing was saved. " + sentence(reason)
                + "\n\nYour farm records are unchanged.";
    }

    /** Ends {@code text} with exactly one full stop. */
    static String sentence(String text) {
        String value = text == null || text.isBlank() ? "something went wrong" : text.trim();
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ".";
    }

    public static String recordsMissing(Path data) {
        return "The farm records folder was not found: " + data;
    }

    public static String folderNotWritable(Path folder) {
        return "FlowersForever cannot save backups in " + folder + ". Please choose another folder.";
    }

    public static String folderUnavailable(Path folder) {
        return "Automatic backup skipped: the backup folder is not available right now (" + folder + "). "
                + "If it is on a USB drive, plug it in. FlowersForever will try again later.";
    }

    public static String notEnoughSpace(Path folder, long needed) {
        return "There is not enough free space in " + folder + " for a backup (about " + size(needed)
                + " is needed). Please choose another folder or free up some space.";
    }

    public static String writeFailed(Path folder, String detail) {
        return "The backup could not be written to " + folder + (detail == null ? "." : " (" + detail + ").");
    }

    public static String databaseCopyFailed(String detail) {
        return "The farm records could not be copied right now (" + detail + "). Please try again in a minute.";
    }

    public static String backupMissing(Path archive) {
        return "The backup file was not found: " + archive;
    }

    public static String checksumMissing(Path archive) {
        return "This backup cannot be restored because its check file (" + archive.getFileName()
                + ".sha256) is missing. FlowersForever only restores backups it can check. Nothing was changed.";
    }

    public static String notRestorable(String detail) {
        return "This backup cannot be restored: " + sentence(detail) + " Nothing was changed.";
    }

    public static String confirmRestore(BackupDetails details, Path safetyFolder) {
        String made = details.madeAt().map(BackupMessages::when).orElse("an unknown date");
        return "Restore your farm records from the backup made on " + made + "?\n\n"
                + "Backup: " + details.archive().getFileName() + " (" + details.files() + " files, "
                + size(details.bytes()) + ", checked)\n\n"
                + "What happens:\n"
                + "1. FlowersForever saves a checked backup of your current records in " + safetyFolder + ".\n"
                + "2. It closes for a moment and opens again with the restored records.\n\n"
                + "Anything entered after " + made + " will not be in the restored records, but it stays in "
                + "that new backup, so nothing is lost."
                + (details.upgradesOnOpen() ? "\n\nThis backup is from an older version; FlowersForever will "
                + "upgrade it when it opens." : "");
    }

    public static String restartManually() {
        return "FlowersForever will close now. Open it again to finish restoring your farm records.";
    }

    public static String restoreDone(String madeAt, Optional<Path> safetyBackup, Optional<Path> keptAside) {
        StringBuilder text = new StringBuilder("Your farm records were restored from the backup made on ")
                .append(madeAt.isBlank() ? "the chosen date" : madeAt).append(".");
        if (safetyBackup.isPresent() || keptAside.isPresent()) {
            text.append("\n\nThe records you had just before restoring are kept, so nothing was lost:");
            safetyBackup.ifPresent(path -> text.append("\n• checked backup: ").append(path));
            keptAside.ifPresent(path -> text.append("\n• previous records folder: ").append(path));
        }
        return text.toString();
    }

    public static String restoreUndone(String reason) {
        return "The restored records did not open (" + reason + "), so FlowersForever put back the records you "
                + "had before. Nothing was lost. You can try an older backup.";
    }

    public static String restoreSkipped(String reason) {
        return "The restore did not happen: " + sentence(reason) + " Nothing was changed.";
    }

    public static String restoreNeedsHelp(Path previous, String detail) {
        return "FlowersForever could not finish putting your previous records back (" + detail + "). Nothing was "
                + "deleted: your previous records are in " + previous + ". Please do not enter new work; contact "
                + "RicheyWorks, or follow docs/BACKUP_RESTORE.md.";
    }

    public static String automaticOn(BackupPreferences preferences) {
        return "Automatic backups are on: " + preferences.schedule().label().toLowerCase(Locale.ROOT) + " to "
                + preferences.folder().map(Path::toString).orElse("?") + ", keeping the newest "
                + preferences.keep() + ".";
    }

    public static String automaticOff() {
        return "Automatic backups are off. You can still use File > Back up now at any time.";
    }

    public static String overdue(LocalDateTime last, String problem) {
        return "Automatic backups have not worked since " + when(last) + ".\n\n" + problem
                + "\n\nYou can check the folder in File > Automatic backups…, or use File > Back up now.";
    }
}

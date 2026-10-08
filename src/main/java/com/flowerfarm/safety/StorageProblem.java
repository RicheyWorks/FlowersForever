package com.flowerfarm.safety;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.ReadOnlyFileSystemException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

/**
 * Recognises the ways saving to the computer itself can fail (disk full, no
 * permission, read-only folder, file in use, damaged records) and says so in plain
 * words. Anything else is not a storage problem and is left to the caller.
 */
public enum StorageProblem {

    DISK_FULL("the disk is full",
            "Free up some space on this computer (empty the Recycle Bin or move large files), then try again."),
    READ_ONLY("the folder or file is read-only",
            "Make sure the folder is not set to read-only and is not on a locked USB stick or CD, then try again."),
    ACCESS_DENIED("Windows did not give FlowersForever permission to write there",
            "Choose a folder you own (for example Documents), or ask whoever manages this computer, then try again."),
    LOCKED("the file is being used by another program",
            "Close the other program (or the other FlowersForever window) that has it open, then try again."),
    DAMAGED("the farm records file looks damaged",
            "Close FlowersForever and open it again; it checks the records and can restore your latest backup.");

    private final String what;
    private final String fix;

    StorageProblem(String what, String fix) {
        this.what = what;
        this.fix = fix;
    }

    public String what() {
        return what;
    }

    public String fix() {
        return fix;
    }

    /** One plain-language sentence pair for a failed save. */
    public String explain(String action) {
        return action + " did not finish: " + what + ". " + fix;
    }

    public static Optional<StorageProblem> of(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 20; depth++) {
            Optional<StorageProblem> found = classify(cause);
            if (found.isPresent()) {
                return found;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return Optional.empty();
    }

    private static Optional<StorageProblem> classify(Throwable cause) {
        String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
        if (cause instanceof FileSystemException fs && fs.getReason() != null) {
            text = text + " " + fs.getReason().toLowerCase(Locale.ROOT);
        }
        if (text.contains("not enough space") || text.contains("no space left") || text.contains("disk full")
                || text.contains("disk is full") || text.contains("enospc") || text.contains("quota exceeded")) {
            return Optional.of(DISK_FULL);
        }
        if (cause instanceof ReadOnlyFileSystemException || text.contains("read-only")
                || text.contains("read only") || text.contains("write-protected") || text.contains("write protected")) {
            return Optional.of(READ_ONLY);
        }
        if (cause instanceof SQLException sql) {
            switch (sql.getErrorCode()) {
                case 90097: // DATABASE_IS_READ_ONLY
                    return Optional.of(READ_ONLY);
                case 90020: // DATABASE_ALREADY_OPEN
                    return Optional.of(LOCKED);
                case 90030, 90048: // FILE_CORRUPTED, FILE_VERSION_ERROR
                    return Optional.of(DAMAGED);
                default:
                    break;
            }
        }
        if (text.contains("being used by another process") || text.contains("locked a portion of the file")
                || text.contains("already in use") || text.contains("file is locked")) {
            return Optional.of(LOCKED);
        }
        if (cause instanceof AccessDeniedException || text.contains("access is denied")
                || text.contains("permission denied") || text.contains("operation not permitted")) {
            return Optional.of(ACCESS_DENIED);
        }
        if (text.contains("file corrupted") || text.contains("is corrupted")) {
            return Optional.of(DAMAGED);
        }
        return Optional.empty();
    }
}

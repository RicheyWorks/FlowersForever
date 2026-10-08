package com.flowerfarm.backup;

import java.nio.file.Path;

/** Test-only access to the backup skip rule. */
public final class LiveBackupServiceAccess {

    private LiveBackupServiceAccess() {
    }

    public static boolean skipped(Path data, Path file) {
        return LiveBackupService.skipped(data, file);
    }
}

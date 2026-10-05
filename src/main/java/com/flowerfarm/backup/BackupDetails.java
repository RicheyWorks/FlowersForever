package com.flowerfarm.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

/** What a checked backup contains, shown to the grower before restoring. */
public record BackupDetails(Path archive, String sha256, Optional<LocalDateTime> madeAt, long bytes, int files,
                            String recordsVersion, boolean upgradesOnOpen) {
}

package com.flowerfarm.update;

import java.nio.file.Path;

/** A backup ZIP whose SHA-256 sidecar and contents were both checked after writing. */
public record VerifiedBackup(Path archive, Path checksumFile, String sha256, int fileCount, long bytes) {
}

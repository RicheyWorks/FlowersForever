package com.flowerfarm.update;

import java.nio.file.Path;

/** An installer on disk whose size, SHA-256, and publisher signature all matched the signed manifest. */
public record VerifiedPackage(UpdatePlatform platform, AppVersion version, Path file,
                              String sha256, long size) {
}

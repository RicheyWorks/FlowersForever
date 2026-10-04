package com.flowerfarm.update;

import java.nio.file.Path;

/**
 * Operating-system publisher check (Authenticode on Windows, Developer ID on
 * macOS) applied on top of the app's own Ed25519 package signature. Until a
 * code-signing certificate exists this is {@link #none()}; see
 * {@link AuthenticodePublisherVerifier} for the Windows implementation to switch on.
 */
@FunctionalInterface
public interface PublisherVerifier {

    void verify(Path file, UpdatePlatform platform) throws UpdateVerificationException;

    static PublisherVerifier none() {
        return (file, platform) -> { };
    }
}

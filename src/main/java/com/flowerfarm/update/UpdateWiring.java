package com.flowerfarm.update;

import java.time.Clock;
import java.util.Optional;

/**
 * Builds the update coordinator from its parts. The desktop (through
 * {@link UpdateConfiguration}) and the headless release check use this same
 * method, so both run the same check, download verification, backup, hand-off,
 * and rollback code; only the trusted keys, the transport, and the installer
 * hand-off differ.
 */
public final class UpdateWiring {

    private UpdateWiring() {
    }

    public static UpdateCoordinator coordinator(UpdateSettings settings, TrustedKeys keys, UpdateTransport transport,
                                                Optional<UpdatePlatform> platform, PlatformInstaller installer,
                                                UpdateActivityLog activity) {
        SignedManifestVerifier verifier = new SignedManifestVerifier(keys);
        UpdateStateStore store = new UpdateStateStore(settings.updatesDirectory());
        PublisherVerifier publisher = settings.authenticodeSubject().isBlank()
                ? PublisherVerifier.none() : new AuthenticodePublisherVerifier(settings.authenticodeSubject());
        UpdateChecker checker = new UpdateChecker(settings, verifier, transport, platform, store::failedVersions);
        PackageDownloader downloader = new PackageDownloader(settings.updatesDirectory(), transport, verifier,
                publisher);
        return new UpdateCoordinator(settings, checker, downloader, new DataBackupService(Clock.systemDefaultZone()),
                store, installer, platform, activity, Clock.systemUTC());
    }
}

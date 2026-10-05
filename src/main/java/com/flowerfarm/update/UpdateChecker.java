package com.flowerfarm.update;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Fetches and verifies the signed manifest, then picks the newest release this install should get. */
public final class UpdateChecker {

    static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private final UpdateSettings settings;
    private final SignedManifestVerifier verifier;
    private final UpdateTransport transport;
    private final Optional<UpdatePlatform> platform;
    private final Supplier<Set<String>> failedVersions;

    public UpdateChecker(UpdateSettings settings, SignedManifestVerifier verifier,
                         UpdateTransport transport, Optional<UpdatePlatform> platform,
                         Supplier<Set<String>> failedVersions) {
        this.settings = settings;
        this.verifier = verifier;
        this.transport = transport;
        this.platform = platform;
        this.failedVersions = failedVersions;
    }

    /**
     * @param manual true when the farm chose "Check for updates"; that ignores the
     *               automatic-check preference but never the configuration switch
     */
    public UpdateCheckResult check(boolean manual) {
        if (!settings.enabledByConfiguration() || (!manual && !settings.automaticChecksEnabled())) {
            return UpdateCheckResult.simple(UpdateCheckResult.Status.DISABLED,
                    UpdateMessages.DISABLED);
        }
        if (settings.manifestUrl().isEmpty() || !verifier.hasTrustedKeys()) {
            return UpdateCheckResult.simple(UpdateCheckResult.Status.NOT_CONFIGURED,
                    UpdateMessages.NOT_CONFIGURED);
        }
        byte[] envelope;
        try {
            envelope = transport.fetch(settings.manifestUrl().get(), MAX_MANIFEST_BYTES);
        } catch (IOException | RuntimeException offline) {
            return UpdateCheckResult.simple(UpdateCheckResult.Status.OFFLINE, UpdateMessages.OFFLINE);
        }
        UpdateManifest manifest;
        try {
            manifest = verifier.verify(envelope);
        } catch (UpdateVerificationException rejected) {
            return UpdateCheckResult.simple(UpdateCheckResult.Status.REJECTED, rejected.getMessage());
        }
        return choose(manifest);
    }

    UpdateCheckResult choose(UpdateManifest manifest) {
        AppVersion current = settings.currentVersion();
        if (manifest.paused()) {
            String reason = manifest.pauseMessage().isBlank() ? "" : " " + manifest.pauseMessage().trim();
            return new UpdateCheckResult(UpdateCheckResult.Status.PAUSED,
                    UpdateMessages.PAUSED + reason, Optional.of(manifest), Optional.empty(), Optional.empty());
        }
        if (platform.isEmpty()) {
            return upToDate(manifest, current);
        }
        Set<String> failed = failedVersions.get();
        Optional<UpdateManifest.Release> newest = manifest.releases().stream()
                .filter(release -> release.offeredTo(settings.channel()))
                .filter(release -> release.version().isNewerThan(current))
                .filter(release -> release.packageFor(platform.get()).isPresent())
                .filter(release -> !failed.contains(release.version().toString()))
                .max(Comparator.comparing(UpdateManifest.Release::version));
        if (newest.isEmpty()) {
            return upToDate(manifest, current);
        }
        UpdateManifest.Release release = newest.get();
        UpdateManifest.PackageInfo info = release.packageFor(platform.get()).orElseThrow();
        if (release.minimumVersion() != null && current.compareTo(release.minimumVersion()) < 0) {
            return new UpdateCheckResult(UpdateCheckResult.Status.MANUAL_INSTALL_REQUIRED,
                    UpdateMessages.manualInstall(release.version(), current),
                    Optional.of(manifest), Optional.of(release), Optional.of(info));
        }
        return new UpdateCheckResult(UpdateCheckResult.Status.UPDATE_AVAILABLE,
                UpdateMessages.available(release.version(), current, release.notes()),
                Optional.of(manifest), Optional.of(release), Optional.of(info));
    }

    private static UpdateCheckResult upToDate(UpdateManifest manifest, AppVersion current) {
        return new UpdateCheckResult(UpdateCheckResult.Status.UP_TO_DATE,
                UpdateMessages.upToDate(current), Optional.of(manifest), Optional.empty(), Optional.empty());
    }
}

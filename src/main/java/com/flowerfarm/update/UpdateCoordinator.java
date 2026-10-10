package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * The update workflow, in the only order it may run:
 * check → download and verify (new installer and the current version's
 * installer for rollback) → close the database → verified backup →
 * record progress → hand off to the installer. Any failure before the
 * hand-off leaves the installed version and the records untouched.
 */
public final class UpdateCoordinator {

    /** Closes the database so the records can be backed up. Called once, right before the backup. */
    @FunctionalInterface
    public interface DatabaseShutdown {
        void closeDatabase() throws Exception;
    }

    /** Both installers needed for an update, already verified. */
    public record PreparedUpdate(VerifiedPackage next, Optional<VerifiedPackage> rollback, String notes) {
    }

    public record InstallOutcome(boolean handedOff, boolean databaseClosed, String message,
                                 Optional<VerifiedBackup> backup) {
    }

    private final UpdateSettings settings;
    private final UpdateChecker checker;
    private final PackageDownloader downloader;
    private final DataBackupService backups;
    private final UpdateStateStore store;
    private final PlatformInstaller installer;
    private final Optional<UpdatePlatform> platform;
    private final UpdateActivityLog activity;
    private final Clock clock;

    public UpdateCoordinator(UpdateSettings settings, UpdateChecker checker, PackageDownloader downloader,
                             DataBackupService backups, UpdateStateStore store, PlatformInstaller installer,
                             Optional<UpdatePlatform> platform, UpdateActivityLog activity, Clock clock) {
        this.settings = settings;
        this.checker = checker;
        this.downloader = downloader;
        this.backups = backups;
        this.store = store;
        this.installer = installer;
        this.platform = platform;
        this.activity = activity;
        this.clock = clock;
    }

    public UpdateCheckResult check(boolean manual) {
        UpdateCheckResult result = checker.check(manual);
        if (result.status() == UpdateCheckResult.Status.UPDATE_AVAILABLE) {
            activity.record("UPDATE_AVAILABLE", true, "Version " + result.release().orElseThrow().version()
                    + " is available (current " + settings.currentVersion() + ").");
        } else if (result.status() == UpdateCheckResult.Status.REJECTED) {
            activity.record("UPDATE_REJECTED", false, result.message());
        }
        return result;
    }

    public boolean canInstall() {
        return installer.canInstall() && platform.isPresent();
    }

    public UpdateSettings settings() {
        return settings;
    }

    /** Downloads and verifies the new installer, and secures the current version's installer for rollback. */
    public PreparedUpdate prepare(UpdateCheckResult result, LongConsumer progress) throws UpdateVerificationException {
        if (!result.updateAvailable() || platform.isEmpty()) {
            throw new UpdateVerificationException("There is no update to install.");
        }
        UpdateManifest manifest = result.manifest().orElseThrow();
        UpdateManifest.Release release = result.release().orElseThrow();
        UpdatePlatform kind = platform.get();
        VerifiedPackage next;
        try {
            next = downloader.obtain(kind, release.version(), result.packageInfo().orElseThrow(), progress);
        } catch (UpdateVerificationException rejected) {
            activity.record("UPDATE_DOWNLOAD_REJECTED", false, rejected.getMessage());
            throw rejected;
        }
        Optional<VerifiedPackage> rollback = rollbackInstaller(manifest, kind);
        if (rollback.isEmpty() && settings.requireRollbackInstaller()) {
            String message = "FlowersForever could not get a checked copy of the installer for the version you "
                    + "have now (" + settings.currentVersion() + "). It keeps that copy so it can go back if an "
                    + "update fails, so the update was not installed. Your records were not changed.";
            activity.record("UPDATE_STOPPED", false, message);
            throw new UpdateVerificationException(message);
        }
        activity.record("UPDATE_DOWNLOADED", true, "Version " + release.version()
                + " downloaded; size, checksum, and publisher signature verified.");
        return new PreparedUpdate(next, rollback, release.notes());
    }

    private Optional<VerifiedPackage> rollbackInstaller(UpdateManifest manifest, UpdatePlatform kind) {
        AppVersion current = settings.currentVersion();
        Optional<UpdateManifest.PackageInfo> listed = manifest.releases().stream()
                .filter(release -> release.version().equals(current))
                .map(release -> release.packageFor(kind))
                .flatMap(Optional::stream)
                .findFirst();
        if (listed.isPresent()) {
            try {
                return Optional.of(downloader.obtain(kind, current, listed.get(), bytes -> { }));
            } catch (UpdateVerificationException unavailable) {
                // Fall back to a cached copy below.
            }
        }
        return downloader.cached(kind, current);
    }

    /**
     * Closes the database, makes the verified backup, records progress, and starts
     * the installer. When this returns {@code handedOff}, the caller must exit the
     * process promptly. When it returns not handed off with {@code databaseClosed},
     * the app must restart (nothing was installed).
     */
    public InstallOutcome install(PreparedUpdate prepared, DatabaseShutdown shutdown) {
        try {
            reverify(prepared.next());
            if (prepared.rollback().isPresent()) {
                reverify(prepared.rollback().get());
            }
        } catch (UpdateVerificationException changed) {
            return new InstallOutcome(false, false, changed.getMessage(), Optional.empty());
        }
        if (!installer.canInstall()) {
            return new InstallOutcome(false, false, UpdateMessages.CANNOT_INSTALL_HERE, Optional.empty());
        }
        activity.record("UPDATE_STARTED", true, "Closing the records to back them up before installing "
                + prepared.next().version() + ".");
        try {
            shutdown.closeDatabase();
        } catch (Exception closeFailed) {
            return new InstallOutcome(false, true, UpdateMessages.backupFailed(
                    "The records could not be closed safely: " + closeFailed.getMessage()), Optional.empty());
        }
        VerifiedBackup backup;
        try {
            backup = backups.backup(settings.dataDirectory(), settings.backupDirectory(),
                    "before-" + prepared.next().version());
        } catch (UpdateVerificationException backupFailed) {
            UpdateState state = store.load();
            state.pendingActivity = "Update to " + prepared.next().version() + " stopped: "
                    + backupFailed.getMessage();
            state.pendingActivitySuccess = false;
            store.save(state);
            return new InstallOutcome(false, true, UpdateMessages.backupFailed(backupFailed.getMessage()),
                    Optional.empty());
        }
        UpdateState state = store.load();
        state.phase = UpdateState.Phase.INSTALL_HANDED_OFF;
        state.fromVersion = settings.currentVersion().toString();
        state.toVersion = prepared.next().version().toString();
        state.backupArchive = backup.archive().toString();
        state.newInstaller = prepared.next().file().toString();
        state.rollbackInstaller = prepared.rollback().map(pkg -> pkg.file().toString()).orElse(null);
        state.rollbackSha256 = prepared.rollback().map(VerifiedPackage::sha256).orElse(null);
        state.rollbackSize = prepared.rollback().map(VerifiedPackage::size).orElse(0L);
        state.handedOffAt = Instant.now(clock).toString();
        state.launchAttempts = 0;
        state.pendingActivity = "Backed up records to " + backup.archive() + " (" + backup.fileCount()
                + " files, SHA-256 verified) and started the installer for " + prepared.next().version() + ".";
        state.pendingActivitySuccess = true;
        store.save(state);
        try {
            installer.launch(prepared.next(), store.directory().resolve("handoff"), PlatformInstaller.Purpose.UPDATE);
        } catch (IOException launchFailed) {
            UpdateState reverted = store.load();
            reverted.clearAttempt();
            reverted.pendingActivity = "Update to " + prepared.next().version()
                    + " was not started: " + launchFailed.getMessage();
            reverted.pendingActivitySuccess = false;
            store.save(reverted);
            return new InstallOutcome(false, true, "The installer could not be started, so nothing was "
                    + "installed. Your records were backed up to " + backup.archive() + " and were not changed.",
                    Optional.of(backup));
        }
        return new InstallOutcome(true, true, "Installing FlowersForever " + prepared.next().version() + "…",
                Optional.of(backup));
    }

    static void reverify(VerifiedPackage pkg) throws UpdateVerificationException {
        try {
            if (!Files.isRegularFile(pkg.file()) || Files.size(pkg.file()) != pkg.size()
                    || !UpdateSignatures.sha256Hex(pkg.file()).equals(pkg.sha256())) {
                throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                        "The installer changed after it was checked."));
            }
        } catch (IOException unreadable) {
            throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                    "The installer could not be read again."), unreadable);
        }
    }

    /** Path helper for tests and docs. */
    public Path updatesDirectory() {
        return store.directory();
    }
}

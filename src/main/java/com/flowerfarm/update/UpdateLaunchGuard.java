package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Runs in {@code main} around the Spring start. On the first launch after an
 * update the new version must pass its database health check before any work
 * is done. If it fails (or a previous first launch never finished), the guard
 * restores the pre-update backup and starts the previous installer. Nothing is
 * ever deleted: the records from the failed start are kept beside the folder.
 */
public final class UpdateLaunchGuard {

    static final Duration HANDOFF_WINDOW = Duration.ofMinutes(30);
    static final int MAX_ROLLBACK_LAUNCHES = 2;

    public record RollbackOutcome(boolean installerStarted, String message) {
    }

    private final UpdateSettings settings;
    private final UpdateStateStore store;
    private final DataBackupService backups;
    private final PlatformInstaller installer;
    private final Optional<UpdatePlatform> platform;
    private final Clock clock;
    private UpdateState.Decision decision = UpdateState.Decision.NORMAL;

    public UpdateLaunchGuard(UpdateSettings settings, UpdateStateStore store, DataBackupService backups,
                             PlatformInstaller installer, Optional<UpdatePlatform> platform, Clock clock) {
        this.settings = settings;
        this.store = store;
        this.backups = backups;
        this.installer = installer;
        this.platform = platform;
        this.clock = clock;
    }

    public UpdateState.Decision beforeStart() {
        decision = decide();
        return decision;
    }

    public UpdateState.Decision decision() {
        return decision;
    }

    private UpdateState.Decision decide() {
        UpdateState state = store.load();
        AppVersion current = settings.currentVersion();
        switch (state.phase) {
            case INSTALL_HANDED_OFF -> {
                if (is(current, state.toVersion)) {
                    state.phase = UpdateState.Phase.FIRST_LAUNCH_RUNNING;
                    state.launchAttempts = 1;
                    store.save(state);
                    return UpdateState.Decision.VERIFY_FIRST_LAUNCH;
                }
                if (handoffMayStillBeRunning(state)) {
                    return UpdateState.Decision.NORMAL;
                }
                state.notice = UpdateMessages.installNotCompleted(version(state.toVersion), current);
                state.pendingActivity = "Update to " + state.toVersion + " was not installed; still on " + current + ".";
                state.pendingActivitySuccess = false;
                state.clearAttempt();
                store.save(state);
                return UpdateState.Decision.NORMAL;
            }
            case FIRST_LAUNCH_RUNNING -> {
                if (is(current, state.toVersion)) {
                    return UpdateState.Decision.ROLLBACK_NOW;
                }
                state.clearAttempt();
                store.save(state);
                return UpdateState.Decision.NORMAL;
            }
            case ROLLED_BACK -> {
                if (is(current, state.toVersion)) {
                    // The previous version's installer did not finish; try it again a limited number of times.
                    return state.launchAttempts < MAX_ROLLBACK_LAUNCHES
                            ? UpdateState.Decision.ROLLBACK_NOW : block(state, "the previous version could not be reinstalled");
                }
                state.clearAttempt();
                store.save(state);
                return UpdateState.Decision.NORMAL;
            }
            case ROLLBACK_NEEDS_HELP -> {
                if (is(current, state.toVersion)) {
                    return UpdateState.Decision.BLOCKED;
                }
                state.clearAttempt();
                store.save(state);
                return UpdateState.Decision.NORMAL;
            }
            default -> {
                return UpdateState.Decision.NORMAL;
            }
        }
    }

    private UpdateState.Decision block(UpdateState state, String reason) {
        state.phase = UpdateState.Phase.ROLLBACK_NEEDS_HELP;
        state.failureReason = reason;
        state.notice = UpdateMessages.rollbackNeedsHelp(version(state.toVersion), path(state.backupArchive), reason);
        store.save(state);
        return UpdateState.Decision.BLOCKED;
    }

    /** The first launch passed its health check. */
    public void confirmHealthy() {
        UpdateState state = store.load();
        if (state.phase != UpdateState.Phase.FIRST_LAUNCH_RUNNING) {
            return;
        }
        state.notice = UpdateMessages.updated(version(state.toVersion), path(state.backupArchive));
        state.pendingActivity = "Updated from " + state.fromVersion + " to " + state.toVersion
                + "; first-launch database health check passed.";
        state.pendingActivitySuccess = true;
        state.clearAttempt();
        store.save(state);
        decision = UpdateState.Decision.NORMAL;
    }

    /**
     * Restores the pre-update backup (only on the first attempt) and starts the
     * previous version's installer. The database must already be closed.
     */
    public RollbackOutcome rollBack(String reason) {
        UpdateState state = store.load();
        AppVersion failed = version(state.toVersion);
        AppVersion previous = version(state.fromVersion);
        if (state.phase != UpdateState.Phase.ROLLED_BACK) {
            Path backup = path(state.backupArchive);
            if (backup == null) {
                block(state, "no pre-update backup was recorded");
                return new RollbackOutcome(false, state.notice);
            }
            Path keptAside;
            try {
                keptAside = backups.restore(backup, settings.dataDirectory());
            } catch (UpdateVerificationException restoreFailed) {
                block(state, restoreFailed.getMessage());
                return new RollbackOutcome(false, state.notice);
            }
            state.phase = UpdateState.Phase.ROLLED_BACK;
            state.keptAside = keptAside == null ? null : keptAside.toString();
            state.failureReason = reason;
            state.failedVersions.add(failed.toString());
            state.notice = UpdateMessages.rolledBack(failed, previous, backup, keptAside);
            state.pendingActivity = "Update to " + failed + " failed its first-launch check (" + reason
                    + "); records restored from " + backup + " and reinstalling " + previous + ".";
            state.pendingActivitySuccess = false;
            state.launchAttempts = 0;
            store.save(state);
        }
        Optional<VerifiedPackage> previousInstaller = previousInstaller(state, previous);
        if (previousInstaller.isEmpty()) {
            block(state, "the installer for " + previous + " is missing or damaged");
            return new RollbackOutcome(false, state.notice);
        }
        state.launchAttempts++;
        store.save(state);
        try {
            installer.launch(previousInstaller.get(), store.directory().resolve("handoff"),
                    PlatformInstaller.Purpose.ROLLBACK);
        } catch (IOException launchFailed) {
            block(state, "the previous installer could not be started: " + launchFailed.getMessage());
            return new RollbackOutcome(false, state.notice);
        }
        return new RollbackOutcome(true, state.notice);
    }

    private Optional<VerifiedPackage> previousInstaller(UpdateState state, AppVersion previous) {
        if (state.rollbackInstaller == null || state.rollbackSha256 == null || platform.isEmpty()) {
            return Optional.empty();
        }
        VerifiedPackage pkg = new VerifiedPackage(platform.get(), previous, Path.of(state.rollbackInstaller),
                state.rollbackSha256, state.rollbackSize);
        try {
            UpdateCoordinator.reverify(pkg);
            return Optional.of(pkg);
        } catch (UpdateVerificationException damaged) {
            return Optional.empty();
        }
    }

    /** One-time message for the desktop (updated, rolled back, not installed). Cleared once read. */
    public Optional<String> takeNotice() {
        UpdateState state = store.load();
        if (state.notice == null || state.notice.isBlank()) {
            return Optional.empty();
        }
        String notice = state.notice;
        if (state.phase != UpdateState.Phase.ROLLBACK_NEEDS_HELP) {
            state.notice = null;
            store.save(state);
        }
        return Optional.of(notice);
    }

    /** Writes any pending Activity History line once the database is available. */
    public void flushActivity(UpdateActivityLog log) {
        UpdateState state = store.load();
        if (state.pendingActivity == null) {
            return;
        }
        log.record(state.pendingActivitySuccess ? "UPDATE_RESULT" : "UPDATE_PROBLEM", state.pendingActivitySuccess,
                state.pendingActivity);
        state.pendingActivity = null;
        store.save(state);
    }

    /** The current notice without clearing it (used when the app is blocked before Spring starts). */
    public Optional<String> peekNotice() {
        return Optional.ofNullable(store.load().notice);
    }

    private boolean handoffMayStillBeRunning(UpdateState state) {
        if (state.handedOffAt == null) {
            return false;
        }
        Instant handedOff;
        try {
            handedOff = Instant.parse(state.handedOffAt);
        } catch (DateTimeParseException invalid) {
            return false;
        }
        Path exitCode = store.directory().resolve("handoff")
                .resolve("msiexec-update-" + state.toVersion + ".exitcode");
        try {
            if (Files.isRegularFile(exitCode)
                    && !Files.getLastModifiedTime(exitCode).toInstant().isBefore(handedOff)) {
                return false; // The installer already finished without installing the new version.
            }
        } catch (IOException unreadable) {
            return false;
        }
        return handedOff.plus(HANDOFF_WINDOW).isAfter(Instant.now(clock));
    }

    private static boolean is(AppVersion current, String version) {
        return version != null && current.equals(version(version));
    }

    private static AppVersion version(String value) {
        try {
            return AppVersion.parse(value);
        } catch (IllegalArgumentException invalid) {
            return AppVersion.parse("0.0.0");
        }
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}

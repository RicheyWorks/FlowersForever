package com.flowerfarm.update;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Durable update progress, stored beside (not inside) the farm records so a
 * restore never overwrites it. All fields are plain strings so the file stays
 * readable by a person and by any version of the app. Fields are package-private
 * and are serialized directly by {@link UpdateStateStore}.
 */
public final class UpdateState {

    public enum Phase {
        /** Nothing in flight. */
        IDLE,
        /** The old version backed up the records and started the installer, then exited. */
        INSTALL_HANDED_OFF,
        /** The new version is on its first launch and has not yet passed its health check. */
        FIRST_LAUNCH_RUNNING,
        /** The new version restored the backup and started the previous installer. */
        ROLLED_BACK,
        /** Rollback could not finish by itself; a person must help. Nothing was deleted. */
        ROLLBACK_NEEDS_HELP
    }

    public enum Decision {
        /** Start normally. */
        NORMAL,
        /** Start, then run the database health check before any work. */
        VERIFY_FIRST_LAUNCH,
        /** The previous first launch never passed its health check: roll back without starting. */
        ROLLBACK_NOW,
        /** Do not open the records; show the help message and exit. */
        BLOCKED
    }

    Phase phase = Phase.IDLE;
    String fromVersion;
    String toVersion;
    String backupArchive;
    String newInstaller;
    String rollbackInstaller;
    String rollbackSha256;
    long rollbackSize;
    String handedOffAt;
    int launchAttempts;
    String keptAside;
    String failureReason;
    /** A message to show once on the next desktop start, then clear. */
    String notice;
    /** Activity-history line to record once Spring is up, then clear. */
    String pendingActivity;
    boolean pendingActivitySuccess = true;
    /** Versions that failed their health check here; never offered again automatically. */
    Set<String> failedVersions = new LinkedHashSet<>();

    /** Where an app update stands; {@link Phase#IDLE} when nothing is in flight. */
    public Phase phase() {
        return phase;
    }

    public UpdateState copy() {
        UpdateState copy = new UpdateState();
        copy.phase = phase;
        copy.fromVersion = fromVersion;
        copy.toVersion = toVersion;
        copy.backupArchive = backupArchive;
        copy.newInstaller = newInstaller;
        copy.rollbackInstaller = rollbackInstaller;
        copy.rollbackSha256 = rollbackSha256;
        copy.rollbackSize = rollbackSize;
        copy.handedOffAt = handedOffAt;
        copy.launchAttempts = launchAttempts;
        copy.keptAside = keptAside;
        copy.failureReason = failureReason;
        copy.notice = notice;
        copy.pendingActivity = pendingActivity;
        copy.pendingActivitySuccess = pendingActivitySuccess;
        copy.failedVersions = new LinkedHashSet<>(failedVersions == null ? Set.of() : failedVersions);
        return copy;
    }

    /** Ends an update attempt but keeps the failed-version list. */
    public void clearAttempt() {
        phase = Phase.IDLE;
        fromVersion = null;
        toVersion = null;
        newInstaller = null;
        rollbackInstaller = null;
        rollbackSha256 = null;
        rollbackSize = 0;
        handedOffAt = null;
        launchAttempts = 0;
        failureReason = null;
    }
}

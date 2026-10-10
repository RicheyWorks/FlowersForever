package com.flowerfarm.update;

import java.util.Optional;

/** Outcome of one update check, with a plain-language message for the farm. */
public record UpdateCheckResult(Status status, String message,
                                Optional<UpdateManifest> manifest,
                                Optional<UpdateManifest.Release> release,
                                Optional<UpdateManifest.PackageInfo> packageInfo) {

    public enum Status {
        /** Checks are switched off by the farm or by configuration. */
        DISABLED,
        /** No update source or publisher key is built into this copy. */
        NOT_CONFIGURED,
        /** The update server could not be reached. Never shown as an error. */
        OFFLINE,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        /** The publisher paused rollouts (kill switch). */
        PAUSED,
        /** The manifest failed signature or content checks and was ignored. */
        REJECTED,
        /** A newer version exists but must be installed with a fresh installer. */
        MANUAL_INSTALL_REQUIRED
    }

    static UpdateCheckResult simple(Status status, String message) {
        return new UpdateCheckResult(status, message, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean updateAvailable() {
        return status == Status.UPDATE_AVAILABLE;
    }
}

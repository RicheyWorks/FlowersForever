package com.flowerfarm.update;

import java.nio.file.Path;

/** Every update message a farm sees, kept in one place and in plain words. */
public final class UpdateMessages {

    public static final String DISABLED =
            "Automatic update checks are turned off. Choose Help > Check for updates any time.";
    public static final String NOT_CONFIGURED =
            "This copy of FlowersForever is not set up to receive updates yet. "
                    + "Ask RicheyWorks for the latest installer.";
    public static final String OFFLINE =
            "FlowersForever could not reach the update server, which is fine. "
                    + "You can keep working offline; it will check again next time.";
    public static final String PAUSED =
            "Updates are paused by RicheyWorks right now. Your current version keeps working.";
    public static final String CANNOT_INSTALL_HERE =
            "This copy of FlowersForever cannot install updates by itself "
                    + "(it was not installed with the FlowersForever installer). "
                    + "Ask RicheyWorks for the new installer.";

    private UpdateMessages() {
    }

    public static String upToDate(AppVersion current) {
        return "You have the latest version of FlowersForever (" + current + ").";
    }

    public static String available(AppVersion next, AppVersion current, String notes) {
        String whatsNew = notes == null || notes.isBlank() ? "" : "\n\nWhat's new:\n" + notes.trim();
        return "A new version of FlowersForever is ready: " + next + " (you have " + current + ")."
                + whatsNew
                + "\n\nBefore installing, FlowersForever saves a checked backup of your farm records. "
                + "If the new version does not start correctly, it goes back to " + current
                + " and puts your records back exactly as they were.";
    }

    public static String manualInstall(AppVersion next, AppVersion current) {
        return "FlowersForever " + next + " is available, but it cannot be installed automatically from "
                + current + ". Ask RicheyWorks for the new installer. Your records are not affected.";
    }

    public static String readyToInstall(AppVersion next) {
        return "FlowersForever " + next + " is downloaded and checked.\n\n"
                + "When you choose Install now, FlowersForever will close, back up your farm records, "
                + "install the update, and open again. Windows may ask you to allow the installer.";
    }

    public static String backupFailed(String reason) {
        return "The update was stopped before anything was installed because the backup of your "
                + "farm records could not be checked. Your records were not changed.\n\n" + reason;
    }

    public static String downloadRejected(String reason) {
        return "The downloaded update was rejected and deleted. Nothing was installed and your "
                + "records were not changed.\n\n" + reason;
    }

    public static String updated(AppVersion to, Path backup) {
        return "FlowersForever was updated to " + to + ". Your farm records were checked and are safe."
                + (backup == null ? "" : "\nA backup from before the update is kept at:\n" + backup);
    }

    public static String rolledBack(AppVersion failed, AppVersion restored, Path backup, Path keptAside) {
        return "The update to " + failed + " did not start correctly, so FlowersForever went back to "
                + restored + " and restored your farm records from the backup made just before the update."
                + (backup == null ? "" : "\nBackup used: " + backup)
                + (keptAside == null ? "" : "\nThe records from the failed start were also kept, at: " + keptAside)
                + "\nNothing was lost. FlowersForever will not offer " + failed + " again.";
    }

    public static String rollbackNeedsHelp(AppVersion failed, Path backup, String reason) {
        return "The update to " + failed + " did not start correctly and FlowersForever could not finish "
                + "going back by itself. Your records are safe in the backup made before the update"
                + (backup == null ? "." : ":\n" + backup)
                + "\nPlease contact RicheyWorks before entering new work. (" + reason + ")";
    }

    public static String installNotCompleted(AppVersion attempted, AppVersion current) {
        return "The update to " + attempted + " was not installed (it may have been cancelled). "
                + "You are still on " + current + " and your records were not changed.";
    }
}

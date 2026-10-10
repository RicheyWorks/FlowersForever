package com.flowerfarm.update;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Picks the hand-off for this operating system. */
public final class PlatformInstallers {

    private PlatformInstallers() {
    }

    public static PlatformInstaller forThisProcess(Optional<UpdatePlatform> platform, Optional<Path> launcher) {
        if (platform.isEmpty()) {
            return PlatformInstaller.unsupported();
        }
        return switch (platform.get()) {
            case WINDOWS_MSI -> WindowsMsiInstaller.forThisProcess(launcher);
            case MACOS_DMG -> new MacDmgInstaller(launcher, PlatformInstaller.ProcessStarter.system(),
                    ProcessHandle.current().pid());
        };
    }

    /**
     * The silent hand-off for the headless release check. Only Windows MSI installs
     * are supported; elsewhere the check reports that this copy cannot update itself.
     */
    public static PlatformInstaller silentForThisProcess(Optional<UpdatePlatform> platform, Optional<Path> launcher,
                                                         List<String> relaunchArguments) {
        if (platform.isEmpty() || platform.get() != UpdatePlatform.WINDOWS_MSI) {
            return PlatformInstaller.unsupported();
        }
        return WindowsMsiInstaller.silentForThisProcess(launcher, relaunchArguments);
    }
}

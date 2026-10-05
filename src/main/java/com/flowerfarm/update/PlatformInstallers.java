package com.flowerfarm.update;

import java.nio.file.Path;
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
}

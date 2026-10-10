package com.flowerfarm.update;

import java.util.Locale;
import java.util.Optional;

/** Installer kinds a manifest can carry. The key is the manifest's package name. */
public enum UpdatePlatform {
    WINDOWS_MSI("windows-msi", ".msi"),
    MACOS_DMG("macos-dmg", ".dmg");

    private final String key;
    private final String extension;

    UpdatePlatform(String key, String extension) {
        this.key = key;
        this.extension = extension;
    }

    public String key() {
        return key;
    }

    public String extension() {
        return extension;
    }

    public static Optional<UpdatePlatform> fromKey(String key) {
        for (UpdatePlatform platform : values()) {
            if (platform.key.equals(key)) {
                return Optional.of(platform);
            }
        }
        return Optional.empty();
    }

    /** The installer kind for this operating system, or empty when there is none. */
    public static Optional<UpdatePlatform> forOperatingSystem(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return Optional.of(MACOS_DMG);
        }
        if (os.contains("win")) {
            return Optional.of(WINDOWS_MSI);
        }
        return Optional.empty();
    }
}

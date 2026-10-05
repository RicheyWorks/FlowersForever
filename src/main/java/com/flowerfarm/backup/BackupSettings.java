package com.flowerfarm.backup;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where the farm records live, where backup bookkeeping is kept (beside the
 * records, never inside them, so a restore cannot overwrite it), and the folder
 * suggested for backups.
 */
public record BackupSettings(Path dataDirectory, Path stateDirectory, Path defaultFolder) {

    public static final String STATE_FOLDER = "backup-settings";

    public static BackupSettings from(Function<String, String> property) {
        Path data = Path.of(value(property, "flowerfarm.data-dir", "data")).toAbsolutePath().normalize();
        Path state = Optional.ofNullable(blankToNull(property.apply("flowerfarm.backups.state-directory")))
                .map(Path::of)
                .orElseGet(() -> {
                    Path parent = data.getParent();
                    return parent == null ? data.resolveSibling(STATE_FOLDER) : parent.resolve(STATE_FOLDER);
                })
                .toAbsolutePath().normalize();
        Path folder = Optional.ofNullable(blankToNull(property.apply("flowerfarm.backups.default-folder")))
                .or(() -> Optional.ofNullable(blankToNull(property.apply("flowerfarm.updates.backup-directory"))))
                .map(Path::of)
                .orElseGet(() -> Path.of(System.getProperty("user.home"), "Documents", "FlowersForever Backups"))
                .toAbsolutePath().normalize();
        return new BackupSettings(data, state, folder);
    }

    /** Settings read from JVM system properties only (used before Spring starts). */
    public static BackupSettings fromSystemProperties() {
        return from(System::getProperty);
    }

    /** True when {@code folder} is the records folder or inside it. */
    public boolean insideRecords(Path folder) {
        return folder.toAbsolutePath().normalize().startsWith(dataDirectory);
    }

    private static String value(Function<String, String> property, String key, String fallback) {
        String value = blankToNull(property.apply(key));
        return value == null ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.flowerfarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Update configuration. Built-in defaults come from Spring properties
 * ({@code flowerfarm.updates.*}); the farm's own on/off choice is kept in
 * {@code <updates>/update-preferences.properties} so it survives upgrades.
 *
 * @param manifestUrl where the signed manifest lives; empty disables all network use
 * @param dataDirectory the live farm-records folder
 * @param updatesDirectory downloads, cached installers, and update state (beside, not inside, the data)
 * @param backupDirectory second location for pre-update backups
 * @param launcherPath the packaged launcher (jpackage.app-path), empty for developer runs
 */
public record UpdateSettings(boolean enabledByConfiguration, boolean checkOnLaunch,
                             Optional<URI> manifestUrl, UpdateChannel channel,
                             AppVersion currentVersion, Path dataDirectory,
                             Path updatesDirectory, Path backupDirectory,
                             Optional<Path> launcherPath, boolean requireRollbackInstaller,
                             boolean allowLoopbackHttp, Duration connectTimeout,
                             Duration requestTimeout, String authenticodeSubject) {

    public static final String PREFERENCES_FILE = "update-preferences.properties";
    private static final String AUTOMATIC_CHECKS = "automaticChecks";

    /** Reads settings from a property lookup such as Spring's {@code Environment::getProperty}. */
    public static UpdateSettings from(Function<String, String> property) {
        Path dataDirectory = Path.of(value(property, "flowerfarm.data-dir", "data"))
                .toAbsolutePath().normalize();
        Path updates = Optional.ofNullable(blankToNull(property.apply("flowerfarm.updates.directory")))
                .map(Path::of)
                .orElseGet(() -> defaultUpdatesDirectory(dataDirectory))
                .toAbsolutePath().normalize();
        Path backups = Optional.ofNullable(blankToNull(property.apply("flowerfarm.updates.backup-directory")))
                .map(Path::of)
                .orElseGet(UpdateSettings::defaultBackupDirectory)
                .toAbsolutePath().normalize();
        Optional<URI> manifest = parseUri(property.apply("flowerfarm.updates.manifest-url"));
        UpdateChannel channel = UpdateChannel.fromName(
                value(property, "flowerfarm.updates.channel", "stable")).orElse(UpdateChannel.STABLE);
        Optional<Path> launcher = Optional.ofNullable(blankToNull(property.apply("jpackage.app-path")))
                .map(Path::of);
        return new UpdateSettings(
                Boolean.parseBoolean(value(property, "flowerfarm.updates.enabled", "true")),
                Boolean.parseBoolean(value(property, "flowerfarm.updates.check-on-launch", "true")),
                manifest, channel,
                AppVersion.current(property.apply("flowerfarm.updates.current-version")),
                dataDirectory, updates, backups, launcher,
                Boolean.parseBoolean(value(property, "flowerfarm.updates.require-rollback-installer", "true")),
                Boolean.parseBoolean(value(property, "flowerfarm.updates.allow-loopback-http", "false")),
                Duration.ofSeconds(Long.parseLong(value(property, "flowerfarm.updates.connect-timeout-seconds", "5"))),
                Duration.ofSeconds(Long.parseLong(value(property, "flowerfarm.updates.request-timeout-seconds", "20"))),
                value(property, "flowerfarm.updates.authenticode-subject", ""));
    }

    /** Settings read from JVM system properties only (used before Spring starts). */
    public static UpdateSettings fromSystemProperties() {
        return from(System::getProperty);
    }

    static Path defaultUpdatesDirectory(Path dataDirectory) {
        Path parent = dataDirectory.getParent();
        return parent == null ? dataDirectory.resolveSibling("FlowersForever-updates")
                : parent.resolve("updates");
    }

    static Path defaultBackupDirectory() {
        return Path.of(System.getProperty("user.home"), "Documents", "FlowersForever Backups");
    }

    /** True when the update source is set up: a manifest URL and at least one trusted key. */
    public boolean configured(TrustedKeys keys) {
        return manifestUrl.isPresent() && !keys.isEmpty();
    }

    /** The farm's own on/off choice; defaults to on. */
    public boolean automaticChecksEnabled() {
        Path file = updatesDirectory.resolve(PREFERENCES_FILE);
        if (!Files.isRegularFile(file)) {
            return true;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException unreadable) {
            return true;
        }
        return Boolean.parseBoolean(properties.getProperty(AUTOMATIC_CHECKS, "true"));
    }

    public void setAutomaticChecksEnabled(boolean enabled) throws IOException {
        Files.createDirectories(updatesDirectory);
        Properties properties = new Properties();
        properties.setProperty(AUTOMATIC_CHECKS, Boolean.toString(enabled));
        Path temporary = Files.createTempFile(updatesDirectory, ".preferences-", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "FlowersForever update preferences");
        }
        Files.move(temporary, updatesDirectory.resolve(PREFERENCES_FILE),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Optional<URI> parseUri(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(trimmed);
            return uri.getScheme() == null || uri.getHost() == null ? Optional.empty() : Optional.of(uri);
        } catch (URISyntaxException invalid) {
            return Optional.empty();
        }
    }

    private static String value(Function<String, String> property, String key, String fallback) {
        String value = blankToNull(property.apply(key));
        return value == null ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

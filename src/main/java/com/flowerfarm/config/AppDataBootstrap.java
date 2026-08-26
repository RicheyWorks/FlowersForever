package com.flowerfarm.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves durable, writable runtime storage before Spring creates the data source.
 *
 * <p>Developer-run JARs remain portable and use {@code ./data}. Native packages
 * set {@code flowerfarm.packaged=true} and use the current Windows user's local
 * application-data directory. {@code FLOWERFARM_DATA_DIR} or the matching JVM
 * property always wins, which keeps managed installations and recovery drills
 * deterministic.
 */
public final class AppDataBootstrap {

    static final String DATA_PROPERTY = "flowerfarm.data-dir";
    static final String PACKAGED_PROPERTY = "flowerfarm.packaged";
    private static final String MIGRATION_MARKER = ".legacy-data-imported";

    private AppDataBootstrap() {
    }

    /** Configure Spring properties and create the runtime directories. */
    public static Path configure() {
        boolean explicitDataDirectory = firstNonBlank(
                System.getProperty(DATA_PROPERTY), System.getenv("FLOWERFARM_DATA_DIR")) != null;
        Path dataDirectory = resolveDataDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(dataDirectory);
            Files.createDirectories(dataDirectory.resolve("exports"));
            Files.createDirectories(dataDirectory.resolve("backups"));
            Files.createDirectories(dataDirectory.resolve("logs"));
            verifyWritable(dataDirectory);
            if (!explicitDataDirectory) {
                migrateLegacyData(dataDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Flowers Forever cannot open its data folder. Check folder permissions or set "
                            + "FLOWERFARM_DATA_DIR to a writable location. Data folder: "
                            + dataDirectory, exception);
        }

        setDefault(DATA_PROPERTY, dataDirectory.toString());
        setDefault("flowerfarm.exports.directory", dataDirectory.resolve("exports").toString());
        setDefault("logging.file.name", dataDirectory.resolve("logs").resolve("flowersforever.log").toString());

        Map<String, String> localFiles = new LinkedHashMap<>();
        localFiles.put("connector.airtable.local-file", "airtable-mirror.json");
        localFiles.put("connector.webhook.local-file", "webhook-last-payload.json");
        localFiles.put("connector.shopify.local-file", "shopify-mirror.json");
        localFiles.put("connector.square.local-file", "square-mirror.json");
        localFiles.put("connector.google-sheets.local-file", "google-sheets-mirror.json");
        localFiles.put("connector.farmbrite.local-file", "farmbrite-mirror.json");
        localFiles.put("connector.floranext.local-file", "floranext-mirror.json");
        localFiles.forEach((property, file) -> setDefault(property, dataDirectory.resolve(file).toString()));
        return dataDirectory;
    }

    static Path resolveDataDirectory() {
        String explicit = firstNonBlank(
                System.getProperty(DATA_PROPERTY),
                System.getenv("FLOWERFARM_DATA_DIR"));
        if (explicit != null) {
            return Path.of(explicit);
        }
        if (!Boolean.parseBoolean(System.getProperty(PACKAGED_PROPERTY, "false"))) {
            return Path.of("data");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "FlowersForever", "data");
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Local",
                "FlowersForever", "data");
    }

    private static void verifyWritable(Path directory) throws IOException {
        Path probe = Files.createTempFile(directory, ".write-check-", ".tmp");
        Files.delete(probe);
    }

    /**
     * Copies data from the former {@code ./data} location on the first packaged
     * launch. The source is intentionally retained as a rollback copy.
     */
    private static void migrateLegacyData(Path target) throws IOException {
        if (!Boolean.parseBoolean(System.getProperty(PACKAGED_PROPERTY, "false"))) {
            return;
        }
        Path legacy = Path.of("data").toAbsolutePath().normalize();
        migrateLegacyData(target, legacy);
    }

    static void migrateLegacyData(Path target, Path legacy) throws IOException {
        Path marker = target.resolve(MIGRATION_MARKER);
        if (Files.exists(marker) || legacy.equals(target) || !Files.isDirectory(legacy)
                || !Files.isRegularFile(legacy.resolve("flowerfarm.mv.db"))
                || directoryHasUserData(target)) {
            return;
        }
        try (var paths = Files.walk(legacy)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                Path relative = legacy.relativize(source);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Legacy data contains an unsafe path: " + relative);
                }
                Path destinationParent = destination.getParent();
                if (destinationParent == null) {
                    throw new IOException("Legacy data has no destination directory: " + relative);
                }
                Files.createDirectories(destinationParent);
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        Files.writeString(marker,
                "Legacy data copied from " + legacy + ". Original retained for rollback.\n");
    }

    private static boolean directoryHasUserData(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.anyMatch(path -> !Files.isDirectory(path)
                    && !path.getFileName().toString().startsWith(".write-check-"));
        }
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

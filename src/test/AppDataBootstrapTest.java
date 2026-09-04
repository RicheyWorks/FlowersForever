package com.flowerfarm.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Packaged application data bootstrap")
class AppDataBootstrapTest {

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearProperties() {
        for (String property : new String[] {
                AppDataBootstrap.DATA_PROPERTY, AppDataBootstrap.PACKAGED_PROPERTY,
                "flowerfarm.exports.directory", "flowerfarm.attachments.directory",
                "logging.file.name",
                "connector.airtable.local-file", "connector.webhook.local-file",
                "connector.shopify.local-file", "connector.square.local-file",
                "connector.google-sheets.local-file", "connector.farmbrite.local-file",
                "connector.floranext.local-file"
        }) {
            System.clearProperty(property);
        }
    }

    @Test
    @DisplayName("explicit farm data directory receives durable runtime paths")
    void explicitDirectoryReceivesRuntimePaths() {
        Path data = temporaryDirectory.resolve("farm-data");
        System.setProperty(AppDataBootstrap.DATA_PROPERTY, data.toString());

        assertThat(AppDataBootstrap.configure()).isEqualTo(data.toAbsolutePath().normalize());
        assertThat(Files.isDirectory(data.resolve("exports"))).isTrue();
        assertThat(Files.isDirectory(data.resolve("backups"))).isTrue();
        assertThat(Files.isDirectory(data.resolve("logs"))).isTrue();
        assertThat(Files.isDirectory(data.resolve("attachments"))).isTrue();
        assertThat(System.getProperty(AppDataBootstrap.DATA_PROPERTY))
                .isEqualTo(data.toAbsolutePath().toString());
        assertThat(System.getProperty("logging.file.name"))
                .endsWith(Path.of("logs", "flowersforever.log").toString());
        assertThat(System.getProperty("flowerfarm.attachments.directory"))
                .isEqualTo(data.resolve("attachments").toAbsolutePath().toString());
        assertThat(System.getProperty("connector.shopify.local-file"))
                .isEqualTo(data.resolve("shopify-mirror.json").toAbsolutePath().toString());
    }

    @Test
    @DisplayName("legacy packaged database is copied without deleting the rollback source")
    void legacyDatabaseIsCopiedForward() throws Exception {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("packaged");
        Files.createDirectories(legacy.resolve("exports"));
        Files.createDirectories(target);
        Files.writeString(legacy.resolve("flowerfarm.mv.db"), "legacy database");
        Files.writeString(legacy.resolve("exports").resolve("inventory.csv"), "legacy export");

        AppDataBootstrap.migrateLegacyData(target, legacy);

        assertThat(target.resolve("flowerfarm.mv.db")).hasContent("legacy database");
        assertThat(target.resolve("exports").resolve("inventory.csv")).hasContent("legacy export");
        assertThat(target.resolve(".legacy-data-imported")).isRegularFile();
        assertThat(legacy.resolve("flowerfarm.mv.db")).hasContent("legacy database");
    }
}

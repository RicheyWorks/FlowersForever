package com.flowerfarm.update;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses a real H2 file database and the app's real Flyway migrations. The "new
 * version" adds a broken migration, exactly like a bad upgrade would.
 */
@DisplayName("Rollback with a real H2 database and Flyway migrations")
class UpdateDatabaseRollbackTest {

    @TempDir
    Path temp;

    private String url(Path data) {
        return "jdbc:h2:file:" + data.resolve("flowerfarm").toAbsolutePath() + ";DB_CLOSE_DELAY=-1";
    }

    private Flyway flyway(Path data, Path... extraLocations) {
        List<String> locations = new ArrayList<>(List.of("classpath:db/migration/h2"));
        for (Path extra : extraLocations) {
            locations.add("filesystem:" + extra.toAbsolutePath());
        }
        return Flyway.configure().dataSource(url(data), "sa", "")
                .locations(locations.toArray(String[]::new))
                .baselineOnMigrate(true).baselineVersion("1").load();
    }

    private void shutdown(Path data) {
        try (Connection connection = DriverManager.getConnection(url(data) + ";IFEXISTS=TRUE", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException alreadyClosed) {
            // closed
        }
    }

    private void execute(Path data, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(data), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String query(Path data, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(data), "sa", "");
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    @Test
    @DisplayName("health check passes on a fully migrated database and fails when upgrade steps did not run")
    void healthCheck() throws Exception {
        Path data = temp.resolve("FlowersForever/data");
        Files.createDirectories(data);
        flyway(data).migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url(data), "sa", "");

        new DatabaseHealthCheck(dataSource, Optional.of(flyway(data))).verify();

        Path pending = Files.createDirectories(temp.resolve("pending"));
        Files.writeString(pending.resolve("V998__future_step.sql"), "CREATE TABLE future_step(id INT);");
        assertThatThrownBy(() -> new DatabaseHealthCheck(dataSource, Optional.of(flyway(data, pending))).verify())
                .hasMessageContaining("did not run");
        shutdown(data);
    }

    @Test
    @DisplayName("a failed migration on first launch restores the exact pre-update records")
    void failedMigrationRollsBack() throws Exception {
        Path root = temp;
        UpdateSettings v120 = UpdateTestSupport.settings(root, "1.2.0", null);
        Path data = v120.dataDirectory();
        Files.createDirectories(data);
        flyway(data).migrate();
        execute(data, "CREATE TABLE update_probe(name VARCHAR(80))");
        execute(data, "INSERT INTO update_probe VALUES ('Cafe au Lait dahlias, bed 3')");
        String schemaBefore = query(data, "SELECT MAX(\"installed_rank\") FROM \"flyway_schema_history\"");
        shutdown(data);

        Clock clock = Clock.systemUTC();
        VerifiedBackup backup = new DataBackupService(clock).backup(data, v120.backupDirectory(), "before-1.2.1");
        Path previousMsi = UpdateTestSupport.fakeInstaller(root.resolve("cache"), "FlowersForever-1.2.0.msi", "msi 1.2.0");
        UpdateStateStore store = new UpdateStateStore(v120.updatesDirectory());
        UpdateState handedOff = new UpdateState();
        handedOff.phase = UpdateState.Phase.INSTALL_HANDED_OFF;
        handedOff.fromVersion = "1.2.0";
        handedOff.toVersion = "1.2.1";
        handedOff.backupArchive = backup.archive().toString();
        handedOff.rollbackInstaller = previousMsi.toString();
        handedOff.rollbackSha256 = UpdateSignatures.sha256Hex(previousMsi);
        handedOff.rollbackSize = Files.size(previousMsi);
        handedOff.handedOffAt = clock.instant().toString();
        store.save(handedOff);

        Path broken = Files.createDirectories(root.resolve("broken-1.2.1"));
        Files.writeString(broken.resolve("V999__bad_upgrade.sql"),
                "ALTER TABLE update_probe ADD COLUMN grade INT; THIS IS NOT SQL;");
        List<String> launched = new ArrayList<>();
        UpdateTestSupport.RecordingInstaller installer = new UpdateTestSupport.RecordingInstaller(launched);
        UpdateSettings v121 = UpdateTestSupport.settings(root, "1.2.1", null);
        UpdateLaunchGuard guard = new UpdateLaunchGuard(v121, store, new DataBackupService(clock), installer,
                Optional.of(UpdatePlatform.WINDOWS_MSI), clock);

        UpdateStartup.Result<Object> result = UpdateStartup.start(guard,
                () -> flyway(data, broken).migrate(), () -> shutdown(data), message -> { });

        assertThat(result.started()).isEmpty();
        assertThat(launched).containsExactly("ROLLBACK 1.2.0 FlowersForever-1.2.0.msi");
        assertThat(query(data, "SELECT name FROM update_probe")).isEqualTo("Cafe au Lait dahlias, bed 3");
        assertThat(query(data, "SELECT MAX(\"installed_rank\") FROM \"flyway_schema_history\"")).isEqualTo(schemaBefore);
        assertThat(query(data, "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '999'")).isEqualTo("0");
        assertThat(Path.of(store.load().keptAside).resolve(DataBackupService.DATABASE_FILE)).exists();
        assertThat(store.load().failureReason).contains("startup failed");
        shutdown(data);
    }
}

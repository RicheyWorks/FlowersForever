package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Shared helpers for the in-app backup tests. */
public final class BackupTestSupport {

    private BackupTestSupport() {
    }

    /** A clock the test moves by hand. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public static BackupSettings settings(Path root) {
        return new BackupSettings(root.resolve("FlowersForever/data").toAbsolutePath().normalize(),
                root.resolve("FlowersForever/backup-settings").toAbsolutePath().normalize(),
                root.resolve("Documents/FlowersForever Backups").toAbsolutePath().normalize());
    }

    /** Fake records: a stand-in database file plus one attachment. */
    public static void fakeRecords(Path data, String marker) throws IOException {
        Files.createDirectories(data.resolve("attachments"));
        Files.writeString(data.resolve(DataBackupService.DATABASE_FILE), "database " + marker);
        Files.writeString(data.resolve("attachments/photo.jpg"), "photo " + marker);
    }

    /** Copies the (closed) stand-in database file, the way H2's BACKUP TO would. */
    public static DatabaseSnapshot copyOf(Path data) {
        return zipFile -> {
            try (OutputStream output = Files.newOutputStream(zipFile);
                 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(DataBackupService.DATABASE_FILE));
                Files.copy(data.resolve(DataBackupService.DATABASE_FILE), zip);
                zip.closeEntry();
            } catch (IOException failed) {
                throw new SQLException(failed);
            }
        };
    }

    public static LiveBackupService service(BackupSettings settings, DatabaseSnapshot snapshot, Clock clock) {
        return new LiveBackupService(settings, snapshot, new DataBackupService(clock));
    }

    public static String url(Path data) {
        return "jdbc:h2:file:" + data.resolve("flowerfarm").toAbsolutePath() + ";DB_CLOSE_DELAY=-1";
    }

    /** A real database with all of the app's migrations applied, then closed. */
    public static void realRecords(Path data, String note) throws SQLException, IOException {
        Files.createDirectories(data.resolve("attachments"));
        Files.writeString(data.resolve("attachments/photo.jpg"), "photo " + note);
        Flyway.configure().dataSource(url(data), "sa", "").locations("classpath:db/migration/h2")
                .baselineOnMigrate(true).baselineVersion("1").load().migrate();
        execute(data, "CREATE TABLE IF NOT EXISTS backup_probe(note VARCHAR(100))");
        execute(data, "DELETE FROM backup_probe");
        execute(data, "INSERT INTO backup_probe VALUES ('" + note + "')");
        shutdown(data);
    }

    public static void execute(Path data, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(data), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public static String query(Path data, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(data), "sa", "");
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    public static void shutdown(Path data) {
        try (Connection connection = DriverManager.getConnection(url(data) + ";IFEXISTS=TRUE", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException alreadyClosed) {
            // closed
        }
    }
}

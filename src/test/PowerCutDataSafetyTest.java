package com.flowerfarm.safety;

import com.flowerfarm.backup.BackupSettings;
import com.flowerfarm.backup.DatabaseIntegrityGuard;
import com.flowerfarm.backup.RestoreRecords;
import com.flowerfarm.backup.RestoreStartup;
import com.flowerfarm.update.DataBackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Release check 3: no records lost on a power cut. A power cut can leave the last
 * write torn (cut short) or the file damaged; these tests damage the real records
 * file in those ways and reopen it the way FlowersForever does at start-up.
 */
@DisplayName("Data safety check 3: torn and damaged writes after a power cut")
class PowerCutDataSafetyTest {

    @TempDir
    Path temp;

    private Path data;
    private Path backups;
    private String url;
    private DataBackupService archives;
    private RestoreRecords records;
    private DatabaseIntegrityGuard guard;
    private BackupSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        data = Files.createDirectories(temp.resolve("data"));
        backups = Files.createDirectories(temp.resolve("FlowersForever Backups"));
        url = DataSafetyTestSupport.shippedUrl(data);
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneId.of("America/Los_Angeles"));
        archives = new DataBackupService(clock);
        settings = new BackupSettings(data, temp.resolve(BackupSettings.STATE_FOLDER), backups);
        records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        guard = new DatabaseIntegrityGuard(settings, records, archives, Clock.systemDefaultZone());
        DataSafetyTestSupport.saveRecords(url, 1, 50);
        shutdown();
    }

    @Test
    @DisplayName("A save cut short at the end of the file opens with every earlier record")
    void tornLastWriteKeepsEarlierRecords() throws Exception {
        DataSafetyTestSupport.saveRecords(url, 51, 60);
        shutdown();
        Path file = data.resolve(DataBackupService.DATABASE_FILE);
        archives.backup(data, backups, "manual");
        long size = Files.size(file);
        try (RandomAccessFile raw = new RandomAccessFile(file.toFile(), "rw")) {
            raw.setLength(size - 2048); // the last block never reached the disk
        }

        DatabaseIntegrityGuard.Outcome outcome = guard.ensureHealthy();

        assertThat(DatabaseIntegrityGuard.check(data)).as("after %s", outcome)
                .isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
        assertThat(DataSafetyTestSupport.savedIds(url)).containsAll(range(1, 50));
        assertThat(outcome.health()).isIn(DatabaseIntegrityGuard.Health.HEALTHY,
                DatabaseIntegrityGuard.Health.DAMAGED);
    }

    @Test
    @DisplayName("A garbled records file is detected and the latest verified backup is put back")
    void damagedFileIsReplacedByLatestVerifiedBackup() throws Exception {
        archives.backup(data, backups, "manual");
        garble(data.resolve(DataBackupService.DATABASE_FILE));
        assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.DAMAGED);

        DatabaseIntegrityGuard.Outcome outcome = guard.ensureHealthy();

        assertThat(outcome.restored()).isPresent();
        assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
        assertThat(DataSafetyTestSupport.savedIds(url)).containsExactlyElementsOf(range(1, 50));
        assertThat(outcome.keptAside()).hasValueSatisfying(kept -> {
            assertThat(kept.getFileName().toString()).startsWith("data.before-restore-");
            assertThat(kept.resolve(DataBackupService.DATABASE_FILE)).exists();
        });
        assertThat(records.takeNotice()).hasValueSatisfying(notice -> {
            assertThat(notice.message()).contains("damaged").contains("latest verified backup")
                    .contains("kept (not deleted)");
        });
    }

    @Test
    @DisplayName("A backup that fails its checksum is skipped for an older verified one")
    void damagedNewestBackupFallsBackToOlderVerifiedBackup() throws Exception {
        archives.backup(data, backups, "manual");
        DataSafetyTestSupport.saveRecords(url, 51, 55);
        shutdown();
        Path newest = archives.backup(data, backups, "manual").archive();
        try (RandomAccessFile raw = new RandomAccessFile(newest.toFile(), "rw")) {
            raw.seek(raw.length() / 2);
            raw.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        }
        garble(data.resolve(DataBackupService.DATABASE_FILE));

        DatabaseIntegrityGuard.Outcome outcome = guard.ensureHealthy();

        assertThat(outcome.restored()).hasValueSatisfying(
                backup -> assertThat(backup.archive()).isNotEqualTo(newest));
        assertThat(DataSafetyTestSupport.savedIds(url)).containsExactlyElementsOf(range(1, 50));
    }

    @Test
    @DisplayName("With no backup, nothing is deleted and the grower is told plainly")
    void damagedWithoutBackupDeletesNothing() throws Exception {
        Path file = data.resolve(DataBackupService.DATABASE_FILE);
        garble(file);
        byte[] damaged = Files.readAllBytes(file);

        DatabaseIntegrityGuard.Outcome outcome = guard.ensureHealthy();

        assertThat(outcome.restored()).isEmpty();
        assertThat(Files.readAllBytes(file)).isEqualTo(damaged);
        assertThat(outcome.message()).contains("no verified backup").contains("Nothing has been deleted");
    }

    @Test
    @DisplayName("Healthy records are never touched")
    void healthyRecordsAreLeftAlone() throws Exception {
        archives.backup(data, backups, "manual");
        DatabaseIntegrityGuard.Outcome outcome = guard.ensureHealthy();
        assertThat(outcome.health()).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
        assertThat(outcome.restored()).isEmpty();
        try (Stream<Path> siblings = Files.list(temp)) {
            assertThat(siblings.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith("data."));
        }
    }

    @Test
    @DisplayName("At start-up the damaged records are swapped for the backup before the app opens them")
    void startupRecoversBeforeOpeningRecords() throws Exception {
        archives.backup(data, backups, "manual");
        garble(data.resolve(DataBackupService.DATABASE_FILE));
        RestoreStartup startup = new RestoreStartup(settings, records, archives, Clock.systemDefaultZone(),
                Duration.ofSeconds(1), guard);

        String started = startup.start(() -> {
            assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
            return "opened";
        }, () -> { }, true);

        assertThat(started).isEqualTo("opened");
        assertThat(DataSafetyTestSupport.savedIds(url)).containsExactlyElementsOf(range(1, 50));
    }

    @Test
    @DisplayName("At start-up, damaged records with no backup stop the app with a plain message")
    void startupStopsPlainlyWithoutBackup() throws Exception {
        garble(data.resolve(DataBackupService.DATABASE_FILE));
        RestoreStartup startup = new RestoreStartup(settings, records, archives, Clock.systemDefaultZone(),
                Duration.ofSeconds(1), guard);

        assertThatThrownBy(() -> startup.start(() -> {
            throw new AssertionError("must not open damaged records");
        }, () -> { }, true)).hasMessageContaining("no verified backup").hasMessageContaining("Nothing has been deleted");
    }

    private void shutdown() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    /** Scrambles the file the way a cut-off write of random sectors would. */
    private static void garble(Path file) throws Exception {
        Random random = new Random(42);
        try (RandomAccessFile raw = new RandomAccessFile(file.toFile(), "rw")) {
            byte[] noise = new byte[(int) raw.length()];
            random.nextBytes(noise);
            raw.seek(0);
            raw.write(noise);
        }
    }

    private static java.util.List<Integer> range(int from, int to) {
        return java.util.stream.IntStream.rangeClosed(from, to).boxed().toList();
    }
}

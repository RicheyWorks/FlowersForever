package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/** Recovery must survive a second interruption and use records this app can open. */
class IntegrityRestoreSafetyTest {
    @TempDir Path temp;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneOffset.UTC);

    @Test
    void repeatedRestoresReserveDifferentDestinationsBeforeMovingRecords() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.fakeRecords(data, "backup");
        DataBackupService archives = new DataBackupService(clock);
        Path archive = archives.backup(data, settings.defaultFolder(), "backup").archive();
        BackupTestSupport.fakeRecords(data, "first original");
        java.util.List<Path> planned = new java.util.ArrayList<>();
        DataBackupService.BeforeRestoreSwap beforeMove = previous -> {
            assertThat(previous).doesNotExist();
            assertThat(data.resolve(DataBackupService.DATABASE_FILE)).exists();
            planned.add(previous);
        };

        Path first = archives.restore(archive, data, beforeMove);
        BackupTestSupport.fakeRecords(data, "second original");
        Path second = archives.restore(archive, data, beforeMove);

        assertThat(planned).containsExactly(first, second).doesNotHaveDuplicates();
        assertThat(Files.readString(first.resolve(DataBackupService.DATABASE_FILE)))
                .isEqualTo("database first original");
        assertThat(Files.readString(second.resolve(DataBackupService.DATABASE_FILE)))
                .isEqualTo("database second original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "between", "after"})
    void interruptedAutomaticSwapKeepsItsExactRecoveryPath(String boundary) throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.realRecords(data, "my verified farm");
        DataBackupService archives = new DataBackupService(clock);
        Path archive = archives.backup(data, settings.defaultFolder(), "backup").archive();
        byte[] damage = damage(data);
        RestoreRecords records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        Path previous = data.resolveSibling("data.before-restore-20261007-170000");
        DataBackupService interruptedArchives = spy(archives);
        doAnswer(invocation -> {
            DataBackupService.BeforeRestoreSwap journal = invocation.getArgument(2);
            journal.record(previous);
            assertThat(records.inProgress().orElseThrow().keptAside()).contains(previous);
            assertThat(Files.readAllBytes(data.resolve(DataBackupService.DATABASE_FILE))).isEqualTo(damage);
            if (boundary.equals("before")) throw new InterruptedSwap();
            Files.move(data, previous);
            if (boundary.equals("between")) throw new InterruptedSwap();
            Path unpacked = archives.unpack(archive, temp.resolve("interrupted-staging"));
            Files.move(unpacked, data);
            throw new InterruptedSwap();
        }).when(interruptedArchives).restore(eq(archive), eq(data), any(DataBackupService.BeforeRestoreSwap.class));
        DatabaseIntegrityGuard interruptedGuard = new DatabaseIntegrityGuard(settings, records,
                interruptedArchives, clock);
        RestoreStartup interrupted = new RestoreStartup(settings, records, interruptedArchives, clock,
                Duration.ZERO, interruptedGuard);

        assertThatThrownBy(() -> interrupted.start(() -> "must not start", () -> { }, true))
                .isInstanceOf(InterruptedSwap.class);
        // Mirror AppDataBootstrap after process death, and put an unrelated newer
        // folder nearby so recovery cannot succeed by choosing an arbitrary sibling.
        for (String directory : new String[]{"exports", "backups", "logs", "attachments"}) {
            Files.createDirectories(data.resolve(directory));
        }
        BackupTestSupport.fakeRecords(data.resolveSibling("data.before-restore-99991231-235959"), "other farm");
        DatabaseIntegrityGuard guard = new DatabaseIntegrityGuard(settings, records, archives, clock);
        RestoreStartup restarted = new RestoreStartup(settings, records, archives, clock, Duration.ZERO, guard);
        try {
            String opened = restarted.start(() -> BackupTestSupport.query(data, "SELECT note FROM backup_probe"),
                    () -> BackupTestSupport.shutdown(data), true);
            assertThat(opened).isEqualTo("my verified farm");
            assertThat(records.inProgress()).isEmpty();
            assertThat(Files.readAllBytes(previous.resolve(DataBackupService.DATABASE_FILE))).isEqualTo(damage);
        } finally {
            BackupTestSupport.shutdown(data);
        }
    }

    @Test
    void failedFirstSpringStartRestoresTheDamagedCopyAndKeepsTheVerifiedBackup() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.realRecords(data, "verified farm");
        DataBackupService archives = new DataBackupService(clock);
        Path archive = archives.backup(data, settings.defaultFolder(), "backup").archive();
        byte[] damage = damage(data);
        RestoreRecords records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        DatabaseIntegrityGuard guard = new DatabaseIntegrityGuard(settings, records, archives, clock);
        RestoreStartup startup = new RestoreStartup(settings, records, archives, clock, Duration.ZERO, guard);

        assertThatThrownBy(() -> startup.start(() -> {
            assertThat(BackupTestSupport.query(data, "SELECT note FROM backup_probe")).isEqualTo("verified farm");
            throw new IllegalStateException("First startup failed");
        }, () -> BackupTestSupport.shutdown(data), true)).hasMessage("First startup failed");

        assertThat(Files.readAllBytes(data.resolve(DataBackupService.DATABASE_FILE))).isEqualTo(damage);
        assertThat(archive).exists();
        try (var siblings = Files.list(data.getParent())) {
            assertThat(siblings.filter(path -> path.getFileName().toString()
                    .startsWith("data.failed-restore-20261007-170000-")).toList()).singleElement()
                    .satisfies(path -> assertThat(path.resolve(DataBackupService.DATABASE_FILE)).exists());
        }
        assertThat(records.inProgress()).isEmpty();
    }

    @Test
    void interruptedSwapFindsRecordsAfterBootstrapRecreatesTheDataDirectory() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.fakeRecords(data, "original farm");
        DataBackupService archives = new DataBackupService(clock);
        Path archive = archives.backup(data, settings.defaultFolder(), "backup").archive();
        Path previous = data.resolveSibling("data.before-restore-20261007-170000");
        RestoreRecords records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        records.saveInProgress(new RestoreRecords.InProgress(archive, Optional.of(previous), Optional.empty(), ""));
        Files.move(data, previous);
        // AppDataBootstrap runs before RestoreStartup and recreates these empty folders.
        for (String directory : new String[]{"exports", "backups", "logs", "attachments"}) {
            Files.createDirectories(data.resolve(directory));
        }
        RestoreStartup startup = new RestoreStartup(settings, records, archives, clock, Duration.ZERO);

        String opened = startup.start(() -> Files.readString(data.resolve(DataBackupService.DATABASE_FILE)),
                () -> { }, true);

        assertThat(opened).isEqualTo("database original farm");
        assertThat(records.inProgress()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "failed", "checksum"})
    void incompatibleNewestBackupIsSkippedForAnOlderCompatibleBackup(String incompatibility) throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.realRecords(data, "older compatible farm");
        DataBackupService archives = new DataBackupService(clock);
        Path older = archives.backup(data, settings.defaultFolder(), "backup").archive();
        BackupTestSupport.execute(data, "UPDATE backup_probe SET note = 'incompatible newer farm'");
        switch (incompatibility) {
            case "future" -> BackupTestSupport.execute(data, "INSERT INTO \"flyway_schema_history\" "
                    + "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", \"checksum\", "
                    + "\"installed_by\", \"execution_time\", \"success\") VALUES "
                    + "(9999, '9999', 'from the future', 'SQL', 'V9999__future.sql', 0, 'sa', 1, TRUE)");
            case "failed" -> BackupTestSupport.execute(data,
                    "UPDATE \"flyway_schema_history\" SET \"success\" = FALSE "
                            + "WHERE \"installed_rank\" = (SELECT MAX(\"installed_rank\") FROM \"flyway_schema_history\")");
            case "checksum" -> BackupTestSupport.execute(data,
                    "UPDATE \"flyway_schema_history\" SET \"checksum\" = 0 WHERE \"version\" = '2'");
            default -> throw new AssertionError(incompatibility);
        }
        BackupTestSupport.shutdown(data);
        Path newer = archives.backup(data, settings.defaultFolder(), "backup").archive();
        assertThat(newer).isNotEqualTo(older);
        damage(data);
        RestoreRecords records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        DatabaseIntegrityGuard guard = new DatabaseIntegrityGuard(settings, records, archives, clock);

        DatabaseIntegrityGuard.Outcome recovered = guard.ensureHealthy();

        assertThat(recovered.restored()).hasValueSatisfying(backup -> assertThat(backup.archive()).isEqualTo(older));
        try {
            assertThat(BackupTestSupport.query(data, "SELECT note FROM backup_probe"))
                    .isEqualTo("older compatible farm");
        } finally {
            BackupTestSupport.shutdown(data);
        }
    }

    private static byte[] damage(Path data) throws Exception {
        byte[] bytes = new byte[(int) Files.size(data.resolve(DataBackupService.DATABASE_FILE))];
        new Random(42).nextBytes(bytes);
        Files.write(data.resolve(DataBackupService.DATABASE_FILE), bytes);
        return bytes;
    }

    private static final class InterruptedSwap extends Error {
        private static final long serialVersionUID = 1L;
    }
}

package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateSignatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Automatic recovery must never substitute another farm from a shared backup folder. */
class AutomaticBackupProvenanceTest {
    @TempDir Path temp;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void sharedBackupFolderNeverSelectsTheOtherStoresNewerArchive() throws Exception {
        BackupSettings first = settings("first");
        BackupSettings other = settings("other");
        DataBackupService archives = new DataBackupService(clock);
        BackupTestSupport.realRecords(first.dataDirectory(), "my farm");
        Path mine = archives.backup(first.dataDirectory(), first.defaultFolder(), "backup").archive();
        BackupTestSupport.realRecords(other.dataDirectory(), "another farm");
        Path foreign = archives.backup(other.dataDirectory(), other.defaultFolder(), "backup").archive();
        assertThat(BackupReceipts.recognizes(first, mine)).isTrue();
        assertThat(BackupReceipts.recognizes(first, foreign)).isFalse();
        damage(first.dataDirectory());

        DatabaseIntegrityGuard.Outcome result = guard(first, archives).ensureHealthy();

        assertThat(result.restored()).hasValueSatisfying(backup -> assertThat(backup.archive()).isEqualTo(mine));
        assertNote(first.dataDirectory(), "my farm");
        assertNote(other.dataDirectory(), "another farm");
    }

    @Test
    void legacyBackupWithoutLocalProofStaysAvailableForManualRestoreOnly() throws Exception {
        BackupSettings settings = settings("legacy");
        Path data = settings.dataDirectory();
        BackupTestSupport.realRecords(data, "legacy farm");
        Files.createDirectories(settings.defaultFolder());
        Path archive = settings.defaultFolder().resolve("FlowersForever-backup-20261007-180000.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive));
             var files = Files.walk(data)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry("data/" + data.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        Files.writeString(BackupFile.checksumFor(archive), UpdateSignatures.sha256Hex(archive));
        byte[] damaged = damage(data);
        DataBackupService archives = new DataBackupService(clock);

        DatabaseIntegrityGuard.Outcome result = guard(settings, archives).ensureHealthy();

        assertThat(result.restored()).isEmpty();
        assertThat(result.message()).contains("manual restore", "Nothing has been deleted");
        assertThat(Files.readAllBytes(data.resolve(DataBackupService.DATABASE_FILE))).isEqualTo(damaged);
        assertThat(new RestoreService(settings, archives, "sa", "").inspect(archive).archive()).isEqualTo(archive);
    }

    @Test
    void liveSnapshotUsesOriginalStoreAndCustomSettingsForItsReceipt() throws Exception {
        BackupSettings basic = settings("live");
        BackupSettings settings = new BackupSettings(basic.dataDirectory(), temp.resolve("custom-settings"),
                basic.defaultFolder());
        BackupTestSupport.realRecords(settings.dataDirectory(), "live farm");
        DataBackupService archives = new DataBackupService(clock);
        LiveBackupService live = new LiveBackupService(settings, BackupTestSupport.copyOf(settings.dataDirectory()),
                archives);
        Path archive = live.backUp(settings.defaultFolder(), "auto").archive();
        assertThat(BackupReceipts.recognizes(settings, archive)).isTrue();
        assertThat(settings.stateDirectory().resolve("backup-receipts")).isDirectory();
        damage(settings.dataDirectory());

        DatabaseIntegrityGuard.Outcome result = guard(settings, archives).ensureHealthy();

        assertThat(result.restored()).isPresent();
        assertNote(settings.dataDirectory(), "live farm");
    }

    @Test
    void replacingArchiveAndChecksumCannotReuseAnOlderStoresReceipt() throws Exception {
        BackupSettings mine = settings("mine");
        BackupSettings foreign = settings("foreign");
        BackupTestSupport.realRecords(mine.dataDirectory(), "my farm");
        DataBackupService archives = new DataBackupService(clock);
        Path original = archives.backup(mine.dataDirectory(), mine.defaultFolder(), "backup").archive();
        BackupTestSupport.realRecords(foreign.dataDirectory(), "foreign farm");
        Path replacement = archives.backup(foreign.dataDirectory(), temp.resolve("separate-backups"), "backup")
                .archive();
        Files.copy(replacement, original, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(BackupFile.checksumFor(replacement), BackupFile.checksumFor(original),
                StandardCopyOption.REPLACE_EXISTING);
        byte[] damaged = damage(mine.dataDirectory());

        DatabaseIntegrityGuard.Outcome result = guard(mine, archives).ensureHealthy();

        assertThat(result.restored()).isEmpty();
        assertThat(Files.readAllBytes(mine.dataDirectory().resolve(DataBackupService.DATABASE_FILE)))
                .isEqualTo(damaged);
    }

    private BackupSettings settings(String name) {
        // Same parent and same settings root deliberately exercise per-store isolation.
        return new BackupSettings(temp.resolve("farms/" + name), temp.resolve("farms/backup-settings"),
                temp.resolve("shared-backups"));
    }

    private DatabaseIntegrityGuard guard(BackupSettings settings, DataBackupService archives) {
        return new DatabaseIntegrityGuard(settings, new RestoreRecords(settings.stateDirectory(), settings.dataDirectory()), archives, clock);
    }

    private static byte[] damage(Path data) throws Exception {
        byte[] bytes = new byte[(int) Files.size(data.resolve(DataBackupService.DATABASE_FILE))];
        new Random(42).nextBytes(bytes);
        Files.write(data.resolve(DataBackupService.DATABASE_FILE), bytes);
        return bytes;
    }

    private static void assertNote(Path data, String expected) throws Exception {
        try {
            assertThat(BackupTestSupport.query(data, "SELECT note FROM backup_probe")).isEqualTo(expected);
        } finally {
            BackupTestSupport.shutdown(data);
        }
    }
}

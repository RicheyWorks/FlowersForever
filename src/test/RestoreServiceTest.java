package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.VerifiedBackup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Checking a backup before restoring it")
class RestoreServiceTest {

    @TempDir
    Path temp;

    private BackupSettings settings;
    private DataBackupService archives;
    private RestoreService restores;

    @BeforeEach
    void setUp() {
        settings = BackupTestSupport.settings(temp);
        archives = new DataBackupService(Clock.systemDefaultZone());
        restores = new RestoreService(settings, archives, "sa", "");
    }

    private Path backupOfRealRecords(String note) throws Exception {
        BackupTestSupport.realRecords(settings.dataDirectory(), note);
        return archives.backup(settings.dataDirectory(), settings.defaultFolder(), "backup").archive();
    }

    @Test
    @DisplayName("a good backup is described in plain terms and the live records are untouched")
    void goodBackup() throws Exception {
        Path archive = backupOfRealRecords("zinnias");
        String liveBefore = BackupTestSupport.query(settings.dataDirectory(), "SELECT note FROM backup_probe");
        BackupTestSupport.shutdown(settings.dataDirectory());

        BackupDetails details = restores.inspect(archive);

        assertThat(details.archive()).isEqualTo(archive.toAbsolutePath().normalize());
        assertThat(details.madeAt()).isPresent();
        assertThat(details.files()).isGreaterThanOrEqualTo(2);
        assertThat(details.upgradesOnOpen()).isFalse();
        assertThat(details.sha256()).hasSize(64);
        assertThat(BackupMessages.confirmRestore(details, settings.defaultFolder()))
                .contains("Restore your farm records from the backup made on", "nothing is lost");
        assertThat(BackupTestSupport.query(settings.dataDirectory(), "SELECT note FROM backup_probe"))
                .isEqualTo(liveBefore);
        BackupTestSupport.shutdown(settings.dataDirectory());
    }

    @Test
    @DisplayName("a damaged backup (checksum mismatch) is refused")
    void badChecksum() throws Exception {
        Path archive = backupOfRealRecords("zinnias");
        Files.write(archive, new byte[] {1, 2, 3}, StandardOpenOption.APPEND);

        assertThatThrownBy(() -> restores.inspect(archive)).isInstanceOf(BackupException.class)
                .hasMessageContaining("cannot be restored").hasMessageContaining("Nothing was changed");
    }

    @Test
    @DisplayName("a backup without its check file is refused")
    void missingChecksum() throws Exception {
        Path archive = backupOfRealRecords("zinnias");
        Files.delete(BackupFile.checksumFor(archive));

        assertThatThrownBy(() -> restores.inspect(archive)).isInstanceOf(BackupException.class)
                .hasMessageContaining(".sha256) is missing");
    }

    @Test
    @DisplayName("a ZIP that holds no farm records is refused")
    void notFarmRecords() throws Exception {
        Path other = temp.resolve("Other/data");
        Files.createDirectories(other);
        Files.writeString(other.resolve("readme.txt"), "hello");
        VerifiedBackup backup = archives.backup(other, settings.defaultFolder(), "backup");

        assertThatThrownBy(() -> restores.inspect(backup.archive())).isInstanceOf(BackupException.class)
                .hasMessageContaining("cannot be restored").hasMessageContaining("Nothing was changed");
    }

    @Test
    @DisplayName("a backup made by a newer FlowersForever is refused with what to do")
    void newerVersion() throws Exception {
        BackupTestSupport.realRecords(settings.dataDirectory(), "zinnias");
        BackupTestSupport.execute(settings.dataDirectory(), "INSERT INTO \"flyway_schema_history\" "
                + "(\"installed_rank\", \"version\", \"description\", \"type\", \"script\", \"checksum\", "
                + "\"installed_by\", \"execution_time\", \"success\") VALUES "
                + "(9999, '9999', 'from the future', 'SQL', 'V9999__future.sql', 0, 'sa', 1, TRUE)");
        BackupTestSupport.shutdown(settings.dataDirectory());
        Path archive = archives.backup(settings.dataDirectory(), settings.defaultFolder(), "backup").archive();

        assertThatThrownBy(() -> restores.inspect(archive)).isInstanceOf(BackupException.class)
                .hasMessage(BackupMessages.NEWER_VERSION);
    }

    @Test
    @DisplayName("a backup whose last upgrade step had failed is refused")
    void failedMigration() throws Exception {
        BackupTestSupport.realRecords(settings.dataDirectory(), "zinnias");
        BackupTestSupport.execute(settings.dataDirectory(), "UPDATE \"flyway_schema_history\" SET \"success\" = FALSE "
                + "WHERE \"installed_rank\" = (SELECT MAX(\"installed_rank\") FROM \"flyway_schema_history\")");
        BackupTestSupport.shutdown(settings.dataDirectory());
        Path archive = archives.backup(settings.dataDirectory(), settings.defaultFolder(), "backup").archive();

        assertThatThrownBy(() -> restores.inspect(archive)).isInstanceOf(BackupException.class)
                .hasMessage(BackupMessages.DAMAGED_RECORDS);
    }
}

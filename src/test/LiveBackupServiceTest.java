package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateVerificationException;
import com.flowerfarm.update.VerifiedBackup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Back up now while FlowersForever keeps running")
class LiveBackupServiceTest {

    @TempDir
    Path temp;

    private final List<Path> opened = new ArrayList<>();

    @AfterEach
    void closeDatabases() {
        opened.forEach(BackupTestSupport::shutdown);
    }

    @Test
    @DisplayName("backs up real, open records without closing them, and the checked ZIP restores the same records")
    void liveBackupOfOpenDatabase() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        Path data = settings.dataDirectory();
        BackupTestSupport.realRecords(data, "dahlias planted");
        opened.add(data);
        Path archive;
        try (Connection stillOpen = DriverManager.getConnection(BackupTestSupport.url(data), "sa", "")) {
            assertThat(stillOpen.isValid(1)).isTrue();
            // The update-time backup needs the records closed; the live backup does not.
            assertThatThrownBy(() -> DataBackupService.requireDatabaseClosed(data))
                    .isInstanceOf(UpdateVerificationException.class);
            LiveBackupService service = BackupTestSupport.service(settings,
                    DatabaseSnapshot.h2(new DriverManagerDataSource(BackupTestSupport.url(data), "sa", "")),
                    Clock.systemDefaultZone());

            VerifiedBackup backup = service.backUp(settings.defaultFolder(), "backup");
            archive = backup.archive();

            assertThat(backup.archive().getFileName().toString()).matches("FlowersForever-backup-\\d{8}-\\d{6}\\.zip");
            assertThat(backup.checksumFile()).isRegularFile();
            new DataBackupService(Clock.systemDefaultZone()).verifyChecksum(backup.archive());
            try (ZipFile zip = new ZipFile(backup.archive().toFile())) {
                List<String> names = Collections.list(zip.entries()).stream().map(entry -> entry.getName()).toList();
                assertThat(names).contains("data/flowerfarm.mv.db", "data/attachments/photo.jpg");
                assertThat(names).noneMatch(name -> name.endsWith(".lock.db") || name.endsWith(".trace.db"));
            }
            BackupTestSupport.execute(data, "UPDATE backup_probe SET note = 'entered after the backup'");
        }

        Path elsewhere = temp.resolve("restored/data");
        Files.createDirectories(elsewhere.getParent());
        new DataBackupService(Clock.systemDefaultZone()).restore(archive, elsewhere);
        opened.add(elsewhere);
        assertThat(BackupTestSupport.query(elsewhere, "SELECT note FROM backup_probe")).isEqualTo("dahlias planted");
        assertThat(Files.readString(elsewhere.resolve("attachments/photo.jpg"))).isEqualTo("photo dahlias planted");
        assertThat(settings.stateDirectory()).satisfies(state -> {
            try (Stream<Path> leftovers = Files.exists(state) ? Files.list(state) : Stream.empty()) {
                assertThat(leftovers.filter(path -> path.getFileName().toString().startsWith("work-"))).isEmpty();
            }
        });
    }

    @Test
    @DisplayName("refuses to save a backup inside the records folder")
    void refusesFolderInsideRecords() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        BackupTestSupport.fakeRecords(settings.dataDirectory(), "v1");
        LiveBackupService service = BackupTestSupport.service(settings,
                BackupTestSupport.copyOf(settings.dataDirectory()), Clock.systemDefaultZone());

        assertThatThrownBy(() -> service.backUp(settings.dataDirectory().resolve("backups"), "backup"))
                .isInstanceOf(BackupException.class)
                .hasMessage(BackupMessages.FOLDER_INSIDE_RECORDS);
    }

    @Test
    @DisplayName("a database copy that fails leaves no half-written backup and explains it plainly")
    void failedSnapshotLeavesNothing() throws Exception {
        BackupSettings settings = BackupTestSupport.settings(temp);
        BackupTestSupport.fakeRecords(settings.dataDirectory(), "v1");
        LiveBackupService service = BackupTestSupport.service(settings, zipFile -> {
            throw new SQLException("database is busy");
        }, Clock.systemDefaultZone());

        assertThatThrownBy(() -> service.backUp(settings.defaultFolder(), "backup"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("could not be copied right now")
                .hasMessageContaining("try again");
        try (Stream<Path> files = Files.list(settings.defaultFolder())) {
            assertThat(files).isEmpty();
        }
        try (Stream<Path> files = Files.list(settings.stateDirectory())) {
            assertThat(files).isEmpty();
        }
    }
}

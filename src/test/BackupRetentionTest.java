package com.flowerfarm.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Keeping the newest automatic backups")
class BackupRetentionTest {

    @TempDir
    Path folder;

    private Path backup(String name) throws IOException {
        Path archive = folder.resolve(name);
        Files.writeString(archive, name);
        Files.writeString(BackupFile.checksumFor(archive), "0  " + name + "\n");
        return archive;
    }

    @Test
    @DisplayName("removes only the oldest automatic backups beyond the limit, never manual or pre-update ones")
    void prunesOnlyOldAutomaticBackups() throws IOException {
        for (int day = 1; day <= 13; day++) {
            backup("FlowersForever-auto-202609%02d-020000.zip".formatted(day));
        }
        Path manual = backup("FlowersForever-backup-20260801-120000.zip");
        Path beforeUpdate = backup("FlowersForever-before-1.2.1-20260701-120000.zip");
        Path script = backup("FlowersForever-20260601-120000.zip");
        Path unrelated = folder.resolve("notes.zip");
        Files.writeString(unrelated, "not a backup");

        List<Path> removed = BackupFile.prune(folder, 10);

        assertThat(removed).extracting(path -> path.getFileName().toString()).containsExactlyInAnyOrder(
                "FlowersForever-auto-20260901-020000.zip", "FlowersForever-auto-20260902-020000.zip",
                "FlowersForever-auto-20260903-020000.zip");
        for (Path gone : removed) {
            assertThat(gone).doesNotExist();
            assertThat(BackupFile.checksumFor(gone)).doesNotExist();
        }
        assertThat(BackupFile.list(folder).stream().filter(BackupFile::automatic)).hasSize(10);
        assertThat(List.of(manual, beforeUpdate, script, unrelated)).allSatisfy(path -> assertThat(path).exists());
    }

    @Test
    @DisplayName("lists backups newest first, including ones made by backup-data.ps1")
    void listsNewestFirst() throws IOException {
        backup("FlowersForever-20260601-120000.zip");
        backup("FlowersForever-backup-20260801-120000.zip");
        backup("FlowersForever-backup-20260801-120000-2.zip");
        Files.writeString(folder.resolve("FlowersForever-auto-20260901-020000.zip"), "no check file");

        List<BackupFile> found = BackupFile.list(folder);

        assertThat(found).extracting(file -> file.archive().getFileName().toString()).containsExactly(
                "FlowersForever-auto-20260901-020000.zip", "FlowersForever-backup-20260801-120000-2.zip",
                "FlowersForever-backup-20260801-120000.zip", "FlowersForever-20260601-120000.zip");
        assertThat(found.get(0).hasChecksum()).isFalse();
        assertThat(found.get(3).label()).isEmpty();
        assertThat(found.get(3).madeAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 12, 0));
        assertThat(BackupFile.list(folder.resolve("unplugged-usb"))).isEmpty();
    }

    @Test
    @DisplayName("always keeps at least the newest automatic backup")
    void keepsAtLeastOne() throws IOException {
        backup("FlowersForever-auto-20260901-020000.zip");
        backup("FlowersForever-auto-20260902-020000.zip");

        BackupFile.prune(folder, 0);

        assertThat(BackupFile.list(folder)).extracting(file -> file.archive().getFileName().toString())
                .containsExactly("FlowersForever-auto-20260902-020000.zip");
    }
}

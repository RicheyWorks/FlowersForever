package com.flowerfarm.safety;

import com.flowerfarm.backup.DatabaseIntegrityGuard;
import com.flowerfarm.gui.FarmUi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Release check 4: a failed save (disk full, file locked, permission denied,
 * read-only folder) gives the grower a clear warning, and nothing already saved is
 * lost. The failures are injected into the real database and file code.
 */
@DisplayName("Data safety check 4: failed saves warn clearly and lose nothing")
class FailedSaveDataSafetyTest {

    @TempDir
    Path temp;

    @AfterEach
    void diskHasSpaceAgain() {
        FullDiskFilePath.full = false;
    }

    @Test
    @DisplayName("Disk full in the middle of a save: clear warning, earlier records intact after restart")
    void diskFullDuringSave() throws Exception {
        FullDiskFilePath.install();
        Path data = Files.createDirectories(temp.resolve("data"));
        String options = DataSafetyTestSupport.shippedOptions(data).replace(";AUTO_SERVER=TRUE", "");
        String path = data.toAbsolutePath().toString().replace('\\', '/') + "/flowerfarm";
        String fullDiskUrl = "jdbc:h2:fulldisk:" + path + options;
        DataSafetyTestSupport.saveRecords(fullDiskUrl, 1, 20);

        Throwable failure;
        try (Connection connection = DriverManager.getConnection(fullDiskUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            FullDiskFilePath.full = true;
            failure = catchThrowable(() -> {
                for (int id = 21; id <= 2000; id++) {
                    statement.execute("INSERT INTO SAVED_RECORD VALUES (" + id + ", '" + "x".repeat(150) + "')");
                }
            });
        } catch (SQLException closingAfterFailure) {
            failure = closingAfterFailure;
        }
        FullDiskFilePath.full = false;

        assertThat(failure).isNotNull();
        assertThat(StorageProblem.of(failure)).contains(StorageProblem.DISK_FULL);
        String shown = FarmUi.actionableError("Saving the harvest", failure);
        assertThat(shown).contains("the disk is full").contains("Free up some space")
                .doesNotContain("Exception").doesNotContain("SQL");

        Path plainData = data;
        assertThat(DatabaseIntegrityGuard.check(plainData)).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
        assertThat(DataSafetyTestSupport.savedIds(DataSafetyTestSupport.shippedUrl(plainData)))
                .containsAll(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    @DisplayName("Records file locked by another FlowersForever: clear warning, records untouched")
    void lockedByAnotherProgram() throws Exception {
        Path data = Files.createDirectories(temp.resolve("data"));
        String exclusive = DataSafetyTestSupport.shippedUrl(data).replace(";AUTO_SERVER=TRUE", "");
        DataSafetyTestSupport.saveRecords(exclusive, 1, 5);
        try (ChildJvm other = ChildJvm.start("hold", exclusive)) {
            other.awaitLine("OPEN"::equals);
            Throwable failure = catchThrowable(() -> DataSafetyTestSupport.saveRecords(exclusive, 6, 6));
            assertThat(StorageProblem.of(failure)).contains(StorageProblem.LOCKED);
            assertThat(FarmUi.actionableError("Saving the harvest", failure))
                    .contains("being used by another program").contains("Close the other program");
            assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.IN_USE);
        }
        assertThat(DataSafetyTestSupport.savedIds(exclusive)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Read-only records file: clear warning, records untouched")
    void readOnlyRecords() throws Exception {
        Path data = Files.createDirectories(temp.resolve("data"));
        String url = DataSafetyTestSupport.shippedUrl(data).replace(";AUTO_SERVER=TRUE", "");
        DataSafetyTestSupport.saveRecords(url, 1, 5);
        Path file = data.resolve("flowerfarm.mv.db");
        assertThat(file.toFile().setWritable(false)).isTrue();
        try {
            Throwable failure = catchThrowable(() -> DataSafetyTestSupport.saveRecords(url, 6, 6));
            assertThat(StorageProblem.of(failure)).as("%s", failure).contains(StorageProblem.READ_ONLY);
            assertThat(FarmUi.actionableError("Saving the harvest", failure)).contains("read-only");
        } finally {
            assertThat(file.toFile().setWritable(true)).isTrue();
        }
        assertThat(DataSafetyTestSupport.savedIds(url)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("An export that fails part-way leaves the previous file intact and no stray temp files")
    void failedExportKeepsPreviousFile() throws Exception {
        Path target = temp.resolve("invoice.pdf");
        Files.writeString(target, "previous invoice");
        // A folder in the way makes the final swap fail after all bytes were written.
        Path blocked = Files.createDirectories(temp.resolve("blocked.pdf"));
        Files.writeString(blocked.resolve("inside.txt"), "keep");
        assertThatThrownBy(() -> SafeFiles.write(blocked, "new".getBytes()))
                .isInstanceOf(java.io.IOException.class);
        assertThat(blocked.resolve("inside.txt")).hasContent("keep");

        // Another program holding the file open (Windows refuses to replace it).
        try (FileChannel held = FileChannel.open(target, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            held.lock();
            Throwable failure = catchThrowable(() -> SafeFiles.writeString(target, "new invoice"));
            if (failure != null) {
                assertThat(StorageProblem.of(failure)).isPresent();
            }
        }
        assertThat(Files.readString(target)).isIn("previous invoice", "new invoice");
        try (Stream<Path> files = Files.list(temp)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(name -> name.endsWith(".part"));
        }
    }

    @Test
    @DisplayName("Windows storage errors are described in plain words")
    void windowsMessagesArePlain() {
        assertThat(StorageProblem.of(new FileSystemException("C:\\farm\\a.pdf", null,
                "There is not enough space on the disk"))).contains(StorageProblem.DISK_FULL);
        assertThat(StorageProblem.of(new AccessDeniedException("C:\\Program Files\\a.pdf")))
                .contains(StorageProblem.ACCESS_DENIED);
        assertThat(StorageProblem.of(new FileSystemException("C:\\farm\\a.pdf", null,
                "The process cannot access the file because it is being used by another process")))
                .contains(StorageProblem.LOCKED);
        assertThat(StorageProblem.of(new FileSystemException("E:\\a.pdf", null, "The media is write protected")))
                .contains(StorageProblem.READ_ONLY);
        assertThat(StorageProblem.of(new IllegalArgumentException("Quantity must be positive"))).isEmpty();
        assertThat(FarmUi.actionableError("Saving the order",
                new RuntimeException(new AccessDeniedException("C:\\x"))))
                .isEqualTo("Saving the order did not finish: Windows did not give FlowersForever permission "
                        + "to write there. Choose a folder you own (for example Documents), or ask whoever "
                        + "manages this computer, then try again.");
    }
}

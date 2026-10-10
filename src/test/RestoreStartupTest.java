package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateSignatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Finishing a restore when FlowersForever reopens")
class RestoreStartupTest {

    @TempDir
    Path temp;

    private BackupSettings settings;
    private DataBackupService archives;
    private RestoreRecords records;
    private RestoreStartup startup;
    private Path data;
    private Path archive;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        settings = BackupTestSupport.settings(temp);
        data = settings.dataDirectory();
        archives = new DataBackupService(Clock.systemDefaultZone());
        records = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        startup = new RestoreStartup(settings, records, archives, Clock.systemDefaultZone(), Duration.ofMillis(300));
        BackupTestSupport.fakeRecords(data, "from-the-backup");
        archive = archives.backup(data, settings.defaultFolder(), "backup").archive();
        BackupTestSupport.fakeRecords(data, "current");
    }

    private void request() throws IOException {
        records.saveRequest(new RestoreRecords.Request(archive, UpdateSignatures.sha256Hex(archive),
                Optional.of(settings.defaultFolder().resolve("FlowersForever-before-restore-x.zip")), "Oct 1, 2026"));
    }

    private String records() throws IOException {
        return Files.readString(data.resolve(DataBackupService.DATABASE_FILE));
    }

    private List<Path> siblings(String marker) throws IOException {
        try (Stream<Path> list = Files.list(data.getParent())) {
            return list.filter(path -> path.getFileName().toString().contains(marker)).toList();
        }
    }

    private String startSpring() throws IOException {
        events.add("start:" + records());
        return "started";
    }

    @Test
    @DisplayName("swaps in the checked backup before the records open, and keeps the previous records")
    void restoresOnStart() throws Exception {
        request();

        String started = startup.start(this::startSpring, () -> events.add("close"), true);

        assertThat(started).isEqualTo("started");
        assertThat(events).containsExactly("start:database from-the-backup");
        assertThat(siblings(".before-restore-")).singleElement().satisfies(kept ->
                assertThat(Files.readString(kept.resolve(DataBackupService.DATABASE_FILE))).isEqualTo("database current"));
        assertThat(records.request()).isEmpty();
        assertThat(records.inProgress()).isEmpty();
        RestoreRecords.Notice notice = records.takeNotice().orElseThrow();
        assertThat(notice.success()).isTrue();
        assertThat(notice.operation()).isEqualTo(BackupActivityLog.RESTORE_DONE);
        assertThat(notice.message()).contains("restored from the backup made on Oct 1, 2026", "nothing was lost");
        assertThat(records.takeNotice()).isEmpty();
    }

    @Test
    @DisplayName("if the restored records do not open, puts the previous records back and starts with them")
    void undoesWhenRestoredRecordsFail() throws Exception {
        request();
        int[] calls = {0};

        String started = startup.start(() -> {
            calls[0]++;
            if (records().contains("from-the-backup")) {
                throw new IllegalStateException("Flyway validation failed: checksum mismatch");
            }
            return startSpring();
        }, () -> events.add("close"), true);

        assertThat(started).isEqualTo("started");
        assertThat(calls[0]).isEqualTo(2);
        assertThat(events).containsExactly("close", "start:database current");
        assertThat(records()).isEqualTo("database current");
        assertThat(siblings(".failed-restore-")).singleElement().satisfies(failed ->
                assertThat(Files.readString(failed.resolve(DataBackupService.DATABASE_FILE)))
                        .isEqualTo("database from-the-backup"));
        RestoreRecords.Notice notice = records.takeNotice().orElseThrow();
        assertThat(notice.operation()).isEqualTo(BackupActivityLog.RESTORE_UNDONE);
        assertThat(notice.message()).contains("put back the records you had before", "Nothing was lost",
                "Flyway validation failed");
    }

    @Test
    @DisplayName("a start that crashed after the swap is undone on the next start")
    void crashAfterSwap() throws Exception {
        Path keptAside = archives.restore(archive, data);
        records.saveInProgress(new RestoreRecords.InProgress(archive, Optional.of(keptAside), Optional.empty(), ""));

        startup.start(this::startSpring, () -> events.add("close"), true);

        assertThat(events).containsExactly("start:database current");
        assertThat(records.inProgress()).isEmpty();
        assertThat(records.takeNotice()).hasValueSatisfying(notice ->
                assertThat(notice.operation()).isEqualTo(BackupActivityLog.RESTORE_UNDONE));
    }

    @Test
    @DisplayName("a start that stopped between the two folder moves finds the previous records again")
    void crashBetweenMoves() throws Exception {
        Path aside = data.resolveSibling("data.before-restore-20261004-120000");
        Files.move(data, aside);
        records.saveInProgress(new RestoreRecords.InProgress(archive, Optional.of(aside), Optional.empty(), ""));

        startup.start(this::startSpring, () -> events.add("close"), true);

        assertThat(events).containsExactly("start:database current");
        assertThat(aside).doesNotExist();
    }

    @Test
    @DisplayName("records still open in another window: nothing changes and the grower is told why")
    void recordsStillOpen() throws Exception {
        request();
        try (FileChannel channel = FileChannel.open(data.resolve(DataBackupService.DATABASE_FILE),
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            assertThat(held.isValid()).isTrue();
            // Do not read the locked file here: on Windows an exclusive lock blocks even a read.
            startup.start(() -> {
                events.add("start-while-locked");
                return "started";
            }, () -> events.add("close"), true);
        }

        assertThat(records()).isEqualTo("database current");
        assertThat(events).containsExactly("start-while-locked");
        assertThat(records.request()).isEmpty();
        assertThat(records.takeNotice()).hasValueSatisfying(notice ->
                assertThat(notice.message()).isEqualTo(BackupMessages.RESTORE_LOCKED));
    }

    @Test
    @DisplayName("a backup that changed after it was checked is not restored")
    void changedBackup() throws Exception {
        records.saveRequest(new RestoreRecords.Request(archive, "0".repeat(64), Optional.empty(), ""));

        startup.start(this::startSpring, () -> events.add("close"), true);

        assertThat(records()).isEqualTo("database current");
        assertThat(records.takeNotice()).hasValueSatisfying(notice -> assertThat(notice.message())
                .contains("changed after it was checked", "Nothing was changed"));
    }

    @Test
    @DisplayName("a restore waiting while an app update finishes is cancelled, not mixed in")
    void notDuringUpdateRecovery() throws Exception {
        request();

        startup.start(this::startSpring, () -> events.add("close"), false);

        assertThat(records()).isEqualTo("database current");
        assertThat(records.request()).isEmpty();
        assertThat(records.takeNotice()).hasValueSatisfying(notice ->
                assertThat(notice.message()).isEqualTo(BackupMessages.RESTORE_NOT_NOW));
    }

    @Test
    @DisplayName("with nothing to restore, the start is unchanged")
    void normalStart() throws Exception {
        assertThat(startup.start(this::startSpring, () -> events.add("close"), true)).isEqualTo("started");
        assertThat(events).containsExactly("start:database current");
        assertThat(records.takeNotice()).isEmpty();
    }
}

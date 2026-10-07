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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared backup settings must never route another store's pending folder swap. */
class RestoreJournalOwnershipTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anotherStoresPendingOrInProgressRestoreCannotBeAppliedClearedOrOverwritten(boolean inProgress)
            throws Exception {
        Path first = temp.resolve("farm-a");
        Path other = temp.resolve("farm-b");
        Path state = temp.resolve("shared-settings");
        BackupTestSupport.fakeRecords(first, "farm A");
        BackupTestSupport.fakeRecords(other, "farm B");
        RestoreRecords owner = new RestoreRecords(state, first);
        Path retained = first.resolveSibling("farm-a.before-restore-20261007-180000");
        save(owner, inProgress, retained);
        if (inProgress) {
            Files.move(first, retained);
            Files.createDirectories(first.resolve("attachments"));
        }
        String name = inProgress ? RestoreRecords.IN_PROGRESS : RestoreRecords.REQUEST;
        byte[] journal = Files.readAllBytes(state.resolve(name));
        RestoreRecords foreign = new RestoreRecords(state, other);
        RestoreStartup startup = startup(other, state, foreign);

        assertThatThrownBy(() -> startup.start(() -> {
            throw new AssertionError("must stop before Spring opens another store");
        }, () -> { }, true)).isInstanceOf(DamagedRecordsException.class)
                .hasMessageContaining("different records folder").hasMessageContaining("Nothing was changed");
        assertThatThrownBy(() -> save(foreign, inProgress, temp.resolve("unrelated")))
                .hasMessageContaining("different records folder");
        foreign.clear(name);

        assertThat(Files.readAllBytes(state.resolve(name))).isEqualTo(journal);
        assertThat(Files.readString(other.resolve(DataBackupService.DATABASE_FILE))).isEqualTo("database farm B");
        assertThat(Files.readString((inProgress ? retained : first).resolve(DataBackupService.DATABASE_FILE)))
                .isEqualTo("database farm A");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unboundLegacyInstructionsArePreservedForManualReview(boolean inProgress) throws Exception {
        Path data = temp.resolve("data");
        Path state = temp.resolve("backup-settings");
        BackupTestSupport.fakeRecords(data, "my farm");
        save(new RestoreRecords(state), inProgress, temp.resolve("older-data"));
        String name = inProgress ? RestoreRecords.IN_PROGRESS : RestoreRecords.REQUEST;
        byte[] journal = Files.readAllBytes(state.resolve(name));
        RestoreRecords bound = new RestoreRecords(state, data);

        assertThatThrownBy(() -> startup(data, state, bound).start(() -> {
            throw new AssertionError("must not guess the origin of a legacy journal");
        }, () -> { }, true)).isInstanceOf(DamagedRecordsException.class)
                .hasMessageContaining("do not identify their original records folder")
                .hasMessageContaining("contact support");
        bound.clear(name);
        assertThat(Files.readAllBytes(state.resolve(name))).isEqualTo(journal);
        assertThat(Files.readString(data.resolve(DataBackupService.DATABASE_FILE))).isEqualTo("database my farm");
    }

    @Test
    void missingRecordsWithoutAnExactRetainedPathNeverStartABlankFarm() throws Exception {
        Path data = temp.resolve("data");
        Path state = temp.resolve("backup-settings");
        BackupTestSupport.fakeRecords(data, "my farm");
        RestoreRecords records = new RestoreRecords(state, data);
        records.saveInProgress(new RestoreRecords.InProgress(temp.resolve("backup.zip"), Optional.empty(),
                Optional.empty(), ""));
        Path retained = temp.resolve("data.before-restore-20261007-180000");
        Files.move(data, retained);
        Files.createDirectories(data.resolve("attachments"));

        assertThatThrownBy(() -> startup(data, state, records).start(() -> {
            throw new AssertionError("must not open a new empty database");
        }, () -> { }, true)).isInstanceOf(DamagedRecordsException.class)
                .hasMessageContaining("could not identify the previous farm records");
        assertThat(records.inProgress()).isPresent();
        assertThat(Files.readString(retained.resolve(DataBackupService.DATABASE_FILE))).isEqualTo("database my farm");
    }

    private void save(RestoreRecords records, boolean inProgress, Path retained) throws Exception {
        if (inProgress) {
            records.saveInProgress(new RestoreRecords.InProgress(temp.resolve("backup.zip"), Optional.of(retained),
                    Optional.empty(), ""));
        } else {
            records.saveRequest(new RestoreRecords.Request(temp.resolve("backup.zip"), "0".repeat(64),
                    Optional.empty(), ""));
        }
    }

    private static RestoreStartup startup(Path data, Path state, RestoreRecords records) {
        Clock clock = Clock.systemUTC();
        BackupSettings settings = new BackupSettings(data, state, state.resolve("archives"));
        return new RestoreStartup(settings, records, new DataBackupService(clock), clock, Duration.ZERO);
    }
}

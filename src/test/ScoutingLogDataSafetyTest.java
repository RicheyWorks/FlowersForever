package com.flowerfarm.safety;

import com.flowerfarm.scouting.ScoutingStore;
import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.VerifiedBackup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scouting log lives in its own file next to the database. These checks kill the
 * app the hard way while it saves, and confirm backups and restores carry the log.
 */
@DisplayName("Data safety: scouting log survives forced close and backup/restore")
class ScoutingLogDataSafetyTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("Killing the app mid-save never leaves a half-written scouting log")
    void hardKillDuringSavesKeepsAReadableLog() throws Exception {
        Path data = Files.createDirectories(temp.resolve("data"));
        int saved;
        try (ChildJvm child = ChildJvm.start("scouting", data.toString())) {
            child.awaitLine(line -> line.startsWith("SAVED ") && DataSafetyTestSupport.parse(line) >= 25);
            child.kill();
            saved = DataSafetyTestSupport.parse(child.lastProgress());
        }
        ScoutingStore store = new ScoutingStore(data, Clock.systemUTC());
        ScoutingStore.Log log = store.load();
        assertThat(store.takeNotice()).as("no recovery was needed").isEmpty();
        assertThat(log.findings().size()).as("every confirmed save is there").isBetween(saved, saved + 1);
        try (var files = Files.list(store.file().getParent())) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .allMatch(ScoutingLogDataSafetyTest::isScoutingRecordOrInterruptedStagingFile)
                    .contains("scouting-log.json");
        }
    }

    private static boolean isScoutingRecordOrInterruptedStagingFile(String name) {
        // A forced process kill cannot run the writer's finally block. Only the named
        // committed log/backup count as records; unique .part files may remain inert.
        return name.equals("scouting-log.json") || name.equals("scouting-log.json.bak")
                || name.matches("\\.scouting-log\\.json(?:\\.bak)?-[A-Za-z0-9_-]+\\.part");
    }

    @Test
    @DisplayName("Interrupted staging files cannot replace confirmed scouting records")
    void interruptedStagingFilesAreIgnoredWhenConfirmedLogIsReadable() throws Exception {
        ScoutingStore store = new ScoutingStore(temp.resolve("data"), Clock.systemUTC());
        store.save(CrashChild.scoutingLog(4));
        byte[] confirmed = Files.readAllBytes(store.file());
        Path partial = store.file().resolveSibling(".scouting-log.json-123.part");
        Files.writeString(partial, "{\"version\":1,\"findings\":[");
        ScoutingStore newer = new ScoutingStore(temp.resolve("uncommitted-fixture"), Clock.systemUTC());
        newer.save(CrashChild.scoutingLog(9));
        Path stagedBackup = store.file().resolveSibling(".scouting-log.json.bak-456.part");
        Files.copy(newer.file(), stagedBackup);

        ScoutingStore reopened = new ScoutingStore(temp.resolve("data"), Clock.systemUTC());
        assertThat(reopened.load().findings()).hasSize(4);
        assertThat(reopened.takeNotice()).isEmpty();
        assertThat(Files.readAllBytes(store.file())).isEqualTo(confirmed);
        assertThat(Files.readAllBytes(stagedBackup)).isEqualTo(Files.readAllBytes(newer.file()));
        assertThat(partial).exists();
    }

    @Test
    @DisplayName("Damaged scouting records recover only from the committed backup, never staging")
    void recoveryIgnoresNewerUncommittedStagingFile() throws Exception {
        ScoutingStore store = new ScoutingStore(temp.resolve("data"), Clock.systemUTC());
        store.save(CrashChild.scoutingLog(4));
        store.save(CrashChild.scoutingLog(6));
        ScoutingStore newer = new ScoutingStore(temp.resolve("uncommitted-fixture"), Clock.systemUTC());
        newer.save(CrashChild.scoutingLog(9));
        Path staged = store.file().resolveSibling(".scouting-log.json-789.part");
        Files.copy(newer.file(), staged);
        Files.writeString(store.file(), "{torn");

        ScoutingStore reopened = new ScoutingStore(temp.resolve("data"), Clock.systemUTC());
        assertThat(reopened.load().findings()).hasSize(4);
        assertThat(reopened.takeNotice()).contains("last good copy");
        assertThat(Files.readAllBytes(staged)).isEqualTo(Files.readAllBytes(newer.file()));
    }

    @Test
    @DisplayName("Backups include the scouting log and a restore brings it back")
    void backupAndRestoreCarryTheScoutingLog() throws Exception {
        Path data = Files.createDirectories(temp.resolve("data"));
        String url = DataSafetyTestSupport.shippedUrl(data);
        DataSafetyTestSupport.saveRecords(url, 1, 3);
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T22:00:00Z"), ZoneId.of("America/Los_Angeles"));
        ScoutingStore store = new ScoutingStore(data, clock);
        store.save(CrashChild.scoutingLog(4));
        DataBackupService backups = new DataBackupService(clock);
        VerifiedBackup backup = backups.backup(data, temp.resolve("backups"), "before-scouting-test");

        store.save(CrashChild.scoutingLog(9));
        backups.restore(backup.archive(), data);

        assertThat(new ScoutingStore(data, clock).load().findings()).hasSize(4);
    }
}

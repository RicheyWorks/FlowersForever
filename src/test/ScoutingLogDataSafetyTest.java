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
                    .allMatch(name -> name.startsWith("scouting-log.json"))
                    .contains("scouting-log.json");
        }
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

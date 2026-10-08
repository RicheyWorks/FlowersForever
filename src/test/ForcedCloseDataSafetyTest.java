package com.flowerfarm.safety;

import com.flowerfarm.backup.DatabaseIntegrityGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Release check 2: no saved records are lost when FlowersForever is forced closed in
 * the middle of a save. A separate JVM saves records with the app's real database
 * settings and is killed the hard way (no shutdown hooks, no clean close).
 */
@DisplayName("Data safety check 2: forced close in the middle of a save")
class ForcedCloseDataSafetyTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("The shipped database settings write each save to disk when it is committed")
    void shippedSettingsCommitImmediately() throws Exception {
        assertThat(DataSafetyTestSupport.shippedOptions(temp)).contains(";WRITE_DELAY=0");
    }

    @Test
    @DisplayName("Every save the app confirmed is still there after repeated hard kills mid-save")
    void hardKillMidSaveLosesNothingConfirmed() throws Exception {
        Path data = Files.createDirectories(temp.resolve("data"));
        String url = DataSafetyTestSupport.shippedUrl(data);
        int confirmedSoFar = 0;
        // Back-to-back saves (always mid-write) and paced saves (like a grower clicking Save).
        long[] pauses = {0, 0, 40, 40};
        for (long pause : pauses) {
            int lastConfirmed;
            try (ChildJvm child = ChildJvm.start("records", url, String.valueOf(pause))) {
                int target = confirmedSoFar + 25;
                child.awaitLine(line -> line.startsWith("SAVED ") && DataSafetyTestSupport.parse(line) >= target);
                child.kill();
                lastConfirmed = DataSafetyTestSupport.parse(child.lastProgress());
            }
            assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
            TreeSet<Integer> ids = DataSafetyTestSupport.savedIds(url);
            for (int id = 1; id <= lastConfirmed; id++) {
                assertThat(ids).as("confirmed save #%d after kill (pause %d ms)", id, pause).contains(id);
            }
            confirmedSoFar = ids.last();
        }
        assertThat(confirmedSoFar).isGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("Exported files are either the old or the new version after a hard kill, never half-written")
    void hardKillMidFileWriteNeverLeavesAHalfFile() throws Exception {
        Path target = temp.resolve("harvest-log.pdf");
        for (int round = 0; round < 3; round++) {
            try (ChildJvm child = ChildJvm.start("file", target.toString(), "4")) {
                child.awaitLine(line -> line.startsWith("WROTE "));
                child.awaitLine(line -> line.startsWith("WROTE "));
                child.kill();
            }
            byte[] bytes = Files.readAllBytes(target);
            assertThat(bytes).hasSize(4 * 1024 * 1024);
            byte first = bytes[0];
            assertThat(first == 'A' || first == 'B').isTrue();
            for (byte b : bytes) {
                if (b != first) {
                    throw new AssertionError("The file mixes old and new content after a kill");
                }
            }
        }
    }
}

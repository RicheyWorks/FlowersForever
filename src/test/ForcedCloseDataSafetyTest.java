package com.flowerfarm.safety;

import com.flowerfarm.backup.DatabaseIntegrityGuard;
import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.IOException;
import java.time.Duration;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
            String stoppedChild;
            try (ChildJvm child = ChildJvm.start("records", url, String.valueOf(pause))) {
                int target = confirmedSoFar + 25;
                child.awaitLine(line -> line.startsWith("SAVED ") && DataSafetyTestSupport.parse(line) >= target);
                child.kill();
                lastConfirmed = DataSafetyTestSupport.parse(child.lastProgress());
                stoppedChild = child.processStatus();
            }
            assertHealthyAfterKill(data, pause, lastConfirmed, stoppedChild);
            TreeSet<Integer> ids = DataSafetyTestSupport.savedIds(url);
            for (int id = 1; id <= lastConfirmed; id++) {
                assertThat(ids).as("confirmed save #%d after kill (pause %d ms)", id, pause).contains(id);
            }
            confirmedSoFar = ids.last();
        }
        assertThat(confirmedSoFar).isGreaterThanOrEqualTo(100);
    }

    private static void assertHealthyAfterKill(Path data, long pause, int lastConfirmed,
                                               String stoppedChild) throws IOException {
        DatabaseIntegrityGuard.Health health = DatabaseIntegrityGuard.check(data);
        long lockWaitMillis = 0;
        if (health == DatabaseIntegrityGuard.Health.IN_USE) {
            // kill() already observed process termination. Windows may still be
            // releasing that dead process's file lock; never retry a damaged or
            // unreadable database, nor any assertion about acknowledged records.
            long started = System.nanoTime();
            try {
                await().alias("database file lock release after " + stoppedChild)
                        .atMost(Duration.ofSeconds(5)).pollDelay(Duration.ZERO)
                        .pollInterval(Duration.ofMillis(50)).until(() -> {
                            try {
                                DataBackupService.requireDatabaseClosed(data);
                                return true;
                            } catch (UpdateVerificationException stillLocked) {
                                return false;
                            }
                        });
                lockWaitMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                System.out.println("Database file lock released after " + lockWaitMillis
                        + " ms following " + stoppedChild + ", pause=" + pause + " ms");
                health = DatabaseIntegrityGuard.check(data);
            } catch (ConditionTimeoutException stillLocked) {
                lockWaitMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            }
        }
        if (health == DatabaseIntegrityGuard.Health.IN_USE) {
            Throwable cause;
            try {
                DataBackupService.requireDatabaseClosed(data);
                cause = new IllegalStateException("The lock check passed on the diagnostic retry; "
                        + "the final IN_USE result still fails this test.");
            } catch (UpdateVerificationException locked) {
                cause = locked;
            }
            try (FileChannel channel = FileChannel.open(data.resolve(DataBackupService.DATABASE_FILE),
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                cause.addSuppressed(new IllegalStateException(lock == null
                        ? "Raw file-lock probe: another process still holds the database lock."
                        : "Raw file-lock probe: the database lock is available now."));
            } catch (IOException | RuntimeException lockFailure) {
                cause.addSuppressed(lockFailure);
            }
            String files;
            try (var children = Files.list(data)) {
                files = children.map(path -> path.getFileName().toString()).sorted().toList().toString();
            }
            throw new AssertionError("Expected HEALTHY after completed hard kill; found IN_USE. "
                    + stoppedChild + ", pause=" + pause + " ms, confirmed=" + lastConfirmed
                    + ", waited for lock=" + lockWaitMillis + " ms (maximum 5000 ms)"
                    + ", records=" + data + ", files=" + files, cause);
        }
        assertThat(health).as("database health after %s, pause %d ms, confirmed save %d, lock wait %d ms",
                stoppedChild, pause, lastConfirmed, lockWaitMillis)
                .isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
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

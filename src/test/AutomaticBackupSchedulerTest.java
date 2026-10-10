package com.flowerfarm.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Automatic backups on a schedule")
class AutomaticBackupSchedulerTest {

    @TempDir
    Path temp;

    private BackupSettings settings;
    private BackupTestSupport.MutableClock clock;
    private BackupPreferencesStore store;
    private final List<String> activity = new ArrayList<>();
    private AutomaticBackupScheduler scheduler;
    private Path usb;

    @BeforeEach
    void setUp() throws IOException {
        settings = BackupTestSupport.settings(temp);
        BackupTestSupport.fakeRecords(settings.dataDirectory(), "v1");
        clock = new BackupTestSupport.MutableClock(Instant.parse("2026-10-01T09:00:00Z"));
        store = new BackupPreferencesStore(settings.stateDirectory());
        usb = Files.createDirectories(temp.resolve("USB/FlowersForever"));
        scheduler = new AutomaticBackupScheduler(
                BackupTestSupport.service(settings, BackupTestSupport.copyOf(settings.dataDirectory()), clock),
                store, (operation, success, message) -> activity.add(operation + ":" + success), clock);
    }

    private void turnOn(Path folder, BackupSchedule schedule, int keep) throws IOException {
        store.save(store.load().withChoices(true, Optional.of(folder), schedule, keep));
    }

    private long automaticBackups() {
        return BackupFile.list(usb).stream().filter(BackupFile::automatic).count();
    }

    @Test
    @DisplayName("does nothing until the grower turns automatic backups on")
    void offByDefault() {
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.NOT_ENABLED);
        assertThat(automaticBackups()).isZero();
    }

    @Test
    @DisplayName("backs up right away the first time, then once a day, to the chosen folder")
    void dailySchedule() throws IOException {
        turnOn(usb, BackupSchedule.DAILY, 10);

        AutomaticBackupScheduler.Result first = scheduler.runIfDue();
        assertThat(first.status()).isEqualTo(AutomaticBackupScheduler.Status.DONE);
        assertThat(first.message()).startsWith("Automatic backup saved and checked: FlowersForever-auto-");
        assertThat(BackupFile.checksumFor(first.backup().orElseThrow().archive())).isRegularFile();
        assertThat(store.load().lastAutomatic()).contains(clock.instant());
        assertThat(activity).containsExactly("AUTO_BACKUP:true");

        clock.advance(Duration.ofHours(3));
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.NOT_DUE);
        clock.advance(Duration.ofHours(21));
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.DONE);
        assertThat(automaticBackups()).isEqualTo(2);
    }

    @Test
    @DisplayName("weekly backups wait a week, and missed ones run at the next chance")
    void weeklySchedule() throws IOException {
        turnOn(usb, BackupSchedule.WEEKLY, 10);
        scheduler.runIfDue();

        clock.advance(Duration.ofDays(2));
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.NOT_DUE);
        clock.advance(Duration.ofDays(12));
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.DONE);
        assertThat(automaticBackups()).isEqualTo(2);
    }

    @Test
    @DisplayName("keeps only the newest automatic backups the grower asked for")
    void retention() throws IOException {
        turnOn(usb, BackupSchedule.DAILY, 2);
        for (int day = 0; day < 4; day++) {
            assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.DONE);
            clock.advance(Duration.ofDays(1));
        }
        assertThat(automaticBackups()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unplugged USB drive skips the backup with a plain message and never creates the folder")
    void unpluggedFolder() throws IOException {
        Path unplugged = temp.resolve("E-drive/FlowersForever");
        turnOn(unplugged, BackupSchedule.DAILY, 10);

        AutomaticBackupScheduler.Result result = scheduler.runIfDue();

        assertThat(result.status()).isEqualTo(AutomaticBackupScheduler.Status.FOLDER_UNAVAILABLE);
        assertThat(result.message()).contains("not available right now", "plug it in", "try again later");
        assertThat(unplugged).doesNotExist();
        assertThat(store.load().lastProblem()).contains(result.message());
        assertThat(scheduler.overdueWarning()).isEmpty();
    }

    @Test
    @DisplayName("warns once backups have been failing for three intervals, and clears after a good backup")
    void overdueWarning() throws IOException {
        turnOn(usb, BackupSchedule.DAILY, 10);
        scheduler.runIfDue();
        Path gone = temp.resolve("USB-unplugged");
        store.save(store.load().withChoices(true, Optional.of(gone), BackupSchedule.DAILY, 10));

        clock.advance(Duration.ofDays(1));
        scheduler.runIfDue();
        assertThat(scheduler.overdueWarning()).isEmpty();
        clock.advance(Duration.ofDays(3));
        scheduler.runIfDue();
        assertThat(scheduler.overdueWarning()).hasValueSatisfying(text -> assertThat(text)
                .contains("have not worked since", "plug it in"));

        store.save(store.load().withChoices(true, Optional.of(usb), BackupSchedule.DAILY, 10));
        assertThat(scheduler.runIfDue().status()).isEqualTo(AutomaticBackupScheduler.Status.DONE);
        assertThat(scheduler.overdueWarning()).isEmpty();
    }

    @Test
    @DisplayName("reminds a grower who has no backups for two weeks and has automatic backups off")
    void reminder() throws IOException {
        assertThat(scheduler.reminder()).contains(BackupMessages.REMINDER);
        store.save(store.load().withBackupAt(clock.instant(), false));
        assertThat(scheduler.reminder()).isEmpty();
        clock.advance(Duration.ofDays(15));
        assertThat(scheduler.reminder()).contains(BackupMessages.REMINDER);
        turnOn(usb, BackupSchedule.DAILY, 10);
        assertThat(scheduler.reminder()).isEmpty();
    }
}

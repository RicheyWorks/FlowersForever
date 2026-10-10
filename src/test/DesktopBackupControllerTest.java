package com.flowerfarm.gui;

import com.flowerfarm.backup.AutomaticBackupScheduler;
import com.flowerfarm.backup.BackupActivityLog;
import com.flowerfarm.backup.BackupFile;
import com.flowerfarm.backup.BackupMessages;
import com.flowerfarm.backup.BackupPreferencesStore;
import com.flowerfarm.backup.BackupSchedule;
import com.flowerfarm.backup.BackupSettings;
import com.flowerfarm.backup.BackupTestSupport;
import com.flowerfarm.backup.LiveBackupService;
import com.flowerfarm.backup.RestoreRecords;
import com.flowerfarm.backup.RestoreService;
import com.flowerfarm.update.DataBackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("File menu backups: Back up now, Restore, Automatic backups")
class DesktopBackupControllerTest {

    @TempDir
    Path temp;

    private BackupSettings settings;
    private BackupPreferencesStore store;
    private RestoreRecords restoreRecords;
    private final List<String> events = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private final List<String> activity = new ArrayList<>();
    private final Deque<Integer> answers = new ArrayDeque<>();
    private Consumer<JPanel> panelEditor = panel -> { };
    private boolean updateInProgress;
    private boolean unfinishedWorkOk = true;
    private DesktopBackupController controller;

    private final FarmDialogs dialogs = new FarmDialogs() {
        @Override
        public int showOption(Component parent, Object message, String title, int optionType, int messageType,
                              Icon icon, Object[] options, Object initialValue) {
            if (message instanceof JPanel panel) {
                panelEditor.accept(panel);
            } else {
                messages.add("ask: " + message);
            }
            return answers.isEmpty() ? -1 : answers.removeFirst();
        }

        @Override
        public int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
            return answers.isEmpty() ? -1 : answers.removeFirst();
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            messages.add(String.valueOf(message));
        }
    };

    private Optional<File> chosenBackup = Optional.empty();
    private Optional<File> chosenDirectory = Optional.empty();
    private final FarmFileChooser files = new FarmFileChooser() {
        @Override
        public Optional<File> chooseSave(Component parent, String title, File suggestedFile) {
            return Optional.empty();
        }

        @Override
        public Optional<File> chooseBackup(Component parent, String title, File folder) {
            return chosenBackup;
        }

        @Override
        public Optional<File> chooseDirectory(Component parent, String title, File folder) {
            return chosenDirectory;
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        settings = BackupTestSupport.settings(temp);
        BackupTestSupport.realRecords(settings.dataDirectory(), "current records");
        store = new BackupPreferencesStore(settings.stateDirectory());
        restoreRecords = new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
        Clock clock = Clock.systemUTC();
        LiveBackupService backups = BackupTestSupport.service(settings,
                BackupTestSupport.copyOf(settings.dataDirectory()), clock);
        BackupActivityLog log = (operation, success, message) -> activity.add(operation);
        controller = new DesktopBackupController(backups,
                new RestoreService(settings, new DataBackupService(clock), "sa", ""),
                new AutomaticBackupScheduler(backups, store, log, clock), store, restoreRecords, log,
                () -> updateInProgress, () -> events.add("shutdown"), Runnable::run, Runnable::run,
                (first, period, task) -> events.add("scheduled every " + period.toMinutes() + " min"),
                code -> events.add("exit " + code), () -> true, () -> {
                    events.add("relaunch");
                    return true;
                }, clock);
        controller.attach(new DesktopBackupController.Host(null, dialogs, files, statuses::add,
                () -> {
                    events.add("unfinished-work check");
                    return unfinishedWorkOk;
                }, () -> events.add("save window")));
        messages.clear();
        statuses.clear();
    }

    private Path olderBackup() throws Exception {
        return new DataBackupService(Clock.systemDefaultZone())
                .backup(settings.dataDirectory(), settings.defaultFolder(), "backup").archive();
    }

    @Test
    @DisplayName("attaching schedules the automatic backup checks")
    void attachSchedules() {
        assertThat(events).containsExactly("scheduled every 30 min");
    }

    @Test
    @DisplayName("Back up now saves a checked backup while the app stays open, and says so plainly")
    void backUpNow() {
        answers.add(0);

        controller.backUpNow();

        assertThat(messages).last().asString().contains("Your farm records are backed up, and the copy was checked",
                "USB drive");
        assertThat(BackupFile.list(settings.defaultFolder())).singleElement()
                .satisfies(file -> assertThat(file.label()).isEqualTo("backup"));
        assertThat(activity).contains(BackupActivityLog.BACKUP_CREATED);
        assertThat(store.load().lastBackup()).isPresent();
        assertThat(events).doesNotContain("shutdown", "exit 0");
        assertThat(controller.busy()).isFalse();
    }

    @Test
    @DisplayName("Restore checks the backup, saves the current records first, then closes and reopens")
    void restoreFlow() throws Exception {
        Path archive = olderBackup();
        answers.add(0); // Restore the newest backup
        answers.add(0); // Restore and reopen

        controller.restoreFromBackup();

        RestoreRecords.Request request = restoreRecords.request().orElseThrow();
        assertThat(request.archive()).isEqualTo(archive.toAbsolutePath().normalize());
        assertThat(request.safetyBackup()).hasValueSatisfying(safety -> {
            assertThat(safety).isRegularFile();
            assertThat(safety.getFileName().toString()).startsWith("FlowersForever-before-restore-");
        });
        assertThat(events).containsSubsequence("unfinished-work check", "save window", "shutdown", "relaunch",
                "exit 0");
        assertThat(activity).contains(BackupActivityLog.RESTORE_REQUESTED);
    }

    @Test
    @DisplayName("cancelling at the confirmation changes nothing")
    void restoreCancelled() throws Exception {
        olderBackup();
        answers.add(0);
        answers.add(1);

        controller.restoreFromBackup();

        assertThat(restoreRecords.request()).isEmpty();
        assertThat(BackupFile.list(settings.defaultFolder())).hasSize(1);
        assertThat(events).doesNotContain("shutdown", "exit 0");
        assertThat(statuses).last().isEqualTo("Restore cancelled. Nothing was changed.");
    }

    @Test
    @DisplayName("unfinished work stops the restore before anything closes")
    void unfinishedWorkStopsRestore() throws Exception {
        olderBackup();
        unfinishedWorkOk = false;
        answers.add(0);
        answers.add(0);

        controller.restoreFromBackup();

        assertThat(restoreRecords.request()).isEmpty();
        assertThat(events).doesNotContain("shutdown", "exit 0");
    }

    @Test
    @DisplayName("a damaged backup is refused before anything closes")
    void damagedBackupRefused() throws Exception {
        Path archive = olderBackup();
        Files.write(archive, new byte[] {9}, StandardOpenOption.APPEND);
        answers.add(0);

        controller.restoreFromBackup();

        assertThat(messages).last().asString().contains("cannot be restored", "Nothing was changed");
        assertThat(restoreRecords.request()).isEmpty();
        assertThat(events).doesNotContain("shutdown");
    }

    @Test
    @DisplayName("restore waits while an app update is being finished")
    void notDuringUpdate() {
        updateInProgress = true;

        controller.restoreFromBackup();

        assertThat(messages).containsExactly(BackupMessages.UPDATE_IN_PROGRESS);
    }

    @Test
    @DisplayName("choosing a folder turns automatic backups on and makes the first one right away")
    void automaticBackupsOn() {
        Path usb = temp.resolve("USB");
        usb.toFile().mkdirs();
        chosenDirectory = Optional.of(usb.toFile());
        panelEditor = panel -> {
            find(panel, "backup.automatic", JCheckBox.class).setSelected(true);
            find(panel, "backup.chooseFolder", JButton.class).doClick();
            find(panel, "backup.schedule", JComboBox.class).setSelectedItem(BackupSchedule.WEEKLY);
            find(panel, "backup.keep", JSpinner.class).setValue(3);
        };
        answers.add(0);

        controller.automaticBackups();

        assertThat(store.load().automatic()).isTrue();
        assertThat(store.load().folder()).contains(usb.toAbsolutePath().normalize());
        assertThat(store.load().schedule()).isEqualTo(BackupSchedule.WEEKLY);
        assertThat(store.load().keep()).isEqualTo(3);
        assertThat(BackupFile.list(usb)).singleElement().satisfies(file -> assertThat(file.automatic()).isTrue());
        assertThat(statuses).anySatisfy(text -> assertThat(text).startsWith("Automatic backups are on: every week"));
    }

    @Test
    @DisplayName("a backup folder inside the records folder is refused")
    void folderInsideRecordsRefused() {
        chosenDirectory = Optional.of(settings.dataDirectory().resolve("x").toFile());
        panelEditor = panel -> {
            find(panel, "backup.automatic", JCheckBox.class).setSelected(true);
            find(panel, "backup.chooseFolder", JButton.class).doClick();
        };
        answers.add(0);
        answers.add(1);

        controller.automaticBackups();

        assertThat(messages).containsExactly(BackupMessages.FOLDER_INSIDE_RECORDS);
        assertThat(store.load().automatic()).isFalse();
    }

    @Test
    @DisplayName("an unplugged backup drive only shows a status message, never a pop-up")
    void unpluggedDriveIsQuiet() throws Exception {
        store.save(store.load().withChoices(true, Optional.of(temp.resolve("E-drive")), BackupSchedule.DAILY, 10));

        controller.automaticTick();

        assertThat(messages).isEmpty();
        assertThat(statuses).singleElement().asString().contains("plug it in");
    }

    @Test
    @DisplayName("the result of a restore is shown once after reopening and recorded in Activity History")
    void noticeAfterReopen() throws Exception {
        restoreRecords.saveNotice(new RestoreRecords.Notice("Your farm records were restored.", true,
                BackupActivityLog.RESTORE_DONE));
        DesktopBackupController.Host host = new DesktopBackupController.Host(null, dialogs, files, statuses::add,
                () -> true, () -> { });

        controller.attach(host);
        controller.attach(host);

        assertThat(messages).containsOnlyOnce("Your farm records were restored.");
        assertThat(activity).containsOnlyOnce(BackupActivityLog.RESTORE_DONE);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                try {
                    return find(container, name, type);
                } catch (IllegalArgumentException notHere) {
                    // keep looking
                }
            }
        }
        throw new IllegalArgumentException("No " + name);
    }
}

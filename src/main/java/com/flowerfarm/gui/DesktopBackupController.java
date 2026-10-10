package com.flowerfarm.gui;

import com.flowerfarm.backup.AutomaticBackupScheduler;
import com.flowerfarm.backup.BackupActivityLog;
import com.flowerfarm.backup.BackupDetails;
import com.flowerfarm.backup.BackupException;
import com.flowerfarm.backup.BackupFile;
import com.flowerfarm.backup.BackupMessages;
import com.flowerfarm.backup.BackupPreferences;
import com.flowerfarm.backup.BackupPreferencesStore;
import com.flowerfarm.backup.LiveBackupService;
import com.flowerfarm.backup.RestoreRecords;
import com.flowerfarm.backup.RestoreService;
import com.flowerfarm.update.UpdateFirstLaunchVerifier;
import com.flowerfarm.update.InstalledAppLauncher;
import com.flowerfarm.update.UpdateSettings;
import com.flowerfarm.update.UpdateState;
import com.flowerfarm.update.UpdateStateStore;
import com.flowerfarm.update.VerifiedBackup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * File > Back up now, Restore from a backup, and Automatic backups. Backups run
 * on one background thread while the farm keeps working; dialogs go through
 * {@link FarmDialogs}. A restore checks the backup, saves a backup of the current
 * records, then closes and reopens FlowersForever to swap the records in safely
 * (see {@link com.flowerfarm.backup.RestoreStartup}).
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopBackupController {

    static final String TITLE = BackupMessages.TITLE;
    static final String MANUAL_LABEL = "backup";
    static final String SAFETY_LABEL = "before-restore";
    static final Duration FIRST_CHECK = Duration.ofMinutes(2);
    static final Duration CHECK_EVERY = Duration.ofMinutes(30);

    /** Frame-level hooks provided by {@link FlowerFarmGUI}. */
    public record Host(JFrame frame, FarmDialogs dialogs, FarmFileChooser files, Consumer<String> status,
                       BooleanSupplier confirmClose, Runnable beforeClose) {
    }

    /** Runs {@code task} repeatedly in the background. */
    @FunctionalInterface
    interface Repeater {
        void every(Duration first, Duration period, Runnable task);
    }

    private final LiveBackupService backups;
    private final RestoreService restores;
    private final AutomaticBackupScheduler automatic;
    private final BackupPreferencesStore preferences;
    private final RestoreRecords restoreRecords;
    private final BackupActivityLog activity;
    private final BooleanSupplier updateInProgress;
    private final Runnable shutdown;
    private final Executor background;
    private final Executor swing;
    private final Repeater repeater;
    private final IntConsumer exit;
    private final BooleanSupplier canRelaunch;
    private final BooleanSupplier relaunch;
    private final Clock clock;
    private Host host;
    private volatile boolean busy;

    @Autowired
    public DesktopBackupController(LiveBackupService backups, RestoreService restores,
                                   AutomaticBackupScheduler automatic, BackupPreferencesStore preferences,
                                   RestoreRecords restoreRecords, BackupActivityLog activity,
                                   ObjectProvider<UpdateSettings> updates, ConfigurableApplicationContext context,
                                   DataSource dataSource) {
        this(backups, restores, automatic, preferences, restoreRecords, activity,
                () -> updateInProgress(updates.getIfAvailable()),
                () -> {
                    UpdateFirstLaunchVerifier.shutdownEmbeddedDatabase(dataSource);
                    context.close();
                }, BackgroundThread.INSTANCE.executor(), SwingUtilities::invokeLater,
                (first, period, task) -> BackgroundThread.INSTANCE.executor().scheduleWithFixedDelay(task,
                        first.toSeconds(), period.toSeconds(), TimeUnit.SECONDS),
                System::exit, DesktopBackupController::hasInstalledLauncher,
                DesktopBackupController::relaunchInstalledApp, Clock.systemUTC());
    }

    DesktopBackupController(LiveBackupService backups, RestoreService restores, AutomaticBackupScheduler automatic,
                            BackupPreferencesStore preferences, RestoreRecords restoreRecords,
                            BackupActivityLog activity, BooleanSupplier updateInProgress, Runnable shutdown,
                            Executor background, Executor swing, Repeater repeater, IntConsumer exit,
                            BooleanSupplier canRelaunch, BooleanSupplier relaunch, Clock clock) {
        this.backups = backups;
        this.restores = restores;
        this.automatic = automatic;
        this.preferences = preferences;
        this.restoreRecords = restoreRecords;
        this.activity = activity;
        this.updateInProgress = updateInProgress;
        this.shutdown = shutdown;
        this.background = background;
        this.swing = swing;
        this.repeater = repeater;
        this.exit = exit;
        this.canRelaunch = canRelaunch;
        this.relaunch = relaunch;
        this.clock = clock;
    }

    /** Called once the main window is visible. */
    public void attach(Host frameHost) {
        this.host = frameHost;
        restoreRecords.takeNotice().ifPresent(notice -> {
            activity.record(notice.operation(), notice.success(), notice.message());
            message(notice.message(), notice.success() ? JOptionPane.INFORMATION_MESSAGE
                    : JOptionPane.WARNING_MESSAGE);
        });
        Optional<String> overdue = automatic.overdueWarning();
        if (overdue.isPresent()) {
            message(overdue.get(), JOptionPane.WARNING_MESSAGE);
        } else {
            automatic.reminder().ifPresent(host.status());
        }
        repeater.every(FIRST_CHECK, CHECK_EVERY, this::automaticTick);
    }

    /** Runs on the background thread: an automatic backup when one is due. */
    void automaticTick() {
        if (busy) {
            return;
        }
        AutomaticBackupScheduler.Result result = automatic.runIfDue();
        if (!result.message().isBlank() && host != null) {
            swing.execute(() -> host.status().accept(result.message()));
        }
    }

    /** File > Back up now. */
    public void backUpNow() {
        if (host == null) {
            return;
        }
        if (busy) {
            message(BackupMessages.BUSY, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path folder = preferences.load().folder().orElse(backups.settings().defaultFolder());
        Object[] options = {"Back up now", "Choose another folder…", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(),
                "Save a checked backup of your farm records to:\n" + folder
                        + "\n\nYou can keep working while it runs.",
                TITLE, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 1) {
            Optional<Path> chosen = host.files().chooseDirectory(host.frame(), "Choose where to save the backup",
                    folder.toFile()).map(file -> file.toPath());
            if (chosen.isEmpty()) {
                return;
            }
            folder = chosen.get();
        } else if (choice != 0) {
            return;
        }
        if (backups.settings().insideRecords(folder)) {
            message(BackupMessages.FOLDER_INSIDE_RECORDS, JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path target = folder;
        busy = true;
        host.status().accept("Backing up farm records…");
        background.execute(() -> {
            try {
                VerifiedBackup backup = backups.backUp(target, MANUAL_LABEL);
                remember(preferences.load().withBackupAt(clock.instant(), false));
                activity.record(BackupActivityLog.BACKUP_CREATED, true, "Backup saved and checked: "
                        + backup.archive());
                swing.execute(() -> {
                    busy = false;
                    host.status().accept("Backup saved and checked: " + backup.archive().getFileName());
                    message(BackupMessages.backupSaved(backup), JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (BackupException failed) {
                activity.record(BackupActivityLog.BACKUP_FAILED, false, failed.getMessage());
                swing.execute(() -> {
                    busy = false;
                    host.status().accept("The backup did not finish. Your farm records are unchanged.");
                    message(BackupMessages.backupFailed(failed.getMessage()), JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    /** File > Restore from a backup. */
    public void restoreFromBackup() {
        if (host == null) {
            return;
        }
        if (busy) {
            message(BackupMessages.BUSY, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (updateInProgress.getAsBoolean()) {
            message(BackupMessages.UPDATE_IN_PROGRESS, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Optional<Path> archive = chooseBackup();
        if (archive.isEmpty()) {
            return;
        }
        busy = true;
        host.status().accept("Checking the backup…");
        Path chosen = archive.get();
        background.execute(() -> {
            try {
                BackupDetails details = restores.inspect(chosen);
                swing.execute(() -> confirmRestore(details));
            } catch (BackupException notRestorable) {
                swing.execute(() -> {
                    busy = false;
                    host.status().accept("That backup cannot be restored. Nothing was changed.");
                    message(notRestorable.getMessage(), JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private Optional<Path> chooseBackup() {
        List<BackupFile> found = knownBackups();
        Path browseFrom = preferences.load().folder().orElse(backups.settings().defaultFolder());
        if (!found.isEmpty()) {
            BackupFile newest = found.get(0);
            Object[] options = {"Restore the newest backup", "Choose a different backup…", "Cancel"};
            int choice = host.dialogs().showOption(host.frame(),
                    "The newest backup was made on " + BackupMessages.when(newest.madeAt()) + ":\n"
                            + newest.archive() + "\n\nWhich backup do you want to restore?",
                    TITLE, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
                    options[0]);
            if (choice == 0) {
                return Optional.of(newest.archive());
            }
            if (choice != 1) {
                return Optional.empty();
            }
            Path parent = newest.archive().getParent();
            browseFrom = parent == null ? browseFrom : parent;
        } else {
            message(BackupMessages.NO_BACKUPS_FOUND, JOptionPane.INFORMATION_MESSAGE);
        }
        return host.files().chooseBackup(host.frame(), "Choose the backup to restore", browseFrom.toFile())
                .map(file -> file.toPath());
    }

    /** Backups with check files in the automatic folder and the usual backups folder, newest first. */
    List<BackupFile> knownBackups() {
        Map<Path, BackupFile> unique = new LinkedHashMap<>();
        List<Path> folders = new ArrayList<>();
        preferences.load().folder().ifPresent(folders::add);
        folders.add(backups.settings().defaultFolder());
        for (Path folder : folders) {
            for (BackupFile file : BackupFile.list(folder)) {
                if (file.hasChecksum()) {
                    unique.putIfAbsent(file.archive().toAbsolutePath().normalize(), file);
                }
            }
        }
        List<BackupFile> all = new ArrayList<>(unique.values());
        all.sort((left, right) -> {
            int byTime = right.madeAt().compareTo(left.madeAt());
            return byTime != 0 ? byTime : Integer.compare(right.counter(), left.counter());
        });
        return all;
    }

    private void confirmRestore(BackupDetails details) {
        Path safetyFolder = preferences.load().folder().orElse(backups.settings().defaultFolder());
        Object[] options = {"Restore and reopen", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(), BackupMessages.confirmRestore(details, safetyFolder),
                TITLE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (choice != 0 || !host.confirmClose().getAsBoolean()) {
            busy = false;
            host.status().accept("Restore cancelled. Nothing was changed.");
            return;
        }
        host.status().accept("Saving a backup of your current records first…");
        String madeAt = details.madeAt().map(BackupMessages::when).orElse("");
        background.execute(() -> {
            try {
                VerifiedBackup safety = backups.backUp(safetyFolder, SAFETY_LABEL);
                remember(preferences.load().withBackupAt(clock.instant(), false));
                restoreRecords.saveRequest(new RestoreRecords.Request(details.archive(), details.sha256(),
                        Optional.of(safety.archive()), madeAt));
                activity.record(BackupActivityLog.RESTORE_REQUESTED, true, "Restoring " + details.archive()
                        + "; the records from before are saved in " + safety.archive());
                swing.execute(this::closeAndReopen);
            } catch (BackupException | IOException failed) {
                restoreRecords.clear(RestoreRecords.REQUEST);
                swing.execute(() -> {
                    busy = false;
                    host.status().accept("Restore cancelled. Nothing was changed.");
                    message("The restore did not start, because the backup of your current records did not "
                            + "finish: " + failed.getMessage() + "\n\nNothing was changed.",
                            JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void closeAndReopen() {
        boolean canReopen = canRelaunch.getAsBoolean();
        if (!canReopen) {
            message(BackupMessages.restartManually(), JOptionPane.INFORMATION_MESSAGE);
        }
        host.beforeClose().run();
        shutdown.run();
        if (canReopen) {
            relaunch.getAsBoolean();
        }
        exit.accept(0);
    }

    /** File > Automatic backups. */
    public void automaticBackups() {
        if (host == null) {
            return;
        }
        BackupPreferences current = preferences.load();
        BackupSettingsForm form = new BackupSettingsForm(current, backups.settings().defaultFolder(), host.files());
        Object[] options = {"Save", "Cancel"};
        for (int attempt = 0; attempt < 5; attempt++) {
            int choice = host.dialogs().showOption(host.frame(), form.panel(), "Automatic backups",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice != 0) {
                return;
            }
            Optional<Path> folder = form.folder();
            if (form.automatic() && folder.isEmpty()) {
                message(BackupMessages.CHOOSE_FOLDER_FIRST, JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (folder.isPresent() && backups.settings().insideRecords(folder.get())) {
                message(BackupMessages.FOLDER_INSIDE_RECORDS, JOptionPane.WARNING_MESSAGE);
                continue;
            }
            BackupPreferences chosen = preferences.load().withChoices(form.automatic(), folder, form.schedule(),
                    form.keep());
            if (!remember(chosen)) {
                message("Your choices could not be saved. Please try again.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            host.status().accept(chosen.automatic() ? BackupMessages.automaticOn(chosen)
                    : BackupMessages.automaticOff());
            if (chosen.automatic()) {
                background.execute(this::automaticTick);
            }
            return;
        }
    }

    boolean busy() {
        return busy;
    }

    private boolean remember(BackupPreferences updated) {
        try {
            preferences.save(updated);
            return true;
        } catch (IOException cannotSave) {
            return false;
        }
    }

    private void message(String text, int type) {
        host.dialogs().showMessage(host.frame(), text, TITLE, type);
    }

    private static boolean hasInstalledLauncher() {
        String launcher = System.getProperty("jpackage.app-path");
        try {
            return launcher != null && !launcher.isBlank()
                    && InstalledAppLauncher.canRelaunch(Path.of(launcher));
        } catch (IllegalArgumentException unavailable) {
            return false;
        }
    }

    private static boolean updateInProgress(UpdateSettings settings) {
        if (settings == null) {
            return false;
        }
        return new UpdateStateStore(settings.updatesDirectory()).load().phase() != UpdateState.Phase.IDLE;
    }

    private static boolean relaunchInstalledApp() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return false;
        }
        try {
            InstalledAppLauncher.relaunch(Path.of(launcher));
            return true;
        } catch (IOException | IllegalArgumentException cannotStart) {
            return false;
        }
    }

    /** One daemon thread for every backup job, created only when first used. */
    private enum BackgroundThread {
        INSTANCE;

        private ScheduledExecutorService executor;

        synchronized ScheduledExecutorService executor() {
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "flowersforever-backups");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }
}

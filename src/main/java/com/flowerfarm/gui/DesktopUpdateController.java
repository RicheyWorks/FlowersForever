package com.flowerfarm.gui;

import com.flowerfarm.update.UpdateCheckResult;
import com.flowerfarm.update.UpdateCoordinator;
import com.flowerfarm.update.UpdateFirstLaunchVerifier;
import com.flowerfarm.update.UpdateLaunchGuard;
import com.flowerfarm.update.UpdateMessages;
import com.flowerfarm.update.UpdateVerificationException;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Desktop side of automatic updates: the launch check, Help > Check for updates,
 * and the Update now / Later / Install now prompts. Network and disk work runs
 * on one background thread; dialogs run on the Swing thread through
 * {@link FarmDialogs}. Offline and "nothing new" stay silent on launch.
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopUpdateController {

    static final String TITLE = "FlowersForever updates";

    /** Frame-level hooks provided by {@link FlowerFarmGUI}. */
    public record Host(JFrame frame, FarmDialogs dialogs, Consumer<String> status,
                       BooleanSupplier confirmClose, Runnable beforeClose) {
    }

    private final UpdateCoordinator coordinator;
    private final ObjectProvider<UpdateLaunchGuard> guard;
    private final UpdateCoordinator.DatabaseShutdown shutdown;
    private final Executor background;
    private final Executor swing;
    private final IntConsumer exit;
    private final Runnable relaunch;
    private Host host;
    private volatile boolean busy;

    @Autowired
    public DesktopUpdateController(UpdateCoordinator coordinator, ObjectProvider<UpdateLaunchGuard> guard,
                                   ConfigurableApplicationContext context, DataSource dataSource) {
        this(coordinator, guard, () -> {
            UpdateFirstLaunchVerifier.shutdownEmbeddedDatabase(dataSource);
            context.close();
        }, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "flowersforever-updates");
            thread.setDaemon(true);
            return thread;
        }), SwingUtilities::invokeLater, System::exit, () -> relaunchInstalledApp(coordinator));
    }

    DesktopUpdateController(UpdateCoordinator coordinator, ObjectProvider<UpdateLaunchGuard> guard,
                            UpdateCoordinator.DatabaseShutdown shutdown, Executor background, Executor swing,
                            IntConsumer exit, Runnable relaunch) {
        this.coordinator = coordinator;
        this.guard = guard;
        this.shutdown = shutdown;
        this.background = background;
        this.swing = swing;
        this.exit = exit;
        this.relaunch = relaunch;
    }

    /** Called once the main window is visible. */
    public void attach(Host frameHost) {
        this.host = frameHost;
        UpdateLaunchGuard launchGuard = guard.getIfAvailable();
        if (launchGuard != null) {
            launchGuard.takeNotice().ifPresent(notice -> host.dialogs().showMessage(host.frame(), notice, TITLE,
                    notice.contains("did not") || notice.contains("not installed")
                            ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE));
        }
        if (coordinator.settings().checkOnLaunch()) {
            check(false);
        }
    }

    /** Help > Check for updates. */
    public void checkNow() {
        check(true);
    }

    private void check(boolean manual) {
        if (host == null || busy) {
            return;
        }
        if (manual) {
            host.status().accept("Checking for updates…");
        }
        background.execute(() -> {
            UpdateCheckResult result = coordinator.check(manual);
            swing.execute(() -> showResult(result, manual));
        });
    }

    void showResult(UpdateCheckResult result, boolean manual) {
        switch (result.status()) {
            case UPDATE_AVAILABLE -> offer(result);
            case MANUAL_INSTALL_REQUIRED, PAUSED -> {
                host.status().accept(firstLine(result.message()));
                if (manual) {
                    host.dialogs().showMessage(host.frame(), result.message(), TITLE, JOptionPane.INFORMATION_MESSAGE);
                }
            }
            case REJECTED -> {
                host.status().accept("Update information was ignored because it was not signed correctly.");
                if (manual) {
                    host.dialogs().showMessage(host.frame(), result.message(), TITLE, JOptionPane.WARNING_MESSAGE);
                }
            }
            default -> {
                if (manual) {
                    host.status().accept(firstLine(result.message()));
                    showWithAutomaticToggle(result.message());
                }
            }
        }
    }

    private void offer(UpdateCheckResult result) {
        String version = result.release().map(release -> release.version().toString()).orElse("");
        host.status().accept("FlowersForever " + version + " is ready to install.");
        if (!coordinator.canInstall()) {
            host.dialogs().showMessage(host.frame(), result.message() + "\n\n" + UpdateMessages.CANNOT_INSTALL_HERE,
                    TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object[] options = {"Update now", "Later"};
        int choice = host.dialogs().showOption(host.frame(), result.message(), "New version ready",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            host.status().accept("Update postponed. FlowersForever will remind you next time.");
            return;
        }
        busy = true;
        host.status().accept("Downloading FlowersForever " + version + "…");
        background.execute(() -> {
            try {
                long expected = result.packageInfo().map(info -> info.size()).orElse(1L);
                int[] lastPercent = {-1};
                UpdateCoordinator.PreparedUpdate prepared = coordinator.prepare(result, bytes -> {
                    int percent = (int) Math.min(100, bytes * 100 / Math.max(1, expected));
                    if (percent / 10 != lastPercent[0] / 10) {
                        lastPercent[0] = percent;
                        swing.execute(() -> host.status().accept("Downloading update… " + percent + "%"));
                    }
                });
                swing.execute(() -> confirmInstall(prepared));
            } catch (UpdateVerificationException rejected) {
                busy = false;
                swing.execute(() -> {
                    host.status().accept("Update not installed.");
                    host.dialogs().showMessage(host.frame(), rejected.getMessage(), TITLE, JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }

    private void confirmInstall(UpdateCoordinator.PreparedUpdate prepared) {
        Object[] options = {"Install now", "Later"};
        int choice = host.dialogs().showOption(host.frame(), UpdateMessages.readyToInstall(prepared.next().version()),
                "Ready to install", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                options, options[0]);
        if (choice != 0 || !host.confirmClose().getAsBoolean()) {
            busy = false;
            host.status().accept("Update downloaded. It will be offered again next time you open FlowersForever.");
            return;
        }
        host.beforeClose().run();
        if (host.frame() != null) {
            host.frame().setVisible(false);
        }
        background.execute(() -> {
            UpdateCoordinator.InstallOutcome outcome = coordinator.install(prepared, shutdown);
            if (outcome.handedOff()) {
                exit.accept(0);
                return;
            }
            swing.execute(() -> {
                busy = false;
                if (outcome.databaseClosed()) {
                    host.dialogs().showMessage(null, outcome.message() + "\n\nFlowersForever will now reopen.",
                            TITLE, JOptionPane.WARNING_MESSAGE);
                    relaunch.run();
                    exit.accept(1);
                } else {
                    if (host.frame() != null) {
                        host.frame().setVisible(true);
                    }
                    host.dialogs().showMessage(host.frame(), outcome.message(), TITLE, JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }

    private void showWithAutomaticToggle(String message) {
        boolean automatic = coordinator.settings().automaticChecksEnabled();
        String toggle = automatic ? "Turn off automatic checks" : "Turn on automatic checks";
        Object[] options = {"OK", toggle};
        String detail = message + "\n\nAutomatic checks when FlowersForever opens are "
                + (automatic ? "on" : "off") + ". The only information sent is a request for the update list.";
        int choice = host.dialogs().showOption(host.frame(), detail, TITLE, JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 1) {
            try {
                coordinator.settings().setAutomaticChecksEnabled(!automatic);
                host.status().accept(automatic ? "Automatic update checks turned off."
                        : "Automatic update checks turned on.");
            } catch (IOException failure) {
                host.status().accept("The update setting could not be saved.");
            }
        }
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static void relaunchInstalledApp(UpdateCoordinator coordinator) {
        Optional<Path> launcher = coordinator.settings().launcherPath();
        if (launcher.isEmpty()) {
            return;
        }
        try {
            Path directory = launcher.get().toAbsolutePath().getParent();
            ProcessBuilder builder = new ProcessBuilder(List.of(launcher.get().toString()));
            if (directory != null) {
                builder.directory(directory.toFile());
            }
            builder.start();
        } catch (IOException ignored) {
            // The farm can reopen FlowersForever from the Start menu.
        }
    }
}

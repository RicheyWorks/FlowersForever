package com.flowerfarm.gui;

import com.flowerfarm.service.GrowerReminder;
import com.flowerfarm.service.GrowerReminderService;
import com.flowerfarm.service.GrowerReminders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Help > Today’s reminders…, startup tray nudge, and dashboard banner refresh.
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopRemindersController {

    public static final String PREF_TRAY = "reminders.tray";
    public static final String PREF_LAST_TRAY_DATE = "reminders.lastTrayDate";
    public static final String PREF_BANNER_HIDDEN_DATE = "reminders.bannerHiddenDate";

    /** Frame-level hooks from {@link FlowerFarmGUI}. */
    public record Host(JFrame frame, FarmDialogs dialogs, java.util.function.Consumer<String> status,
                       java.util.function.Consumer<String> selectTab,
                       Runnable showHarvestEntry) {
    }

    private final GrowerReminderService reminders;
    private final FarmNotifier notifier;
    private final Preferences prefs;
    private Host host;
    private RemindersPanel dashboardBanner;

    @Autowired
    public DesktopRemindersController(GrowerReminderService reminders) {
        this(reminders, FarmNotifier.systemTray(),
                Preferences.userNodeForPackage(DesktopRemindersController.class));
    }

    DesktopRemindersController(GrowerReminderService reminders, FarmNotifier notifier,
                               Preferences prefs) {
        this.reminders = Objects.requireNonNull(reminders, "reminders");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.prefs = Objects.requireNonNull(prefs, "prefs");
    }

    public void attach(Host frameHost) {
        this.host = Objects.requireNonNull(frameHost, "frameHost");
    }

    public void attachDashboardBanner(RemindersPanel banner) {
        this.dashboardBanner = banner;
        refreshBanner();
    }

    public void refreshBanner() {
        if (dashboardBanner == null) {
            return;
        }
        GrowerReminders.Digest digest = reminders.collect();
        String hidden = prefs.get(PREF_BANNER_HIDDEN_DATE, "");
        boolean hide = digest.date().toString().equals(hidden);
        dashboardBanner.showDigest(digest);
        dashboardBanner.setVisible(!hide && !digest.isEmpty());
    }

    public void hideBannerForToday() {
        GrowerReminders.Digest digest = reminders.collect();
        prefs.put(PREF_BANNER_HIDDEN_DATE, digest.date().toString());
        if (dashboardBanner != null) {
            dashboardBanner.setVisible(false);
        }
        if (host != null) {
            host.status().accept("Reminders banner hidden until tomorrow. Help > Today’s reminders… still works.");
        }
    }

    public void showToday() {
        if (host == null) {
            return;
        }
        GrowerReminders.Digest digest = reminders.collect();
        RemindersPanel panel = new RemindersPanel(this::openReminder, () -> { });
        panel.showDigest(digest);
        Object[] options = {"Close"};
        host.dialogs().showOption(host.frame(), panel, RemindersPanel.TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        refreshBanner();
    }

    /** Once per day tray balloon when reminders are waiting (no modal popup). */
    public void maybeNotifyOnStartup() {
        if (host == null || !prefs.getBoolean(PREF_TRAY, true)) {
            return;
        }
        GrowerReminders.Digest digest = reminders.collect();
        refreshBanner();
        if (digest.isEmpty()) {
            host.status().accept("Reminders: you’re caught up for today.");
            return;
        }
        String today = digest.date().toString();
        if (today.equals(prefs.get(PREF_LAST_TRAY_DATE, ""))) {
            host.status().accept("Reminders: " + digest.items().size()
                    + " waiting — Help > Today’s reminders…");
            return;
        }
        notifier.notify("FlowersForever reminders", GrowerReminders.traySummary(digest));
        prefs.put(PREF_LAST_TRAY_DATE, today);
        host.status().accept("Reminders: " + digest.items().size()
                + " waiting — Help > Today’s reminders…");
    }

    public void openReminder(GrowerReminder reminder) {
        if (host == null || reminder == null) {
            return;
        }
        if ("Harvest Log".equals(reminder.targetTab())) {
            host.showHarvestEntry().run();
        } else {
            host.selectTab().accept(reminder.targetTab());
        }
        host.status().accept("Opened " + reminder.targetTab() + " · " + reminder.title());
    }

    GrowerReminders.Digest peek(LocalDate date) {
        return reminders.collect(date);
    }
}

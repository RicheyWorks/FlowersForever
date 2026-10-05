package com.flowerfarm.gui;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/** Replaceable desktop notification boundary (tray balloon when the OS allows). */
@FunctionalInterface
public interface FarmNotifier {

    void notify(String title, String message);

    static FarmNotifier none() {
        return (title, message) -> { };
    }

    /**
     * Shows a short tray balloon when a system tray is available. Silent no-op in
     * headless environments or when the tray is unsupported.
     */
    static FarmNotifier systemTray() {
        return (title, message) -> {
            if (!SystemTray.isSupported()) {
                return;
            }
            try {
                SystemTray tray = SystemTray.getSystemTray();
                TrayIcon icon = existingOrAdd(tray);
                if (icon != null) {
                    icon.displayMessage(
                            title == null ? "FlowersForever" : title,
                            message == null ? "" : message,
                            TrayIcon.MessageType.INFO);
                }
            } catch (Exception ignored) {
                // Tray access is best-effort; in-app reminders still work.
            }
        };
    }

    private static TrayIcon existingOrAdd(SystemTray tray) throws Exception {
        for (TrayIcon icon : tray.getTrayIcons()) {
            if ("FlowersForever".equals(icon.getToolTip())) {
                return icon;
            }
        }
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xFF2E7D32);
            }
        }
        TrayIcon icon = new TrayIcon(image, "FlowersForever");
        icon.setImageAutoSize(true);
        tray.add(icon);
        return icon;
    }
}

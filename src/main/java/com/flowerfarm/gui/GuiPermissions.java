package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.TabHost;

import javax.swing.*;
import java.awt.*;

/**
 * Shared GUI permission checks for VIEWER / HAND / OWNER barn roles.
 */
public final class GuiPermissions {

    /** Tooltip applied to disabled write controls for VIEWER sessions. */
    public static final String VIEWER_READONLY_TIP =
            "VIEWER role is read-only — sign in as HAND or OWNER to edit.";

    public static final String OWNER_CLEAR_TIP =
            "Only OWNER can clear the activity history.";

    private GuiPermissions() {}

    /**
     * @return true if the user may mutate data; false if blocked (dialog shown).
     */
    public static boolean requireWrite(TabHost host, Component parent, String actionLabel) {
        if (host != null && host.canMutateData()) {
            return true;
        }
        // Headless component tests and isolated views have no authenticated host.
        if (host == null) {
            return true;
        }
        String action = actionLabel == null || actionLabel.isBlank() ? "this action" : actionLabel;
        JOptionPane.showMessageDialog(parent,
                "Your role is read-only (VIEWER).\n"
                        + "Sign in as HAND or OWNER to " + action + ".\n\n"
                        + "Session: " + host.sessionDisplayName(),
                "Permission denied",
                JOptionPane.WARNING_MESSAGE);
        host.setStatus("Blocked: VIEWER cannot " + action + ".");
        return false;
    }

    public static boolean requireOwnerClear(TabHost host, Component parent) {
        if (host != null && host.canClearHistory()) {
            return true;
        }
        if (host == null) {
            return true;
        }
        JOptionPane.showMessageDialog(parent,
                "Only OWNER can clear the activity history.\n"
                        + "Session: " + host.sessionDisplayName(),
                "Permission denied",
                JOptionPane.WARNING_MESSAGE);
        host.setStatus("Only OWNER can clear activity history.");
        return false;
    }

    /**
     * Enable/disable write controls and attach a read-only tip when disabled.
     * Search, filter, refresh, and export controls should not use this helper.
     */
    public static void setWritable(boolean canWrite, Component... components) {
        setWritable(canWrite, VIEWER_READONLY_TIP, components);
    }

    public static void setWritable(boolean canWrite, String disabledTip, Component... components) {
        if (components == null) {
            return;
        }
        String tip = disabledTip == null || disabledTip.isBlank() ? VIEWER_READONLY_TIP : disabledTip;
        for (Component c : components) {
            if (c == null) {
                continue;
            }
            c.setEnabled(canWrite);
            if (c instanceof JComponent jc) {
                if (!canWrite) {
                    jc.putClientProperty("flowerfarm.savedTip", jc.getToolTipText());
                    jc.setToolTipText(tip);
                } else {
                    Object saved = jc.getClientProperty("flowerfarm.savedTip");
                    if (saved instanceof String s) {
                        jc.setToolTipText(s.isBlank() ? null : s);
                    } else if (tip.equals(jc.getToolTipText())) {
                        jc.setToolTipText(null);
                    }
                    jc.putClientProperty("flowerfarm.savedTip", null);
                }
            }
        }
    }
}

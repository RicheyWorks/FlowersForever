package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmSession;
import com.flowerfarm.gui.tabs.TabHost;

import javax.swing.*;
import java.awt.*;

/**
 * Shared GUI permission checks for VIEWER / HAND / OWNER barn roles.
 */
public final class GuiPermissions {

    private GuiPermissions() {}

    /**
     * @return true if the user may mutate data; false if blocked (dialog shown).
     */
    public static boolean requireWrite(TabHost host, Component parent, String actionLabel) {
        if (host != null && host.canMutateData()) {
            return true;
        }
        // Auth off → TabHost defaults true; when host is null, fall back to session
        if (host == null && FarmSession.canMutateData()) {
            return true;
        }
        String action = actionLabel == null || actionLabel.isBlank() ? "this action" : actionLabel;
        JOptionPane.showMessageDialog(parent,
                "Your role is read-only (VIEWER).\n"
                        + "Sign in as HAND or OWNER to " + action + ".\n\n"
                        + "Session: " + FarmSession.displayName(),
                "Permission denied",
                JOptionPane.WARNING_MESSAGE);
        if (host != null) {
            host.setStatus("Blocked: VIEWER cannot " + action + ".");
        }
        return false;
    }

    public static boolean requireOwnerClear(TabHost host, Component parent) {
        if (host != null && host.canClearHistory()) {
            return true;
        }
        if (host == null && FarmSession.canClearHistory()) {
            return true;
        }
        JOptionPane.showMessageDialog(parent,
                "Only OWNER can clear the audit history.\n"
                        + "Session: " + FarmSession.displayName(),
                "Permission denied",
                JOptionPane.WARNING_MESSAGE);
        if (host != null) {
            host.setStatus("Blocked: only OWNER can clear audit history.");
        }
        return false;
    }
}

package com.flowerfarm.gui;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Boundary for native Swing choices that must remain modal in the installed app
 * but deterministic in accessibility-driven user journeys.
 */
public interface FarmDialogs {

    int showOption(Component parent, Object message, String title,
                   int optionType, int messageType, Icon icon,
                   Object[] options, Object initialValue);

    int showConfirm(Component parent, Object message, String title,
                    int optionType, int messageType);

    void showMessage(Component parent, Object message, String title, int messageType);

    /** Uses the normal installed-app Swing dialogs. */
    static FarmDialogs swing() {
        return SwingDialogs.INSTANCE;
    }

    enum SwingDialogs implements FarmDialogs {
        INSTANCE;

        @Override
        public int showOption(Component parent, Object message, String title,
                              int optionType, int messageType, Icon icon,
                              Object[] options, Object initialValue) {
            return JOptionPane.showOptionDialog(parent, message, title, optionType,
                    messageType, icon, options, initialValue);
        }

        @Override
        public int showConfirm(Component parent, Object message, String title,
                               int optionType, int messageType) {
            return JOptionPane.showConfirmDialog(
                    parent, message, title, optionType, messageType);
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            JOptionPane.showMessageDialog(parent, message, title, messageType);
        }
    }
}

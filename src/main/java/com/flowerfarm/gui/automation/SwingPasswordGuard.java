package com.flowerfarm.gui.automation;

import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import java.awt.Component;
import java.awt.Container;

/** Keeps composed password controls behind the same boundary as standalone fields. */
final class SwingPasswordGuard {
    private SwingPasswordGuard() { }

    static void requireNoPasswordEditor(JComboBox<?> combo) {
        if (hasPasswordEditor(combo)) {
            throw rejected();
        }
    }

    static boolean hasPasswordEditor(JComboBox<?> combo) {
        return combo.isEditable() && combo.getEditor() != null
                && containsPassword(combo.getEditor().getEditorComponent());
    }

    static void requireNoPassword(Component component) {
        if (containsPassword(component)) {
            throw rejected();
        }
    }

    private static boolean containsPassword(Component component) {
        if (component instanceof JPasswordField) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsPassword(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException(
                "Password controls are never accessible through the automation harness");
    }
}

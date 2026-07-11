package com.flowerfarm.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GuiPermissions writable styling")
class GuiPermissionsTest {

    @Test
    @DisplayName("setWritable(false) disables controls and sets VIEWER tip")
    void disablesWithTip() {
        JButton btn = new JButton("Add");
        btn.setToolTipText("Adds a harvest row");
        JTextField field = new JTextField("x");

        GuiPermissions.setWritable(false, btn, field);

        assertThat(btn.isEnabled()).isFalse();
        assertThat(field.isEnabled()).isFalse();
        assertThat(btn.getToolTipText()).isEqualTo(GuiPermissions.VIEWER_READONLY_TIP);
        assertThat(field.getToolTipText()).isEqualTo(GuiPermissions.VIEWER_READONLY_TIP);
    }

    @Test
    @DisplayName("setWritable(true) restores enabled state and original tip")
    void restoresWritable() {
        JButton btn = new JButton("Save");
        btn.setToolTipText("Persist edits");

        GuiPermissions.setWritable(false, btn);
        GuiPermissions.setWritable(true, btn);

        assertThat(btn.isEnabled()).isTrue();
        assertThat(btn.getToolTipText()).isEqualTo("Persist edits");
    }

    @Test
    @DisplayName("null components are ignored")
    void nullSafe() {
        GuiPermissions.setWritable(false, (java.awt.Component) null);
        GuiPermissions.setWritable(true);
    }
}

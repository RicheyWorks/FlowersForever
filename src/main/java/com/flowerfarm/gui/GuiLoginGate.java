package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmSession;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.auth.FarmUserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * Optional desktop login for shared barn PCs (multi-user + roles).
 * Active when {@code flowerfarm.auth.enabled=true}.
 */
@Component
public class GuiLoginGate {

    private final boolean enabled;
    private final FarmUserDirectory directory;

    public GuiLoginGate(
            @Value("${flowerfarm.auth.enabled:false}") boolean enabled,
            FarmUserDirectory directory) {
        this.enabled = enabled;
        this.directory = directory;
        FarmSession.setAuthEnabled(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return true if auth is disabled or credentials accepted; false if cancelled / failed out.
     */
    public boolean promptUntilAuthenticatedOrCancel() {
        FarmSession.setAuthEnabled(enabled);
        if (!enabled) {
            FarmSession.clear();
            return true;
        }

        JPanel panel = buildLoginPanel();
        JTextField userField = (JTextField) panel.getClientProperty("userField");
        JPasswordField passField = (JPasswordField) panel.getClientProperty("passField");

        for (int attempt = 1; attempt <= 3; attempt++) {
            int result = JOptionPane.showConfirmDialog(
                    null, panel, "FlowersForever · barn login (" + attempt + "/3)",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                FarmSession.clear();
                return false;
            }
            String u = userField.getText() == null ? "" : userField.getText().trim();
            char[] p = passField.getPassword();
            Optional<FarmUser> user = directory.authenticate(u, p);
            Arrays.fill(p, '\0');
            passField.setText("");
            if (user.isPresent()) {
                FarmSession.set(user.get());
                return true;
            }
            JOptionPane.showMessageDialog(null,
                    "Invalid username or password.\nAttempts left: " + (3 - attempt),
                    "Login failed", JOptionPane.ERROR_MESSAGE);
            userField.requestFocusInWindow();
        }
        FarmSession.clear();
        return false;
    }

    /**
     * Switch user without restarting the app (re-prompt). Returns false if cancelled.
     */
    public boolean promptSwitchUser() {
        if (!enabled) {
            return true;
        }
        FarmSession.clear();
        return promptUntilAuthenticatedOrCancel();
    }

    private JPanel buildLoginPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 4, 8));

        JLabel title = new JLabel("<html><b>Kitsap barn login</b><br/>"
                + "<span style='font-size:10px;color:gray'>Roles: Owner · Hand · Viewer</span></html>");
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField userField = new JTextField(18);
        JPasswordField passField = new JPasswordField(18);
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        form.add(new JLabel("Username"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(userField, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        form.add(new JLabel("Password"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(passField, c);
        root.add(form, BorderLayout.CENTER);

        JTextArea roles = new JTextArea(
                "OWNER — full access (clear audit)\n"
                        + "HAND  — inventory, harvest, CRM, connectors\n"
                        + "VIEWER — read-only (no writes)\n\n"
                        + "Defaults: farm/kitsap · hand/harvest · viewer/view");
        roles.setEditable(false);
        roles.setOpaque(false);
        roles.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        roles.setBorder(BorderFactory.createTitledBorder("Role guide"));
        root.add(roles, BorderLayout.SOUTH);

        root.putClientProperty("userField", userField);
        root.putClientProperty("passField", passField);
        return root;
    }

    /** Demo helper: list usernames for status tip (no passwords). */
    public String accountHint() {
        if (!enabled) {
            return "auth off";
        }
        StringBuilder sb = new StringBuilder();
        for (FarmUser u : directory.listUsers()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(u.username()).append('=').append(u.role().label());
        }
        return sb.toString();
    }
}

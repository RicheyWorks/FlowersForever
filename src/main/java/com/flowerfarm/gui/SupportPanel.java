package com.flowerfarm.gui;

import com.flowerfarm.support.SupportGuide;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * In-app support page: promise, changeable support email, diagnostics, copy or mailto.
 */
public final class SupportPanel extends JPanel {

    private final JTextField email = new JTextField(28);
    private final JTextArea details = new JTextArea();
    private final JLabel feedback = FarmUi.mutedLabel(" ");
    private final String version;
    private final String diagnostics;
    private final FarmClipboard clipboard;
    private final FarmMailer mailer;
    private final Consumer<String> status;

    public SupportPanel(String supportEmail, String appVersion, String diagnosticsText,
                        FarmClipboard clipboard, FarmMailer mailer, Consumer<String> status) {
        this.version = Objects.requireNonNullElse(appVersion, "unknown");
        this.diagnostics = Objects.requireNonNullElse(diagnosticsText, "");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.mailer = Objects.requireNonNull(mailer, "mailer");
        this.status = Objects.requireNonNullElse(status, ignored -> { });

        setName("support.panel");
        setLayout(new BorderLayout(0, FarmUi.SECTION_GAP));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        getAccessibleContext().setAccessibleName(SupportGuide.TITLE);

        email.setName("support.email");
        email.setText(supportEmail == null ? "" : supportEmail);
        email.getAccessibleContext().setAccessibleName("Support email");

        details.setName("support.diagnostics");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setText(this.diagnostics);
        details.getAccessibleContext().setAccessibleName("Version and computer details");
        JScrollPane scroll = new JScrollPane(details);
        scroll.setPreferredSize(new Dimension(460, 110));

        feedback.setName("support.feedback");

        JButton copy = FarmUi.secondaryButton("Copy details");
        copy.setName("support.copy");
        copy.setToolTipText("Copy the support address, subject, and version details to paste into email.");
        copy.addActionListener(event -> copyDetails());

        JButton openMail = FarmUi.primaryButton("Open email draft…");
        openMail.setName("support.openMail");
        openMail.setToolTipText("Open a prefilled message in your email app.");
        openMail.addActionListener(event -> openEmailDraft());

        JPanel emailRow = new JPanel(new GridBagLayout());
        emailRow.setOpaque(false);
        emailRow.setName("support.emailRow");
        GridBagConstraints at = new GridBagConstraints();
        at.insets = new Insets(2, 0, 2, 8);
        at.anchor = GridBagConstraints.WEST;
        at.gridx = 0;
        at.gridy = 0;
        emailRow.add(new JLabel("Support email:"), at);
        at.gridx = 1;
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        emailRow.add(email, at);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        actions.setName("support.actions");
        actions.add(openMail);
        actions.add(copy);

        JPanel body = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        body.setOpaque(false);
        body.add(emailRow, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        south.add(feedback, BorderLayout.SOUTH);
        body.add(south, BorderLayout.SOUTH);

        add(FarmUi.section(SupportGuide.TITLE, SupportGuide.pageGuidance(), body), BorderLayout.CENTER);
    }

    public String email() {
        return email.getText() == null ? "" : email.getText().trim();
    }

    void setEmail(String value) {
        email.setText(value == null ? "" : value);
    }

    String feedbackText() {
        return feedback.getText();
    }

    private void copyDetails() {
        String subject = SupportGuide.emailSubject(version);
        String body = SupportGuide.emailBody(diagnostics);
        String payload = SupportGuide.clipboardPayload(email(), subject, body);
        clipboard.copyText(payload);
        announce(SupportGuide.statusCopied());
    }

    private void openEmailDraft() {
        String address = SupportGuide.normalizeEmail(email());
        if (!SupportGuide.looksLikeEmail(address)) {
            announce(SupportGuide.statusBadEmail());
            return;
        }
        String subject = SupportGuide.emailSubject(version);
        String body = SupportGuide.emailBody(diagnostics);
        try {
            URI mailto = SupportGuide.mailtoUri(address, subject, body);
            mailer.openMailto(mailto);
            announce(SupportGuide.statusMailOpened());
        } catch (Exception failed) {
            clipboard.copyText(SupportGuide.clipboardPayload(address, subject, body));
            announce(SupportGuide.statusMailUnavailable());
        }
    }

    private void announce(String message) {
        feedback.setText(message);
        status.accept(message);
    }
}

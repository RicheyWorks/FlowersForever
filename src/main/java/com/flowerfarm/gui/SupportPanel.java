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
 * In-app support page: GitHub Issues, copy diagnostics, optional support email.
 */
public final class SupportPanel extends JPanel {

    private final JTextField email = new JTextField(28);
    private final JTextArea details = new JTextArea();
    private final JLabel feedback = FarmUi.mutedLabel(" ");
    private final String version;
    private final String diagnostics;
    private final FarmClipboard clipboard;
    private final FarmMailer mailer;
    private final FarmBrowser browser;
    private final Consumer<String> status;

    public SupportPanel(String supportEmail, String appVersion, String diagnosticsText,
                        FarmClipboard clipboard, FarmMailer mailer, Consumer<String> status) {
        this(supportEmail, appVersion, diagnosticsText, clipboard, mailer, FarmBrowser.none(), status);
    }

    public SupportPanel(String supportEmail, String appVersion, String diagnosticsText,
                        FarmClipboard clipboard, FarmMailer mailer, FarmBrowser browser,
                        Consumer<String> status) {
        this.version = Objects.requireNonNullElse(appVersion, "unknown");
        this.diagnostics = Objects.requireNonNullElse(diagnosticsText, "");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.mailer = Objects.requireNonNull(mailer, "mailer");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.status = Objects.requireNonNullElse(status, ignored -> { });

        setName("support.panel");
        setLayout(new BorderLayout(0, FarmUi.SECTION_GAP));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        getAccessibleContext().setAccessibleName(SupportGuide.TITLE);

        email.setName("support.email");
        email.setText(SupportGuide.sanitizeStoredEmail(supportEmail));
        email.getAccessibleContext().setAccessibleName("Support email (optional)");
        email.setToolTipText("Leave blank unless RicheyWorks gave you a support address.");

        details.setName("support.diagnostics");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setText(this.diagnostics);
        details.getAccessibleContext().setAccessibleName("Version and computer details");
        JScrollPane scroll = new JScrollPane(details);
        scroll.setPreferredSize(new Dimension(460, 110));

        feedback.setName("support.feedback");

        JButton openIssues = FarmUi.primaryButton("Open GitHub Issues…");
        openIssues.setName("support.openIssues");
        openIssues.setToolTipText("Open a new issue on RicheyWorks/FlowersForever and paste the copied details.");
        openIssues.addActionListener(event -> openIssues());

        JButton copy = FarmUi.secondaryButton("Copy details");
        copy.setName("support.copy");
        copy.setToolTipText("Copy GitHub Issues link, optional email, subject, and version details.");
        copy.addActionListener(event -> copyDetails());

        JButton openMail = FarmUi.secondaryButton("Open email draft…");
        openMail.setName("support.openMail");
        openMail.setToolTipText("Only when a real support email is set below.");
        openMail.addActionListener(event -> openEmailDraft());

        JPanel emailRow = new JPanel(new GridBagLayout());
        emailRow.setOpaque(false);
        emailRow.setName("support.emailRow");
        GridBagConstraints at = new GridBagConstraints();
        at.insets = new Insets(2, 0, 2, 8);
        at.anchor = GridBagConstraints.WEST;
        at.gridx = 0;
        at.gridy = 0;
        emailRow.add(new JLabel("Support email (optional):"), at);
        at.gridx = 1;
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        emailRow.add(email, at);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        actions.setName("support.actions");
        actions.add(openIssues);
        actions.add(copy);
        actions.add(openMail);

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
        return SupportGuide.sanitizeStoredEmail(email.getText());
    }

    void setEmail(String value) {
        email.setText(SupportGuide.sanitizeStoredEmail(value));
    }

    String feedbackText() {
        return feedback.getText();
    }

    private void copyDetails() {
        String subject = SupportGuide.emailSubject(version);
        String body = SupportGuide.emailBody(diagnostics);
        clipboard.copyText(SupportGuide.clipboardPayload(email(), subject, body));
        announce(SupportGuide.statusCopied());
    }

    private void openIssues() {
        String subject = SupportGuide.emailSubject(version);
        String body = SupportGuide.emailBody(diagnostics);
        clipboard.copyText(SupportGuide.clipboardPayload(email(), subject, body));
        try {
            browser.open(SupportGuide.issuesUri());
            announce(SupportGuide.statusIssuesOpened());
        } catch (Exception failed) {
            announce(SupportGuide.statusIssuesUnavailable());
        }
    }

    private void openEmailDraft() {
        String address = email();
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

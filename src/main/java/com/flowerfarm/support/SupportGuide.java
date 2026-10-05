package com.flowerfarm.support;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Plain-language pilot support: contact copy, diagnostics, and mailto helpers.
 * Pure logic so tests and the desktop window share one path.
 */
public final class SupportGuide {

    public static final String TITLE = "Contact / Report a problem";
    public static final String PREF_EMAIL = "support.email";
    /** Default inbox Richey can change later in this dialog. */
    public static final String DEFAULT_EMAIL = "support@richeyworks.com";

    private SupportGuide() {
    }

    public static String pageGuidance() {
        return "During the private pilot, RicheyWorks aims to reply the same day "
                + "(Pacific time) on business days. Tell us what you were trying to do "
                + "and what you saw. The details below help without digging through settings.";
    }

    public static String diagnostics(String version, String osName, String osVersion,
                                     String osArch, String javaVersion) {
        return "FlowersForever version: " + safe(version)
                + "\nOperating system: " + safe(osName) + " " + safe(osVersion)
                + " (" + safe(osArch) + ")"
                + "\nJava: " + safe(javaVersion);
    }

    /** Snapshot from this computer for the contact form. */
    public static String liveDiagnostics(String appVersion) {
        return diagnostics(
                appVersion,
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"));
    }

    public static String emailSubject(String version) {
        return "FlowersForever pilot help (" + safe(version) + ")";
    }

    public static String emailBody(String diagnosticsText) {
        return "What I was trying to do:\n\n"
                + "What I saw (error text, wrong screen, etc.):\n\n"
                + "---\nPlease leave this section for support:\n"
                + safe(diagnosticsText) + "\n";
    }

    public static String clipboardPayload(String supportEmail, String subject, String body) {
        return "To: " + safe(supportEmail)
                + "\nSubject: " + safe(subject)
                + "\n\n" + safe(body);
    }

    public static URI mailtoUri(String email, String subject, String body) {
        String address = normalizeEmail(email);
        if (!looksLikeEmail(address)) {
            throw new IllegalArgumentException("Support email is not set");
        }
        return URI.create("mailto:" + address
                + "?subject=" + encode(subject)
                + "&body=" + encode(body));
    }

    public static boolean looksLikeEmail(String email) {
        if (email == null) {
            return false;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            return false;
        }
        String domain = trimmed.substring(at + 1);
        return domain.indexOf('.') > 0 && !trimmed.contains(" ") && !trimmed.contains("\t");
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String statusCopied() {
        return "Support details copied — paste them into your email if the draft did not open.";
    }

    public static String statusMailOpened() {
        return "Email draft opened — add what happened, then send.";
    }

    public static String statusMailUnavailable() {
        return "Could not open email — details were copied so you can paste them instead.";
    }

    public static String statusBadEmail() {
        return "Enter a valid support email (name@example.com) before opening a draft.";
    }

    public static String statusSaved(String email) {
        return "Support email saved as " + normalizeEmail(email) + ".";
    }

    public static String menuDescription() {
        return "Email RicheyWorks or copy version and computer details to report a problem.";
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}

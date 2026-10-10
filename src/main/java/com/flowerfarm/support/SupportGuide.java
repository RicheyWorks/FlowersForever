package com.flowerfarm.support;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Plain-language pilot support: diagnostics, optional mailto, and GitHub Issues.
 * Pure logic so tests and the desktop window share one path.
 */
public final class SupportGuide {

    public static final String TITLE = "Contact / Report a problem";
    public static final String PREF_EMAIL = "support.email";
    /** Blank by default — no guessed inbox. Growers use GitHub Issues or paste. */
    public static final String DEFAULT_EMAIL = "";
    /** Retired bad default (NXDOMAIN); still cleared if found in prefs. */
    public static final String LEGACY_BAD_DEFAULT = "support@richeyworks.com";
    public static final String ISSUES_URL =
            "https://github.com/RicheyWorks/FlowersForever/issues/new";

    private SupportGuide() {
    }

    public static String pageGuidance() {
        return "During the private pilot, the fastest path is GitHub Issues for "
                + "RicheyWorks/FlowersForever, or copy the details below into a message "
                + "to your RicheyWorks contact. Aim is same-day reply (Pacific time) on "
                + "business days. Optional: set a support email below only if you were "
                + "given one.";
    }

    public static String diagnostics(String version, String osName, String osVersion,
                                     String osArch, String javaVersion) {
        return "FlowersForever version: " + safe(version)
                + "\nOperating system: " + safe(osName) + " " + safe(osVersion)
                + " (" + safe(osArch) + ")"
                + "\nJava: " + safe(javaVersion);
    }

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
        String address = sanitizeStoredEmail(supportEmail);
        StringBuilder out = new StringBuilder();
        out.append("GitHub Issues: ").append(ISSUES_URL).append('\n');
        if (!address.isBlank()) {
            out.append("To: ").append(address).append('\n');
        }
        out.append("Subject: ").append(safe(subject)).append("\n\n").append(safe(body));
        return out.toString();
    }

    public static URI mailtoUri(String email, String subject, String body) {
        String address = sanitizeStoredEmail(email);
        if (!looksLikeEmail(address)) {
            throw new IllegalArgumentException("Support email is not set");
        }
        return URI.create("mailto:" + address
                + "?subject=" + encode(subject)
                + "&body=" + encode(body));
    }

    public static URI issuesUri() {
        return URI.create(ISSUES_URL);
    }

    /**
     * Clears blank values and the retired {@code support@richeyworks.com} default
     * so growers never open a bouncing mailbox.
     */
    public static String sanitizeStoredEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || LEGACY_BAD_DEFAULT.equals(normalized)) {
            return "";
        }
        return normalized;
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
        return "Details copied — paste into GitHub Issues or a message to your RicheyWorks contact.";
    }

    public static String statusMailOpened() {
        return "Email draft opened — add what happened, then send.";
    }

    public static String statusMailUnavailable() {
        return "Could not open email — details were copied so you can paste them instead.";
    }

    public static String statusBadEmail() {
        return "No support email is set. Use Open GitHub Issues… or Copy details, "
                + "or enter an address you were given.";
    }

    public static String statusIssuesOpened() {
        return "GitHub Issues opened — paste the copied details into the new issue.";
    }

    public static String statusIssuesUnavailable() {
        return "Could not open a browser — GitHub Issues link and details were copied.";
    }

    public static String statusSaved(String email) {
        String address = sanitizeStoredEmail(email);
        if (address.isBlank()) {
            return "Support email cleared — GitHub Issues and Copy details stay available.";
        }
        return "Support email saved as " + address + ".";
    }

    public static String statusClearedLegacy() {
        return "Cleared the old bouncing support address. Use GitHub Issues or Copy details.";
    }

    public static String menuDescription() {
        return "Open GitHub Issues or copy version and computer details to report a problem.";
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}

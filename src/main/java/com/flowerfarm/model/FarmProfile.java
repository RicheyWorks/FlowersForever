package com.flowerfarm.model;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer-owned farm identity used by the desktop shell and generated documents.
 *
 * <p>The FlowersForever product brand stays stable while each installed farm can
 * present its own business name, location, and contact details to buyers.
 */
public class FarmProfile {

    private String name = "Your Flower Farm";
    private String location = "";
    private String tagline = "Locally grown flowers";
    private String email = "";
    private String phone = "";
    private String website = "";
    private String timeZone = ZoneId.systemDefault().getId();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = clean(name, "Your Flower Farm");
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = clean(location, "");
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = clean(tagline, "Locally grown flowers");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = clean(email, "");
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = clean(phone, "");
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = clean(website, "");
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        String candidate = clean(timeZone, ZoneId.systemDefault().getId());
        try {
            ZoneId.of(candidate);
            this.timeZone = candidate;
        } catch (DateTimeException exception) {
            this.timeZone = ZoneId.systemDefault().getId();
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    /** Human-readable farm location, with a neutral fallback for the app shell. */
    public String locationLabel() {
        return location.isBlank() ? "LOCAL FARM" : location;
    }

    /** Farm-owned heading followed by the document purpose. */
    public String documentBanner(String documentName) {
        List<String> parts = new ArrayList<>();
        parts.add(name);
        if (!location.isBlank()) {
            parts.add(location);
        }
        if (documentName != null && !documentName.isBlank()) {
            parts.add(documentName.trim());
        }
        return String.join("  ·  ", parts);
    }

    /** Multi-line identity block suitable for invoices and statements. */
    public String contactBlock() {
        List<String> lines = new ArrayList<>();
        lines.add(name);
        addIfPresent(lines, location);
        addIfPresent(lines, phone);
        addIfPresent(lines, email);
        addIfPresent(lines, website);
        return String.join("\n", lines);
    }

    /** Short buyer-facing thank-you sentence that belongs to the farm. */
    public String thankYouLine() {
        return "Thank you for supporting " + name + ".";
    }

    /** Quiet software attribution for document footers. */
    public String softwareFooter() {
        return "Prepared with FlowersForever · local-first cut-flower farm operations";
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value.trim());
        }
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

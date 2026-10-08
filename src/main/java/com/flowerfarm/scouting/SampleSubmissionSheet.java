package com.flowerfarm.scouting;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A printable one-page HTML sheet to put in the box with a sample. It does not
 * replace the lab's own signed form; it carries the sample ID and the grower's notes.
 */
public final class SampleSubmissionSheet {

    /** Grower details printed on the sheet. */
    public record Grower(String farmName, String location, String phone, String email) {
    }

    private SampleSubmissionSheet() {
    }

    public static String html(Scouting.LabSample sample, Scouting.Finding finding, Grower grower, LocalDate printed) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(grower, "grower");
        LabDirectory.Lab lab = LabDirectory.byId(sample.labId()).orElse(null);
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Sample ").append(esc(sample.id())).append("</title>")
                .append("<style>body{font-family:Arial,Helvetica,sans-serif;margin:24px;color:#111}")
                .append("h1{font-size:20px;margin:0 0 4px}h2{font-size:15px;margin:18px 0 6px;")
                .append("border-bottom:1px solid #999}table{border-collapse:collapse;width:100%}")
                .append("td{padding:4px 6px;vertical-align:top;border:1px solid #ccc}td.k{width:32%;")
                .append("font-weight:bold;background:#f3f3f3}.id{font-size:28px;font-weight:bold;")
                .append("letter-spacing:1px}.note{font-size:12px;color:#444}</style></head><body>\n");
        html.append("<h1>Sample submission sheet</h1>")
                .append("<div class=\"id\">Sample ID: ").append(esc(sample.id())).append("</div>")
                .append("<p class=\"note\">Put this sheet in the box with the sample, together with the lab's own ")
                .append("signed submission form. Printed ").append(esc(String.valueOf(printed))).append(".</p>\n");
        html.append("<h2>Grower</h2><table>");
        row(html, "Farm", grower.farmName());
        row(html, "Location", grower.location());
        row(html, "Phone", grower.phone());
        row(html, "Email", grower.email());
        html.append("</table>\n<h2>Sample</h2><table>");
        row(html, "Type", sample.type().label());
        row(html, "Lab", lab != null ? lab.name() : sample.labName());
        if (lab != null) {
            row(html, "Lab address", lab.address());
        }
        row(html, "Collected", String.valueOf(sample.collected()));
        row(html, "Shipped", sample.shipped() == null ? "" : sample.shipped().toString());
        row(html, "Tracking number", sample.tracking());
        if (!sample.soilBlock().isEmpty()) {
            row(html, "Soil block / water source", sample.soilBlock());
        }
        html.append("</table>\n");
        if (finding != null) {
            html.append("<h2>What we saw</h2><table>");
            row(html, "Date scouted", String.valueOf(finding.date()));
            row(html, "Crop", finding.crop());
            row(html, "Variety", finding.variety());
            row(html, "Bed / block", finding.bed());
            row(html, "Suspected problem", finding.organism().isEmpty() ? finding.category().label()
                    : finding.organism() + " (" + finding.category().label() + ")");
            row(html, "Symptoms", finding.symptoms());
            row(html, "Severity", finding.severity().label());
            row(html, "Plants affected", finding.incidencePercent() == null ? ""
                    : trimNumber(finding.incidencePercent()) + "%");
            row(html, "Action taken so far", finding.action());
            html.append("</table>\n");
        }
        html.append("<h2>Packing checklist</h2><ul>");
        for (String step : SampleGuides.forType(sample.type()).steps()) {
            html.append("<li>").append(esc(step)).append("</li>");
        }
        html.append("</ul>\n<p class=\"note\">Fees and addresses as checked ").append(LabDirectory.CHECKED)
                .append("; confirm with the lab before sending.</p>\n</body></html>\n");
        return html.toString();
    }

    private static void row(StringBuilder html, String key, String value) {
        html.append("<tr><td class=\"k\">").append(esc(key)).append("</td><td>")
                .append(esc(value == null ? "" : value)).append("</td></tr>");
    }

    static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Escapes text for HTML element content and attribute values. */
    static String esc(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}

package com.flowerfarm.telemetry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A bounded, explicit spreadsheet layout for the desktop; parsing never writes records. */
public final class EquipmentSpreadsheet {
    public static final String SOURCE = "desktop.spreadsheet";
    public static final int MAX_BYTES = 1024 * 1024;
    public static final List<String> HEADINGS = List.of("Equipment", "Growing area", "Measurement",
            "Value", "Unit", "Condition", "Measured at");
    private static final List<String> METRICS = List.of("air.temperature", "soil.temperature",
            "soil.moisture", "air.humidity", "water.flow", "water.level", "water.ph", "light.level");
    private static final List<String> LABELS = List.of("Air temperature", "Soil temperature",
            "Soil moisture", "Air humidity", "Water flow", "Water level", "Water pH", "Light level");
    private static final List<String> CONDITIONS = List.of("Measured", "Needs checking", "No reading", "Equipment error");
    private static final List<String> QUALITIES = List.of("GOOD", "SUSPECT", "MISSING", "ERROR");

    private EquipmentSpreadsheet() { }

    /** Only the file explicitly selected by the local farmer is read; links and directories are rejected. */
    public static List<Map<String, Object>> read(Path selected, FarmReadingService service) throws IOException {
        Path filename = selected.getFileName();
        if (filename == null || !filename.toString().toLowerCase(Locale.ROOT).endsWith(".csv")
                || !Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Choose a spreadsheet saved as a CSV file, rather than a folder or shortcut.");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(selected, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Use a spreadsheet smaller than 1 MB.");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return parse(text, service);
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Save the spreadsheet as CSV UTF-8, then choose it again.");
        }
    }

    public static List<Map<String, Object>> parse(String text, FarmReadingService service) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Use a spreadsheet smaller than 1 MB.");
        List<List<String>> records = records(text.startsWith("\uFEFF") ? text.substring(1) : text);
        if (records.size() < 2) throw new IllegalArgumentException("Include the heading row and at least one reading.");
        List<String> headings = records.get(0).stream().map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).toList();
        if (headings.stream().distinct().count() != headings.size()) {
            throw new IllegalArgumentException("Each spreadsheet heading must appear only once.");
        }
        for (String heading : HEADINGS) {
            if (!headings.contains(heading.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Add the heading: " + heading + ". Use Spreadsheet layout for an example.");
            }
        }
        if (headings.stream().anyMatch(h -> !"reading reference".equals(h)
                && HEADINGS.stream().noneMatch(known -> known.equalsIgnoreCase(h)))) {
            throw new IllegalArgumentException("Use the headings in Spreadsheet layout; remove extra columns before bringing this file in.");
        }
        List<Map<String, Object>> readings = new ArrayList<>();
        Map<String, Map<String, Object>> events = new HashMap<>();
        for (int index = 1; index < records.size(); index++) {
            try {
                List<String> cells = records.get(index);
                if (cells.size() != headings.size()) throw new IllegalArgumentException("The number of columns does not match the headings.");
                String metric = measurement(cell(cells, headings, "Measurement"));
                String quality = quality(cell(cells, headings, "Condition"));
                Instant time;
                try {
                    time = Instant.parse(cell(cells, headings, "Measured at"));
                } catch (DateTimeParseException invalid) {
                    throw new IllegalArgumentException("Use a measured time with its date and offset, such as 2026-10-01T08:30:00-07:00.");
                }
                var draft = ManualFarmReading.draft("preview", cell(cells, headings, "Equipment"),
                        cell(cells, headings, "Growing area"), metric, cell(cells, headings, "Unit"),
                        cell(cells, headings, "Value"), quality, time);
                String reference = headings.contains("reading reference") ? cell(cells, headings, "Reading reference") : "";
                if (reference.length() > 160 || reference.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Keep Reading reference on one line, up to 160 characters.");
                }
                // Neither value nor file name participates: corrected events conflict instead of silently duplicating.
                String event = draft.get("deviceId") + "\n" + metric + "\n"
                        + (reference.isEmpty() ? "time:" + time : "reference:" + reference);
                Map<String, Object> reading = new HashMap<>(draft);
                reading.put("readingKey", digest(event));
                service.validate(SOURCE, reading);
                if (reading.get("value") instanceof java.math.BigDecimal decimal) {
                    reading.put("value", decimal.stripTrailingZeros());
                }
                reading = Map.copyOf(reading);
                Map<String, Object> previous = events.putIfAbsent((String) reading.get("readingKey"), reading);
                if (previous != null && !previous.equals(reading)) {
                    throw new IllegalArgumentException("This equipment has conflicting readings for the same measurement and time or reference.");
                }
                readings.add(reading);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Reading " + index + ": " + invalid.getMessage());
            }
        }
        return List.copyOf(readings);
    }

    private static String cell(List<String> cells, List<String> headings, String heading) {
        return cells.get(headings.indexOf(heading.toLowerCase(Locale.ROOT))).trim();
    }

    private static String measurement(String value) {
        for (int i = 0; i < LABELS.size(); i++) if (LABELS.get(i).equalsIgnoreCase(value)) return METRICS.get(i);
        if (METRICS.contains(value)) return value;
        throw new IllegalArgumentException("Use a measurement from Spreadsheet layout, such as Soil moisture.");
    }

    private static String quality(String value) {
        for (int i = 0; i < CONDITIONS.size(); i++) {
            if (CONDITIONS.get(i).equalsIgnoreCase(value) || QUALITIES.get(i).equalsIgnoreCase(value)) return QUALITIES.get(i);
        }
        throw new IllegalArgumentException("Use Measured, Needs checking, No reading, or Equipment error for the condition.");
    }

    private static String digest(String event) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(event.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    /** Strict quotes, bounded fields/columns/rows; blank physical lines are ignored. */
    private static List<List<String>> records(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i <= text.length(); i++) {
            char c = i == text.length() ? '\n' : text.charAt(i);
            if (quoted) {
                if (i == text.length()) throw new IllegalArgumentException("Close every quoted cell before bringing in the spreadsheet.");
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == ',' || c == '\n' || c == '\r') {
                if (c == ',' || !row.isEmpty() || field.length() > 0 || closed) row.add(field.toString());
                if (row.size() > 8) throw new IllegalArgumentException("Use at most eight columns from Spreadsheet layout.");
                field.setLength(0);
                closed = false;
                if (c != ',') {
                    if (!row.isEmpty()) records.add(List.copyOf(row));
                    row.clear();
                    if (records.size() > FarmReadingService.MAX_BATCH + 1) throw new IllegalArgumentException("Bring in up to 500 readings at a time.");
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                }
            } else if (c == '"' && field.length() == 0 && !closed) quoted = true;
            else {
                if (closed || c == '"') throw new IllegalArgumentException("Check the quotes and commas in the spreadsheet.");
                field.append(c);
            }
            if (field.length() > 1024) throw new IllegalArgumentException("Use cells of up to 1,024 characters; keep names and values short.");
        }
        return records;
    }
}

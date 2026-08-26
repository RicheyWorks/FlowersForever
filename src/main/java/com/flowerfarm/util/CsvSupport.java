package com.flowerfarm.util;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Small RFC 4180-style CSV reader/writer shared by inventory import and export. */
public final class CsvSupport {

    private CsvSupport() {
    }

    public static String row(char delimiter, Object... values) {
        return row(delimiter, Arrays.asList(values));
    }

    public static String row(char delimiter, Collection<?> values) {
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            encoded.add(escape(value == null ? "" : value.toString(), delimiter));
        }
        return String.join(String.valueOf(delimiter), encoded);
    }

    public static String escape(String value, char delimiter) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(delimiter) >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    /**
     * Reads complete records, including quoted fields containing newlines and
     * doubled quotes. Empty physical lines are ignored.
     */
    public static List<List<String>> readAll(Reader source, char delimiter) throws IOException {
        PushbackReader reader = new PushbackReader(source, 1);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        int raw;
        while ((raw = reader.read()) != -1) {
            char c = (char) raw;
            if (inQuotes) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (c == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (c == delimiter) {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }
                addRecord(records, record, field);
            } else {
                field.append(c);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted CSV field.");
        }
        if (!record.isEmpty() || field.length() > 0) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private static void addRecord(List<List<String>> records,
                                  List<String> record,
                                  StringBuilder field) {
        if (!record.isEmpty() || field.length() > 0) {
            record.add(field.toString());
            records.add(new ArrayList<>(record));
        }
        record.clear();
        field.setLength(0);
    }
}

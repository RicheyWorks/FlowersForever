package com.flowerfarm.telemetry;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Desktop names are reversibly encoded into the existing adapter identifier contract. */
public final class ManualFarmReading {
    public static final String SOURCE = "desktop.manual";
    private static final String PREFIX = "name.";

    private ManualFarmReading() { }

    public static Map<String, Object> draft(String key, String equipment, String area,
            String metric, String unit, String value, String quality, Instant observed) {
        String name = equipment.trim();
        if (name.isEmpty() || name.getBytes(StandardCharsets.UTF_8).length > 84
                || name.chars().anyMatch(Character::isISOControl)
                || !name.equals(new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Use a short equipment name on one line, such as North greenhouse probe.");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("readingKey", key);
        row.put("deviceId", PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8)));
        row.put("location", area.trim());
        row.put("metric", metric);
        row.put("unit", unit.trim());
        row.put("quality", quality);
        row.put("observedAt", observed.toString());
        if (!value.isBlank()) {
            try {
                row.put("value", new BigDecimal(value.trim()));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Enter a number, such as 21.125, without a unit in the value box.");
            }
        }
        return Map.copyOf(row);
    }

    public static String equipmentName(FarmReading reading) {
        if (SOURCE.equals(reading.sourceId()) && reading.deviceId().startsWith(PREFIX)) {
            try {
                byte[] bytes = Base64.getUrlDecoder().decode(reading.deviceId().substring(PREFIX.length()));
                String name = new String(bytes, StandardCharsets.UTF_8);
                if ((PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
                        .equals(reading.deviceId()) && name.equals(name.trim())
                        && !name.isEmpty() && name.chars().noneMatch(Character::isISOControl)
                        && java.util.Arrays.equals(bytes, name.getBytes(StandardCharsets.UTF_8))) {
                    return name;
                }
            } catch (IllegalArgumentException invalid) {
                // Adapter records with an unexpected encoding keep their original identifier.
            }
        }
        return reading.deviceId();
    }
}

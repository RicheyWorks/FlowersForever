package com.flowerfarm.telemetry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded, transactional append-only readings intake shared by all source adapters. */
@Service
public class FarmReadingService {

    public static final int MAX_BATCH = 500;
    public static final int MAX_PAGE = 200;
    public static final int MAX_RAW_BYTES = 8192;
    private static final Set<String> FIELDS = Set.of("readingKey", "deviceId", "metric",
            "unit", "value", "quality", "observedAt", "location", "rawFrame");
    private static final Set<String> QUALITIES = Set.of("GOOD", "SUSPECT", "MISSING", "ERROR");
    private static final String LATEST_REPORTED = "SELECT * FROM (SELECT farm_readings.*, "
            + "ROW_NUMBER() OVER (PARTITION BY source_id, device_id, metric, unit, location "
            + "ORDER BY CAST(observed_at AS TIMESTAMP(9) WITH TIME ZONE) DESC, id DESC) AS reading_rank "
            + "FROM farm_readings) ranked WHERE reading_rank = 1";
    private static final RowMapper<FarmReading> MAPPER = (row, number) -> new FarmReading(
            row.getLong("id"), row.getString("source_id"), row.getString("reading_key"),
            row.getString("device_id"), row.getString("metric"), row.getString("unit"),
            row.getBigDecimal("measured_value"), row.getString("quality"),
            Instant.parse(row.getString("observed_at")), Instant.parse(row.getString("received_at")),
            row.getString("location"), row.getString("raw_frame"));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public FarmReadingService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    FarmReadingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Exactly matching source/key retries are harmless; changed content never overwrites history. */
    @Transactional
    public IntakeResult ingest(String sourceId, Collection<?> readings) {
        String source = identifier(sourceId, 80, "Source identifier");
        if (readings == null || readings.isEmpty() || readings.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Submit between 1 and 500 readings.");
        }
        Instant received = clock.instant();
        List<Input> inputs = readings.stream().map(value -> input(value, received)).toList();
        // A database lock covers the entire commit, including multiple processes using one store.
        jdbc.queryForObject("SELECT id FROM farm_reading_intake_lock WHERE id = 1 FOR UPDATE",
                Integer.class);
        int inserted = 0;
        List<Long> ids = new ArrayList<>();
        for (Input input : inputs) {
            List<FarmReading> existing = jdbc.query(
                    "SELECT * FROM farm_readings WHERE source_id = ? AND reading_key = ?",
                    MAPPER, source, input.readingKey());
            if (!existing.isEmpty()) {
                if (!input.equals(from(existing.get(0)))) {
                    throw new IllegalArgumentException(
                            "A reading key is already saved with different content; use a new key.");
                }
                ids.add(existing.get(0).id());
                continue;
            }
            jdbc.update("INSERT INTO farm_readings (source_id, reading_key, device_id, metric, unit, "
                            + "measured_value, quality, observed_at, received_at, location, raw_frame) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    source, input.readingKey(), input.deviceId(), input.metric(), input.unit(),
                    input.value(), input.quality(), input.observedAt().toString(), received.toString(),
                    input.location(), input.rawFrame());
            Long id = jdbc.queryForObject(
                    "SELECT id FROM farm_readings WHERE source_id = ? AND reading_key = ?",
                    Long.class, source, input.readingKey());
            ids.add(Objects.requireNonNull(id, "Saved reading identifier"));
            inserted++;
        }
        return new IntakeResult(source, inputs.size(), inserted, inputs.size() - inserted, ids);
    }

    /** Receipt-order cursor paging also delivers delayed observations without skipping them. */
    @Transactional(readOnly = true)
    public ReadingPage history(String sourceId, long afterId, int limit) {
        String source = identifier(sourceId, 80, "Source identifier");
        if (afterId < 0 || limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("Use a nonnegative cursor and a limit from 1 to 200.");
        }
        List<FarmReading> rows = jdbc.query(
                "SELECT * FROM farm_readings WHERE source_id = ? AND id > ? ORDER BY id LIMIT ?",
                MAPPER, source, afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<FarmReading> page = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        long next = page.isEmpty() ? afterId : page.get(page.size() - 1).id();
        return new ReadingPage(page, next, more);
    }

    /** Review one desktop draft without writing to the database. Intake validates again at commit. */
    public void validate(String sourceId, Map<String, Object> reading) {
        identifier(sourceId, 80, "Source identifier");
        input(reading, clock.instant());
    }

    /** All equipment, newest receipts first. A fixed cursor keeps older-page navigation stable. */
    @Transactional(readOnly = true)
    public ReadingPage recent(long beforeId, int limit) {
        if (beforeId < 0 || limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("Choose a history page of 1 to 200 readings.");
        }
        List<FarmReading> rows = beforeId == 0
                ? jdbc.query("SELECT * FROM farm_readings ORDER BY id DESC LIMIT ?", MAPPER, limit + 1)
                : jdbc.query("SELECT * FROM farm_readings WHERE id < ? ORDER BY id DESC LIMIT ?",
                        MAPPER, beforeId, limit + 1);
        List<FarmReading> page = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        long next = page.isEmpty() ? beforeId : page.get(page.size() - 1).id();
        return new ReadingPage(page, next, rows.size() > limit);
    }

    /** Latest observation per source/device/measurement/unit/area, including missing and error states. */
    @Transactional(readOnly = true)
    public ReadingPage latestReported(long beforeId, int limit) {
        if (beforeId < 0 || limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("Choose a measurement page of 1 to 200 readings.");
        }
        // Rank before applying the receipt cursor: paging must not revive an older observation.
        List<FarmReading> rows = beforeId == 0
                ? jdbc.query(LATEST_REPORTED + " ORDER BY id DESC LIMIT ?", MAPPER, limit + 1)
                : jdbc.query(LATEST_REPORTED + " AND id < ? ORDER BY id DESC LIMIT ?", MAPPER, beforeId, limit + 1);
        List<FarmReading> page = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        long next = page.isEmpty() ? beforeId : page.get(page.size() - 1).id();
        return new ReadingPage(page, next, rows.size() > limit);
    }

    /** A finite receipt fence for a copy of the append-only history across every source. */
    @Transactional(readOnly = true)
    public ReadingSnapshot snapshotForCopy() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) AS through_id, COUNT(*) AS reading_count FROM farm_readings",
                (row, index) -> new ReadingSnapshot(row.getLong("through_id"), row.getLong("reading_count")));
    }

    /** New receipts after the fence do not extend a running copy. */
    @Transactional(readOnly = true)
    public List<FarmReading> copyPage(ReadingSnapshot snapshot, long afterId, int limit) {
        if (snapshot == null || afterId < 0 || afterId > snapshot.throughId() || limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("Choose a saved-reading copy page of 1 to 200 readings.");
        }
        return List.copyOf(jdbc.query("SELECT * FROM farm_readings WHERE id > ? AND id <= ? ORDER BY id LIMIT ?",
                MAPPER, afterId, snapshot.throughId(), limit));
    }

    public record ReadingSnapshot(long throughId, long readingCount) {
        public ReadingSnapshot {
            if (throughId < 0 || readingCount < 0 || readingCount > throughId
                    || (throughId == 0) != (readingCount == 0)) {
                throw new IllegalArgumentException("Choose a valid saved-reading copy.");
            }
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM farm_readings", Long.class);
        return Objects.requireNonNull(count, "Reading count");
    }

    private static Input from(FarmReading saved) {
        return new Input(saved.readingKey(), saved.deviceId(), saved.metric(), saved.unit(),
                saved.value(), saved.quality(), saved.observedAt(), saved.location(), saved.rawFrame());
    }

    private static Input input(Object value, Instant received) {
        if (!(value instanceof Map<?, ?> row) || !FIELDS.containsAll(row.keySet())) {
            throw new IllegalArgumentException("Use the declared reading fields only.");
        }
        String key = identifier(row.get("readingKey"), 160, "Reading key");
        String device = identifier(row.get("deviceId"), 120, "Device identifier");
        String metric = identifier(row.get("metric"), 80, "Metric identifier");
        String unit = text(row.get("unit"), 40, "Unit", true);
        String quality = row.get("quality") == null ? "GOOD"
                : text(row.get("quality"), 12, "Quality", true);
        if (!QUALITIES.contains(quality)) {
            throw new IllegalArgumentException("Quality must be GOOD, SUSPECT, MISSING, or ERROR.");
        }
        BigDecimal measured = numeric(row.get("value"));
        if ((quality.equals("GOOD") || quality.equals("SUSPECT")) && measured == null) {
            throw new IllegalArgumentException("GOOD and SUSPECT readings require a numeric value.");
        }
        if (quality.equals("MISSING") && measured != null) {
            throw new IllegalArgumentException("MISSING readings must omit value; missing is not zero.");
        }
        Instant observed;
        try {
            observed = Instant.parse(text(row.get("observedAt"), 40, "Observation time", true));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Observation time must be an ISO-8601 instant with offset.");
        }
        if (observed.isBefore(Instant.EPOCH) || observed.isAfter(received.plusSeconds(300))) {
            throw new IllegalArgumentException("Observation time must be after 1970 and no more than five minutes ahead.");
        }
        String location = text(row.get("location"), 180, "Location", false);
        String raw = rawFrame(row.get("rawFrame"));
        return new Input(key, device, metric, unit, measured, quality, observed, location, raw);
    }

    private static BigDecimal numeric(Object value) {
        if (value == null) return null;
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Reading value must be a JSON number.");
        }
        try {
            BigDecimal number = new BigDecimal(value.toString()).stripTrailingZeros();
            if (number.scale() > 12 || (long) number.precision() - number.scale() > 18) {
                throw new ArithmeticException("Precision exceeded");
            }
            return number.setScale(12, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Use a finite reading with at most 18 integer and 12 fractional digits.");
        }
    }

    private static String identifier(Object value, int maximum, String label) {
        String identifier = text(value, maximum, label, true);
        if (!identifier.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(label + " must use letters, numbers, dots, underscores, colons, slashes, or hyphens.");
        }
        return identifier;
    }

    private static String text(Object value, int maximum, String label, boolean required) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException(label + " must be text.");
        }
        String text = value == null ? "" : ((String) value).trim();
        if ((required && text.isEmpty()) || text.length() > maximum
                || text.chars().anyMatch(Character::isISOControl)
                || !text.equals(new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException(label + " is required when declared and must fit its text limit without control characters.");
        }
        return text;
    }

    private static String rawFrame(Object value) {
        if (value == null) return "";
        if (!(value instanceof String raw) || raw.length() > MAX_RAW_BYTES
                || raw.getBytes(StandardCharsets.UTF_8).length > MAX_RAW_BYTES) {
            throw new IllegalArgumentException("Raw frame must be text of at most 8192 UTF-8 bytes.");
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= raw.length() || !Character.isLowSurrogate(raw.charAt(i))) {
                    throw new IllegalArgumentException("Raw frame contains invalid Unicode.");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Raw frame contains invalid Unicode.");
            }
        }
        return raw;
    }

    private record Input(String readingKey, String deviceId, String metric, String unit,
                         BigDecimal value, String quality, Instant observedAt,
                         String location, String rawFrame) { }

    public record IntakeResult(String sourceId, int submitted, int inserted, int replayed,
                               List<Long> readingIds) {
        public IntakeResult { readingIds = List.copyOf(readingIds); }
    }

    public record ReadingPage(List<FarmReading> readings, long nextAfterId, boolean hasMore) {
        public ReadingPage { readings = List.copyOf(readings); }
    }
}

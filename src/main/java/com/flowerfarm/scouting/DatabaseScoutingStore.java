package com.flowerfarm.scouting;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Keeps the scouting log in the farm database (tables from migration V47), so it is saved, backed up and
 * restored with the rest of the farm's records.
 * <ul>
 *   <li>The first time the log is opened, the old JSON log ({@link ScoutingStore}) is copied in, in one
 *       transaction, and the time is recorded. The JSON file and its {@code .bak} copy are not changed or
 *       deleted; they stay behind as a backup and are no longer updated.</li>
 *   <li>If the JSON log cannot be read, nothing is imported or recorded and opening the log reports the
 *       problem, so the database never starts a second log that differs from the file. Once the file is
 *       fixed or restored, the import runs on the next open.</li>
 *   <li>Every save replaces the whole log in one transaction, matching the JSON store's behavior.</li>
 * </ul>
 */
public final class DatabaseScoutingStore implements ScoutingLogStore {

    private static final int MAX_CODE = 40;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ScoutingStore json;
    private final Clock clock;
    private boolean imported;
    private String notice = "";

    public DatabaseScoutingStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ScoutingStore json,
                                 Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized ScoutingStore.Log load() throws IOException {
        importJsonOnce();
        return database("read", () -> transactions.execute(status -> read()));
    }

    @Override
    public synchronized void save(ScoutingStore.Log log) throws IOException {
        Objects.requireNonNull(log, "log");
        importJsonOnce();
        database("save", () -> transactions.execute(status -> {
            write(log);
            return Boolean.TRUE;
        }));
    }

    @Override
    public synchronized String takeNotice() {
        String message = notice;
        notice = "";
        return message;
    }

    /** True once the JSON log has been imported (or there was none to import). */
    public synchronized boolean jsonImported() throws IOException {
        return database("read", this::importedAt) != null;
    }

    private void importJsonOnce() throws IOException {
        if (imported) {
            return;
        }
        if (database("read", this::importedAt) != null) {
            imported = true;
            return;
        }
        boolean hadFile = json.exists();
        // Throws (and records nothing) when the file and its last good copy are both unreadable.
        ScoutingStore.Log legacy = json.load();
        String recovery = json.takeNotice();
        Normalized normalized = normalize(legacy);
        String outcome = database("import", () -> transactions.execute(status -> {
            if (lockedImportedAt() != null) {
                return "";
            }
            long rows = count("scouting_findings") + count("scouting_lab_samples");
            String note;
            if (rows > 0) {
                note = "The database already had scouting records; the JSON log was not copied in.";
            } else if (!hadFile) {
                note = "No JSON scouting log was found; the log starts empty in the database.";
                write(normalized.log());
            } else {
                note = normalized.log().findings().size() + " findings and " + normalized.log().samples().size()
                        + " lab samples copied from " + json.file() + "." + normalized.renamedNote();
                write(normalized.log());
            }
            jdbc.update("UPDATE scouting_settings SET json_imported_at = ?, json_import_note = ? WHERE id = 1",
                    OffsetDateTime.now(clock), note.length() > 2000 ? note.substring(0, 2000) : note);
            return hadFile && rows == 0 ? note : "";
        }));
        imported = true;
        if (outcome != null && !outcome.isEmpty()) {
            notice = join(recovery, "Your scouting log now lives in the farm database. " + outcome + " That file "
                    + "is kept unchanged as a backup and is no longer updated.");
        } else if (!recovery.isEmpty()) {
            notice = recovery;
        }
    }

    private OffsetDateTime importedAt() {
        return jdbc.queryForObject("SELECT json_imported_at FROM scouting_settings WHERE id = 1",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class));
    }

    private OffsetDateTime lockedImportedAt() {
        return jdbc.queryForObject("SELECT json_imported_at FROM scouting_settings WHERE id = 1 FOR UPDATE",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class));
    }

    private long count(String table) {
        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return rows == null ? 0 : rows;
    }

    private ScoutingStore.Log read() {
        Map<String, Integer> settings = jdbc.queryForObject(
                "SELECT log_version, overdue_days FROM scouting_settings WHERE id = 1",
                (rs, row) -> Map.of("version", rs.getInt(1), "overdue", rs.getInt(2)));
        Map<Long, List<Scouting.Photo>> photos = new HashMap<>();
        jdbc.query("SELECT finding_id, sha256, media_type, byte_size, filename, caption, added_on "
                + "FROM scouting_finding_photos ORDER BY finding_id, position", (ResultSet rs) -> {
                    photos.computeIfAbsent(rs.getLong("finding_id"), id -> new ArrayList<>()).add(
                            new Scouting.Photo(rs.getString("sha256"), rs.getString("media_type"),
                                    rs.getLong("byte_size"), rs.getString("filename"), rs.getString("caption"),
                                    rs.getObject("added_on", LocalDate.class)));
                });
        List<Scouting.Finding> findings = jdbc.query("SELECT * FROM scouting_findings ORDER BY id",
                (rs, row) -> new Scouting.Finding(rs.getString("code"), rs.getObject("found_on", LocalDate.class),
                        rs.getObject("planting_id", Long.class), rs.getString("bed"), rs.getString("crop"),
                        rs.getString("variety"), Scouting.Category.valueOf(rs.getString("category")),
                        rs.getString("organism"), rs.getString("symptoms"),
                        Scouting.Severity.valueOf(rs.getString("severity")),
                        rs.getObject("incidence_percent", Double.class), rs.getString("photo_path"),
                        rs.getString("action_taken"), photos.getOrDefault(rs.getLong("id"), List.of())));
        List<Scouting.LabSample> samples = jdbc.query("SELECT * FROM scouting_lab_samples ORDER BY id",
                (rs, row) -> new Scouting.LabSample(rs.getString("code"), rs.getString("finding_code"),
                        rs.getString("soil_block"), Scouting.SampleType.valueOf(rs.getString("sample_type")),
                        rs.getString("lab_id"), rs.getString("lab_name"),
                        rs.getObject("collected_on", LocalDate.class), rs.getObject("shipped_on", LocalDate.class),
                        rs.getString("tracking"), Scouting.SampleStatus.valueOf(rs.getString("status")),
                        money(rs.getBigDecimal("cost")), rs.getString("result_summary"),
                        rs.getString("report_path"), rs.getObject("results_on", LocalDate.class)));
        Map<String, Integer> values = Objects.requireNonNull(settings, "scouting settings");
        return new ScoutingStore.Log(values.get("version"), values.get("overdue"), findings, samples);
    }

    private void write(ScoutingStore.Log log) {
        jdbc.update("UPDATE scouting_settings SET log_version = ?, overdue_days = ? WHERE id = 1", log.version(),
                Scouting.clampDays(log.overdueDays()));
        writeFindings(log.findings());
        writeSamples(log.samples());
    }

    private void writeFindings(List<Scouting.Finding> findings) {
        Map<String, Long> existing = ids("scouting_findings");
        Set<String> kept = new HashSet<>();
        findings.forEach(finding -> kept.add(finding.id()));
        existing.forEach((code, id) -> {
            if (!kept.contains(code)) {
                jdbc.update("DELETE FROM scouting_findings WHERE id = ?", id); // photo links go with it
            }
        });
        for (Scouting.Finding finding : findings) {
            Object[] values = {finding.date(), finding.plantingId(), finding.bed(), finding.crop(), finding.variety(),
                finding.category().name(), finding.organism(), finding.symptoms(), finding.severity().name(),
                finding.incidencePercent(), finding.photoPath(), finding.action()};
            Long id = existing.get(finding.id());
            if (id == null) {
                id = insert("INSERT INTO scouting_findings (found_on, planting_id, bed, crop, variety, category, "
                        + "organism, symptoms, severity, incidence_percent, photo_path, action_taken, code) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", append(values, finding.id()));
            } else {
                jdbc.update("UPDATE scouting_findings SET found_on = ?, planting_id = ?, bed = ?, crop = ?, "
                        + "variety = ?, category = ?, organism = ?, symptoms = ?, severity = ?, "
                        + "incidence_percent = ?, photo_path = ?, action_taken = ? WHERE id = ?",
                        append(values, id));
                jdbc.update("DELETE FROM scouting_finding_photos WHERE finding_id = ?", id);
            }
            int position = 0;
            for (Scouting.Photo photo : finding.photos()) {
                jdbc.update("INSERT INTO scouting_finding_photos (finding_id, position, sha256, media_type, "
                        + "byte_size, filename, caption, added_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id,
                        position++, photo.sha256(), photo.mediaType(), photo.byteSize(), photo.filename(),
                        photo.caption(), photo.added());
            }
        }
    }

    private void writeSamples(List<Scouting.LabSample> samples) {
        Map<String, Long> existing = ids("scouting_lab_samples");
        Set<String> kept = new HashSet<>();
        samples.forEach(sample -> kept.add(sample.id()));
        existing.forEach((code, id) -> {
            if (!kept.contains(code)) {
                jdbc.update("DELETE FROM scouting_lab_samples WHERE id = ?", id);
            }
        });
        for (Scouting.LabSample sample : samples) {
            Object[] values = {sample.findingId(), sample.soilBlock(), sample.type().name(), sample.labId(),
                sample.labName(), sample.collected(), sample.shipped(), sample.tracking(), sample.status().name(),
                sample.cost(), sample.resultSummary(), sample.reportPath(), sample.resultsDate()};
            Long id = existing.get(sample.id());
            if (id == null) {
                jdbc.update("INSERT INTO scouting_lab_samples (finding_code, soil_block, sample_type, lab_id, "
                        + "lab_name, collected_on, shipped_on, tracking, status, cost, result_summary, report_path, "
                        + "results_on, code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        append(values, sample.id()));
            } else {
                jdbc.update("UPDATE scouting_lab_samples SET finding_code = ?, soil_block = ?, sample_type = ?, "
                        + "lab_id = ?, lab_name = ?, collected_on = ?, shipped_on = ?, tracking = ?, status = ?, "
                        + "cost = ?, result_summary = ?, report_path = ?, results_on = ? WHERE id = ?",
                        append(values, id));
            }
        }
    }

    private Map<String, Long> ids(String table) {
        Map<String, Long> ids = new LinkedHashMap<>();
        jdbc.query("SELECT id, code FROM " + table, (ResultSet rs) -> {
            ids.put(rs.getString("code"), rs.getLong("id"));
        });
        return ids;
    }

    private Long insert(String sql, Object[] values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "generated id").longValue();
    }

    private static Object[] append(Object[] values, Object last) {
        Object[] all = Arrays.copyOf(values, values.length + 1);
        all[values.length] = last;
        return all;
    }

    /** Money as entered, but never fewer than two decimals (the column keeps up to four). */
    static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal plain = value.stripTrailingZeros();
        return plain.scale() < 2 ? plain.setScale(2) : plain;
    }

    /** The log to import, with blank, overlong or repeated ids replaced so every row has its own code. */
    private record Normalized(ScoutingStore.Log log, int renamed) {
        String renamedNote() {
            return renamed == 0 ? "" : " " + renamed + (renamed == 1 ? " record had" : " records had")
                    + " a missing or repeated ID and was given a new one.";
        }
    }

    private Normalized normalize(ScoutingStore.Log log) {
        LocalDate today = LocalDate.now(clock);
        List<String> usedFindings = new ArrayList<>();
        Map<String, String> moved = new HashMap<>();
        List<Scouting.Finding> findings = new ArrayList<>();
        int renamed = 0;
        for (Scouting.Finding finding : log.findings()) {
            String code = finding.id() == null ? "" : finding.id().strip();
            if (code.isEmpty() || code.length() > MAX_CODE || usedFindings.contains(code)) {
                String fresh = Scouting.nextId("F", finding.date() == null ? today : finding.date(), usedFindings);
                if (!code.isEmpty() && !usedFindings.contains(code)) {
                    moved.put(code, fresh);
                }
                code = fresh;
                renamed++;
            }
            usedFindings.add(code);
            findings.add(finding.withId(code));
        }
        List<String> usedSamples = new ArrayList<>();
        List<Scouting.LabSample> samples = new ArrayList<>();
        for (Scouting.LabSample sample : log.samples()) {
            String code = sample.id() == null ? "" : sample.id().strip();
            if (code.isEmpty() || code.length() > MAX_CODE || usedSamples.contains(code)) {
                code = Scouting.nextId("S", sample.collected() == null ? today : sample.collected(), usedSamples);
                renamed++;
            }
            usedSamples.add(code);
            String findingId = moved.getOrDefault(sample.findingId(), sample.findingId());
            samples.add(new Scouting.LabSample(code, findingId, sample.soilBlock(), sample.type(), sample.labId(),
                    sample.labName(), sample.collected(), sample.shipped(), sample.tracking(), sample.status(),
                    sample.cost(), sample.resultSummary(), sample.reportPath(), sample.resultsDate()));
        }
        return new Normalized(new ScoutingStore.Log(log.version(), log.overdueDays(), findings, samples), renamed);
    }

    private static String join(String first, String second) {
        return first.isEmpty() ? second : first + " " + second;
    }

    private static <T> T database(String action, Supplier<T> work) throws IOException {
        try {
            return work.get();
        } catch (DataAccessException | TransactionException failure) {
            String verb = switch (action) {
                case "save" -> "saved to";
                case "import" -> "copied into";
                default -> "read from";
            };
            throw new IOException("The scouting log could not be " + verb + " the farm database, so nothing was "
                    + "changed. Close FlowersForever and open it again; if this keeps happening, restore a backup.",
                    failure);
        }
    }
}

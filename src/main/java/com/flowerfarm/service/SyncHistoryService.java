package com.flowerfarm.service;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.repository.SyncHistoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Records and queries connector / CRM / harvest audit history for troubleshooting.
 */
@Service
public class SyncHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SyncHistoryService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final SyncHistoryJpaRepository repository;

    public SyncHistoryService(SyncHistoryJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SyncHistoryEntry record(String connectorName, String operation,
                                   boolean success, String message, String detail,
                                   Integer recordCount) {
        SyncHistoryEntry entry = new SyncHistoryEntry(
                connectorName, operation, success, message, detail, recordCount);
        SyncHistoryEntry saved = repository.save(entry);
        log.debug("Recorded sync history id={} {} {} success={}",
                saved.getId(), operation, connectorName, success);
        return saved;
    }

    @Transactional
    public <T> void recordResult(String connectorName, String operation, ConnectorResult<T> result) {
        if (result == null) {
            record(connectorName, operation, false, "Null result", "", null);
            return;
        }
        Integer count = extractCount(result);
        record(
                result.getConnectorName() != null ? result.getConnectorName() : connectorName,
                operation,
                result.isSuccess(),
                result.getMessage(),
                result.getErrorDetail(),
                count
        );
    }

    @Transactional(readOnly = true)
    public List<SyncHistoryEntry> recent(int limit) {
        int n = clamp(limit);
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, n));
    }

    @Transactional(readOnly = true)
    public List<SyncHistoryEntry> recentForConnector(String connectorName, int limit) {
        int n = clamp(limit);
        if (connectorName == null || connectorName.isBlank()) {
            return recent(n);
        }
        return repository.findByConnectorNameIgnoreCaseOrderByOccurredAtDesc(
                connectorName.trim(), PageRequest.of(0, n));
    }

    /**
     * Flexible filter for audit UI / API.
     * Blank connector/operation/query → ignore that constraint.
     * {@code successOnly} null → any; true → OK only; false → FAIL only.
     */
    @Transactional(readOnly = true)
    public List<SyncHistoryEntry> filter(String connector, String operation,
                                         Boolean successOnly, String messageQuery, int limit) {
        int n = clamp(limit);
        List<SyncHistoryEntry> base = recent(MAX_LIMIT);
        String conn = connector == null ? "" : connector.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(conn) || "*".equals(conn)) {
            conn = "";
        }
        String op = operation == null ? "" : operation.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(op) || "*".equals(op)) {
            op = "";
        }
        String q = messageQuery == null ? "" : messageQuery.trim().toLowerCase(Locale.ROOT);

        Stream<SyncHistoryEntry> stream = base.stream();
        if (!conn.isEmpty()) {
            String c = conn;
            stream = stream.filter(e -> e.getConnectorName() != null
                    && e.getConnectorName().toLowerCase(Locale.ROOT).contains(c));
        }
        if (!op.isEmpty()) {
            String o = op;
            stream = stream.filter(e -> e.getOperation() != null
                    && e.getOperation().equalsIgnoreCase(o));
        }
        if (successOnly != null) {
            boolean want = successOnly;
            stream = stream.filter(e -> e.isSuccess() == want);
        }
        if (!q.isEmpty()) {
            stream = stream.filter(e -> {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                String det = e.getDetail() == null ? "" : e.getDetail().toLowerCase(Locale.ROOT);
                return msg.contains(q) || det.contains(q);
            });
        }
        return stream.limit(n).toList();
    }

    /** Distinct connector names seen in recent history (for UI combos). */
    @Transactional(readOnly = true)
    public List<String> distinctConnectors(int lookback) {
        Set<String> names = new LinkedHashSet<>();
        for (SyncHistoryEntry e : recent(lookback)) {
            if (e.getConnectorName() != null && !e.getConnectorName().isBlank()) {
                names.add(e.getConnectorName());
            }
        }
        return new ArrayList<>(names);
    }

    /** Distinct operations seen in recent history. */
    @Transactional(readOnly = true)
    public List<String> distinctOperations(int lookback) {
        Set<String> ops = new LinkedHashSet<>();
        for (SyncHistoryEntry e : recent(lookback)) {
            if (e.getOperation() != null && !e.getOperation().isBlank()) {
                ops.add(e.getOperation());
            }
        }
        return new ArrayList<>(ops);
    }

    /** Counts for status bar: ok / fail / total over filtered or recent set. */
    public record HistoryStats(int total, int ok, int fail) {}

    @Transactional(readOnly = true)
    public HistoryStats stats(List<SyncHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new HistoryStats(0, 0, 0);
        }
        int ok = 0;
        for (SyncHistoryEntry e : entries) {
            if (e.isSuccess()) {
                ok++;
            }
        }
        return new HistoryStats(entries.size(), ok, entries.size() - ok);
    }

    @Transactional(readOnly = true)
    public void exportToCsv(String filename, List<SyncHistoryEntry> entries) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        if (entries == null) {
            entries = List.of();
        }
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(filename.trim()))) {
            bw.write("Id,When,Connector,Operation,Success,RecordCount,Message,Detail");
            bw.newLine();
            for (SyncHistoryEntry e : entries) {
                String msg = e.getMessage() == null ? "" : e.getMessage().replace("\"", "\"\"");
                String det = e.getDetail() == null ? "" : e.getDetail().replace("\"", "\"\"");
                bw.write(String.format("%s,%s,%s,%s,%s,%s,\"%s\",\"%s\"",
                        e.getId() == null ? "" : e.getId(),
                        e.getOccurredAt(),
                        csvEscape(e.getConnectorName()),
                        csvEscape(e.getOperation()),
                        e.isSuccess(),
                        e.getRecordCount() == null ? "" : e.getRecordCount(),
                        msg,
                        det));
                bw.newLine();
            }
            log.info("Sync history exported to '{}' ({} row(s)).", filename, entries.size());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Sync history export failed: " + ex.getMessage(), ex);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @Transactional
    public void clearAll() {
        repository.deleteAllInBatch();
        log.info("Sync history cleared.");
    }

    private static int clamp(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    @SuppressWarnings("unchecked")
    private static <T> Integer extractCount(ConnectorResult<T> result) {
        if (!result.isSuccess() || result.getPayload() == null) {
            return null;
        }
        Object payload = result.getPayload();
        if (payload instanceof Integer i) {
            return i;
        }
        if (payload instanceof List<?> list) {
            return list.size();
        }
        if (payload instanceof SyncSummary s) {
            return s.total();
        }
        if (payload instanceof Item) {
            return 1;
        }
        return null;
    }
}

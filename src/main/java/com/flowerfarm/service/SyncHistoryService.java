package com.flowerfarm.service;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.repository.SyncHistoryJpaRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(filename.trim(), java.nio.charset.StandardCharsets.UTF_8))) {
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

    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(PNW);

    public record AuditReport(
            LocalDate generatedOn,
            String scopeLabel,
            int total,
            int ok,
            int fail,
            List<SyncHistoryEntry> entries,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("generatedOn", generatedOn.toString());
            m.put("scopeLabel", scopeLabel);
            m.put("total", total);
            m.put("ok", ok);
            m.put("fail", fail);
            m.put("entries", entries.stream().map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.getId());
                row.put("occurredAt", e.getOccurredAt() == null ? "" : e.getOccurredAt().toString());
                row.put("connectorName", e.getConnectorName());
                row.put("operation", e.getOperation());
                row.put("success", e.isSuccess());
                row.put("recordCount", e.getRecordCount());
                row.put("message", e.getMessage());
                row.put("detail", e.getDetail());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Audit report for filtered rows (or recent if entries null).
     * {@code scopeLabel} describes the filter for the PDF header.
     */
    public AuditReport buildAuditReport(List<SyncHistoryEntry> entries, String scopeLabel) {
        List<SyncHistoryEntry> rows = entries == null ? List.of() : List.copyOf(entries);
        HistoryStats s = stats(rows);
        String scope = scopeLabel == null || scopeLabel.isBlank() ? "Current view" : scopeLabel.trim();
        String text = formatAuditText(scope, s, rows);
        return new AuditReport(LocalDate.now(PNW), scope, s.total(), s.ok(), s.fail(), rows, text);
    }

    /**
     * Convenience: build from the same filter API used by REST.
     */
    @Transactional(readOnly = true)
    public AuditReport buildAuditReport(String connector, String operation,
                                        Boolean successOnly, String messageQuery, int limit) {
        List<SyncHistoryEntry> rows = filter(connector, operation, successOnly, messageQuery, limit);
        StringBuilder scope = new StringBuilder("Filter");
        if (connector != null && !connector.isBlank() && !"all".equalsIgnoreCase(connector.trim())) {
            scope.append(" · connector=").append(connector.trim());
        }
        if (operation != null && !operation.isBlank() && !"all".equalsIgnoreCase(operation.trim())) {
            scope.append(" · op=").append(operation.trim());
        }
        if (successOnly != null) {
            scope.append(successOnly ? " · OK only" : " · FAIL only");
        }
        if (messageQuery != null && !messageQuery.isBlank()) {
            scope.append(" · q=").append(messageQuery.trim());
        }
        scope.append(" · limit=").append(clamp(limit));
        return buildAuditReport(rows, scope.toString());
    }

    public byte[] generateAuditPdf(AuditReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required.");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 36, 36, 42, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Color failSoft = new Color(255, 235, 230);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Audit History",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Ops Audit History", titleFont));
            doc.add(new Paragraph(
                    "Generated: " + report.generatedOn()
                            + "     ·     Scope: " + report.scopeLabel(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Total", String.valueOf(report.total()), brandSoft));
            summary.addCell(summaryCell("OK", String.valueOf(report.ok()), brandSoft));
            summary.addCell(summaryCell("FAIL", String.valueOf(report.fail()),
                    report.fail() > 0 ? failSoft : brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Events (newest first)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.entries().isEmpty()) {
                doc.add(new Paragraph("No audit rows in this view.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1.6f, 1.2f, 1.3f, 0.7f, 0.7f, 3.0f});
                t.setWidthPercentage(100);
                headerCell(t, "When (PNW)");
                headerCell(t, "Source");
                headerCell(t, "Op");
                headerCell(t, "OK?");
                headerCell(t, "N");
                headerCell(t, "Message");
                for (SyncHistoryEntry e : report.entries()) {
                    Color bg = e.isSuccess() ? Color.WHITE : failSoft;
                    String when = e.getOccurredAt() == null ? "" : WHEN.format(e.getOccurredAt());
                    t.addCell(cell(when, body, bg));
                    t.addCell(cell(e.getConnectorName(), body, bg));
                    t.addCell(cell(e.getOperation(), body, bg));
                    t.addCell(cell(e.isSuccess() ? "OK" : "FAIL", body, bg));
                    t.addCell(cell(e.getRecordCount() == null ? "" : String.valueOf(e.getRecordCount()), body, bg));
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (e.getDetail() != null && !e.getDetail().isBlank()) {
                        msg = msg + (msg.isEmpty() ? "" : " — ") + e.getDetail();
                    }
                    t.addCell(cell(truncate(msg, 120), body, bg));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Sources include connectors (farmbrite, shopify…), crm, harvest. "
                            + "Failures are highlighted for barn troubleshooting.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated audit history PDF (total={}, fail={})", report.total(), report.fail());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build audit PDF: " + e.getMessage(), e);
        }
    }

    private static String formatAuditText(String scope, HistoryStats s, List<SyncHistoryEntry> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("OPS AUDIT HISTORY — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("Generated: ").append(LocalDate.now(PNW)).append('\n');
        sb.append("Scope: ").append(scope).append('\n');
        sb.append(String.format(Locale.US, "Total: %d  ·  OK: %d  ·  FAIL: %d%n",
                s.total(), s.ok(), s.fail()));
        sb.append('\n');
        if (rows.isEmpty()) {
            sb.append("  (no rows)\n");
        } else {
            sb.append(String.format(Locale.US, "%-16s %-12s %-12s %-5s %s%n",
                    "When", "Source", "Op", "OK?", "Message"));
            sb.append("-".repeat(72)).append('\n');
            for (SyncHistoryEntry e : rows) {
                String when = e.getOccurredAt() == null ? "" : WHEN.format(e.getOccurredAt());
                sb.append(String.format(Locale.US, "%-16s %-12s %-12s %-5s %s%n",
                        truncate(when, 16),
                        truncate(e.getConnectorName(), 12),
                        truncate(e.getOperation(), 12),
                        e.isSuccess() ? "OK" : "FAIL",
                        truncate(e.getMessage(), 36)));
            }
        }
        sb.append("\nTip: export CSV for spreadsheets; PDF for the office binder.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(3);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(200, 210, 200));
        return cell;
    }

    private static PdfPCell summaryCell(String label, String value, Color bg) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
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

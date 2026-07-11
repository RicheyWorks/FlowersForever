package com.flowerfarm.service;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.OrderLine;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates PDF reports combining harvest activity and wholesale sales
 * for a date range (default: trailing 7 days).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HarvestService harvestService;
    private final OrderService orderService;

    public ReportService(HarvestService harvestService, OrderService orderService) {
        this.harvestService = harvestService;
        this.orderService = orderService;
    }

    /** Weekly report ending today (inclusive), 7 days total. */
    @Transactional(readOnly = true)
    public byte[] generateWeeklyReportPdf() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        return generateReportPdf(from, to);
    }

    @Transactional(readOnly = true)
    public byte[] generateReportPdf(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }

        List<HarvestEntry> harvests = harvestService.findBetween(from, to);
        List<CustomerOrder> orders = orderService.findBetween(from, to);
        Map<String, Double> harvestTotals = harvestService.totalsByCrop(); // season-to-date snapshot
        double salesRevenue = orders.stream()
                .filter(o -> {
                    String s = o.getStatus();
                    return "FULFILLED".equalsIgnoreCase(s) || "CONFIRMED".equalsIgnoreCase(s);
                })
                .mapToDouble(CustomerOrder::lineTotal)
                .sum();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            // Brand banner
            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap County  ·  PNW West of the Cascades",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Weekly Farm Operations Report", titleFont));
            doc.add(new Paragraph(
                    "Period: " + DAY.format(from) + "  →  " + DAY.format(to)
                            + "     ·     Generated: " + DAY.format(LocalDate.now()),
                    small));
            doc.add(Chunk.NEWLINE);

            // Summary strip
            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Harvest entries", String.valueOf(harvests.size()), brandSoft, body));
            summary.addCell(summaryCell("Orders", String.valueOf(orders.size()), brandSoft, body));
            summary.addCell(summaryCell("Sales revenue",
                    String.format("$%,.2f", salesRevenue), brandSoft, body));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            // ── Harvest section ──────────────────────────────────────────────
            doc.add(new Paragraph("1. Harvest activity", h2));
            doc.add(new Paragraph(
                    harvests.isEmpty()
                            ? "No harvest entries in this period."
                            : harvests.size() + " harvest entry(ies) logged this period.",
                    body));
            doc.add(Chunk.NEWLINE);

            if (!harvests.isEmpty()) {
                PdfPTable table = new PdfPTable(new float[]{2, 3, 1.5f, 1.5f, 2});
                table.setWidthPercentage(100);
                headerCell(table, "Date");
                headerCell(table, "Crop");
                headerCell(table, "Qty");
                headerCell(table, "Unit");
                headerCell(table, "Bed / Field");
                for (HarvestEntry h : harvests) {
                    table.addCell(cell(DAY.format(h.getHarvestDate()), body));
                    table.addCell(cell(h.getCropName(), body));
                    table.addCell(cell(String.format("%.1f", h.getQuantity()), body));
                    table.addCell(cell(h.getUnit(), body));
                    table.addCell(cell(nullToEmpty(h.getBedOrField()), body));
                }
                doc.add(table);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Season-to-date harvest totals (all time)", h2));
            if (harvestTotals.isEmpty()) {
                doc.add(new Paragraph("(none yet)", body));
            } else {
                PdfPTable totals = new PdfPTable(new float[]{3, 2});
                totals.setWidthPercentage(50);
                headerCell(totals, "Crop");
                headerCell(totals, "Total qty");
                harvestTotals.forEach((crop, qty) -> {
                    totals.addCell(cell(crop, body));
                    totals.addCell(cell(String.format("%.1f", qty), body));
                });
                doc.add(totals);
            }

            doc.add(Chunk.NEWLINE);

            // ── Sales section ────────────────────────────────────────────────
            doc.add(new Paragraph("2. Sales / wholesale orders", h2));
            doc.add(new Paragraph(
                    orders.isEmpty()
                            ? "No orders in this period."
                            : orders.size() + " order(s) · confirmed/fulfilled revenue: $"
                            + String.format("%,.2f", salesRevenue),
                    body));
            doc.add(Chunk.NEWLINE);

            if (!orders.isEmpty()) {
                PdfPTable table = new PdfPTable(new float[]{1.5f, 2.5f, 1.5f, 1.5f, 1.5f});
                table.setWidthPercentage(100);
                headerCell(table, "Date");
                headerCell(table, "Customer");
                headerCell(table, "Status");
                headerCell(table, "Lines");
                headerCell(table, "Total $");
                for (CustomerOrder o : orders) {
                    table.addCell(cell(DAY.format(o.getOrderDate()), body));
                    table.addCell(cell(o.getCustomer() != null ? o.getCustomer().getName() : "?", body));
                    table.addCell(cell(o.getStatus(), body));
                    table.addCell(cell(String.valueOf(o.getLines().size()), body));
                    table.addCell(cell(String.format("%.2f", o.lineTotal()), body));
                }
                doc.add(table);

                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("Order line detail", h2));
                for (CustomerOrder o : orders) {
                    String cust = o.getCustomer() != null ? o.getCustomer().getName() : "?";
                    doc.add(new Paragraph(
                            "Order #" + o.getId() + " · " + cust + " · " + o.getStatus(),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                    if (o.getLines().isEmpty()) {
                        doc.add(new Paragraph("  (no lines)", small));
                    } else {
                        for (OrderLine line : o.getLines()) {
                            doc.add(new Paragraph(String.format(
                                    "  • %s — %.1f %s @ $%.2f = $%.2f",
                                    line.getProductName(), line.getQuantity(), line.getUnit(),
                                    line.getUnitPrice(), line.lineTotal()), body));
                        }
                    }
                }
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated farm PDF report {} → {} ({} harvests, {} orders)",
                    from, to, harvests.size(), orders.size());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build PDF report: " + e.getMessage(), e);
        }
    }

    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        cell.setBorderColor(new Color(200, 210, 200));
        return cell;
    }

    private static PdfPCell summaryCell(String label, String value, Color bg, Font body) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

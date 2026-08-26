package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService.BedProduction;
import com.flowerfarm.service.HarvestService.BedProductionReport;
import com.flowerfarm.service.HarvestService.HarvestLogReport;
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
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/** Renders harvest report models as printable PDF and portable CSV documents. */
@Component
public class HarvestReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(HarvestReportGenerator.class);

    public String exportBedProductionCsv(BedProductionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("bed,totalQuantity,entryCount,firstDate,lastDate,crop,cropQuantity\n");
        for (BedProduction b : report.beds()) {
            if (b.byCrop().isEmpty()) {
                sb.append(csv(b.bed())).append(',')
                        .append(b.totalQuantity()).append(',')
                        .append(b.entryCount()).append(',')
                        .append(b.firstDate()).append(',')
                        .append(b.lastDate()).append(",,\n");
                continue;
            }
            for (Map.Entry<String, Double> crop : b.byCrop().entrySet()) {
                sb.append(csv(b.bed())).append(',')
                        .append(b.totalQuantity()).append(',')
                        .append(b.entryCount()).append(',')
                        .append(b.firstDate()).append(',')
                        .append(b.lastDate()).append(',')
                        .append(csv(crop.getKey())).append(',')
                        .append(crop.getValue()).append('\n');
            }
        }
        return sb.toString();
    }


    public byte[] generateBedProductionPdf(BedProductionReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required.");
        }
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

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap County  ·  Bed Production",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Bed / Field Production Report", titleFont));
            doc.add(new Paragraph(
                    "Period: " + report.from() + "  →  " + report.to()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Beds", String.valueOf(report.bedCount()), brandSoft));
            summary.addCell(summaryCell("Harvest logs", String.valueOf(report.entryCount()), brandSoft));
            summary.addCell(summaryCell("Total qty",
                    String.format(Locale.US, "%,.1f", report.grandTotal()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Beds ranked by quantity", h2));
            doc.add(Chunk.NEWLINE);
            if (report.beds().isEmpty()) {
                doc.add(new Paragraph(
                        "No harvest rows in range — log cuts with a Bed / field value.", body));
            } else {
                PdfPTable bedTable = new PdfPTable(new float[]{0.7f, 2.5f, 1.3f, 1.0f, 2.5f, 2.2f});
                bedTable.setWidthPercentage(100);
                headerCell(bedTable, "#");
                headerCell(bedTable, "Bed / Field");
                headerCell(bedTable, "Qty");
                headerCell(bedTable, "Logs");
                headerCell(bedTable, "Top crop");
                headerCell(bedTable, "Dates");
                int rank = 1;
                for (BedProduction b : report.beds()) {
                    String topCrop = "—";
                    if (!b.byCrop().isEmpty()) {
                        Map.Entry<String, Double> first = b.byCrop().entrySet().iterator().next();
                        topCrop = first.getKey() + " ("
                                + String.format(Locale.US, "%.0f", first.getValue()) + ")";
                    }
                    String dates = (b.firstDate().isEmpty() ? "?" : b.firstDate())
                            + " → "
                            + (b.lastDate().isEmpty() ? "?" : b.lastDate());
                    bedTable.addCell(cell(String.valueOf(rank++), body));
                    bedTable.addCell(cell(b.bed(), body));
                    bedTable.addCell(cell(String.format(Locale.US, "%.1f", b.totalQuantity()), body));
                    bedTable.addCell(cell(String.valueOf(b.entryCount()), body));
                    bedTable.addCell(cell(topCrop, body));
                    bedTable.addCell(cell(dates, body));
                }
                doc.add(bedTable);
            }

            if (!report.beds().isEmpty()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("2. Crop mix by bed", h2));
                doc.add(Chunk.NEWLINE);
                for (BedProduction b : report.beds()) {
                    doc.add(new Paragraph(
                            b.bed() + "  ·  qty " + String.format(Locale.US, "%,.1f", b.totalQuantity()),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, brandGreen)));
                    if (b.byCrop().isEmpty()) {
                        doc.add(new Paragraph("  (no crop detail)", small));
                    } else {
                        PdfPTable crops = new PdfPTable(new float[]{3, 1.5f});
                        crops.setWidthPercentage(60);
                        headerCell(crops, "Crop");
                        headerCell(crops, "Qty");
                        for (Map.Entry<String, Double> c : b.byCrop().entrySet()) {
                            crops.addCell(cell(c.getKey(), body));
                            crops.addCell(cell(String.format(Locale.US, "%.1f", c.getValue()), body));
                        }
                        doc.add(crops);
                    }
                    doc.add(Chunk.NEWLINE);
                }
            }

            doc.add(new Paragraph(
                    "Tip: consistent bed names (Bed A, Tunnel 1) make this report sharper.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated bed production PDF {} → {} ({} beds, qty {})",
                    report.from(), report.to(), report.bedCount(), report.grandTotal());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build bed production PDF: " + e.getMessage(), e);
        }
    }


    public byte[] generateHarvestLogPdf(HarvestLogReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required.");
        }
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

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Harvest Log",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Harvest Log", titleFont));
            doc.add(new Paragraph(
                    "Period: " + report.from() + "  →  " + report.to()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Entries", String.valueOf(report.entryCount()), brandSoft));
            summary.addCell(summaryCell("Total qty",
                    String.format(Locale.US, "%,.1f", report.totalQuantity()), brandSoft));
            summary.addCell(summaryCell("Crops",
                    String.valueOf(report.byCrop().size()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. By crop", h2));
            doc.add(Chunk.NEWLINE);
            if (report.byCrop().isEmpty()) {
                doc.add(new Paragraph("No harvests in this period.", body));
            } else {
                PdfPTable crops = new PdfPTable(new float[]{3, 1.5f});
                crops.setWidthPercentage(55);
                headerCell(crops, "Crop");
                headerCell(crops, "Qty");
                for (Map.Entry<String, Double> c : report.byCrop().entrySet()) {
                    crops.addCell(cell(c.getKey(), body));
                    crops.addCell(cell(String.format(Locale.US, "%,.1f", c.getValue()), body));
                }
                doc.add(crops);
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("2. Detail log (chronological)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.entries().isEmpty()) {
                doc.add(new Paragraph("No rows — log cuts after morning harvest.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1.3f, 2.2f, 1.0f, 1.0f, 1.4f, 2.0f});
                t.setWidthPercentage(100);
                headerCell(t, "Date");
                headerCell(t, "Crop");
                headerCell(t, "Qty");
                headerCell(t, "Unit");
                headerCell(t, "Bed");
                headerCell(t, "Notes");
                for (HarvestEntry e : report.entries()) {
                    t.addCell(cell(e.getHarvestDate() == null ? "" : e.getHarvestDate().toString(), body));
                    t.addCell(cell(e.getCropName(), body));
                    t.addCell(cell(String.format(Locale.US, "%,.1f", e.getQuantity()), body));
                    t.addCell(cell(e.getUnit() == null ? "" : e.getUnit(), body));
                    t.addCell(cell(e.getBedOrField() == null || e.getBedOrField().isBlank()
                            ? "—" : e.getBedOrField(), body));
                    t.addCell(cell(e.getNotes() == null ? "" : e.getNotes(), body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: bed production PDF ranks fields; this sheet is the raw cut list for the cooler.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated harvest log PDF {} → {} ({} entries, qty {})",
                    report.from(), report.to(), report.entryCount(), report.totalQuantity());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build harvest log PDF: " + e.getMessage(), e);
        }
    }


    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
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

    private static PdfPCell summaryCell(String label, String value, Color bg) {
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


    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

}

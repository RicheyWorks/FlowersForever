package com.flowerfarm.service;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.InventoryService.LowStockReport;
import com.flowerfarm.service.InventoryService.LowStockLine;
import com.flowerfarm.service.InventoryService.PriceListLine;
import com.flowerfarm.service.InventoryService.PriceListReport;
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
import java.util.Locale;

/** Renders inventory report models as printable PDF documents. */
@Component
public class InventoryReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(InventoryReportGenerator.class);
    private final FarmProfile farmProfile;

    public InventoryReportGenerator(FarmProfile farmProfile) {
        this.farmProfile = farmProfile;
    }

    public byte[] generateLowStockPdf(LowStockReport report) {
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
            Color warnSoft = new Color(255, 243, 224);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    farmProfile.documentBanner("Low-Stock Reorder"),
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Low-Stock Reorder List", titleFont));
            doc.add(new Paragraph(
                    "Date: " + report.date()
                            + "     ·     Threshold: ≤ " + report.threshold()
                            + "     ·     SKUs: " + report.skuCount(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Low SKUs",
                    String.valueOf(report.lowStockCount()),
                    report.lowStockCount() > 0 ? warnSoft : brandSoft));
            summary.addCell(summaryCell("Suggest cost $",
                    String.format(Locale.US, "%,.0f", report.suggestedOrderCostTotal()), brandSoft));
            summary.addCell(summaryCell("Threshold",
                    "≤ " + report.threshold(), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Reorder sheet (lowest first)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.lines().isEmpty()) {
                doc.add(new Paragraph(
                        "All SKUs above threshold — no restock needed today.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{2.8f, 1.5f, 0.9f, 1.0f, 1.1f, 1.2f});
                t.setWidthPercentage(100);
                headerCell(t, "SKU");
                headerCell(t, "Category");
                headerCell(t, "On hand");
                headerCell(t, "Unit");
                headerCell(t, "Order qty");
                headerCell(t, "Est. cost");
                for (LowStockLine line : report.lines()) {
                    Color rowBg = line.quantity() == 0 ? new Color(255, 235, 230) : Color.WHITE;
                    t.addCell(cell(line.name(), body, rowBg));
                    t.addCell(cell(line.category(), body, rowBg));
                    t.addCell(cell(InventoryQuantity.format(line.quantity()), body, rowBg));
                    t.addCell(cell(line.unit(), body, rowBg));
                    t.addCell(cell(String.valueOf(line.suggestedOrderQty()), body, rowBg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.suggestedOrderCost()), body, rowBg));
                }
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(String.format(Locale.US,
                        "Suggested restock total (at cost): $%,.2f",
                        report.suggestedOrderCostTotal()), body));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: target qty = max(2× threshold, threshold+1). "
                            + "Zero-qty rows are highlighted for harvest priority.",
                    small));
            doc.add(new Paragraph(
                    farmProfile.softwareFooter(), small));

            doc.close();
            log.info("Generated low-stock reorder PDF (threshold={}, lines={})",
                    report.threshold(), report.lowStockCount());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build low-stock PDF: " + e.getMessage(), e);
        }
    }


    public byte[] generatePriceListPdf(PriceListReport report) {
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
                    farmProfile.documentBanner("Price List"),
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Wholesale / Market Price List", titleFont));
            doc.add(new Paragraph(
                    "Date: " + report.date()
                            + "     ·     "
                            + (report.inStockOnly() ? "In-stock only" : "All SKUs")
                            + "     ·     SKUs: " + report.skuCount(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("SKUs", String.valueOf(report.skuCount()), brandSoft));
            summary.addCell(summaryCell("Sell value $",
                    String.format(Locale.US, "%,.0f", report.sellValue()), brandSoft));
            summary.addCell(summaryCell("Stock by unit",
                    report.quantitySummary(), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Catalog (by category)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.lines().isEmpty()) {
                doc.add(new Paragraph(
                        report.inStockOnly()
                                ? "No in-stock SKUs — harvest or clear in-stock filter."
                                : "Inventory is empty.",
                        body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{2.6f, 1.6f, 1.1f, 1.0f, 0.9f, 1.2f});
                t.setWidthPercentage(100);
                headerCell(t, "SKU");
                headerCell(t, "Category");
                headerCell(t, "Unit $");
                headerCell(t, "Unit");
                headerCell(t, "Qty");
                headerCell(t, "Ext. $");
                for (PriceListLine line : report.lines()) {
                    Color bg = line.quantity() <= 0 ? new Color(245, 245, 245) : Color.WHITE;
                    t.addCell(cell(line.name(), body, bg));
                    t.addCell(cell(line.category(), body, bg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.unitPrice()), body, bg));
                    t.addCell(cell(line.unit(), body, bg));
                    t.addCell(cell(InventoryQuantity.format(line.quantity()), body, bg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.lineSellValue()), body, bg));
                }
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(String.format(Locale.US,
                        "Catalog sell value: $%,.2f   ·   Cost basis: $%,.2f",
                        report.sellValue(), report.costBasis()), body));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: booth sheet = in-stock only; full list includes zeros for planning. "
                            + "Low-stock reorder is a separate PDF.",
                    small));
            doc.add(new Paragraph(
                    farmProfile.softwareFooter(), small));

            doc.close();
            log.info("Generated price list PDF (skus={}, inStockOnly={})",
                    report.skuCount(), report.inStockOnly());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build price list PDF: " + e.getMessage(), e);
        }
    }


    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
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

}

package com.flowerfarm.service;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.model.FarmProfile;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Renders harvest report models as printable PDF and portable CSV documents. */
@Component
public class HarvestReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(HarvestReportGenerator.class);
    private final FarmProfile farmProfile;

    public HarvestReportGenerator(FarmProfile farmProfile) {
        this.farmProfile = farmProfile;
    }

    public String exportBedProductionCsv(BedProductionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("bed,totalQuantity,entryCount,firstDate,lastDate,crop,cropQuantity\n");
        for (BedProduction b : report.beds()) {
            if (b.byProductUnit().isEmpty()) {
                sb.append(csv(b.bed())).append(',')
                        .append(b.totalQuantity()).append(',')
                        .append(b.entryCount()).append(',')
                        .append(b.firstDate()).append(',')
                        .append(b.lastDate()).append(",,\n");
                continue;
            }
            for (Map.Entry<String, Double> crop : b.byProductUnit().entrySet()) {
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
                    farmProfile.documentBanner("Bed Production"),
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
                    "See quantities by bed", brandSoft));
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
                    if (!b.byProductUnit().isEmpty()) {
                        Map.Entry<String, Double> first = b.byProductUnit().entrySet().iterator().next();
                        topCrop = first.getKey() + " ("
                                + InventoryQuantity.format(first.getValue()) + ")";
                    }
                    String dates = (b.firstDate().isEmpty() ? "?" : b.firstDate())
                            + " → "
                            + (b.lastDate().isEmpty() ? "?" : b.lastDate());
                    bedTable.addCell(cell(String.valueOf(rank++), body));
                    bedTable.addCell(cell(b.bed(), body));
                    bedTable.addCell(cell(b.quantitySummary(), body));
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
                            b.bed() + "  ·  qty " + b.quantitySummary(),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, brandGreen)));
                    if (b.byProductUnit().isEmpty()) {
                        doc.add(new Paragraph("  (no crop detail)", small));
                    } else {
                        PdfPTable crops = new PdfPTable(new float[]{3, 1.5f});
                        crops.setWidthPercentage(60);
                        headerCell(crops, "Crop");
                        headerCell(crops, "Qty");
                        for (Map.Entry<String, Double> c : b.byProductUnit().entrySet()) {
                            crops.addCell(cell(c.getKey(), body));
                            crops.addCell(cell(InventoryQuantity.format(c.getValue()), body));
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
                    farmProfile.softwareFooter(), small));

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
                    farmProfile.documentBanner("Harvest Log"),
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

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Entries", String.valueOf(report.entryCount()), brandSoft));
            summary.addCell(summaryCell("Total qty",
                    InventoryQuantity.summary(report.entries(), HarvestEntry::getQuantity, HarvestEntry::getUnit), brandSoft));
            summary.addCell(summaryCell("Crops",
                    String.valueOf(report.byCrop().size()), brandSoft));
            summary.addCell(summaryCell("Loss / culls",
                    InventoryQuantity.summary(report.entries(), HarvestEntry::getLossQuantity, HarvestEntry::getUnit), brandSoft));
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
                for (Map.Entry<String, Double> c : HarvestService.groupByProductUnit(report.entries()).entrySet()) {
                    crops.addCell(cell(c.getKey(), body));
                    crops.addCell(cell(InventoryQuantity.format(c.getValue()), body));
                }
                doc.add(crops);
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("2. Detail log (chronological)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.entries().isEmpty()) {
                doc.add(new Paragraph("No rows — log cuts after morning harvest.", body));
            } else {
                PdfPTable t = new PdfPTable(
                        new float[]{1.15f, 1.65f, 0.75f, 0.8f, 1.0f, 1.55f, 0.75f, 1.25f});
                t.setWidthPercentage(100);
                headerCell(t, "Date");
                headerCell(t, "Crop");
                headerCell(t, "Qty");
                headerCell(t, "Unit");
                headerCell(t, "Grade");
                headerCell(t, "Lot");
                headerCell(t, "Loss");
                headerCell(t, "Storage");
                for (HarvestEntry e : report.entries()) {
                    t.addCell(cell(e.getHarvestDate() == null ? "" : e.getHarvestDate().toString(), body));
                    t.addCell(cell(e.getCropName(), body));
                    t.addCell(cell(InventoryQuantity.format(e.getQuantity()), body));
                    t.addCell(cell(e.getUnit() == null ? "" : e.getUnit(), body));
                    t.addCell(cell(String.valueOf(e.getGrade()), body));
                    t.addCell(cell(e.getLotCode(), body));
                    t.addCell(cell(InventoryQuantity.format(e.getLossQuantity()), body));
                    t.addCell(cell(e.getStorageLocation() == null
                            || e.getStorageLocation().isBlank()
                            ? "—" : e.getStorageLocation(), body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: bed production PDF ranks fields; this sheet is the raw cut list for the cooler.",
                    small));
            doc.add(new Paragraph(
                    farmProfile.softwareFooter(), small));

            doc.close();
            log.info("Generated harvest log PDF {} → {} ({} entries, qty {})",
                    report.from(), report.to(), report.entryCount(), report.totalQuantity());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build harvest log PDF: " + e.getMessage(), e);
        }
    }

    /** Builds a printable 4 × 6 inch traceability label with machine-readable lot identity. */
    public byte[] generateHarvestLotLabelPdf(HarvestEntry entry) {
        if (entry == null || entry.getId() == null
                || entry.getLotCode() == null || entry.getLotCode().isBlank()) {
            throw new IllegalArgumentException("A saved harvest lot is required.");
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Rectangle label = new Rectangle(288, 432);
            Document document = new Document(label, 24, 24, 24, 24);
            PdfWriter.getInstance(document, output);
            document.open();

            Color green = new Color(34, 100, 54);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, green);
            Font lot = FontFactory.getFont(FontFactory.COURIER_BOLD, 14, Color.BLACK);
            Font detail = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            Paragraph heading = new Paragraph(farmProfile.documentBanner("Harvest Lot"), title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            document.add(Chunk.NEWLINE);

            com.lowagie.text.Image qr = com.lowagie.text.Image.getInstance(qrPng(entry));
            qr.scaleAbsolute(180, 180);
            qr.setAlignment(Element.ALIGN_CENTER);
            document.add(qr);

            Paragraph code = new Paragraph(entry.getLotCode(), lot);
            code.setAlignment(Element.ALIGN_CENTER);
            document.add(code);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Crop: " + entry.getCropName(), detail));
            document.add(new Paragraph("Date: " + entry.getHarvestDate()
                    + "    Grade: " + entry.getGrade(), detail));
            document.add(new Paragraph("Marketable: "
                    + InventoryQuantity.format(entry.getQuantity()) + " " + entry.getUnit()
                    + "    Loss: " + InventoryQuantity.format(entry.getLossQuantity()),
                    detail));
            document.add(new Paragraph("Source: " + display(entry.getBedOrField())
                    + "    Storage: " + display(entry.getStorageLocation()), detail));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                    "Scan, then use the lot code in FlowersForever or the AI lookup tool.", detail));
            document.close();
            return output.toByteArray();
        } catch (DocumentException | java.io.IOException | WriterException exception) {
            throw new IllegalStateException(
                    "Failed to build harvest lot label: " + exception.getMessage(), exception);
        }
    }

    private static byte[] qrPng(HarvestEntry entry)
            throws WriterException, java.io.IOException {
        String payload = "{\"schema\":\"flowersforever.harvest-lot.v1\",\"lotCode\":\""
                + entry.getLotCode() + "\"}";
        Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, 480, 480, hints);
        BufferedImage image = new BufferedImage(
                matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) {
            throw new java.io.IOException("PNG writer is unavailable.");
        }
        return png.toByteArray();
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
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

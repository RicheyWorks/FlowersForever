package com.flowerfarm.service;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * End-of-day Kitsap farm closeout — bookend to {@link MorningBriefingService}.
 * Summarizes today's fulfilled sales, harvest, leftover pipeline, and stock.
 */
@Service
public class DayCloseoutService {

    private static final Logger log = LoggerFactory.getLogger(DayCloseoutService.class);
    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");
    private static final int DEFAULT_LOW_STOCK = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final OrderService orderService;

    public DayCloseoutService(InventoryService inventoryService,
                              HarvestService harvestService,
                              OrderService orderService) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.orderService = orderService;
    }

    public record FulfilledLine(long orderId, String customer, double total, int lineCount) {}

    public record LowStockLine(String name, int quantity, String unit) {}

    public record DayCloseout(
            LocalDate date,
            String generatedAt,
            String location,
            int fulfilledCount,
            double fulfilledRevenue,
            List<FulfilledLine> fulfilledOrders,
            int cancelledCount,
            int remainingConfirmedCount,
            double remainingPipelineRevenue,
            double todayHarvestQty,
            int todayHarvestEntries,
            double weekHarvestQty,
            double weekRealizedRevenue,
            double weekPipelineRevenue,
            double inventorySellValue,
            int lowStockThreshold,
            List<LowStockLine> lowStock,
            List<String> nextSteps,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("generatedAt", generatedAt);
            m.put("location", location);
            m.put("fulfilledCount", fulfilledCount);
            m.put("fulfilledRevenue", fulfilledRevenue);
            m.put("fulfilledOrders", fulfilledOrders.stream().map(o -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderId", o.orderId());
                row.put("customer", o.customer());
                row.put("total", o.total());
                row.put("lineCount", o.lineCount());
                return row;
            }).toList());
            m.put("cancelledCount", cancelledCount);
            m.put("remainingConfirmedCount", remainingConfirmedCount);
            m.put("remainingPipelineRevenue", remainingPipelineRevenue);
            m.put("todayHarvestQty", todayHarvestQty);
            m.put("todayHarvestEntries", todayHarvestEntries);
            m.put("weekHarvestQty", weekHarvestQty);
            m.put("weekRealizedRevenue", weekRealizedRevenue);
            m.put("weekPipelineRevenue", weekPipelineRevenue);
            m.put("inventorySellValue", inventorySellValue);
            m.put("lowStockThreshold", lowStockThreshold);
            m.put("lowStock", lowStock.stream().map(l -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", l.name());
                row.put("quantity", l.quantity());
                row.put("unit", l.unit());
                return row;
            }).toList());
            m.put("nextSteps", nextSteps);
            m.put("plainText", plainText);
            return m;
        }
    }

    @Transactional(readOnly = true)
    public DayCloseout build() {
        LocalDate today = LocalDate.now(PNW);
        String when = LocalTime.now(PNW).format(TIME);

        List<CustomerOrder> dayOrders = orderService.findBetween(today, today);
        List<FulfilledLine> fulfilled = new ArrayList<>();
        double fulfilledRev = 0;
        int cancelled = 0;
        int remainingConfirmed = 0;
        double remainingPipe = 0;
        for (CustomerOrder o : dayOrders) {
            String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase(Locale.ROOT);
            double total = o.lineTotal();
            switch (s) {
                case "FULFILLED" -> {
                    fulfilledRev += total;
                    String cust = o.getCustomer() != null && o.getCustomer().getName() != null
                            ? o.getCustomer().getName() : "(unknown)";
                    fulfilled.add(new FulfilledLine(
                            o.getId() == null ? 0L : o.getId(),
                            cust,
                            total,
                            o.getLines() == null ? 0 : o.getLines().size()));
                }
                case "CANCELLED" -> cancelled++;
                case "CONFIRMED" -> {
                    remainingConfirmed++;
                    remainingPipe += total;
                }
                default -> { /* DRAFT etc. */ }
            }
        }
        fulfilled.sort(Comparator.comparing(FulfilledLine::orderId));

        List<HarvestEntry> harvests = harvestService.findBetween(today, today);
        double todayHarvest = harvests.stream().mapToDouble(HarvestEntry::getQuantity).sum();
        double weekHarvest = harvestService.totalQuantityLast7Days();
        OrderService.WeekRevenueSummary rev = orderService.weekRevenueSummary();
        InventoryService.InventoryKpiSnapshot inv =
                inventoryService.inventoryKpis(DEFAULT_LOW_STOCK);

        List<LowStockLine> low = new ArrayList<>();
        for (Item item : inventoryService.getAllItems()) {
            if (item.getQuantity() <= DEFAULT_LOW_STOCK) {
                low.add(new LowStockLine(
                        item.getName(),
                        item.getQuantity(),
                        item.getUnit() == null ? "" : item.getUnit()));
            }
        }
        low.sort(Comparator.comparingInt(LowStockLine::quantity));

        List<String> next = buildNextSteps(
                fulfilled.size(), fulfilledRev, remainingConfirmed, remainingPipe,
                todayHarvest, cancelled, low);
        String text = formatPlainText(
                today, when, fulfilled, fulfilledRev, cancelled,
                remainingConfirmed, remainingPipe, todayHarvest, harvests.size(),
                weekHarvest, rev, inv.sellValue(), low, next);

        return new DayCloseout(
                today,
                when,
                "Port Orchard / Kitsap County, WA",
                fulfilled.size(),
                fulfilledRev,
                List.copyOf(fulfilled),
                cancelled,
                remainingConfirmed,
                remainingPipe,
                todayHarvest,
                harvests.size(),
                weekHarvest,
                rev.realized(),
                rev.pipeline(),
                inv.sellValue(),
                DEFAULT_LOW_STOCK,
                List.copyOf(low),
                List.copyOf(next),
                text
        );
    }

    public byte[] generatePdf(DayCloseout closeout) {
        if (closeout == null) {
            throw new IllegalArgumentException("closeout is required.");
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
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Day Closeout",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("End-of-Day Farm Closeout", titleFont));
            doc.add(new Paragraph(
                    closeout.date() + "  ·  " + closeout.generatedAt()
                            + " America/Los_Angeles  ·  " + closeout.location(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Fulfilled",
                    String.valueOf(closeout.fulfilledCount()), brandSoft));
            summary.addCell(summaryCell("Sales $",
                    String.format(Locale.US, "%,.0f", closeout.fulfilledRevenue()), brandSoft));
            summary.addCell(summaryCell("Harvest qty",
                    String.format(Locale.US, "%,.0f", closeout.todayHarvestQty()), brandSoft));
            summary.addCell(summaryCell("Left pipeline",
                    String.valueOf(closeout.remainingConfirmedCount()),
                    closeout.remainingConfirmedCount() > 0 ? warnSoft : brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Next steps (tomorrow)", h2));
            doc.add(Chunk.NEWLINE);
            if (closeout.nextSteps().isEmpty()) {
                doc.add(new Paragraph("Quiet close — nothing flagged for tomorrow.", body));
            } else {
                int i = 1;
                for (String s : closeout.nextSteps()) {
                    doc.add(new Paragraph(i++ + ". " + s, body));
                }
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("2. Fulfilled orders today", h2));
            if (closeout.fulfilledOrders().isEmpty()) {
                doc.add(new Paragraph("No FULFILLED orders dated today.", body));
            } else {
                doc.add(Chunk.NEWLINE);
                PdfPTable t = new PdfPTable(new float[]{0.8f, 3, 1.2f, 1});
                t.setWidthPercentage(100);
                headerCell(t, "#");
                headerCell(t, "Customer");
                headerCell(t, "Total");
                headerCell(t, "Lines");
                for (FulfilledLine o : closeout.fulfilledOrders()) {
                    t.addCell(cell(String.valueOf(o.orderId()), body));
                    t.addCell(cell(o.customer(), body));
                    t.addCell(cell(String.format(Locale.US, "$%,.2f", o.total()), body));
                    t.addCell(cell(String.valueOf(o.lineCount()), body));
                }
                doc.add(t);
                doc.add(new Paragraph(
                        String.format(Locale.US, "Day total: $%,.2f", closeout.fulfilledRevenue()),
                        body));
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("3. Harvest today", h2));
            doc.add(new Paragraph(
                    closeout.todayHarvestEntries() == 0
                            ? "No harvest entries logged today."
                            : closeout.todayHarvestEntries() + " entr"
                            + (closeout.todayHarvestEntries() == 1 ? "y" : "ies")
                            + " · qty "
                            + String.format(Locale.US, "%,.1f", closeout.todayHarvestQty())
                            + "  (week total "
                            + String.format(Locale.US, "%,.0f", closeout.weekHarvestQty()) + ")",
                    body));
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("4. Leftover pipeline (CONFIRMED, order date = today)", h2));
            doc.add(new Paragraph(
                    closeout.remainingConfirmedCount() == 0
                            ? "No open CONFIRMED orders for today — clean slate."
                            : closeout.remainingConfirmedCount() + " order(s) still open · $"
                            + String.format(Locale.US, "%,.2f", closeout.remainingPipelineRevenue())
                            + "  (cancel, reschedule, or fulfill before bed).",
                    body));
            if (closeout.cancelledCount() > 0) {
                doc.add(new Paragraph(
                        closeout.cancelledCount() + " CANCELLED order(s) today.",
                        body));
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("5. Week snapshot", h2));
            doc.add(new Paragraph(String.format(Locale.US,
                    "Realized $%,.2f  ·  Pipeline $%,.2f  ·  Inventory sell value $%,.2f",
                    closeout.weekRealizedRevenue(),
                    closeout.weekPipelineRevenue(),
                    closeout.inventorySellValue()), body));
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("6. Low stock (≤ " + closeout.lowStockThreshold() + ")", h2));
            if (closeout.lowStock().isEmpty()) {
                doc.add(new Paragraph("Stock levels healthy after today's sales.", body));
            } else {
                doc.add(Chunk.NEWLINE);
                PdfPTable t = new PdfPTable(new float[]{3, 1.2f, 1.5f});
                t.setWidthPercentage(70);
                headerCell(t, "SKU");
                headerCell(t, "Qty");
                headerCell(t, "Unit");
                for (LowStockLine l : closeout.lowStock()) {
                    t.addCell(cell(l.name(), body));
                    t.addCell(cell(String.valueOf(l.quantity()), body));
                    t.addCell(cell(l.unit(), body));
                }
                doc.add(t);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated day closeout PDF for {} (fulfilled={}, harvestQty={})",
                    closeout.date(), closeout.fulfilledCount(), closeout.todayHarvestQty());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build day closeout PDF: " + e.getMessage(), e);
        }
    }

    private static List<String> buildNextSteps(int fulfilledCount,
                                               double fulfilledRev,
                                               int remainingConfirmed,
                                               double remainingPipe,
                                               double todayHarvest,
                                               int cancelled,
                                               List<LowStockLine> low) {
        List<String> next = new ArrayList<>();
        if (remainingConfirmed > 0) {
            next.add("Finish " + remainingConfirmed + " open CONFIRMED order(s) ($"
                    + String.format(Locale.US, "%,.2f", remainingPipe)
                    + ") — Market Day fulfill or reschedule.");
        }
        if (fulfilledCount == 0) {
            next.add("No sales fulfilled today — confirm whether market / wholesale ran.");
        } else {
            next.add("Banked $" + String.format(Locale.US, "%,.2f", fulfilledRev)
                    + " from " + fulfilledCount + " fulfilled order(s).");
        }
        if (todayHarvest <= 0) {
            next.add("No harvest logged today — plan morning cut or note rest day.");
        }
        if (!low.isEmpty()) {
            next.add("Restock " + low.size() + " low SKU(s) before next market (e.g. "
                    + low.get(0).name() + ").");
        }
        if (cancelled > 0) {
            next.add(cancelled + " cancelled order(s) — check customer follow-up notes.");
        }
        if (next.isEmpty()) {
            next.add("Quiet close — all clear for tomorrow's briefing.");
        }
        return next;
    }

    private static String formatPlainText(LocalDate date, String time,
                                          List<FulfilledLine> fulfilled,
                                          double fulfilledRev,
                                          int cancelled,
                                          int remainingConfirmed,
                                          double remainingPipe,
                                          double todayHarvest,
                                          int harvestEntries,
                                          double weekHarvest,
                                          OrderService.WeekRevenueSummary rev,
                                          double invValue,
                                          List<LowStockLine> low,
                                          List<String> next) {
        StringBuilder sb = new StringBuilder();
        sb.append("DAY CLOSEOUT — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════════\n");
        sb.append("Date: ").append(date).append("  ·  ").append(time)
                .append(" America/Los_Angeles\n");
        sb.append(String.format(Locale.US,
                "Today: fulfilled %d ($%,.2f)  ·  harvest %,.0f  ·  open CONFIRMED %d%n",
                fulfilled.size(), fulfilledRev, todayHarvest, remainingConfirmed));
        sb.append(String.format(Locale.US,
                "Week: harvest %,.0f  ·  realized $%,.2f  ·  pipeline $%,.2f%n",
                weekHarvest, rev.realized(), rev.pipeline()));
        sb.append('\n');

        sb.append("NEXT STEPS (TOMORROW)\n");
        sb.append("────────────────────\n");
        int i = 1;
        for (String s : next) {
            sb.append("  ").append(i++).append(". ").append(s).append('\n');
        }
        sb.append('\n');

        sb.append("FULFILLED TODAY\n");
        sb.append("───────────────\n");
        if (fulfilled.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (FulfilledLine o : fulfilled) {
                sb.append(String.format(Locale.US, "  #%d  %-24s  $%,.2f  (%d lines)%n",
                        o.orderId(), o.customer(), o.total(), o.lineCount()));
            }
            sb.append(String.format(Locale.US, "  TOTAL  $%,.2f%n", fulfilledRev));
        }
        sb.append('\n');

        sb.append("HARVEST TODAY\n");
        sb.append("─────────────\n");
        sb.append(String.format(Locale.US, "  Entries: %d  ·  Qty: %,.1f%n",
                harvestEntries, todayHarvest));
        sb.append('\n');

        sb.append("LEFTOVER PIPELINE\n");
        sb.append("─────────────────\n");
        sb.append(String.format(Locale.US, "  Open CONFIRMED: %d  ·  $%,.2f%n",
                remainingConfirmed, remainingPipe));
        if (cancelled > 0) {
            sb.append(String.format(Locale.US, "  Cancelled today: %d%n", cancelled));
        }
        sb.append('\n');

        sb.append("STOCK / VALUE\n");
        sb.append("─────────────\n");
        sb.append(String.format(Locale.US, "  Inventory sell value: $%,.2f%n", invValue));
        if (low.isEmpty()) {
            sb.append("  Low stock: (healthy)\n");
        } else {
            for (LowStockLine l : low) {
                sb.append(String.format(Locale.US, "  LOW %-24s %4d %s%n",
                        l.name(), l.quantity(), l.unit()));
            }
        }
        sb.append('\n');
        sb.append("Tip: print PDF for the barn board; open Morning briefing at dawn.\n");
        return sb.toString();
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
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }
}

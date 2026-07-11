package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.HarvestService.BedProduction;
import com.flowerfarm.service.HarvestService.BedProductionReport;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.IrrigationAdvisorService.Priority;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.ProductNeed;
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
 * Start-of-day Kitsap farm briefing — packing, beds, water, and low stock in one place.
 * Offline-safe by default (climatology irrigation; no required live weather).
 */
@Service
public class MorningBriefingService {

    private static final Logger log = LoggerFactory.getLogger(MorningBriefingService.class);
    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");
    private static final int DEFAULT_LOW_STOCK = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final OrderService orderService;
    private final MarketDayPackingService marketDayPackingService;
    private final IrrigationAdvisorService irrigationAdvisorService;

    public MorningBriefingService(InventoryService inventoryService,
                                  HarvestService harvestService,
                                  OrderService orderService,
                                  MarketDayPackingService marketDayPackingService,
                                  IrrigationAdvisorService irrigationAdvisorService) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.marketDayPackingService = marketDayPackingService;
        this.irrigationAdvisorService = irrigationAdvisorService;
    }

    public record LowStockLine(String name, int quantity, String unit) {}

    public record MorningBriefing(
            LocalDate date,
            String generatedAt,
            String location,
            double weekHarvestQty,
            double weekRealizedRevenue,
            double weekPipelineRevenue,
            MarketDayPlan marketDay,
            BedProductionReport weekBeds,
            IrrigationAdvice irrigation,
            List<LowStockLine> lowStock,
            int lowStockThreshold,
            List<String> actionItems,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("generatedAt", generatedAt);
            m.put("location", location);
            m.put("weekHarvestQty", weekHarvestQty);
            m.put("weekRealizedRevenue", weekRealizedRevenue);
            m.put("weekPipelineRevenue", weekPipelineRevenue);
            m.put("marketDay", marketDay != null ? marketDay.toMap() : Map.of());
            m.put("weekBeds", weekBeds != null ? weekBeds.toMap() : Map.of());
            m.put("irrigation", irrigation != null ? irrigation.toMap() : Map.of());
            m.put("lowStockThreshold", lowStockThreshold);
            m.put("lowStock", lowStock.stream().map(l -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", l.name());
                row.put("quantity", l.quantity());
                row.put("unit", l.unit());
                return row;
            }).toList());
            m.put("actionItems", actionItems);
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * @param preferLiveWeather when true, irrigation may call Open-Meteo (falls back offline)
     */
    @Transactional(readOnly = true)
    public MorningBriefing build(boolean preferLiveWeather) {
        LocalDate today = LocalDate.now(PNW);
        String when = LocalTime.now(PNW).format(TIME);

        MarketDayPlan pack = marketDayPackingService.planForDay(today);
        BedProductionReport beds = harvestService.productionByBedLast7Days();
        IrrigationAdvice water = irrigationAdvisorService.advise(preferLiveWeather);
        InventoryService.InventoryKpiSnapshot inv =
                inventoryService.inventoryKpis(DEFAULT_LOW_STOCK);
        OrderService.WeekRevenueSummary rev = orderService.weekRevenueSummary();
        double weekHarvest = harvestService.totalQuantityLast7Days();

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

        List<String> actions = buildActions(pack, beds, water, low, weekHarvest);
        String text = formatPlainText(today, when, pack, beds, water, low, weekHarvest, rev, actions);

        return new MorningBriefing(
                today,
                when,
                "Port Orchard / Kitsap County, WA",
                weekHarvest,
                rev.realized(),
                rev.pipeline(),
                pack,
                beds,
                water,
                List.copyOf(low),
                DEFAULT_LOW_STOCK,
                List.copyOf(actions),
                text
        );
    }

    /** Offline-safe briefing (climatology irrigation). */
    @Transactional(readOnly = true)
    public MorningBriefing buildOffline() {
        return build(false);
    }

    public byte[] generatePdf(MorningBriefing briefing) {
        if (briefing == null) {
            throw new IllegalArgumentException("briefing is required.");
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
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Morning Briefing",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Morning Farm Briefing", titleFont));
            doc.add(new Paragraph(
                    briefing.date() + "  ·  " + briefing.generatedAt()
                            + " America/Los_Angeles  ·  " + briefing.location(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Week harvest",
                    String.format(Locale.US, "%,.0f", briefing.weekHarvestQty()), brandSoft));
            summary.addCell(summaryCell("Realized $",
                    String.format(Locale.US, "%,.0f", briefing.weekRealizedRevenue()), brandSoft));
            summary.addCell(summaryCell("Pipeline $",
                    String.format(Locale.US, "%,.0f", briefing.weekPipelineRevenue()), brandSoft));
            summary.addCell(summaryCell("Low stock",
                    String.valueOf(briefing.lowStock().size()),
                    briefing.lowStock().isEmpty() ? brandSoft : warnSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            // Actions
            doc.add(new Paragraph("1. Priority actions", h2));
            doc.add(Chunk.NEWLINE);
            if (briefing.actionItems().isEmpty()) {
                doc.add(new Paragraph("All clear — no urgent flags this morning.", body));
            } else {
                int i = 1;
                for (String a : briefing.actionItems()) {
                    doc.add(new Paragraph(i++ + ". " + a, body));
                }
            }
            doc.add(Chunk.NEWLINE);

            // Market day
            MarketDayPlan pack = briefing.marketDay();
            doc.add(new Paragraph("2. Market day packing", h2));
            doc.add(new Paragraph(
                    pack.orderCount() == 0
                            ? "No CONFIRMED orders for today."
                            : pack.orderCount() + " order(s) · pipeline $"
                            + String.format(Locale.US, "%,.2f", pack.pipelineValue())
                            + " · shortfalls " + pack.shortfallSkuCount(),
                    body));
            if (pack.shortfallSkuCount() > 0) {
                doc.add(Chunk.NEWLINE);
                PdfPTable t = new PdfPTable(new float[]{3, 1.2f, 1.2f, 1.5f});
                t.setWidthPercentage(100);
                headerCell(t, "Product");
                headerCell(t, "Need");
                headerCell(t, "Stock");
                headerCell(t, "Short");
                for (ProductNeed p : pack.pickList()) {
                    if (!p.shortfall()) {
                        continue;
                    }
                    t.addCell(cell(p.productName(), body));
                    t.addCell(cell(String.format(Locale.US, "%.1f", p.neededQty()), body));
                    t.addCell(cell(String.valueOf(p.stockOnHand()), body));
                    t.addCell(cell(String.format(Locale.US, "%.1f", p.shortfallQty()), body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);

            // Beds
            BedProductionReport beds = briefing.weekBeds();
            doc.add(new Paragraph("3. Top beds (last 7 days)", h2));
            if (beds.beds().isEmpty()) {
                doc.add(new Paragraph("No harvests logged this week.", body));
            } else {
                doc.add(Chunk.NEWLINE);
                PdfPTable t = new PdfPTable(new float[]{0.6f, 2.5f, 1.3f, 2.5f});
                t.setWidthPercentage(100);
                headerCell(t, "#");
                headerCell(t, "Bed");
                headerCell(t, "Qty");
                headerCell(t, "Top crop");
                int rank = 1;
                for (BedProduction b : beds.beds()) {
                    if (rank > 8) {
                        break;
                    }
                    String top = "—";
                    if (!b.byCrop().isEmpty()) {
                        Map.Entry<String, Double> e = b.byCrop().entrySet().iterator().next();
                        top = e.getKey() + " (" + String.format(Locale.US, "%.0f", e.getValue()) + ")";
                    }
                    t.addCell(cell(String.valueOf(rank++), body));
                    t.addCell(cell(b.bed(), body));
                    t.addCell(cell(String.format(Locale.US, "%.1f", b.totalQuantity()), body));
                    t.addCell(cell(top, body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);

            // Water
            IrrigationAdvice water = briefing.irrigation();
            doc.add(new Paragraph("4. Irrigation", h2));
            doc.add(new Paragraph(
                    water.priority().name() + " · " + water.mode() + " · " + water.headline(),
                    body));
            doc.add(Chunk.NEWLINE);

            // Low stock
            doc.add(new Paragraph("5. Low stock (≤ " + briefing.lowStockThreshold() + ")", h2));
            if (briefing.lowStock().isEmpty()) {
                doc.add(new Paragraph("Stock levels healthy.", body));
            } else {
                doc.add(Chunk.NEWLINE);
                PdfPTable t = new PdfPTable(new float[]{3, 1.2f, 1.5f});
                t.setWidthPercentage(70);
                headerCell(t, "SKU");
                headerCell(t, "Qty");
                headerCell(t, "Unit");
                for (LowStockLine l : briefing.lowStock()) {
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
            log.info("Generated morning briefing PDF for {} ({} actions)",
                    briefing.date(), briefing.actionItems().size());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build morning briefing PDF: " + e.getMessage(), e);
        }
    }

    private static List<String> buildActions(MarketDayPlan pack,
                                             BedProductionReport beds,
                                             IrrigationAdvice water,
                                             List<LowStockLine> low,
                                             double weekHarvest) {
        List<String> actions = new ArrayList<>();
        if (pack.orderCount() > 0) {
            actions.add("Market Day: pack " + pack.orderCount()
                    + " CONFIRMED order(s) ($"
                    + String.format(Locale.US, "%,.2f", pack.pipelineValue()) + " pipeline).");
        }
        if (pack.shortfallSkuCount() > 0) {
            actions.add("Resolve " + pack.shortfallSkuCount()
                    + " packing shortfall SKU(s) before load-out.");
        }
        if (water.priority() == Priority.HIGH || water.priority() == Priority.MEDIUM) {
            actions.add("Water (" + water.priority().name() + "): " + water.headline());
        }
        if (!low.isEmpty()) {
            actions.add("Restock " + low.size() + " low-inventory SKU(s) (e.g. "
                    + low.get(0).name() + ").");
        }
        if (weekHarvest <= 0) {
            actions.add("No harvest logged in 7 days — open Harvest Log after morning cut.");
        } else if (!beds.beds().isEmpty()) {
            BedProduction top = beds.beds().get(0);
            actions.add("Top bed this week: " + top.bed() + " ("
                    + String.format(Locale.US, "%.0f", top.totalQuantity()) + " qty).");
        }
        return actions;
    }

    private static String formatPlainText(LocalDate date, String time,
                                          MarketDayPlan pack,
                                          BedProductionReport beds,
                                          IrrigationAdvice water,
                                          List<LowStockLine> low,
                                          double weekHarvest,
                                          OrderService.WeekRevenueSummary rev,
                                          List<String> actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("MORNING BRIEFING — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("Date: ").append(date).append("  ·  ").append(time)
                .append(" America/Los_Angeles\n");
        sb.append(String.format(Locale.US,
                "Week harvest: %,.0f   Realized $%,.2f   Pipeline $%,.2f%n",
                weekHarvest, rev.realized(), rev.pipeline()));
        sb.append('\n');

        sb.append("PRIORITY ACTIONS\n");
        sb.append("────────────────\n");
        if (actions.isEmpty()) {
            sb.append("  All clear — no urgent flags.\n");
        } else {
            int i = 1;
            for (String a : actions) {
                sb.append("  ").append(i++).append(". ").append(a).append('\n');
            }
        }
        sb.append('\n');

        sb.append("MARKET DAY\n");
        sb.append("──────────\n");
        sb.append(String.format(Locale.US,
                "  Orders: %d  ·  Pipeline $%,.2f  ·  Shortfalls: %d%n",
                pack.orderCount(), pack.pipelineValue(), pack.shortfallSkuCount()));
        for (ProductNeed p : pack.pickList()) {
            if (p.shortfall()) {
                sb.append(String.format(Locale.US,
                        "  SHORT %-24s need %.1f stock %d%n",
                        p.productName(), p.neededQty(), p.stockOnHand()));
            }
        }
        sb.append('\n');

        sb.append("TOP BEDS (7d)\n");
        sb.append("─────────────\n");
        if (beds.beds().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            int r = 1;
            for (BedProduction b : beds.beds()) {
                if (r > 5) {
                    break;
                }
                sb.append(String.format(Locale.US, "  %d. %-16s %8.1f%n",
                        r++, b.bed(), b.totalQuantity()));
            }
        }
        sb.append('\n');

        sb.append("IRRIGATION\n");
        sb.append("──────────\n");
        sb.append("  ").append(water.priority().name()).append(" · ").append(water.mode())
                .append('\n');
        sb.append("  ").append(water.headline()).append('\n');
        sb.append('\n');

        sb.append("LOW STOCK (≤10)\n");
        sb.append("───────────────\n");
        if (low.isEmpty()) {
            sb.append("  (healthy)\n");
        } else {
            for (LowStockLine l : low) {
                sb.append(String.format(Locale.US, "  %-24s %4d %s%n",
                        l.name(), l.quantity(), l.unit()));
            }
        }
        sb.append('\n');
        sb.append("Tip: open Market Day packing PDF + Harvest Log after the cut.\n");
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

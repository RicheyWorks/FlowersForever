package com.flowerfarm.service;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Market-morning packing list for Kitsap / Port Orchard growers.
 *
 * <p>Pulls CONFIRMED (and optionally DRAFT) orders for a date — or a trailing
 * window when {@code date} is null — aggregates a product pick list against
 * inventory, and formats per-customer packing slips for the van load-out.
 */
@Service
public class MarketDayPackingService {

    private static final Logger log = LoggerFactory.getLogger(MarketDayPackingService.class);

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public MarketDayPackingService(OrderService orderService, InventoryService inventoryService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    public record PackLine(
            String productName,
            String unit,
            double quantity,
            double unitPrice,
            double lineTotal
    ) {}

    public record CustomerPack(
            long orderId,
            String customerName,
            String customerType,
            String status,
            LocalDate orderDate,
            String notes,
            List<PackLine> lines,
            double orderTotal
    ) {}

    public record ProductNeed(
            String productName,
            String unit,
            double neededQty,
            int stockOnHand,
            boolean shortfall,
            double shortfallQty
    ) {}

    public record MarketDayPlan(
            LocalDate marketDate,
            LocalDate from,
            LocalDate to,
            String scope,
            int orderCount,
            double pipelineValue,
            List<CustomerPack> customers,
            List<ProductNeed> pickList,
            int shortfallSkuCount,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("marketDate", marketDate.toString());
            m.put("from", from.toString());
            m.put("to", to.toString());
            m.put("scope", scope);
            m.put("orderCount", orderCount);
            m.put("pipelineValue", pipelineValue);
            m.put("shortfallSkuCount", shortfallSkuCount);
            m.put("pickList", pickList.stream().map(p -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productName", p.productName());
                row.put("unit", p.unit());
                row.put("neededQty", p.neededQty());
                row.put("stockOnHand", p.stockOnHand());
                row.put("shortfall", p.shortfall());
                row.put("shortfallQty", p.shortfallQty());
                return row;
            }).toList());
            m.put("customers", customers.stream().map(c -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderId", c.orderId());
                row.put("customerName", c.customerName());
                row.put("customerType", c.customerType());
                row.put("status", c.status());
                row.put("orderDate", c.orderDate().toString());
                row.put("notes", c.notes());
                row.put("orderTotal", c.orderTotal());
                row.put("lines", c.lines().stream().map(l -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("productName", l.productName());
                    line.put("unit", l.unit());
                    line.put("quantity", l.quantity());
                    line.put("unitPrice", l.unitPrice());
                    line.put("lineTotal", l.lineTotal());
                    return line;
                }).toList());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Build packing plan.
     *
     * @param marketDate focus date (default today); when {@code windowDays} &gt; 0, includes
     *                   orders from {@code marketDate - windowDays + 1} through {@code marketDate}
     * @param windowDays 1 = single day only; 7 = trailing week ending marketDate
     * @param includeDraft include DRAFT orders (default false)
     * @param includeFulfilled include already-fulfilled (default false — usually already packed)
     */
    @Transactional(readOnly = true)
    public MarketDayPlan buildPlan(LocalDate marketDate, int windowDays,
                                   boolean includeDraft, boolean includeFulfilled) {
        LocalDate day = marketDate != null ? marketDate : LocalDate.now();
        int window = windowDays <= 0 ? 1 : windowDays;
        LocalDate from = day.minusDays(window - 1L);
        LocalDate to = day;

        List<CustomerOrder> orders = orderService.findBetween(from, to).stream()
                .filter(o -> includeStatus(o.getStatus(), includeDraft, includeFulfilled))
                .sorted(Comparator
                        .comparing((CustomerOrder o) -> o.getCustomer() != null
                                ? o.getCustomer().getName() : "", String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CustomerOrder::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<CustomerPack> packs = new ArrayList<>();
        Map<String, Agg> needs = new LinkedHashMap<>();

        for (CustomerOrder order : orders) {
            List<PackLine> lines = new ArrayList<>();
            for (OrderLine line : order.getLines()) {
                PackLine pl = new PackLine(
                        line.getProductName(),
                        line.getUnit(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.lineTotal());
                lines.add(pl);
                String key = normalizeKey(line.getProductName()) + "|" + normalizeKey(line.getUnit());
                Agg agg = needs.computeIfAbsent(key, k -> new Agg(line.getProductName(), line.getUnit()));
                agg.qty += line.getQuantity();
            }
            String custName = order.getCustomer() != null ? order.getCustomer().getName() : "(unknown)";
            String custType = order.getCustomer() != null ? order.getCustomer().getCustomerType() : "";
            packs.add(new CustomerPack(
                    order.getId() != null ? order.getId() : 0L,
                    custName,
                    custType == null ? "" : custType,
                    order.getStatus(),
                    order.getOrderDate(),
                    order.getNotes() == null ? "" : order.getNotes(),
                    List.copyOf(lines),
                    order.lineTotal()
            ));
        }

        List<Item> inventory = inventoryService.getAllItems();
        List<ProductNeed> pickList = new ArrayList<>();
        int shortfalls = 0;
        for (Agg agg : needs.values()) {
            int stock = stockForProduct(inventory, agg.productName);
            double shortBy = Math.max(0, agg.qty - stock);
            boolean shortfall = shortBy > 0.0001;
            if (shortfall) {
                shortfalls++;
            }
            pickList.add(new ProductNeed(
                    agg.productName,
                    agg.unit,
                    round1(agg.qty),
                    stock,
                    shortfall,
                    round1(shortBy)
            ));
        }
        pickList.sort(Comparator.comparing(ProductNeed::productName, String.CASE_INSENSITIVE_ORDER));

        double pipeline = packs.stream().mapToDouble(CustomerPack::orderTotal).sum();
        String scope = describeScope(includeDraft, includeFulfilled);
        String text = formatPlainText(day, from, to, scope, packs, pickList, pipeline, shortfalls);

        return new MarketDayPlan(
                day, from, to, scope,
                packs.size(),
                round2(pipeline),
                List.copyOf(packs),
                List.copyOf(pickList),
                shortfalls,
                text
        );
    }

    /** Convenience: confirmed pipeline for a single calendar day. */
    @Transactional(readOnly = true)
    public MarketDayPlan planForDay(LocalDate marketDate) {
        return buildPlan(marketDate, 1, false, false);
    }

    /**
     * Printable packing PDF: brand banner, pick list with shortfalls, per-customer slips.
     */
    public byte[] generatePackingPdf(MarketDayPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan is required.");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Color shortRed = new Color(255, 235, 230);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap County  ·  Market Day Pack",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Market Day Packing List", titleFont));
            String period = plan.from().equals(plan.to())
                    ? plan.marketDate().toString()
                    : plan.from() + " → " + plan.to();
            doc.add(new Paragraph(
                    "Date: " + period + "     ·     Scope: " + plan.scope()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Orders", String.valueOf(plan.orderCount()), brandSoft));
            summary.addCell(summaryCell("Pipeline $",
                    String.format(Locale.US, "%,.2f", plan.pipelineValue()), brandSoft));
            summary.addCell(summaryCell("Shortfalls",
                    String.valueOf(plan.shortfallSkuCount()),
                    plan.shortfallSkuCount() > 0 ? shortRed : brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Pick list (aggregate vs stock)", h2));
            doc.add(Chunk.NEWLINE);
            if (plan.pickList().isEmpty()) {
                doc.add(new Paragraph(
                        "No CONFIRMED orders in range — create CRM orders first.", body));
            } else {
                PdfPTable pick = new PdfPTable(new float[]{3, 1.2f, 1.2f, 1.2f, 1.5f});
                pick.setWidthPercentage(100);
                headerCell(pick, "Product");
                headerCell(pick, "Need");
                headerCell(pick, "Unit");
                headerCell(pick, "Stock");
                headerCell(pick, "Status");
                for (ProductNeed p : plan.pickList()) {
                    Color rowBg = p.shortfall() ? shortRed : Color.WHITE;
                    pick.addCell(cell(p.productName(), body, rowBg));
                    pick.addCell(cell(String.format(Locale.US, "%.1f", p.neededQty()), body, rowBg));
                    pick.addCell(cell(p.unit(), body, rowBg));
                    pick.addCell(cell(String.valueOf(p.stockOnHand()), body, rowBg));
                    pick.addCell(cell(p.shortfall()
                            ? "SHORT " + String.format(Locale.US, "%.1f", p.shortfallQty())
                            : "OK", body, rowBg));
                }
                doc.add(pick);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("2. Pack slips by customer", h2));
            doc.add(Chunk.NEWLINE);
            if (plan.customers().isEmpty()) {
                doc.add(new Paragraph("(none)", body));
            } else {
                for (CustomerPack c : plan.customers()) {
                    String head = "#" + c.orderId() + "  " + c.customerName();
                    if (c.customerType() != null && !c.customerType().isBlank()) {
                        head += "  [" + c.customerType() + "]";
                    }
                    head += "  " + c.status() + "  $"
                            + String.format(Locale.US, "%.2f", c.orderTotal());
                    doc.add(new Paragraph(head,
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, brandGreen)));
                    if (c.notes() != null && !c.notes().isBlank()) {
                        doc.add(new Paragraph("Notes: " + c.notes(), small));
                    }
                    if (c.lines().isEmpty()) {
                        doc.add(new Paragraph("  (no lines)", small));
                    } else {
                        PdfPTable lines = new PdfPTable(new float[]{3, 1.2f, 1.2f, 1.2f, 1.4f});
                        lines.setWidthPercentage(100);
                        headerCell(lines, "Product");
                        headerCell(lines, "Qty");
                        headerCell(lines, "Unit");
                        headerCell(lines, "Price");
                        headerCell(lines, "Line $");
                        for (PackLine l : c.lines()) {
                            lines.addCell(cell(l.productName(), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.1f", l.quantity()), body, Color.WHITE));
                            lines.addCell(cell(l.unit(), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.2f", l.unitPrice()), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.2f", l.lineTotal()), body, Color.WHITE));
                        }
                        doc.add(lines);
                    }
                    doc.add(Chunk.NEWLINE);
                }
            }

            doc.add(new Paragraph(
                    "Tip: Fulfill in CRM after the market to deduct inventory & record revenue.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated market-day packing PDF for {} ({} orders, {} shortfalls)",
                    plan.marketDate(), plan.orderCount(), plan.shortfallSkuCount());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build packing PDF: " + e.getMessage(), e);
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
        cell.setBackgroundColor(bg != null ? bg : Color.WHITE);
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

    public String exportCsv(MarketDayPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("section,orderId,customer,status,product,unit,qty,unitPrice,lineTotal,stock,shortfall\n");
        for (ProductNeed p : plan.pickList()) {
            sb.append("PICK,,,").append(csv(p.productName())).append(',')
                    .append(csv(p.unit())).append(',')
                    .append(p.neededQty()).append(",,,")
                    .append(p.stockOnHand()).append(',')
                    .append(p.shortfall() ? p.shortfallQty() : 0)
                    .append('\n');
        }
        for (CustomerPack c : plan.customers()) {
            if (c.lines().isEmpty()) {
                sb.append("ORDER,").append(c.orderId()).append(',')
                        .append(csv(c.customerName())).append(',')
                        .append(csv(c.status())).append(",,,,,,\n");
                continue;
            }
            for (PackLine l : c.lines()) {
                sb.append("ORDER,").append(c.orderId()).append(',')
                        .append(csv(c.customerName())).append(',')
                        .append(csv(c.status())).append(',')
                        .append(csv(l.productName())).append(',')
                        .append(csv(l.unit())).append(',')
                        .append(l.quantity()).append(',')
                        .append(l.unitPrice()).append(',')
                        .append(l.lineTotal()).append(",,\n");
            }
        }
        return sb.toString();
    }

    private static boolean includeStatus(String status, boolean includeDraft, boolean includeFulfilled) {
        if (status == null) {
            return false;
        }
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            return true;
        }
        if (includeDraft && "DRAFT".equalsIgnoreCase(status)) {
            return true;
        }
        if (includeFulfilled && "FULFILLED".equalsIgnoreCase(status)) {
            return true;
        }
        return false;
    }

    private static String describeScope(boolean draft, boolean fulfilled) {
        StringBuilder s = new StringBuilder("CONFIRMED");
        if (draft) {
            s.append("+DRAFT");
        }
        if (fulfilled) {
            s.append("+FULFILLED");
        }
        return s.toString();
    }

    private static int stockForProduct(List<Item> inventory, String productName) {
        if (productName == null || productName.isBlank()) {
            return 0;
        }
        String target = productName.trim().toLowerCase(Locale.ROOT);
        // Prefer exact name match, then starts-with / contains (same spirit as fulfill)
        int exact = 0;
        int fuzzy = 0;
        boolean anyExact = false;
        for (Item item : inventory) {
            if (item.getName() == null) {
                continue;
            }
            String n = item.getName().trim().toLowerCase(Locale.ROOT);
            if (n.equals(target)) {
                exact += item.getQuantity();
                anyExact = true;
            } else if (n.contains(target) || target.contains(n)) {
                fuzzy += item.getQuantity();
            }
        }
        return anyExact ? exact : fuzzy;
    }

    private static String formatPlainText(LocalDate marketDate, LocalDate from, LocalDate to,
                                          String scope, List<CustomerPack> packs,
                                          List<ProductNeed> pickList, double pipeline,
                                          int shortfalls) {
        StringBuilder sb = new StringBuilder();
        sb.append("MARKET DAY PACKING LIST — Port Orchard / Kitsap County\n");
        sb.append("══════════════════════════════════════════════════════\n");
        sb.append("Market date : ").append(marketDate).append('\n');
        if (!from.equals(to)) {
            sb.append("Orders from : ").append(from).append(" → ").append(to).append('\n');
        }
        sb.append("Scope       : ").append(scope).append('\n');
        sb.append("Orders      : ").append(packs.size())
                .append("   Pipeline $").append(String.format(Locale.US, "%,.2f", pipeline)).append('\n');
        if (shortfalls > 0) {
            sb.append("⚠ Shortfalls: ").append(shortfalls).append(" product(s) under stock\n");
        }
        sb.append('\n');

        sb.append("PICK LIST (aggregate)\n");
        sb.append("─────────────────────\n");
        if (pickList.isEmpty()) {
            sb.append("  (no confirmed orders in range — create CONFIRMED CRM orders first)\n");
        } else {
            sb.append(String.format(Locale.US, "  %-28s %8s %-10s %8s %s%n",
                    "Product", "Need", "Unit", "Stock", "Status"));
            for (ProductNeed p : pickList) {
                String flag = p.shortfall()
                        ? "SHORT " + String.format(Locale.US, "%.1f", p.shortfallQty())
                        : "OK";
                sb.append(String.format(Locale.US, "  %-28s %8.1f %-10s %8d %s%n",
                        truncate(p.productName(), 28), p.neededQty(), p.unit(),
                        p.stockOnHand(), flag));
            }
        }

        sb.append('\n');
        sb.append("BY CUSTOMER (pack slips)\n");
        sb.append("───────────────────────\n");
        if (packs.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (CustomerPack c : packs) {
                sb.append('\n');
                sb.append("  #").append(c.orderId()).append("  ")
                        .append(c.customerName());
                if (c.customerType() != null && !c.customerType().isBlank()) {
                    sb.append("  [").append(c.customerType()).append(']');
                }
                sb.append("  ").append(c.status())
                        .append("  $").append(String.format(Locale.US, "%.2f", c.orderTotal()))
                        .append('\n');
                if (c.notes() != null && !c.notes().isBlank()) {
                    sb.append("    notes: ").append(c.notes()).append('\n');
                }
                for (PackLine l : c.lines()) {
                    sb.append(String.format(Locale.US, "    • %-24s %6.1f %-8s @ $%.2f  = $%.2f%n",
                            truncate(l.productName(), 24), l.quantity(), l.unit(),
                            l.unitPrice(), l.lineTotal()));
                }
            }
        }
        sb.append('\n');
        sb.append("Tip: Fulfill in CRM after the market to deduct inventory & record revenue.\n");
        return sb.toString();
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class Agg {
        final String productName;
        final String unit;
        double qty;

        Agg(String productName, String unit) {
            this.productName = productName;
            this.unit = unit == null ? "" : unit;
        }
    }
}

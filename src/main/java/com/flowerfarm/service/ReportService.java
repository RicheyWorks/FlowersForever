package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final FarmProfile farmProfile;

    public ReportService(HarvestService harvestService, OrderService orderService,
                         FarmProfile farmProfile) {
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.farmProfile = farmProfile;
    }

    /** Weekly report ending today (inclusive), 7 days total. */
    @Transactional(readOnly = true)
    public byte[] generateWeeklyReportPdf() {
        LocalDate to = LocalDate.now(farmProfile.zoneId());
        LocalDate from = to.minusDays(6);
        return generateReportPdf(from, to);
    }

    /**
     * Builds the same headline sales and activity measures used by the PDF so
     * workers can verify a date range before choosing a destination file.
     */
    @Transactional(readOnly = true)
    public ReportPreview preview(LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<HarvestEntry> harvests = harvestService.findBetween(from, to);
        List<CustomerOrder> orders = orderService.findBetween(from, to);
        int confirmedOrders = 0;
        int fulfilledOrders = 0;
        double committedRevenue = 0;
        double fulfilledRevenue = 0;
        for (CustomerOrder order : orders) {
            String status = order.getStatus();
            if ("CONFIRMED".equalsIgnoreCase(status)) {
                confirmedOrders++;
                committedRevenue += order.lineTotal();
            } else if ("FULFILLED".equalsIgnoreCase(status)) {
                fulfilledOrders++;
                committedRevenue += order.lineTotal();
                fulfilledRevenue += order.lineTotal();
            }
        }
        SalesPerformance performance = analyzeSales(orders);
        return new ReportPreview(
                from, to, harvests.size(), orders.size(), confirmedOrders, fulfilledOrders,
                committedRevenue, fulfilledRevenue,
                performance.products(), performance.customers());
    }

    @Transactional(readOnly = true)
    public byte[] generateReportPdf(LocalDate from, LocalDate to) {
        validateRange(from, to);

        List<HarvestEntry> harvests = harvestService.findBetween(from, to);
        List<CustomerOrder> orders = orderService.findBetween(from, to);
        Map<String, Double> harvestTotals = harvestService.totalsByCrop(); // season-to-date snapshot
        double committedRevenue = orders.stream()
                .filter(o -> {
                    String s = o.getStatus();
                    return "FULFILLED".equalsIgnoreCase(s) || "CONFIRMED".equalsIgnoreCase(s);
                })
                .mapToDouble(CustomerOrder::lineTotal)
                .sum();
        double fulfilledRevenue = orders.stream()
                .filter(o -> "FULFILLED".equalsIgnoreCase(o.getStatus()))
                .mapToDouble(CustomerOrder::lineTotal)
                .sum();
        SalesPerformance performance = analyzeSales(orders);

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
                    farmProfile.documentBanner("Farm Operations Report"),
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
                            + "     ·     Generated: "
                            + DAY.format(LocalDate.now(farmProfile.zoneId())),
                    small));
            doc.add(Chunk.NEWLINE);

            // Summary strip
            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Harvest entries", String.valueOf(harvests.size()), brandSoft, body));
            summary.addCell(summaryCell("Orders", String.valueOf(orders.size()), brandSoft, body));
            summary.addCell(summaryCell("Committed revenue",
                    String.format("$%,.2f", committedRevenue), brandSoft, body));
            summary.addCell(summaryCell("Fulfilled revenue",
                    String.format("$%,.2f", fulfilledRevenue), brandSoft, body));
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

            // ── Bed / field production (period) ──────────────────────────────
            HarvestService.BedProductionReport bedReport =
                    harvestService.productionByBed(from, to);
            doc.add(new Paragraph("2. Bed / field production (this period)", h2));
            doc.add(new Paragraph(
                    bedReport.bedCount() == 0
                            ? "No bed-tagged harvests in this period (set Bed / field when logging cuts)."
                            : bedReport.bedCount() + " bed(s) · " + bedReport.entryCount()
                            + " log(s) · total qty " + String.format("%,.1f", bedReport.grandTotal()),
                    body));
            doc.add(Chunk.NEWLINE);
            if (!bedReport.beds().isEmpty()) {
                PdfPTable bedTable = new PdfPTable(new float[]{2.5f, 1.5f, 1.2f, 2.5f, 2.5f});
                bedTable.setWidthPercentage(100);
                headerCell(bedTable, "Bed / Field");
                headerCell(bedTable, "Qty");
                headerCell(bedTable, "Logs");
                headerCell(bedTable, "Top crop");
                headerCell(bedTable, "Dates");
                for (HarvestService.BedProduction b : bedReport.beds()) {
                    String topCrop = "—";
                    if (!b.byCrop().isEmpty()) {
                        Map.Entry<String, Double> first = b.byCrop().entrySet().iterator().next();
                        topCrop = first.getKey() + " (" + String.format("%.0f", first.getValue()) + ")";
                    }
                    bedTable.addCell(cell(b.bed(), body));
                    bedTable.addCell(cell(String.format("%.1f", b.totalQuantity()), body));
                    bedTable.addCell(cell(String.valueOf(b.entryCount()), body));
                    bedTable.addCell(cell(topCrop, body));
                    bedTable.addCell(cell(b.firstDate() + " → " + b.lastDate(), body));
                }
                doc.add(bedTable);
            }

            doc.add(Chunk.NEWLINE);

            // ── Sales section ────────────────────────────────────────────────
            doc.add(new Paragraph("3. Sales / wholesale orders", h2));
            doc.add(new Paragraph(
                    orders.isEmpty()
                            ? "No orders in this period."
                            : orders.size() + " order(s) · confirmed/fulfilled revenue: $"
                            + String.format("%,.2f", committedRevenue),
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

            if (!performance.products().isEmpty()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("4. Product performance", h2));
                doc.add(new Paragraph(
                        "Confirmed and fulfilled sales ranked by committed revenue.", body));
                doc.add(Chunk.NEWLINE);
                PdfPTable products = new PdfPTable(
                        new float[]{2.5f, 1.5f, 0.8f, 1.2f, 1.3f, 1.3f});
                products.setWidthPercentage(100);
                headerCell(products, "Product");
                headerCell(products, "Qty / unit");
                headerCell(products, "Orders");
                headerCell(products, "Avg price");
                headerCell(products, "Committed");
                headerCell(products, "Fulfilled");
                for (ProductPerformance product : performance.products()) {
                    products.addCell(cell(product.productName(), body));
                    products.addCell(cell(String.format(Locale.US, "%,.1f %s",
                            product.quantity(), product.unit()), body));
                    products.addCell(cell(String.valueOf(product.orderCount()), body));
                    products.addCell(cell(String.format(Locale.US, "$%,.2f",
                            product.averageUnitPrice()), body));
                    products.addCell(cell(String.format(Locale.US, "$%,.2f",
                            product.committedRevenue()), body));
                    products.addCell(cell(String.format(Locale.US, "$%,.2f",
                            product.fulfilledRevenue()), body));
                }
                doc.add(products);
            }

            if (!performance.customers().isEmpty()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("5. Customer performance", h2));
                doc.add(new Paragraph(
                        "Confirmed and fulfilled sales ranked by committed revenue.", body));
                doc.add(Chunk.NEWLINE);
                PdfPTable customers = new PdfPTable(new float[]{3, 1, 1.5f, 1.5f, 1.5f});
                customers.setWidthPercentage(100);
                headerCell(customers, "Customer");
                headerCell(customers, "Orders");
                headerCell(customers, "Avg order");
                headerCell(customers, "Committed");
                headerCell(customers, "Fulfilled");
                for (CustomerPerformance customer : performance.customers()) {
                    customers.addCell(cell(customer.customerName(), body));
                    customers.addCell(cell(String.valueOf(customer.orderCount()), body));
                    customers.addCell(cell(String.format(Locale.US, "$%,.2f",
                            customer.averageOrderValue()), body));
                    customers.addCell(cell(String.format(Locale.US, "$%,.2f",
                            customer.committedRevenue()), body));
                    customers.addCell(cell(String.format(Locale.US, "$%,.2f",
                            customer.fulfilledRevenue()), body));
                }
                doc.add(customers);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(farmProfile.softwareFooter(), small));

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

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
    }

    private static SalesPerformance analyzeSales(List<CustomerOrder> orders) {
        Map<String, ProductAccumulator> productTotals = new LinkedHashMap<>();
        Map<String, CustomerAccumulator> customerTotals = new LinkedHashMap<>();
        for (CustomerOrder order : orders == null ? List.<CustomerOrder>of() : orders) {
            boolean fulfilled = "FULFILLED".equalsIgnoreCase(order.getStatus());
            boolean confirmed = "CONFIRMED".equalsIgnoreCase(order.getStatus());
            if (!fulfilled && !confirmed) {
                continue;
            }
            String customerName = order.getCustomer() == null
                    || order.getCustomer().getName() == null
                    || order.getCustomer().getName().isBlank()
                    ? "Unknown customer" : order.getCustomer().getName().trim();
            CustomerAccumulator customer = customerTotals.computeIfAbsent(
                    customerName.toLowerCase(Locale.ROOT),
                    ignored -> new CustomerAccumulator(customerName));
            customer.orderCount++;
            customer.committedRevenue += order.lineTotal();
            if (fulfilled) {
                customer.fulfilledRevenue += order.lineTotal();
            }

            Set<String> seenProducts = new HashSet<>();
            for (OrderLine line : order.getLines()) {
                String productName = line.getProductName() == null
                        || line.getProductName().isBlank()
                        ? "Unknown product" : line.getProductName().trim();
                String unit = line.getUnit() == null || line.getUnit().isBlank()
                        ? "unit" : line.getUnit().trim();
                String key = productName.toLowerCase(Locale.ROOT) + "\u0000"
                        + unit.toLowerCase(Locale.ROOT);
                ProductAccumulator product = productTotals.computeIfAbsent(
                        key, ignored -> new ProductAccumulator(productName, unit));
                product.quantity += line.getQuantity();
                product.committedRevenue += line.lineTotal();
                if (fulfilled) {
                    product.fulfilledRevenue += line.lineTotal();
                }
                if (seenProducts.add(key)) {
                    product.orderCount++;
                }
            }
        }

        List<ProductPerformance> products = new ArrayList<>();
        for (ProductAccumulator product : productTotals.values()) {
            products.add(new ProductPerformance(
                    product.productName, product.unit, product.quantity, product.orderCount,
                    product.committedRevenue, product.fulfilledRevenue,
                    product.quantity == 0 ? 0
                            : product.committedRevenue / product.quantity));
        }
        products.sort(Comparator.comparingDouble(ProductPerformance::committedRevenue)
                .reversed().thenComparing(ProductPerformance::productName,
                        String.CASE_INSENSITIVE_ORDER));

        List<CustomerPerformance> customers = new ArrayList<>();
        for (CustomerAccumulator customer : customerTotals.values()) {
            customers.add(new CustomerPerformance(
                    customer.customerName, customer.orderCount,
                    customer.committedRevenue, customer.fulfilledRevenue,
                    customer.orderCount == 0 ? 0
                            : customer.committedRevenue / customer.orderCount));
        }
        customers.sort(Comparator.comparingDouble(CustomerPerformance::committedRevenue)
                .reversed().thenComparing(CustomerPerformance::customerName,
                        String.CASE_INSENSITIVE_ORDER));
        return new SalesPerformance(List.copyOf(products), List.copyOf(customers));
    }

    /** Headline measures for the selected reporting period. */
    public record ReportPreview(
            LocalDate from,
            LocalDate to,
            int harvestEntries,
            int orders,
            int confirmedOrders,
            int fulfilledOrders,
            double committedRevenue,
            double fulfilledRevenue,
            List<ProductPerformance> products,
            List<CustomerPerformance> customers
    ) {
        public ReportPreview {
            products = products == null ? List.of() : List.copyOf(products);
            customers = customers == null ? List.of() : List.copyOf(customers);
        }

        public ReportPreview(LocalDate from, LocalDate to, int harvestEntries, int orders,
                             int confirmedOrders, int fulfilledOrders,
                             double committedRevenue, double fulfilledRevenue) {
            this(from, to, harvestEntries, orders, confirmedOrders, fulfilledOrders,
                    committedRevenue, fulfilledRevenue, List.of(), List.of());
        }
    }

    /** One product/unit combination ranked inside a selected sales period. */
    public record ProductPerformance(
            String productName,
            String unit,
            double quantity,
            int orderCount,
            double committedRevenue,
            double fulfilledRevenue,
            double averageUnitPrice
    ) { }

    /** One customer ranked inside a selected sales period. */
    public record CustomerPerformance(
            String customerName,
            int orderCount,
            double committedRevenue,
            double fulfilledRevenue,
            double averageOrderValue
    ) { }

    private record SalesPerformance(
            List<ProductPerformance> products,
            List<CustomerPerformance> customers
    ) { }

    private static final class ProductAccumulator {
        private final String productName;
        private final String unit;
        private double quantity;
        private int orderCount;
        private double committedRevenue;
        private double fulfilledRevenue;

        private ProductAccumulator(String productName, String unit) {
            this.productName = productName;
            this.unit = unit;
        }
    }

    private static final class CustomerAccumulator {
        private final String customerName;
        private int orderCount;
        private double committedRevenue;
        private double fulfilledRevenue;

        private CustomerAccumulator(String customerName) {
            this.customerName = customerName;
        }
    }
}

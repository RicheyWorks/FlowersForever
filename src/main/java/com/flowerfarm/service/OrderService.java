package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.repository.CustomerJpaRepository;
import com.flowerfarm.repository.CustomerOrderJpaRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderJpaRepository orderRepository;
    private final CustomerJpaRepository customerRepository;
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;

    public OrderService(CustomerOrderJpaRepository orderRepository,
                        CustomerJpaRepository customerRepository,
                        InventoryService inventoryService,
                        SyncHistoryService syncHistoryService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> getAll() {
        return new ArrayList<>(orderRepository.findAllByOrderByOrderDateDescIdDesc());
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> findBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
        return new ArrayList<>(orderRepository.findByOrderDateBetweenOrderByOrderDateDescIdDesc(from, to));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> findByCustomer(Long customerId) {
        return new ArrayList<>(orderRepository.findByCustomerIdOrderByOrderDateDescIdDesc(customerId));
    }

    /**
     * Create an order for an existing customer with zero or more lines.
     */
    @Transactional
    public CustomerOrder create(Long customerId, LocalDate orderDate, String status, String notes,
                                List<OrderLine> lines) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + customerId));
        CustomerOrder order = new CustomerOrder(customer, orderDate, status, notes);
        if (lines != null) {
            for (OrderLine line : lines) {
                order.addLine(new OrderLine(
                        line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
            }
        }
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm",
                "ORDER_CREATE",
                true,
                "Order #" + saved.getId() + " created for " + customer.getName(),
                "status=" + saved.getStatus() + ", lines=" + saved.getLines().size()
                        + ", total=$" + String.format("%.2f", saved.lineTotal()),
                saved.getLines().size()
        );
        return saved;
    }

    @Transactional
    public CustomerOrder updateStatus(Long orderId, String status) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        String next = status == null ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
        String previous = order.getStatus();
        if ("FULFILLED".equals(next) && !"FULFILLED".equalsIgnoreCase(previous)) {
            return fulfill(orderId);
        }
        if (next.equalsIgnoreCase(previous)) {
            return order;
        }
        // Prevent reverse inventory thrash: cannot un-fulfill via status alone
        if ("FULFILLED".equalsIgnoreCase(previous) && !"FULFILLED".equals(next)) {
            throw new IllegalArgumentException(
                    "Cannot change status of a FULFILLED order (inventory already deducted).");
        }
        order.setStatus(next);
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm",
                "ORDER_STATUS",
                true,
                "Order #" + orderId + " status " + previous + " → " + saved.getStatus(),
                "notes=" + saved.getNotes(),
                1
        );
        log.info("[crm] Order #{} status {} → {}", orderId, previous, saved.getStatus());
        return saved;
    }

    /** DRAFT → CONFIRMED (pipeline). No inventory change. */
    @Transactional
    public CustomerOrder confirm(Long orderId) {
        return updateStatus(orderId, "CONFIRMED");
    }

    /** Mark CANCELLED if not already fulfilled. */
    @Transactional
    public CustomerOrder cancel(Long orderId) {
        return updateStatus(orderId, "CANCELLED");
    }

    @Transactional
    public CustomerOrder updateNotes(Long orderId, String notes) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        order.setNotes(notes);
        return orderRepository.save(order);
    }

    /**
     * Filter helper for CRM UI / API. Blank status or customer → ignore that constraint.
     * Null from/to → unbounded on that side.
     */
    @Transactional(readOnly = true)
    public List<CustomerOrder> filter(String status, String customerQuery,
                                      LocalDate from, LocalDate to) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(st) || "*".equals(st)) {
            st = "";
        }
        String cq = customerQuery == null ? "" : customerQuery.trim().toLowerCase(Locale.ROOT);
        Stream<CustomerOrder> stream = getAll().stream();
        if (!st.isEmpty()) {
            String statusKey = st;
            stream = stream.filter(o -> o.getStatus() != null
                    && o.getStatus().equalsIgnoreCase(statusKey));
        }
        if (!cq.isEmpty()) {
            stream = stream.filter(o -> {
                if (o.getCustomer() == null || o.getCustomer().getName() == null) {
                    return false;
                }
                return o.getCustomer().getName().toLowerCase(Locale.ROOT).contains(cq);
            });
        }
        if (from != null) {
            stream = stream.filter(o -> o.getOrderDate() != null && !o.getOrderDate().isBefore(from));
        }
        if (to != null) {
            stream = stream.filter(o -> o.getOrderDate() != null && !o.getOrderDate().isAfter(to));
        }
        return stream.toList();
    }

    /** Export a filtered subset (e.g. current CRM filter). */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename, List<CustomerOrder> orders) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        if (orders == null) {
            orders = List.of();
        }
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(filename.trim(), java.nio.charset.StandardCharsets.UTF_8))) {
            bw.write("OrderId,Date,Customer,Status,LineCount,Total,Notes");
            bw.newLine();
            for (CustomerOrder o : orders) {
                String cust = o.getCustomer() == null ? "" : o.getCustomer().getName();
                String notes = o.getNotes() == null ? "" : o.getNotes().replace("\"", "\"\"");
                bw.write(String.format("%s,%s,%s,%s,%d,%.2f,\"%s\"",
                        o.getId(),
                        o.getOrderDate(),
                        csvEscape(cust),
                        o.getStatus(),
                        o.getLines() == null ? 0 : o.getLines().size(),
                        o.lineTotal(),
                        notes));
                bw.newLine();
            }
            log.info("Orders exported to '{}' ({} row(s)).", filename, orders.size());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Order export failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Marks the order FULFILLED, decrements matching inventory SKUs by product name,
     * and records a CRM audit event in sync history.
     */
    @Transactional
    public CustomerOrder fulfill(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));

        if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
            log.info("Order #{} already fulfilled — no inventory change.", orderId);
            return order;
        }

        int matched = 0;
        int missing = 0;
        StringBuilder detail = new StringBuilder();
        for (OrderLine line : order.getLines()) {
            int qty = (int) Math.round(line.getQuantity());
            var updated = inventoryService.decrementQuantityByName(line.getProductName(), qty);
            if (updated.isPresent()) {
                matched++;
                detail.append(line.getProductName()).append(" -").append(qty).append("; ");
            } else {
                missing++;
                detail.append(line.getProductName()).append(" (no inventory match); ");
                log.warn("Fulfill order #{}: no inventory SKU named '{}'", orderId, line.getProductName());
            }
        }

        order.setStatus("FULFILLED");
        CustomerOrder saved = orderRepository.save(order);

        String msg = "Order #" + orderId + " fulfilled — inventory matched " + matched
                + " line(s)" + (missing > 0 ? ", " + missing + " unmatched" : "");
        syncHistoryService.record(
                "crm",
                "ORDER_FULFILL",
                missing == 0,
                msg,
                detail.toString(),
                matched
        );
        log.info("[crm] {}", msg);
        return saved;
    }

    @Transactional
    public CustomerOrder addLine(Long orderId, OrderLine line) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (line == null) {
            throw new IllegalArgumentException("Line must not be null.");
        }
        order.addLine(new OrderLine(
                line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
        return orderRepository.save(order);
    }

    @Transactional
    public void delete(Long id) {
        if (orderRepository.findById(id).isEmpty()) {
            throw new IndexOutOfBoundsException("No order with id=" + id);
        }
        orderRepository.deleteById(id);
    }

    /**
     * Booked revenue (CONFIRMED + FULFILLED) between dates.
     * Prefer {@link #weekRevenueSummary()} on the dashboard for realized vs pipeline split.
     */
    @Transactional(readOnly = true)
    public double fulfilledRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, true, true, false);
    }

    /** Realized cash: FULFILLED orders only. */
    @Transactional(readOnly = true)
    public double realizedRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, true, false, false);
    }

    /** Pipeline: CONFIRMED orders not yet fulfilled. */
    @Transactional(readOnly = true)
    public double pipelineRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, false, true, false);
    }

    private double revenueBetween(LocalDate from, LocalDate to,
                                  boolean includeFulfilled, boolean includeConfirmed, boolean includeDraft) {
        return findBetween(from, to).stream()
                .filter(o -> matchesStatus(o.getStatus(), includeFulfilled, includeConfirmed, includeDraft))
                .mapToDouble(CustomerOrder::lineTotal)
                .sum();
    }

    private static boolean matchesStatus(String status, boolean fulfilled, boolean confirmed, boolean draft) {
        if (status == null) {
            return false;
        }
        if (fulfilled && "FULFILLED".equalsIgnoreCase(status)) {
            return true;
        }
        if (confirmed && "CONFIRMED".equalsIgnoreCase(status)) {
            return true;
        }
        return draft && "DRAFT".equalsIgnoreCase(status);
    }

    /**
     * Trailing 7-day revenue breakdown for dashboard KPIs.
     * {@code realized} = FULFILLED only; {@code pipeline} = CONFIRMED; {@code booked} = sum of both.
     */
    @Transactional(readOnly = true)
    public WeekRevenueSummary weekRevenueSummary() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        double realized = 0;
        double pipeline = 0;
        double draft = 0;
        int fulfilledCount = 0;
        int confirmedCount = 0;
        int draftCount = 0;
        for (CustomerOrder o : findBetween(from, to)) {
            double total = o.lineTotal();
            String s = o.getStatus();
            if ("FULFILLED".equalsIgnoreCase(s)) {
                realized += total;
                fulfilledCount++;
            } else if ("CONFIRMED".equalsIgnoreCase(s)) {
                pipeline += total;
                confirmedCount++;
            } else if ("DRAFT".equalsIgnoreCase(s)) {
                draft += total;
                draftCount++;
            }
        }
        return new WeekRevenueSummary(from, to, realized, pipeline, draft,
                fulfilledCount, confirmedCount, draftCount);
    }

    /**
     * Daily realized (FULFILLED) revenue for the last 7 days (index 0 = 6 days ago).
     * Use for accurate cash-trend sparklines.
     */
    @Transactional(readOnly = true)
    public double[] dailyRevenueLast7Days() {
        return dailyRevenueLast7Days(true, false);
    }

    /**
     * Daily revenue series. {@code includeFulfilled}/{@code includeConfirmed} control status filter.
     */
    @Transactional(readOnly = true)
    public double[] dailyRevenueLast7Days(boolean includeFulfilled, boolean includeConfirmed) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        java.util.Map<LocalDate, Double> byDay = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            byDay.put(from.plusDays(i), 0.0);
        }
        for (CustomerOrder o : findBetween(from, today)) {
            if (!matchesStatus(o.getStatus(), includeFulfilled, includeConfirmed, false)) {
                continue;
            }
            byDay.merge(o.getOrderDate(), o.lineTotal(), Double::sum);
        }
        double[] out = new double[7];
        int i = 0;
        for (double v : byDay.values()) {
            out[i++] = v;
        }
        return out;
    }

    /** Compact week revenue snapshot for API / dashboard. */
    public record WeekRevenueSummary(
            LocalDate from,
            LocalDate to,
            double realized,
            double pipeline,
            double draft,
            int fulfilledOrderCount,
            int confirmedOrderCount,
            int draftOrderCount
    ) {
        public double booked() {
            return realized + pipeline;
        }
    }

    /** Realized (FULFILLED) revenue for the prior 7-day window (days -13 … -7). */
    @Transactional(readOnly = true)
    public double realizedRevenuePrior7Days() {
        LocalDate to = LocalDate.now().minusDays(7);
        LocalDate from = to.minusDays(6);
        return realizedRevenueBetween(from, to);
    }

    /** Percent change in realized revenue vs prior week; null if prior was zero. */
    @Transactional(readOnly = true)
    public Double realizedWeekOverWeekPercentChange() {
        double current = weekRevenueSummary().realized();
        double prior = realizedRevenuePrior7Days();
        if (prior <= 0) {
            return null;
        }
        return ((current - prior) / prior) * 100.0;
    }

    /** Export all orders + line totals for accounting. */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename) {
        exportToCsv(filename, getAll());
    }

    /**
     * Wholesale / florist invoice (or packing slip) PDF for a single order.
     * Suitable for CONFIRMED, FULFILLED, or DRAFT — status is printed on the sheet.
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required.");
        }
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        return generateInvoicePdf(order);
    }

    public byte[] generateInvoicePdf(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required.");
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
                    "FlowersForever  ·  Port Orchard / Kitsap County  ·  Wholesale Invoice",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            String orderNo = order.getId() == null ? "—" : String.valueOf(order.getId());
            String status = order.getStatus() == null ? "DRAFT" : order.getStatus();
            doc.add(new Paragraph("Invoice / Order #" + orderNo, titleFont));
            doc.add(new Paragraph(
                    "Order date: " + order.getOrderDate()
                            + "     ·     Status: " + status
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            Customer c = order.getCustomer();
            String custName = c != null && c.getName() != null ? c.getName() : "(no customer)";
            String contact = c != null && c.getContactName() != null ? c.getContactName() : "";
            String email = c != null && c.getEmail() != null ? c.getEmail() : "";
            String phone = c != null && c.getPhone() != null ? c.getPhone() : "";
            String type = c != null && c.getCustomerType() != null ? c.getCustomerType() : "";

            PdfPTable parties = new PdfPTable(new float[]{1, 1});
            parties.setWidthPercentage(100);
            parties.addCell(infoBlock("Bill to / ship to",
                    custName
                            + (type.isBlank() ? "" : "  [" + type + "]")
                            + (contact.isBlank() ? "" : "\nAttn: " + contact)
                            + (email.isBlank() ? "" : "\n" + email)
                            + (phone.isBlank() ? "" : "\n" + phone),
                    brandSoft, body));
            parties.addCell(infoBlock("From",
                    "FlowersForever Farm\nPort Orchard / Kitsap County, WA\nPNW West of the Cascades",
                    brandSoft, body));
            doc.add(parties);
            doc.add(Chunk.NEWLINE);

            if (order.getNotes() != null && !order.getNotes().isBlank()) {
                doc.add(new Paragraph("Notes: " + order.getNotes(), body));
                doc.add(Chunk.NEWLINE);
            }

            doc.add(new Paragraph("Line items", h2));
            doc.add(Chunk.NEWLINE);
            List<OrderLine> lines = order.getLines() == null ? List.of() : order.getLines();
            if (lines.isEmpty()) {
                doc.add(new Paragraph("(no line items)", body));
            } else {
                PdfPTable table = new PdfPTable(new float[]{3.2f, 1.1f, 1.1f, 1.2f, 1.3f});
                table.setWidthPercentage(100);
                headerCell(table, "Product");
                headerCell(table, "Qty");
                headerCell(table, "Unit");
                headerCell(table, "Unit $");
                headerCell(table, "Line $");
                for (OrderLine line : lines) {
                    table.addCell(cell(line.getProductName(), body));
                    table.addCell(cell(String.format(Locale.US, "%.1f", line.getQuantity()), body));
                    table.addCell(cell(line.getUnit() == null ? "" : line.getUnit(), body));
                    table.addCell(cell(String.format(Locale.US, "%,.2f", line.getUnitPrice()), body));
                    table.addCell(cell(String.format(Locale.US, "%,.2f", line.lineTotal()), body));
                }
                doc.add(table);
            }
            doc.add(Chunk.NEWLINE);

            PdfPTable totalBox = new PdfPTable(1);
            totalBox.setWidthPercentage(40);
            totalBox.setHorizontalAlignment(Element.ALIGN_RIGHT);
            PdfPCell totalCell = new PdfPCell(new Phrase(
                    String.format(Locale.US, "Total  $%,.2f", order.lineTotal()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, brandGreen)));
            totalCell.setBackgroundColor(brandSoft);
            totalCell.setPadding(10);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setBorderColor(new Color(180, 200, 180));
            totalBox.addCell(totalCell);
            doc.add(totalBox);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph(
                    "Thank you for supporting local PNW cut flowers. "
                            + "Questions: farm@flowersforever.example (demo)",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated order invoice PDF for order #{} (${})",
                    orderNo, String.format(Locale.US, "%.2f", order.lineTotal()));
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build order invoice PDF: " + e.getMessage(), e);
        }
    }

    /** Plain-text invoice for CLI / quick view. */
    @Transactional(readOnly = true)
    public String formatInvoiceText(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required.");
        }
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        return formatInvoiceText(order);
    }

    public String formatInvoiceText(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required.");
        }
        StringBuilder sb = new StringBuilder();
        String orderNo = order.getId() == null ? "—" : String.valueOf(order.getId());
        Customer c = order.getCustomer();
        String cust = c != null && c.getName() != null ? c.getName() : "(no customer)";
        sb.append("INVOICE / ORDER #").append(orderNo).append('\n');
        sb.append("═══════════════════════════════════════\n");
        sb.append("Date: ").append(order.getOrderDate())
                .append("  ·  Status: ").append(order.getStatus()).append('\n');
        sb.append("Customer: ").append(cust);
        if (c != null && c.getCustomerType() != null && !c.getCustomerType().isBlank()) {
            sb.append(" [").append(c.getCustomerType()).append(']');
        }
        sb.append('\n');
        if (c != null) {
            if (c.getContactName() != null && !c.getContactName().isBlank()) {
                sb.append("Attn: ").append(c.getContactName()).append('\n');
            }
            if (c.getEmail() != null && !c.getEmail().isBlank()) {
                sb.append(c.getEmail()).append('\n');
            }
            if (c.getPhone() != null && !c.getPhone().isBlank()) {
                sb.append(c.getPhone()).append('\n');
            }
        }
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            sb.append("Notes: ").append(order.getNotes()).append('\n');
        }
        sb.append('\n');
        sb.append(String.format(Locale.US, "%-24s %8s %8s %10s %10s%n",
                "Product", "Qty", "Unit", "Unit $", "Line $"));
        sb.append("-".repeat(64)).append('\n');
        List<OrderLine> lines = order.getLines() == null ? List.of() : order.getLines();
        if (lines.isEmpty()) {
            sb.append("  (no lines)\n");
        } else {
            for (OrderLine line : lines) {
                sb.append(String.format(Locale.US, "%-24s %8.1f %8s %10.2f %10.2f%n",
                        truncate(line.getProductName(), 24),
                        line.getQuantity(),
                        truncate(line.getUnit() == null ? "" : line.getUnit(), 8),
                        line.getUnitPrice(),
                        line.lineTotal()));
            }
        }
        sb.append("-".repeat(64)).append('\n');
        sb.append(String.format(Locale.US, "TOTAL  $%,.2f%n", order.lineTotal()));
        sb.append("\nFlowersForever · Port Orchard / Kitsap County, WA\n");
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

    private static PdfPCell infoBlock(String title, String bodyText, Color bg, Font body) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(title + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10,
                new Color(34, 100, 54))));
        p.add(new Chunk(bodyText == null ? "" : bodyText, body));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(10);
        cell.setBorderColor(new Color(180, 200, 180));
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
}

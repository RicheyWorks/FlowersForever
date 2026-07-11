package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
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
 * Wholesale customer account statement — multi-order rollup for florists / markets.
 * Complements per-order {@link OrderService#generateInvoicePdf(Long)}.
 */
@Service
public class CustomerStatementService {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatementService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 90;

    private final CustomerService customerService;
    private final OrderService orderService;

    public CustomerStatementService(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    public record StatementLine(
            long orderId,
            LocalDate orderDate,
            String status,
            int lineCount,
            double total,
            String notes
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", orderId);
            m.put("orderDate", orderDate.toString());
            m.put("status", status);
            m.put("lineCount", lineCount);
            m.put("total", total);
            m.put("notes", notes == null ? "" : notes);
            return m;
        }
    }

    public record CustomerStatement(
            LocalDate generatedOn,
            LocalDate from,
            LocalDate to,
            Long customerId,
            String customerName,
            String contactName,
            String email,
            String phone,
            String customerType,
            int orderCount,
            double fulfilledTotal,
            double pipelineTotal,
            double draftTotal,
            double cancelledTotal,
            double grandTotal,
            List<StatementLine> orders,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("generatedOn", generatedOn.toString());
            m.put("from", from.toString());
            m.put("to", to.toString());
            m.put("customerId", customerId);
            m.put("customerName", customerName);
            m.put("contactName", contactName);
            m.put("email", email);
            m.put("phone", phone);
            m.put("customerType", customerType);
            m.put("orderCount", orderCount);
            m.put("fulfilledTotal", fulfilledTotal);
            m.put("pipelineTotal", pipelineTotal);
            m.put("draftTotal", draftTotal);
            m.put("cancelledTotal", cancelledTotal);
            m.put("grandTotal", grandTotal);
            m.put("orders", orders.stream().map(StatementLine::toMap).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Statement for one customer. Null {@code from}/{@code to} defaults to trailing
     * {@value #DEFAULT_LOOKBACK_DAYS} days through today.
     */
    @Transactional(readOnly = true)
    public CustomerStatement build(Long customerId, LocalDate from, LocalDate to) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required.");
        }
        Customer customer = customerService.findById(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + customerId));

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_LOOKBACK_DAYS);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }

        List<CustomerOrder> raw = orderService.findByCustomer(customerId);
        List<CustomerOrder> inRange = raw.stream()
                .filter(o -> o.getOrderDate() != null
                        && !o.getOrderDate().isBefore(start)
                        && !o.getOrderDate().isAfter(end))
                .sorted(Comparator.comparing(CustomerOrder::getOrderDate)
                        .thenComparing(o -> o.getId() == null ? 0L : o.getId()))
                .toList();

        List<StatementLine> lines = new ArrayList<>();
        double fulfilled = 0;
        double pipeline = 0;
        double draft = 0;
        double cancelled = 0;
        double grand = 0;
        for (CustomerOrder o : inRange) {
            double total = o.lineTotal();
            grand += total;
            String status = o.getStatus() == null ? "DRAFT" : o.getStatus().toUpperCase(Locale.ROOT);
            switch (status) {
                case "FULFILLED" -> fulfilled += total;
                case "CONFIRMED" -> pipeline += total;
                case "CANCELLED" -> cancelled += total;
                default -> draft += total;
            }
            lines.add(new StatementLine(
                    o.getId() == null ? 0L : o.getId(),
                    o.getOrderDate(),
                    status,
                    o.getLines() == null ? 0 : o.getLines().size(),
                    total,
                    o.getNotes() == null ? "" : o.getNotes()
            ));
        }

        String text = formatText(customer, start, end, lines, fulfilled, pipeline, draft, cancelled, grand);
        return new CustomerStatement(
                LocalDate.now(),
                start,
                end,
                customer.getId(),
                customer.getName(),
                nullToEmpty(customer.getContactName()),
                nullToEmpty(customer.getEmail()),
                nullToEmpty(customer.getPhone()),
                nullToEmpty(customer.getCustomerType()),
                lines.size(),
                fulfilled,
                pipeline,
                draft,
                cancelled,
                grand,
                List.copyOf(lines),
                text
        );
    }

    public byte[] generatePdf(CustomerStatement statement) {
        if (statement == null) {
            throw new IllegalArgumentException("statement is required.");
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
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Customer Statement",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Account Statement", titleFont));
            doc.add(new Paragraph(
                    "Period: " + statement.from() + " → " + statement.to()
                            + "     ·     Generated: " + statement.generatedOn(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable parties = new PdfPTable(new float[]{1, 1});
            parties.setWidthPercentage(100);
            String billTo = statement.customerName()
                    + (statement.customerType().isBlank() ? "" : "  [" + statement.customerType() + "]")
                    + (statement.contactName().isBlank() ? "" : "\nAttn: " + statement.contactName())
                    + (statement.email().isBlank() ? "" : "\n" + statement.email())
                    + (statement.phone().isBlank() ? "" : "\n" + statement.phone());
            parties.addCell(infoBlock("Account", billTo, brandSoft, body));
            parties.addCell(infoBlock("From",
                    "FlowersForever Farm\nPort Orchard / Kitsap County, WA\nPNW West of the Cascades",
                    brandSoft, body));
            doc.add(parties);
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Orders", String.valueOf(statement.orderCount()), brandSoft));
            summary.addCell(summaryCell("Fulfilled $",
                    String.format(Locale.US, "%,.0f", statement.fulfilledTotal()), brandSoft));
            summary.addCell(summaryCell("Pipeline $",
                    String.format(Locale.US, "%,.0f", statement.pipelineTotal()), brandSoft));
            summary.addCell(summaryCell("All activity $",
                    String.format(Locale.US, "%,.0f", statement.grandTotal()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Orders in period", h2));
            doc.add(Chunk.NEWLINE);
            if (statement.orders().isEmpty()) {
                doc.add(new Paragraph("No orders in this period for this customer.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{0.8f, 1.4f, 1.4f, 0.9f, 1.3f, 2.2f});
                t.setWidthPercentage(100);
                headerCell(t, "#");
                headerCell(t, "Date");
                headerCell(t, "Status");
                headerCell(t, "Lines");
                headerCell(t, "Total");
                headerCell(t, "Notes");
                for (StatementLine line : statement.orders()) {
                    t.addCell(cell(String.valueOf(line.orderId()), body));
                    t.addCell(cell(line.orderDate().toString(), body));
                    t.addCell(cell(line.status(), body));
                    t.addCell(cell(String.valueOf(line.lineCount()), body));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.total()), body));
                    t.addCell(cell(line.notes(), body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("2. Status totals", h2));
            doc.add(new Paragraph(String.format(Locale.US,
                    "Fulfilled $%,.2f  ·  Pipeline (CONFIRMED) $%,.2f  ·  Draft $%,.2f  ·  Cancelled $%,.2f",
                    statement.fulfilledTotal(),
                    statement.pipelineTotal(),
                    statement.draftTotal(),
                    statement.cancelledTotal()), body));
            doc.add(Chunk.NEWLINE);

            PdfPTable totalBox = new PdfPTable(1);
            totalBox.setWidthPercentage(45);
            totalBox.setHorizontalAlignment(Element.ALIGN_RIGHT);
            PdfPCell totalCell = new PdfPCell(new Phrase(
                    String.format(Locale.US, "Period activity  $%,.2f", statement.grandTotal()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, brandGreen)));
            totalCell.setBackgroundColor(brandSoft);
            totalCell.setPadding(10);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setBorderColor(new Color(180, 200, 180));
            totalBox.addCell(totalCell);
            doc.add(totalBox);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph(
                    "Open balance (pipeline + draft, excl. cancelled): $"
                            + String.format(Locale.US, "%,.2f",
                            statement.pipelineTotal() + statement.draftTotal()),
                    body));
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Thank you for supporting local PNW cut flowers. "
                            + "Single-order invoices: CRM → Invoice PDF.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated customer statement PDF for id={} ({} orders, ${})",
                    statement.customerId(), statement.orderCount(),
                    String.format(Locale.US, "%.2f", statement.grandTotal()));
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build customer statement PDF: " + e.getMessage(), e);
        }
    }

    private static String formatText(Customer customer, LocalDate from, LocalDate to,
                                     List<StatementLine> lines,
                                     double fulfilled, double pipeline,
                                     double draft, double cancelled, double grand) {
        StringBuilder sb = new StringBuilder();
        sb.append("CUSTOMER STATEMENT — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("Customer: ").append(customer.getName());
        if (customer.getCustomerType() != null && !customer.getCustomerType().isBlank()) {
            sb.append(" [").append(customer.getCustomerType()).append(']');
        }
        sb.append('\n');
        if (customer.getContactName() != null && !customer.getContactName().isBlank()) {
            sb.append("Attn: ").append(customer.getContactName()).append('\n');
        }
        sb.append("Period: ").append(from).append(" → ").append(to).append('\n');
        sb.append(String.format(Locale.US,
                "Orders: %d  ·  Fulfilled $%,.2f  ·  Pipeline $%,.2f  ·  Activity $%,.2f%n",
                lines.size(), fulfilled, pipeline, grand));
        sb.append('\n');
        if (lines.isEmpty()) {
            sb.append("  (no orders in period)\n");
        } else {
            sb.append(String.format(Locale.US, "%-6s %-12s %-10s %8s %10s%n",
                    "#", "Date", "Status", "Lines", "Total"));
            sb.append("-".repeat(52)).append('\n');
            for (StatementLine l : lines) {
                sb.append(String.format(Locale.US, "%-6d %-12s %-10s %8d %10.2f%n",
                        l.orderId(), l.orderDate(), l.status(), l.lineCount(), l.total()));
            }
            sb.append("-".repeat(52)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL ACTIVITY  $%,.2f%n", grand));
            sb.append(String.format(Locale.US,
                    "  Fulfilled $%,.2f | Pipeline $%,.2f | Draft $%,.2f | Cancelled $%,.2f%n",
                    fulfilled, pipeline, draft, cancelled));
        }
        sb.append("\nTip: per-order invoices via CRM Invoice PDF or /api/orders/{id}/invoice.pdf\n");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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

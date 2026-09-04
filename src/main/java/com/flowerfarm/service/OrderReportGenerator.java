package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.util.CsvSupport;
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
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Formats order accounting exports and customer-facing invoice documents. */
@Component
public class OrderReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrderReportGenerator.class);
    private final FarmProfile farmProfile;

    public OrderReportGenerator(FarmProfile farmProfile) {
        this.farmProfile = farmProfile;
    }

    public void exportCsv(String filename, List<CustomerOrder> orders) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        List<CustomerOrder> rows = orders == null ? List.of() : orders;
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filename.trim(), StandardCharsets.UTF_8))) {
            writer.write("OrderId,PickupDeliveryMarketDate,HandoffType,Customer,ContactName,"
                    + "Phone,Email,AccountDeliveryNote,Status,LineCount,Products,Total,"
                    + "HandoffInstructions");
            writer.newLine();
            for (CustomerOrder order : rows) {
                Customer customer = order.getCustomer();
                List<OrderLine> lines = order.getLines() == null ? List.of() : order.getLines();
                writer.write(String.join(",",
                        csv(order.getId()),
                        csv(order.getOrderDate()),
                        csv(order.getHandoffType()),
                        csv(customer == null ? "" : customer.getName()),
                        csv(customer == null ? "" : customer.getContactName()),
                        csv(customer == null ? "" : customer.getPhone()),
                        csv(customer == null ? "" : customer.getEmail()),
                        csv(customer == null ? "" : customer.getNotes()),
                        csv(order.getStatus()),
                        Integer.toString(lines.size()),
                        csv(productSummary(lines)),
                        String.format(Locale.US, "%.2f", order.lineTotal()),
                        csv(order.getNotes())));
                writer.newLine();
            }
            log.info("Orders exported to '{}' ({} row(s)).", filename, rows.size());
        } catch (IOException ex) {
            throw new IllegalStateException("Order export failed: " + ex.getMessage(), ex);
        }
    }

    public byte[] generateInvoicePdf(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required.");
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    farmProfile.documentBanner("Wholesale Invoice"),
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            document.add(bannerTable);
            document.add(Chunk.NEWLINE);

            String orderNo = order.getId() == null ? "—" : String.valueOf(order.getId());
            String status = order.getStatus() == null ? "DRAFT" : order.getStatus();
            document.add(new Paragraph("Invoice / Order #" + orderNo, titleFont));
            document.add(new Paragraph(
                    "Pickup / delivery / market date: " + order.getOrderDate()
                            + "     ·     Handoff: " + friendlyHandoff(order.getHandoffType())
                            + "     ·     Status: " + status
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            document.add(Chunk.NEWLINE);

            Customer customer = order.getCustomer();
            String name = value(customer == null ? null : customer.getName(), "(no customer)");
            String contact = value(customer == null ? null : customer.getContactName(), "");
            String email = value(customer == null ? null : customer.getEmail(), "");
            String phone = value(customer == null ? null : customer.getPhone(), "");
            String type = value(customer == null ? null : customer.getCustomerType(), "");
            String accountNote = value(customer == null ? null : customer.getNotes(), "");

            PdfPTable parties = new PdfPTable(new float[]{1, 1});
            parties.setWidthPercentage(100);
            parties.addCell(infoBlock("Bill to / ship to",
                    name
                            + (type.isBlank() ? "" : "  [" + type + "]")
                            + (contact.isBlank() ? "" : "\nAttn: " + contact)
                            + (email.isBlank() ? "" : "\n" + email)
                            + (phone.isBlank() ? "" : "\n" + phone)
                            + (accountNote.isBlank()
                            ? "" : "\nAccount delivery note: " + accountNote),
                    brandSoft, body));
            parties.addCell(infoBlock("From",
                    farmProfile.contactBlock(),
                    brandSoft, body));
            document.add(parties);
            document.add(Chunk.NEWLINE);

            if (order.getNotes() != null && !order.getNotes().isBlank()) {
                document.add(new Paragraph(
                        "Handoff instructions: " + order.getNotes(), body));
                document.add(Chunk.NEWLINE);
            }

            document.add(new Paragraph("Line items", heading));
            document.add(Chunk.NEWLINE);
            List<OrderLine> lines = order.getLines() == null ? List.of() : order.getLines();
            if (lines.isEmpty()) {
                document.add(new Paragraph("(no line items)", body));
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
                    table.addCell(cell(value(line.getUnit(), ""), body));
                    table.addCell(cell(String.format(Locale.US, "%,.2f", line.getUnitPrice()), body));
                    table.addCell(cell(String.format(Locale.US, "%,.2f", line.lineTotal()), body));
                }
                document.add(table);
            }
            document.add(Chunk.NEWLINE);

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
            document.add(totalBox);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                    farmProfile.thankYouLine()
                            + (farmProfile.getEmail().isBlank()
                                    ? "" : " Questions: " + farmProfile.getEmail()), small));
            document.add(new Paragraph(
                    farmProfile.softwareFooter(), small));

            document.close();
            log.info("Generated order invoice PDF for order #{} (${})",
                    orderNo, String.format(Locale.US, "%.2f", order.lineTotal()));
            return output.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException(
                    "Failed to build order invoice PDF: " + ex.getMessage(), ex);
        }
    }

    public String formatInvoiceText(CustomerOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required.");
        }
        StringBuilder text = new StringBuilder();
        String orderNo = order.getId() == null ? "—" : String.valueOf(order.getId());
        Customer customer = order.getCustomer();
        String customerName = customer != null && customer.getName() != null
                ? customer.getName() : "(no customer)";
        text.append("INVOICE / ORDER #").append(orderNo).append('\n');
        text.append("═══════════════════════════════════════\n");
        text.append("Pickup / delivery / market date: ").append(order.getOrderDate())
                .append("  ·  Handoff: ").append(friendlyHandoff(order.getHandoffType()))
                .append("  ·  Status: ").append(order.getStatus()).append('\n');
        text.append("Customer: ").append(customerName);
        if (customer != null && customer.getCustomerType() != null
                && !customer.getCustomerType().isBlank()) {
            text.append(" [").append(customer.getCustomerType()).append(']');
        }
        text.append('\n');
        appendCustomerContact(text, customer);
        if (customer != null && customer.getNotes() != null
                && !customer.getNotes().isBlank()) {
            text.append("Account delivery note: ").append(customer.getNotes()).append('\n');
        }
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            text.append("Handoff instructions: ").append(order.getNotes()).append('\n');
        }
        text.append('\n');
        text.append(String.format(Locale.US, "%-24s %8s %8s %10s %10s%n",
                "Product", "Qty", "Unit", "Unit $", "Line $"));
        text.append("-".repeat(64)).append('\n');
        List<OrderLine> lines = order.getLines() == null ? List.of() : order.getLines();
        if (lines.isEmpty()) {
            text.append("  (no lines)\n");
        } else {
            for (OrderLine line : lines) {
                text.append(String.format(Locale.US, "%-24s %8.1f %8s %10.2f %10.2f%n",
                        truncate(line.getProductName(), 24), line.getQuantity(),
                        truncate(value(line.getUnit(), ""), 8),
                        line.getUnitPrice(), line.lineTotal()));
            }
        }
        text.append("-".repeat(64)).append('\n');
        text.append(String.format(Locale.US, "TOTAL  $%,.2f%n", order.lineTotal()));
        text.append('\n').append(farmProfile.documentBanner("Wholesale Invoice")).append('\n');
        return text.toString();
    }

    private static String productSummary(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> quantity(line.getQuantity()) + " " + value(line.getUnit(), "unit")
                        + " " + value(line.getProductName(), "(unnamed product)") + " @ $"
                        + String.format(Locale.US, "%.2f", line.getUnitPrice()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private static String quantity(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.US, "%.4f", value)
                        .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String csv(Object value) {
        return CsvSupport.escape(value == null ? "" : String.valueOf(value), ',');
    }

    private static String friendlyHandoff(String value) {
        String normalized = CustomerOrder.normalizeHandoffType(value);
        if ("UNSPECIFIED".equals(normalized)) {
            return "Unspecified";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void appendCustomerContact(StringBuilder text, Customer customer) {
        if (customer == null) {
            return;
        }
        if (customer.getContactName() != null && !customer.getContactName().isBlank()) {
            text.append("Attn: ").append(customer.getContactName()).append('\n');
        }
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            text.append(customer.getEmail()).append('\n');
        }
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            text.append(customer.getPhone()).append('\n');
        }
    }

    private static String value(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
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

    private static PdfPCell infoBlock(String title, String body, Color background, Font font) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(title + "\n", FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 10, new Color(34, 100, 54))));
        paragraph.add(new Chunk(body == null ? "" : body, font));
        PdfPCell cell = new PdfPCell(paragraph);
        cell.setBackgroundColor(background);
        cell.setPadding(10);
        cell.setBorderColor(new Color(180, 200, 180));
        return cell;
    }
}

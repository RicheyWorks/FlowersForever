package com.flowerfarm.cli;

import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.SyncHistoryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Scanner;

/**
 * Interactive CLI handlers for printable farm and accounting reports.
 */
public final class CliReportCommands {

    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final MorningBriefingService briefingService;
    private final DayCloseoutService closeoutService;
    private final OrderService orderService;
    private final CustomerStatementService statementService;
    private final SyncHistoryService historyService;

    public CliReportCommands(InventoryService inventoryService, HarvestService harvestService,
                             MorningBriefingService briefingService,
                             DayCloseoutService closeoutService, OrderService orderService,
                             CustomerStatementService statementService,
                             SyncHistoryService historyService) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.briefingService = briefingService;
        this.closeoutService = closeoutService;
        this.orderService = orderService;
        this.statementService = statementService;
        this.historyService = historyService;
    }

    public void morningBriefing(Scanner scanner) {
        try {
            System.out.print("Try live weather for irrigation? (y/n) [n]: ");
            boolean live = "y".equalsIgnoreCase(scanner.nextLine().trim());
            System.out.println(live
                    ? "Building briefing (live weather if available)…"
                    : "Building offline morning briefing…");
            var briefing = briefingService.build(live);
            System.out.println();
            System.out.println(briefing.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                String defaultFile = "morning-briefing-" + briefing.date() + ".pdf";
                byte[] pdf = briefingService.generatePdf(briefing);
                writePdf(scanner, defaultFile, pdf);
            }
        } catch (Exception exception) {
            System.out.println("✗ Morning briefing failed: " + exception.getMessage());
        }
    }

    public void dayCloseout(Scanner scanner) {
        try {
            System.out.println("Building end-of-day closeout…");
            var closeout = closeoutService.build();
            System.out.println();
            System.out.println(closeout.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                String defaultFile = "day-closeout-" + closeout.date() + ".pdf";
                writePdf(scanner, defaultFile, closeoutService.generatePdf(closeout));
            }
        } catch (Exception exception) {
            System.out.println("✗ Day closeout failed: " + exception.getMessage());
        }
    }

    public void orderInvoice(Scanner scanner) {
        try {
            System.out.print("Order id: ");
            long id = Long.parseLong(scanner.nextLine().trim());
            System.out.println();
            System.out.println(orderService.formatInvoiceText(id));
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "invoice-order-" + id + ".pdf",
                        orderService.generateInvoicePdf(id));
            }
        } catch (NumberFormatException exception) {
            System.out.println("✗ Enter a numeric order id.");
        } catch (Exception exception) {
            System.out.println("✗ Order invoice failed: " + exception.getMessage());
        }
    }

    public void lowStockReorder(Scanner scanner) {
        try {
            System.out.print("Threshold (qty ≤ N) [10]: ");
            String raw = scanner.nextLine().trim();
            int threshold = raw.isEmpty() ? 10 : Integer.parseInt(raw);
            var report = inventoryService.buildLowStockReport(threshold);
            System.out.println();
            System.out.println(report.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "low-stock-reorder-" + report.date() + ".pdf",
                        inventoryService.generateLowStockPdf(report));
            }
        } catch (NumberFormatException exception) {
            System.out.println("✗ Enter a whole number threshold.");
        } catch (Exception exception) {
            System.out.println("✗ Low-stock reorder failed: " + exception.getMessage());
        }
    }

    public void customerStatement(Scanner scanner) {
        try {
            System.out.print("Customer id: ");
            long id = Long.parseLong(scanner.nextLine().trim());
            System.out.print("From YYYY-MM-DD [90 days ago]: ");
            String fromRaw = scanner.nextLine().trim();
            System.out.print("To YYYY-MM-DD [today]: ");
            String toRaw = scanner.nextLine().trim();
            LocalDate from = fromRaw.isEmpty() ? null : LocalDate.parse(fromRaw);
            LocalDate to = toRaw.isEmpty() ? null : LocalDate.parse(toRaw);
            var statement = statementService.build(id, from, to);
            System.out.println();
            System.out.println(statement.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "statement-customer-" + id + ".pdf",
                        statementService.generatePdf(statement));
            }
        } catch (NumberFormatException exception) {
            System.out.println("✗ Enter a numeric customer id.");
        } catch (Exception exception) {
            System.out.println("✗ Customer statement failed: " + exception.getMessage());
        }
    }

    public void harvestLogPdf(Scanner scanner) {
        try {
            System.out.print("From YYYY-MM-DD [7 days ago]: ");
            String fromRaw = scanner.nextLine().trim();
            System.out.print("To YYYY-MM-DD [today]: ");
            String toRaw = scanner.nextLine().trim();
            LocalDate from = fromRaw.isEmpty() ? null : LocalDate.parse(fromRaw);
            LocalDate to = toRaw.isEmpty() ? null : LocalDate.parse(toRaw);
            var report = harvestService.buildHarvestLogReport(from, to);
            System.out.println();
            System.out.println(report.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "harvest-log-" + report.to() + ".pdf",
                        harvestService.generateHarvestLogPdf(report));
            }
        } catch (Exception exception) {
            System.out.println("✗ Harvest log PDF failed: " + exception.getMessage());
        }
    }

    public void auditHistoryPdf(Scanner scanner) {
        try {
            System.out.print("FAIL only? (y/n) [n]: ");
            boolean failOnly = "y".equalsIgnoreCase(scanner.nextLine().trim());
            System.out.print("Limit [100]: ");
            String limitRaw = scanner.nextLine().trim();
            int limit = limitRaw.isEmpty() ? 100 : Integer.parseInt(limitRaw);
            Boolean success = failOnly ? Boolean.FALSE : null;
            var report = historyService.buildAuditReport(null, null, success, null, limit);
            System.out.println();
            System.out.println(report.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "audit-history-" + report.generatedOn() + ".pdf",
                        historyService.generateAuditPdf(report));
            }
        } catch (NumberFormatException exception) {
            System.out.println("✗ Enter a whole number limit.");
        } catch (Exception exception) {
            System.out.println("✗ Audit history PDF failed: " + exception.getMessage());
        }
    }

    public void priceListPdf(Scanner scanner) {
        try {
            System.out.print("In-stock only? (y/n) [y]: ");
            String raw = scanner.nextLine().trim();
            boolean inStockOnly = raw.isEmpty() || "y".equalsIgnoreCase(raw);
            var report = inventoryService.buildPriceListReport(inStockOnly);
            System.out.println();
            System.out.println(report.plainText());
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                writePdf(scanner, "price-list-" + report.date() + ".pdf",
                        inventoryService.generatePriceListPdf(report));
            }
        } catch (Exception exception) {
            System.out.println("✗ Price list PDF failed: " + exception.getMessage());
        }
    }

    private static void writePdf(Scanner scanner, String defaultFile, byte[] pdf) throws Exception {
        System.out.print("Filename [" + defaultFile + "]: ");
        String file = defaultValue(scanner.nextLine(), defaultFile);
        Files.write(Path.of(file), pdf);
        System.out.println("✓ Wrote " + file + " (" + pdf.length + " bytes)");
    }

    private static String defaultValue(String input, String defaultValue) {
        return input == null || input.isBlank() ? defaultValue : input.trim();
    }
}

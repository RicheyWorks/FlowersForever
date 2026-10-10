package com.flowerfarm.cli;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive CLI — active only when the {@code cli} Spring profile is set.
 *
 * <p>Launch with: {@code java -jar flowerfarm.jar --cli}
 *
 * <p>Because {@code application-cli.properties} sets
 * {@code spring.main.web-application-type=none}, no Tomcat server starts
 * when this runner is active.
 */
@Component
@Profile("cli")
public class FlowerFarmCLI implements ApplicationRunner {

    private final InventoryService  inventoryService;
    private final HarvestService    harvestService;
    private final TrendService      trendService;
    private final ConnectorRegistry connectorRegistry;
    private final IrrigationAdvisorService irrigationAdvisorService;
    private final MarketDayPackingService marketDayPackingService;
    private final CliReportCommands reportCommands;

    public FlowerFarmCLI(InventoryService inventoryService,
                         HarvestService harvestService,
                         TrendService trendService,
                         ConnectorRegistry connectorRegistry,
                         IrrigationAdvisorService irrigationAdvisorService,
                         MarketDayPackingService marketDayPackingService,
                         MorningBriefingService morningBriefingService,
                         DayCloseoutService dayCloseoutService,
                         OrderService orderService,
                         CustomerStatementService customerStatementService,
                         SyncHistoryService syncHistoryService) {
        this.inventoryService  = inventoryService;
        this.harvestService    = harvestService;
        this.trendService      = trendService;
        this.connectorRegistry = connectorRegistry;
        this.irrigationAdvisorService = irrigationAdvisorService;
        this.marketDayPackingService = marketDayPackingService;
        this.reportCommands = new CliReportCommands(inventoryService, harvestService,
                morningBriefingService, dayCloseoutService, orderService,
                customerStatementService, syncHistoryService);
    }

    @Override
    public void run(ApplicationArguments args) {
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
        printBanner();
        CliCommandRouter router = createCommandRouter();

        boolean running = true;
        while (running) {
            router.printMenu();
            String choice = scanner.nextLine().trim();
            running = router.dispatch(choice, scanner);
        }
        scanner.close();
    }

    // ── Menu ─────────────────────────────────────────────────────────────────

    private void printBanner() {
        System.out.println("""
                ╔══════════════════════════════════════════════════════╗
                ║   Flower Farm Manager — PNW (Port Orchard, Kitsap)   ║
                ║                    CLI Mode                          ║
                ╚══════════════════════════════════════════════════════╝
                """);
    }

    private CliCommandRouter createCommandRouter() {
        return new CliCommandRouter(System.out)
                .register("1", "List inventory", scanner -> listInventory())
                .register("2", "Search inventory", this::searchInventory)
                .register("3", "Add item", this::addItem)
                .register("4", "Edit item", this::editItem)
                .register("5", "Delete item", this::deleteItem)
                .register("6", "Export inventory CSV", scanner -> exportCsv())
                .register("7", "Run connector (import/export)", this::runConnector)
                .register("8", "Harvest production forecast", scanner -> runTrendAnalysis())
                .register("9", "Log harvest (single or batch)", this::logHarvest)
                .register("10", "List / export harvest log", this::listAndExportHarvest)
                .register("11", "Kitsap irrigation advice", this::irrigationAdvice)
                .register("12", "Market day packing list", this::marketDayPacking)
                .register("13", "Bed / field production", this::bedProduction)
                .register("14", "Morning briefing", reportCommands::morningBriefing)
                .register("15", "Day closeout", reportCommands::dayCloseout)
                .register("16", "Order invoice PDF", reportCommands::orderInvoice)
                .register("17", "Low-stock reorder list", reportCommands::lowStockReorder)
                .register("18", "Customer statement PDF", reportCommands::customerStatement)
                .register("19", "Harvest log PDF", reportCommands::harvestLogPdf)
                .register("20", "Audit history PDF", reportCommands::auditHistoryPdf)
                .register("21", "Price list PDF", reportCommands::priceListPdf)
                .exit("22", "Exit", scanner -> System.out.println("Goodbye!"));
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void listInventory() {
        List<Item> items = inventoryService.getAllItems();
        if (items.isEmpty()) {
            System.out.println("  (inventory is empty)");
            return;
        }
        System.out.printf("%-30s %-18s %8s %-14s %8s %8s  Notes%n",
                "Name", "Category", "Price", "Unit", "Cost", "Qty");
        System.out.println("-".repeat(100));
        for (Item item : items) {
            System.out.printf("%-30s %-18s %8.2f %-14s %8.2f %8s  %s%n",
                    item.getName(), item.getCategory(), item.getPrice(),
                    item.getUnit(), item.getCost(), InventoryQuantity.format(item.getQuantity()), item.getNotes());
        }
        System.out.println("Total items: " + items.size());
    }

    private void searchInventory(Scanner sc) {
        System.out.print("Search query: ");
        String query = sc.nextLine().trim();
        List<Item> results = inventoryService.searchItems(query);
        if (results.isEmpty()) {
            System.out.println("No results for '" + query + "'.");
        } else {
            results.forEach(item -> System.out.println("  " + item));
        }
    }

    private void addItem(Scanner sc) {
        try {
            System.out.print("Name: ");        String name     = sc.nextLine().trim();
            System.out.print("Category: ");    String category = sc.nextLine().trim();
            System.out.print("Price: ");       double price    = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Unit: ");        String unit     = sc.nextLine().trim();
            System.out.print("Cost: ");        double cost     = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Quantity: ");    double qty      = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Notes: ");       String notes    = sc.nextLine().trim();

            inventoryService.addItem(new Item(name, category, price, unit, cost, qty, notes));
            System.out.println("✓ Added: " + name);
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
        }
    }

    private void editItem(Scanner sc) {
        listInventory();
        System.out.print("Index to edit (0-based): ");
        try {
            int index = Integer.parseInt(sc.nextLine().trim());
            List<Item> items = inventoryService.getAllItems();
            Item current = items.get(index);

            System.out.println("Editing: " + current.getName() + " (press Enter to keep current value)");
            System.out.print("Name [" + current.getName() + "]: ");
            String name = orDefault(sc.nextLine(), current.getName());
            System.out.print("Category [" + current.getCategory() + "]: ");
            String category = orDefault(sc.nextLine(), current.getCategory());
            System.out.print("Price [" + current.getPrice() + "]: ");
            double price = parseDoubleOrDefault(sc.nextLine(), current.getPrice());
            System.out.print("Unit [" + current.getUnit() + "]: ");
            String unit = orDefault(sc.nextLine(), current.getUnit());
            System.out.print("Cost [" + current.getCost() + "]: ");
            double cost = parseDoubleOrDefault(sc.nextLine(), current.getCost());
            System.out.print("Quantity [" + current.getQuantity() + "]: ");
            double qty = parseDoubleOrDefault(sc.nextLine(), current.getQuantity());
            System.out.print("Notes [" + current.getNotes() + "]: ");
            String notes = orDefault(sc.nextLine(), current.getNotes());

            inventoryService.editItem(index, new Item(name, category, price, unit, cost, qty, notes));
            System.out.println("✓ Updated.");
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
        }
    }

    private void deleteItem(Scanner sc) {
        listInventory();
        System.out.print("Index to delete (0-based): ");
        try {
            int index = Integer.parseInt(sc.nextLine().trim());
            List<Item> items = inventoryService.getAllItems();
            System.out.print("Delete '" + items.get(index).getName() + "'? (y/n): ");
            if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                inventoryService.deleteItem(index);
                System.out.println("✓ Deleted.");
            } else {
                System.out.println("Cancelled.");
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
        }
    }

    private void exportCsv() {
        inventoryService.exportToCsv("exported_inventory.csv");
        System.out.println("✓ Exported to exported_inventory.csv");
    }

    private void runConnector(Scanner sc) {
        System.out.println("Available connectors:");
        connectorRegistry.listConnectorInfo().forEach(info ->
                System.out.printf("  %-20s [%-13s]  available=%-5s  %s%n",
                        info.get("name"), info.get("direction"),
                        info.get("available"), info.get("description")));

        System.out.print("Connector name: ");
        String name = sc.nextLine().trim().toLowerCase();
        System.out.print("Operation (import / export / sync): ");
        String op = sc.nextLine().trim().toLowerCase();

        switch (op) {
            case "import" -> {
                ConnectorResult<java.util.List<Item>> r = connectorRegistry.runImport(name);
                System.out.println(r.isSuccess()
                        ? "✓ " + r.getMessage()
                        : "✗ " + r.getMessage() + "\n  " + r.getErrorDetail());
            }
            case "export" -> {
                ConnectorResult<Integer> r = connectorRegistry.runExport(name);
                System.out.println(r.isSuccess()
                        ? "✓ " + r.getMessage()
                        : "✗ " + r.getMessage() + "\n  " + r.getErrorDetail());
            }
            case "sync" -> {
                ConnectorResult<SyncSummary> r = connectorRegistry.runSync(name);
                System.out.println(r.isSuccess()
                        ? "✓ " + r.getMessage()
                        : "✗ " + r.getMessage() + "\n  " + r.getErrorDetail());
            }
            default -> System.out.println("Unknown operation: " + op);
        }
    }

    private void runTrendAnalysis() {
        System.out.println("Forecasting next-week production from real weekly harvest history…");
        TrendService.TrendResult result = trendService.analyzeQuantityTrend();
        if (result.isSuccess()) {
            System.out.println(result.summary());
        } else {
            System.out.println("✗ Trend analysis failed: " + result.error());
        }
    }

    private void logHarvest(Scanner sc) {
        try {
            System.out.print("Mode (1=single, 2=batch): ");
            String mode = sc.nextLine().trim();
            System.out.print("Date [" + LocalDate.now() + "]: ");
            String dateRaw = sc.nextLine().trim();
            LocalDate date = dateRaw.isBlank() ? LocalDate.now() : LocalDate.parse(dateRaw);

            if ("2".equals(mode)) {
                System.out.println("Enter Crop,Qty lines (blank line to finish):");
                System.out.print("Unit default [stems]: ");
                String unit = orDefault(sc.nextLine(), "stems");
                System.out.print("Bed / field: ");
                String bed = sc.nextLine().trim();
                List<HarvestEntry> batch = new ArrayList<>();
                while (true) {
                    System.out.print("  Crop,Qty> ");
                    String line = sc.nextLine();
                    if (line == null || line.isBlank()) {
                        break;
                    }
                    String[] parts = line.split("[,;\\t]", 2);
                    if (parts.length < 2) {
                        System.out.println("  need Crop,Qty");
                        continue;
                    }
                    batch.add(new HarvestEntry(date, parts[0].trim(),
                            Double.parseDouble(parts[1].trim()), unit, bed, "cli-batch"));
                }
                List<HarvestEntry> saved = harvestService.addBatch(batch);
                System.out.println("✓ Batch logged " + saved.size() + " harvest row(s); inventory updated.");
                return;
            }

            System.out.print("Crop / variety: ");
            String crop = sc.nextLine().trim();
            System.out.print("Quantity: ");
            double qty = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Unit [stems]: ");
            String unit = orDefault(sc.nextLine(), "stems");
            System.out.print("Bed / field: ");
            String bed = sc.nextLine().trim();
            System.out.print("Notes: ");
            String notes = sc.nextLine().trim();
            HarvestEntry saved = harvestService.add(new HarvestEntry(date, crop, qty, unit, bed, notes));
            System.out.println("✓ Harvest logged id=" + saved.getId() + " — inventory increased.");
        } catch (Exception e) {
            System.out.println("✗ Harvest error: " + e.getMessage());
        }
    }

    private void listAndExportHarvest(Scanner sc) {
        List<HarvestEntry> entries = harvestService.getAll();
        if (entries.isEmpty()) {
            System.out.println("  (no harvests logged yet)");
        } else {
            System.out.printf("%-6s %-12s %-22s %8s %-10s %-12s  Notes%n",
                    "Id", "Date", "Crop", "Qty", "Unit", "Bed");
            System.out.println("-".repeat(90));
            for (HarvestEntry e : entries) {
                System.out.printf("%-6s %-12s %-22s %8.1f %-10s %-12s  %s%n",
                        e.getId(), e.getHarvestDate(), e.getCropName(), e.getQuantity(),
                        e.getUnit(), e.getBedOrField(), e.getNotes());
            }
            System.out.println("Rows: " + entries.size()
                    + "  ·  week qty: " + harvestService.totalQuantityLast7Days());
        }
        System.out.print("Export to CSV? (y/n): ");
        if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
            System.out.print("Filename [harvest_log.csv]: ");
            String file = orDefault(sc.nextLine(), "harvest_log.csv");
            try {
                harvestService.exportToCsv(file);
                System.out.println("✓ Exported harvest log → " + file);
            } catch (Exception e) {
                System.out.println("✗ Export failed: " + e.getMessage());
            }
        }
    }

    private void bedProduction(Scanner sc) {
        try {
            System.out.print("Period (1=last 7 days, 2=custom dates, 3=all-time) [1]: ");
            String mode = sc.nextLine().trim();
            HarvestService.BedProductionReport report;
            if ("3".equals(mode)) {
                report = harvestService.productionByBed(null, null);
            } else if ("2".equals(mode)) {
                System.out.print("From (YYYY-MM-DD): ");
                LocalDate from = LocalDate.parse(sc.nextLine().trim());
                System.out.print("To (YYYY-MM-DD): ");
                LocalDate to = LocalDate.parse(sc.nextLine().trim());
                report = harvestService.productionByBed(from, to);
            } else {
                report = harvestService.productionByBedLast7Days();
            }
            System.out.println();
            System.out.println(report.plainText());
            System.out.print("Export CSV? (y/n): ");
            if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                String def = "bed-production-" + report.to() + ".csv";
                System.out.print("Filename [" + def + "]: ");
                String file = orDefault(sc.nextLine(), def);
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(file),
                        harvestService.exportBedProductionCsv(report));
                System.out.println("✓ Wrote " + file);
            }
            System.out.print("Export PDF? (y/n): ");
            if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                String def = "bed-production-" + report.to() + ".pdf";
                System.out.print("Filename [" + def + "]: ");
                String file = orDefault(sc.nextLine(), def);
                byte[] pdf = harvestService.generateBedProductionPdf(report);
                java.nio.file.Files.write(java.nio.file.Path.of(file), pdf);
                System.out.println("✓ Wrote " + file + " (" + pdf.length + " bytes)");
            }
        } catch (Exception e) {
            System.out.println("✗ Bed production failed: " + e.getMessage());
        }
    }

    private void marketDayPacking(Scanner sc) {
        try {
            System.out.print("Market date [" + LocalDate.now() + "]: ");
            String dateRaw = sc.nextLine().trim();
            LocalDate date = dateRaw.isBlank() ? LocalDate.now() : LocalDate.parse(dateRaw);
            System.out.print("Window days [1]: ");
            String winRaw = sc.nextLine().trim();
            int window = winRaw.isBlank() ? 1 : Integer.parseInt(winRaw);
            System.out.print("Include DRAFT? (y/n) [n]: ");
            boolean draft = "y".equalsIgnoreCase(sc.nextLine().trim());
            System.out.print("Include FULFILLED? (y/n) [n]: ");
            boolean fulfilled = "y".equalsIgnoreCase(sc.nextLine().trim());

            MarketDayPlan plan = marketDayPackingService.buildPlan(date, window, draft, fulfilled);
            System.out.println();
            System.out.println(plan.plainText());

            System.out.print("Export CSV? (y/n): ");
            if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                System.out.print("Filename [market-day-" + plan.marketDate() + ".csv]: ");
                String file = orDefault(sc.nextLine(), "market-day-" + plan.marketDate() + ".csv");
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(file),
                        marketDayPackingService.exportCsv(plan));
                System.out.println("✓ Wrote " + file);
            }
            System.out.print("Export packing PDF? (y/n): ");
            if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                String def = "market-day-packing-" + plan.marketDate() + ".pdf";
                System.out.print("Filename [" + def + "]: ");
                String file = orDefault(sc.nextLine(), def);
                byte[] pdf = marketDayPackingService.generatePackingPdf(plan);
                java.nio.file.Files.write(java.nio.file.Path.of(file), pdf);
                System.out.println("✓ Wrote " + file + " (" + pdf.length + " bytes)");
            }
            long confirmed = plan.customers().stream()
                    .filter(c -> "CONFIRMED".equalsIgnoreCase(c.status()))
                    .count();
            if (confirmed > 0) {
                System.out.print("Fulfill all " + confirmed + " CONFIRMED order(s)? (y/n): ");
                if ("y".equalsIgnoreCase(sc.nextLine().trim())) {
                    // Re-build CONFIRMED-only plan for safety
                    var safe = marketDayPackingService.buildPlan(date, window, false, false);
                    var result = marketDayPackingService.fulfillConfirmedOrders(safe);
                    System.out.println("Fulfilled=" + result.fulfilled()
                            + " skipped=" + result.skipped()
                            + " failed=" + result.failed());
                    result.messages().forEach(m -> System.out.println("  • " + m));
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            System.out.println("✗ Market day packing failed: " + e.getMessage());
        }
    }

    private void irrigationAdvice(Scanner sc) {
        System.out.print("Mode (1=climatology offline, 2=try live Open-Meteo) [1]: ");
        String mode = sc.nextLine().trim();
        boolean live = "2".equals(mode);
        System.out.println(live
                ? "Fetching Port Orchard forecast (falls back to climatology if offline)…"
                : "Building Kitsap climatology advice…");
        try {
            IrrigationAdvice a = irrigationAdvisorService.advise(live);
            System.out.println();
            System.out.println("  Location : " + a.location());
            System.out.println("  Mode     : " + a.mode());
            System.out.println("  Date     : " + a.asOfDate());
            System.out.println("  Season   : " + a.season());
            System.out.println("  Priority : " + a.priority());
            System.out.println("  Headline : " + a.headline());
            if (a.weekPrecipInches() != null) {
                System.out.printf("  7d precip: %.1f\"  ET₀: %.1f\"  deficit: %.1f\"%n",
                        a.weekPrecipInches(),
                        a.weekEtInches() != null ? a.weekEtInches() : 0.0,
                        a.moistureDeficitInches() != null ? a.moistureDeficitInches() : 0.0);
            }
            if (a.activeBeds() != null && !a.activeBeds().isEmpty()) {
                System.out.println("  Active beds: " + String.join(", ", a.activeBeds()));
            }
            System.out.println("  Actions:");
            for (String action : a.actions()) {
                System.out.println("    • " + action);
            }
            if (a.climateNotes() != null && !a.climateNotes().isBlank()) {
                System.out.println("  Note: " + a.climateNotes());
            }
        } catch (Exception e) {
            System.out.println("✗ Irrigation advice failed: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String orDefault(String input, String def) {
        return (input == null || input.isBlank()) ? def : input.trim();
    }

    private double parseDoubleOrDefault(String input, double def) {
        if (input == null || input.isBlank()) return def;
        try { return Double.parseDouble(input.trim()); } catch (NumberFormatException e) { return def; }
    }

    private int parseIntOrDefault(String input, int def) {
        if (input == null || input.isBlank()) return def;
        try { return Integer.parseInt(input.trim()); } catch (NumberFormatException e) { return def; }
    }
}

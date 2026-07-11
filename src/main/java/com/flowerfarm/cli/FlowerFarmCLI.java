package com.flowerfarm.cli;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
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

    public FlowerFarmCLI(InventoryService inventoryService,
                         HarvestService harvestService,
                         TrendService trendService,
                         ConnectorRegistry connectorRegistry) {
        this.inventoryService  = inventoryService;
        this.harvestService    = harvestService;
        this.trendService      = trendService;
        this.connectorRegistry = connectorRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scanner scanner = new Scanner(System.in);
        printBanner();

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> listInventory();
                case "2" -> searchInventory(scanner);
                case "3" -> addItem(scanner);
                case "4" -> editItem(scanner);
                case "5" -> deleteItem(scanner);
                case "6" -> exportCsv();
                case "7" -> runConnector(scanner);
                case "8" -> runTrendAnalysis();
                case "9" -> logHarvest(scanner);
                case "10" -> listAndExportHarvest(scanner);
                case "11" -> { System.out.println("Goodbye!"); running = false; }
                default  -> System.out.println("Unknown option. Try again.");
            }
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

    private void printMenu() {
        System.out.println("""
                ─────────────────────────────────────────
                 1) List inventory
                 2) Search inventory
                 3) Add item
                 4) Edit item
                 5) Delete item
                 6) Export inventory CSV
                 7) Run connector (import/export)
                 8) Trend analysis (Weka ML)
                 9) Log harvest (single or batch)
                10) List / export harvest log
                11) Exit
                ─────────────────────────────────────────
                Choice: \
                """);
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
            System.out.printf("%-30s %-18s %8.2f %-14s %8.2f %8d  %s%n",
                    item.getName(), item.getCategory(), item.getPrice(),
                    item.getUnit(), item.getCost(), item.getQuantity(), item.getNotes());
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
            System.out.print("Quantity: ");    int    qty      = Integer.parseInt(sc.nextLine().trim());
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
            int qty = parseIntOrDefault(sc.nextLine(), current.getQuantity());
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
        System.out.println("Running Weka linear regression…");
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

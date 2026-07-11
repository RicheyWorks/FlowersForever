package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InMemoryInventoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InventoryService using an in-memory repository.
 * Seed CSV path points at a missing file so init() loads sample data.
 */
@DisplayName("InventoryService")
class InventoryServiceTest {

    private InMemoryInventoryRepository repository;
    private InventoryService service;
    private Path missingSeedCsv;

    @BeforeEach
    void setUp() throws Exception {
        repository = new InMemoryInventoryRepository();
        missingSeedCsv = Files.createTempFile("farm_seed_", ".csv");
        Files.delete(missingSeedCsv); // not found → sample seed
        service = new InventoryService(repository, missingSeedCsv.toString());
        service.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(missingSeedCsv);
    }

    @Test
    @DisplayName("init() loads sample data when DB empty and seed CSV missing")
    void loadsDefaultSampleData() {
        List<Item> items = service.getAllItems();
        assertThat(items).hasSizeGreaterThanOrEqualTo(1);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.getName()).isNotBlank();
            assertThat(i.getId()).isNotNull();
        });
    }

    @Test
    @DisplayName("init() seeds from CSV when present and DB is empty")
    void seedsFromCsvWhenEmpty() throws Exception {
        Path seed = Files.createTempFile("seed_inventory_", ".csv");
        try {
            Files.writeString(seed, InventoryService.CSV_HEADER + "\n"
                    + "Custom Rose,Flowers/Plants,3.00,Per Stem,1.50,12,\"seeded\"\n");
            InMemoryInventoryRepository repo = new InMemoryInventoryRepository();
            InventoryService seeded = new InventoryService(repo, seed.toString());
            seeded.init();

            assertThat(seeded.getAllItems()).hasSize(1);
            assertThat(seeded.getAllItems().get(0).getName()).isEqualTo("Custom Rose");
        } finally {
            Files.deleteIfExists(seed);
        }
    }

    @Test
    @DisplayName("init() does not re-seed when data already exists")
    void doesNotReseedWhenPopulated() {
        int before = service.getAllItems().size();
        service.init(); // second call
        assertThat(service.getAllItems()).hasSize(before);
    }

    @Test
    @DisplayName("getAllItems() returns a defensive copy")
    void getAllItemsReturnsDefensiveCopy() {
        List<Item> copy = service.getAllItems();
        int originalSize = copy.size();
        copy.clear();
        assertThat(service.getAllItems()).hasSize(originalSize);
    }

    @Test
    @DisplayName("addItem() persists and assigns an id")
    void addItemPersistsWithId() {
        int before = service.getAllItems().size();
        Item saved = service.addItem(
                new Item("Damask Rose", "Flowers/Plants", 2.50, "Per Stem", 1.00, 30, "Pink"));

        assertThat(saved.getId()).isNotNull();
        assertThat(service.getAllItems()).hasSize(before + 1);
        assertThat(service.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("addItem() throws for null")
    void addItemThrowsOnNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addItem(null))
                .withMessageContaining("null");
    }

    @Test
    @DisplayName("editItem() updates by index and keeps id")
    void editItemReplacesEntry() {
        Long id = service.getAllItems().get(0).getId();
        Item updated = new Item("Updated Rose", "Flowers/Plants", 5.00, "Per Stem", 3.00, 20, "New notes");

        Item saved = service.editItem(0, updated);

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(service.getAllItems().get(0).getName()).isEqualTo("Updated Rose");
    }

    @Test
    @DisplayName("updateById() updates by primary key")
    void updateByIdWorks() {
        Long id = service.getAllItems().get(0).getId();
        Item saved = service.updateById(id,
                new Item("By Id Rose", "Other", 1.0, "Per Unit", 0.5, 3, "x"));
        assertThat(saved.getName()).isEqualTo("By Id Rose");
        assertThat(service.findById(id).orElseThrow().getName()).isEqualTo("By Id Rose");
    }

    @Test
    @DisplayName("editItem() throws for null replacement")
    void editItemThrowsOnNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.editItem(0, null));
    }

    @ParameterizedTest(name = "index = {0}")
    @ValueSource(ints = {-1, 999})
    @DisplayName("editItem() throws for invalid index")
    void editItemThrowsOnBadIndex(int idx) {
        Item item = new Item("X", "Other", 1.0, "Per Unit", 0.5, 1, "");
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.editItem(idx, item));
    }

    @Test
    @DisplayName("deleteItem() removes by index")
    void deleteItemRemovesEntry() {
        String nameToRemove = service.getAllItems().get(0).getName();
        int before = service.getAllItems().size();
        service.deleteItem(0);
        assertThat(service.getAllItems()).hasSize(before - 1);
        assertThat(service.getAllItems()).extracting(Item::getName).doesNotContain(nameToRemove);
    }

    @Test
    @DisplayName("deleteById() removes by primary key")
    void deleteByIdWorks() {
        Long id = service.getAllItems().get(0).getId();
        service.deleteById(id);
        assertThat(service.findById(id)).isEmpty();
    }

    @ParameterizedTest(name = "index = {0}")
    @ValueSource(ints = {-1, 999})
    @DisplayName("deleteItem() throws for invalid index")
    void deleteItemThrowsOnBadIndex(int idx) {
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.deleteItem(idx));
    }

    @Test
    @DisplayName("searchItems() matches name case-insensitively")
    void searchMatchesName() {
        service.addItem(new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, ""));
        assertThat(service.searchItems("nootka"))
                .isNotEmpty()
                .allSatisfy(i -> assertThat(i.getName().toLowerCase()).contains("nootka"));
    }

    @Test
    @DisplayName("searchItems() matches category")
    void searchMatchesCategory() {
        assertThat(service.searchItems("fertilizer")).isNotEmpty();
    }

    @Test
    @DisplayName("searchItems() empty for no matches / null / blank")
    void searchEmptyCases() {
        assertThat(service.searchItems("zyxwvutsrqponmlkjihgfedcba")).isEmpty();
        assertThat(service.searchItems(null)).isEmpty();
        assertThat(service.searchItems("   ")).isEmpty();
    }

    @Test
    @DisplayName("exportToCsv() writes header and one row per item")
    void exportCsvWritesHeaderAndRows() throws IOException {
        Path export = Files.createTempFile("export_test_", ".csv");
        try {
            service.exportToCsv(export.toString());
            String content = Files.readString(export);
            assertThat(content).startsWith(InventoryService.CSV_HEADER);
            int dataLines = content.split("\n").length - 1;
            assertThat(dataLines).isEqualTo(service.getAllItems().size());
        } finally {
            Files.deleteIfExists(export);
        }
    }

    @Test
    @DisplayName("exportToCsv() rejects null/blank filename")
    void exportCsvThrowsOnBadName() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv(null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv("   "));
    }

    @Test
    @DisplayName("inventoryKpis aggregates sell value, cost, low stock")
    void inventoryKpis() {
        InventoryService.InventoryKpiSnapshot snap = service.inventoryKpis(10);
        assertThat(snap.skuCount()).isEqualTo(service.getAllItems().size());
        assertThat(snap.lowStockThreshold()).isEqualTo(10);
        assertThat(snap.sellValue()).isGreaterThanOrEqualTo(0);
        assertThat(snap.costBasis()).isGreaterThanOrEqualTo(0);
        assertThat(snap.totalUnits()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("buildLowStockReport + PDF for threshold")
    void lowStockReportAndPdf() {
        service.addItem(new Item("Zero Stock", "Other", 2.0, "each", 1.0, 0, ""));
        service.addItem(new Item("Almost Out", "Other", 3.0, "each", 1.5, 5, ""));
        service.addItem(new Item("Healthy Stock", "Other", 4.0, "each", 2.0, 100, ""));

        InventoryService.LowStockReport report = service.buildLowStockReport(10);
        assertThat(report.plainText()).contains("LOW-STOCK REORDER");
        assertThat(report.lines()).extracting(InventoryService.LowStockLine::name)
                .contains("Zero Stock", "Almost Out")
                .doesNotContain("Healthy Stock");
        assertThat(report.lines().get(0).quantity())
                .isLessThanOrEqualTo(report.lines().get(report.lines().size() - 1).quantity());
        // Zero → target max(20, 11)=20 → suggest 20; cost 20 * 1.0
        InventoryService.LowStockLine zero = report.lines().stream()
                .filter(l -> l.name().equals("Zero Stock")).findFirst().orElseThrow();
        assertThat(zero.suggestedOrderQty()).isEqualTo(20);
        assertThat(zero.suggestedOrderCost()).isEqualTo(20.0);
        assertThat(report.toMap()).containsKeys("lines", "suggestedOrderCostTotal");

        byte[] pdf = service.generateLowStockPdf(report);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generateLowStockPdf(null));
    }

    @Test
    @DisplayName("buildPriceListReport + PDF; inStockOnly omits zeros")
    void priceListReportAndPdf() {
        service.addItem(new Item("Booth Daisy", "Flowers/Plants", 2.5, "stems", 1.0, 20, ""));
        service.addItem(new Item("Gone Crop", "Flowers/Plants", 3.0, "stems", 1.0, 0, ""));

        InventoryService.PriceListReport all = service.buildPriceListReport(false);
        assertThat(all.plainText()).contains("PRICE LIST");
        assertThat(all.lines()).extracting(InventoryService.PriceListLine::name)
                .contains("Booth Daisy", "Gone Crop");

        InventoryService.PriceListReport stock = service.buildPriceListReport(true);
        assertThat(stock.lines()).extracting(InventoryService.PriceListLine::name)
                .contains("Booth Daisy")
                .doesNotContain("Gone Crop");
        assertThat(stock.inStockOnly()).isTrue();
        assertThat(stock.toMap()).containsKeys("lines", "sellValue");

        byte[] pdf = service.generatePriceListPdf(stock);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePriceListPdf(null));
    }

    @Test
    @DisplayName("concurrent addItem() calls keep a consistent size")
    void concurrentAddsAreThreadSafe() throws InterruptedException {
        int threads = 10;
        int addsPerThread = 5;
        int baseline = service.getAllItems().size();

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int t = i;
            workers[i] = new Thread(() -> {
                for (int j = 0; j < addsPerThread; j++) {
                    service.addItem(new Item("Thread-" + t + "-" + j, "Other", 1.0, "Per Unit", 0.5, 1, ""));
                }
            });
        }
        for (Thread w : workers) w.start();
        for (Thread w : workers) w.join();

        assertThat(service.getAllItems()).hasSize(baseline + threads * addsPerThread);
    }
}

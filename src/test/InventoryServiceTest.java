package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InventoryService.
 *
 * Each test gets a fresh service backed by a temp CSV file so tests
 * are hermetically isolated from each other and from any real
 * farm_inventory.csv in the working directory.
 */
@DisplayName("InventoryService")
class InventoryServiceTest {

    /** Minimal subclass that lets us point the service at a temp file. */
    static class TestablInventoryService extends InventoryService {
        TestablInventoryService(String csvPath) throws Exception {
            // Use reflection to set the private dataFile field before init()
            var field = InventoryService.class.getDeclaredField("dataFile");
            field.setAccessible(true);
            field.set(this, csvPath);
        }
    }

    private InventoryService service;
    private Path tempCsv;

    @BeforeEach
    void setUp() throws Exception {
        // Create a temp file that doesn't exist yet so the service falls back
        // to sample data — each test starts from that clean 4-item baseline.
        tempCsv = Files.createTempFile("farm_test_", ".csv");
        Files.delete(tempCsv); // delete so service treats it as "not found" → sample data

        service = new TestablInventoryService(tempCsv.toString());
        service.init();
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup of any CSV file the service wrote during the test
        new File(tempCsv.toString()).delete();
    }

    // ── init / sample data ───────────────────────────────────────────────────

    @Test
    @DisplayName("init() loads sample data when CSV does not exist")
    void loadsDefaultSampleData() {
        List<Item> items = service.getAllItems();
        assertThat(items).hasSizeGreaterThanOrEqualTo(1);
        assertThat(items).allSatisfy(i -> assertThat(i.getName()).isNotBlank());
    }

    // ── getAllItems ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllItems() returns a defensive copy — mutating it does not affect the service")
    void getAllItemsReturnsDefensiveCopy() {
        List<Item> copy = service.getAllItems();
        int originalSize = copy.size();
        copy.clear();

        assertThat(service.getAllItems()).hasSize(originalSize);
    }

    // ── addItem ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem() appends item and persists to CSV")
    void addItemAppendsAndPersists() throws IOException {
        int before = service.getAllItems().size();
        Item rose = new Item("Damask Rose", "Flowers/Plants", 2.50, "Per Stem", 1.00, 30, "Pink");

        service.addItem(rose);

        assertThat(service.getAllItems()).hasSize(before + 1);
        assertThat(service.getAllItems())
                .extracting(Item::getName)
                .contains("Damask Rose");

        // CSV should exist and contain the new item
        assertThat(Files.readString(tempCsv)).contains("Damask Rose");
    }

    @Test
    @DisplayName("addItem() throws IllegalArgumentException for null item")
    void addItemThrowsOnNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addItem(null))
                .withMessageContaining("null");
    }

    // ── editItem ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("editItem() replaces item at index and persists")
    void editItemReplacesEntry() throws IOException {
        Item updated = new Item("Updated Rose", "Flowers/Plants", 5.00, "Per Stem", 3.00, 20, "New notes");
        service.editItem(0, updated);

        assertThat(service.getAllItems().get(0).getName()).isEqualTo("Updated Rose");
        assertThat(Files.readString(tempCsv)).contains("Updated Rose");
    }

    @Test
    @DisplayName("editItem() throws IllegalArgumentException for null replacement")
    void editItemThrowsOnNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.editItem(0, null));
    }

    @ParameterizedTest(name = "index = {0}")
    @ValueSource(ints = {-1, 999})
    @DisplayName("editItem() throws IndexOutOfBoundsException for invalid index")
    void editItemThrowsOnBadIndex(int idx) {
        Item item = new Item("X", "Other", 1.0, "Per Unit", 0.5, 1, "");
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.editItem(idx, item));
    }

    // ── deleteItem ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteItem() removes item at index and persists")
    void deleteItemRemovesEntry() {
        String nameToRemove = service.getAllItems().get(0).getName();
        int before = service.getAllItems().size();

        service.deleteItem(0);

        assertThat(service.getAllItems()).hasSize(before - 1);
        assertThat(service.getAllItems())
                .extracting(Item::getName)
                .doesNotContain(nameToRemove);
    }

    @ParameterizedTest(name = "index = {0}")
    @ValueSource(ints = {-1, 999})
    @DisplayName("deleteItem() throws IndexOutOfBoundsException for invalid index")
    void deleteItemThrowsOnBadIndex(int idx) {
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.deleteItem(idx));
    }

    // ── searchItems ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchItems() matches on name (case-insensitive)")
    void searchMatchesName() {
        service.addItem(new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, ""));
        List<Item> results = service.searchItems("nootka");

        assertThat(results)
                .isNotEmpty()
                .allSatisfy(i -> assertThat(i.getName().toLowerCase()).contains("nootka"));
    }

    @Test
    @DisplayName("searchItems() matches on category (case-insensitive)")
    void searchMatchesCategory() {
        List<Item> results = service.searchItems("fertilizer");
        // sample data includes "Fertilizers" category
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("searchItems() returns empty list for no matches")
    void searchReturnsEmptyOnNoMatch() {
        List<Item> results = service.searchItems("zyxwvutsrqponmlkjihgfedcba");
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("searchItems() returns empty list for null query")
    void searchReturnsEmptyOnNull() {
        assertThat(service.searchItems(null)).isEmpty();
    }

    @Test
    @DisplayName("searchItems() returns empty list for blank query")
    void searchReturnsEmptyOnBlank() {
        assertThat(service.searchItems("   ")).isEmpty();
    }

    // ── exportToCsv ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("exportToCsv() writes a header row and one row per item")
    void exportCsvWritesHeaderAndRows() throws IOException {
        Path export = Files.createTempFile("export_test_", ".csv");
        try {
            service.exportToCsv(export.toString());

            String content = Files.readString(export);
            assertThat(content).startsWith("Name,Category,Price");
            // Each item should have a line
            int dataLines = content.split("\n").length - 1; // minus header
            assertThat(dataLines).isEqualTo(service.getAllItems().size());
        } finally {
            Files.deleteIfExists(export);
        }
    }

    @Test
    @DisplayName("exportToCsv() throws IllegalArgumentException for null filename")
    void exportCsvThrowsOnNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.exportToCsv(null));
    }

    @Test
    @DisplayName("exportToCsv() throws IllegalArgumentException for blank filename")
    void exportCsvThrowsOnBlank() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.exportToCsv("   "));
    }

    // ── Concurrent safety (smoke test) ────────────────────────────────────────

    @Test
    @DisplayName("concurrent addItem() calls don't corrupt the list size")
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

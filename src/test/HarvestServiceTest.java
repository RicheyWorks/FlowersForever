package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.HarvestJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HarvestService")
class HarvestServiceTest {

    @Mock HarvestJpaRepository repository;
    @Mock InventoryService inventoryService;
    @Mock SyncHistoryService syncHistoryService;
    HarvestService service;

    @BeforeEach
    void setUp() {
        service = new HarvestService(repository, inventoryService, syncHistoryService);
        lenient().when(syncHistoryService.record(any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(null);
    }

    @Test
    @DisplayName("add() saves harvest, increments inventory, records HARVEST_LOG")
    void addIncrementsInventory() {
        when(repository.save(any())).thenAnswer(inv -> {
            HarvestEntry e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
        when(inventoryService.incrementQuantityByName(eq("Nootka Rose"), eq(120), eq("stems"), anyString()))
                .thenReturn(new Item("Nootka Rose", "Flowers/Plants", 0, "stems", 0, 170, ""));

        HarvestEntry saved = service.add(new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Nootka Rose", 120, "stems", "Bed A", "morning"));

        assertThat(saved.getId()).isEqualTo(7L);
        verify(inventoryService).incrementQuantityByName(eq("Nootka Rose"), eq(120), eq("stems"), anyString());
        verify(syncHistoryService).record(eq("harvest"), eq("HARVEST_LOG"), eq(true), any(), any(), eq(120));
    }

    @Test
    @DisplayName("add() rejects null")
    void addNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.add(null));
    }

    @Test
    @DisplayName("findBetween validates range")
    void findBetweenValidates() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findBetween(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)));
    }

    @Test
    @DisplayName("totalsByCrop aggregates quantities")
    void totalsByCrop() {
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(
                new HarvestEntry(LocalDate.now(), "Nootka Rose", 50, "stems", "", ""),
                new HarvestEntry(LocalDate.now(), "Nootka Rose", 30, "stems", "", ""),
                new HarvestEntry(LocalDate.now(), "Dahlia", 12, "stems", "", "")
        ));

        Map<String, Double> totals = service.totalsByCrop();
        assertThat(totals.get("Nootka Rose")).isEqualTo(80.0);
        assertThat(totals.get("Dahlia")).isEqualTo(12.0);
    }

    @Test
    @DisplayName("delete() reverses inventory and records HARVEST_UNDO")
    void deleteReversesInventory() {
        HarvestEntry existing = new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Nootka Rose", 40, "stems", "A", "");
        existing.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(inventoryService.decrementQuantityByName("Nootka Rose", 40))
                .thenReturn(Optional.of(new Item("Nootka Rose", "Flowers/Plants", 0, "stems", 0, 60, "")));

        service.delete(5L);

        verify(inventoryService).decrementQuantityByName("Nootka Rose", 40);
        verify(repository).deleteById(5L);
        verify(syncHistoryService).record(eq("harvest"), eq("HARVEST_UNDO"), eq(true), any(), any(), eq(40));
    }

    @Test
    @DisplayName("delete() throws when missing")
    void deleteMissing() {
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.delete(5L));
    }

    @Test
    @DisplayName("totalQuantityLast7Days sums period")
    void weekTotal() {
        when(repository.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(any(), any()))
                .thenReturn(List.of(
                        new HarvestEntry(LocalDate.now(), "A", 10, "stems", "", ""),
                        new HarvestEntry(LocalDate.now(), "B", 5, "stems", "", "")
                ));
        assertThat(service.totalQuantityLast7Days()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("weekOverWeekPercentChange when prior week has data")
    void weekOverWeek() {
        // first call = current week, second = prior week (order of service method calls)
        when(repository.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(any(), any()))
                .thenReturn(
                        List.of(new HarvestEntry(LocalDate.now(), "A", 20, "stems", "", "")),
                        List.of(new HarvestEntry(LocalDate.now().minusDays(10), "A", 10, "stems", "", ""))
                );
        Double pct = service.weekOverWeekPercentChange();
        assertThat(pct).isEqualTo(100.0);
    }

    @Test
    @DisplayName("update() same crop applies quantity delta to inventory")
    void updateSameCropDelta() {
        HarvestEntry existing = new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Nootka Rose", 40, "stems", "A", "am");
        existing.setId(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryService.incrementQuantityByName(eq("Nootka Rose"), eq(20), eq("stems"), anyString()))
                .thenReturn(new Item("Nootka Rose", "Flowers/Plants", 0, "stems", 0, 60, ""));

        HarvestEntry incoming = new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Nootka Rose", 60, "stems", "A", "corrected count");
        HarvestEntry saved = service.update(3L, incoming);

        assertThat(saved.getQuantity()).isEqualTo(60.0);
        assertThat(saved.getNotes()).isEqualTo("corrected count");
        verify(inventoryService).incrementQuantityByName(eq("Nootka Rose"), eq(20), eq("stems"), anyString());
        verify(syncHistoryService).record(eq("harvest"), eq("HARVEST_EDIT"), eq(true), any(), any(), eq(20));
    }

    @Test
    @DisplayName("update() crop rename reverses old and applies new")
    void updateCropRename() {
        HarvestEntry existing = new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Nootka Rose", 30, "stems", "A", "");
        existing.setId(4L);
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryService.decrementQuantityByName("Nootka Rose", 30))
                .thenReturn(Optional.of(new Item("Nootka Rose", "Flowers/Plants", 0, "stems", 0, 0, "")));
        when(inventoryService.incrementQuantityByName(eq("Damask Rose"), eq(30), eq("stems"), anyString()))
                .thenReturn(new Item("Damask Rose", "Flowers/Plants", 0, "stems", 0, 30, ""));

        HarvestEntry incoming = new HarvestEntry(
                LocalDate.of(2026, 7, 10), "Damask Rose", 30, "stems", "B", "re-id");
        service.update(4L, incoming);

        verify(inventoryService).decrementQuantityByName("Nootka Rose", 30);
        verify(inventoryService).incrementQuantityByName(eq("Damask Rose"), eq(30), eq("stems"), anyString());
        verify(syncHistoryService).record(eq("harvest"), eq("HARVEST_EDIT"), eq(true), any(), any(), eq(0));
    }

    @Test
    @DisplayName("filter() applies crop and date bounds")
    void filter() {
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(
                new HarvestEntry(LocalDate.of(2026, 7, 1), "Nootka Rose", 10, "stems", "", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 5), "Dahlia", 5, "stems", "", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 8), "Nootka Rose", 20, "stems", "", "")
        ));
        List<HarvestEntry> filtered = service.filter("nootka",
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 10));
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getQuantity()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("filter() applies bed substring")
    void filterByBed() {
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(
                new HarvestEntry(LocalDate.of(2026, 7, 8), "Nootka Rose", 20, "stems", "Bed A", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 8), "Dahlia", 5, "stems", "Bed B", "")
        ));
        List<HarvestEntry> filtered = service.filter("", "bed a", null, null);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getCropName()).isEqualTo("Nootka Rose");
    }

    @Test
    @DisplayName("filter() applies notes substring")
    void filterByNotes() {
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(
                new HarvestEntry(LocalDate.of(2026, 7, 8), "Nootka Rose", 20, "stems", "A", "morning cut"),
                new HarvestEntry(LocalDate.of(2026, 7, 8), "Dahlia", 5, "stems", "B", "evening only")
        ));
        List<HarvestEntry> filtered = service.filter("", "", "morning", null, null);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getCropName()).isEqualTo("Nootka Rose");
    }

    @Test
    @DisplayName("addBatch() logs multiple rows, inventory, and HARVEST_BATCH")
    void addBatch() {
        when(repository.save(any())).thenAnswer(inv -> {
            HarvestEntry e = inv.getArgument(0);
            e.setId(e.getCropName().hashCode() & 0xFFFFL);
            return e;
        });
        when(inventoryService.incrementQuantityByName(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(new Item("x", "Flowers/Plants", 0, "stems", 0, 1, ""));

        List<HarvestEntry> saved = service.addBatch(java.util.Arrays.asList(
                new HarvestEntry(LocalDate.now(), "Nootka Rose", 10, "stems", "A", ""),
                new HarvestEntry(LocalDate.now(), "Dahlia", 5, "stems", "B", ""),
                null, // skipped
                new HarvestEntry(LocalDate.now(), "SkipZero", 0, "stems", "", "") // skipped qty ≤ 0
        ));

        assertThat(saved).hasSize(2);
        verify(inventoryService, times(2))
                .incrementQuantityByName(anyString(), anyInt(), anyString(), anyString());
        verify(syncHistoryService).record(eq("harvest"), eq("HARVEST_BATCH"), eq(true), any(), any(), eq(2));
        verify(syncHistoryService, times(2))
                .record(eq("harvest"), eq("HARVEST_LOG"), eq(true), any(), any(), anyInt());
    }

    @Test
    @DisplayName("addBatch() rejects empty / all-invalid")
    void addBatchEmpty() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.addBatch(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.addBatch(null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.addBatch(List.of(
                new HarvestEntry(LocalDate.now(), "OnlyZero", 0, "stems", "", "")
        )));
    }

    @Test
    @DisplayName("productionByBed aggregates qty and crops; blank bed is unassigned")
    void productionByBed() {
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(
                new HarvestEntry(LocalDate.of(2026, 7, 10), "Nootka Rose", 40, "stems", "Bed A", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 11), "Nootka Rose", 20, "stems", "Bed A", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 11), "Dahlia", 15, "stems", "Bed C", ""),
                new HarvestEntry(LocalDate.of(2026, 7, 12), "Tulip", 10, "stems", "", "")
        ));

        HarvestService.BedProductionReport report = service.productionByBed(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));

        assertThat(report.bedCount()).isEqualTo(3);
        assertThat(report.entryCount()).isEqualTo(4);
        assertThat(report.grandTotal()).isEqualTo(85.0);
        assertThat(report.beds().get(0).bed()).isEqualTo("Bed A");
        assertThat(report.beds().get(0).totalQuantity()).isEqualTo(60.0);
        assertThat(report.beds().get(0).byCrop()).containsEntry("Nootka Rose", 60.0);
        assertThat(report.beds().stream().map(HarvestService.BedProduction::bed))
                .contains("(unassigned)");
        assertThat(report.plainText()).contains("BED / FIELD PRODUCTION");
        assertThat(service.exportBedProductionCsv(report)).contains("Bed A").contains("Nootka Rose");

        byte[] pdf = service.generateBedProductionPdf(report);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generateBedProductionPdf(null));
    }

    @Test
    @DisplayName("productionByBed rejects inverted range")
    void productionByBedValidates() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.productionByBed(
                        LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 10)));
    }

    @Test
    @DisplayName("buildHarvestLogReport chronological + by-crop PDF")
    void harvestLogReportAndPdf() {
        when(repository.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(any(), any()))
                .thenReturn(List.of(
                        new HarvestEntry(LocalDate.of(2026, 7, 12), "Dahlia", 15, "stems", "Bed C", ""),
                        new HarvestEntry(LocalDate.of(2026, 7, 10), "Nootka Rose", 40, "stems", "Bed A", "am cut"),
                        new HarvestEntry(LocalDate.of(2026, 7, 11), "Nootka Rose", 20, "stems", "Bed A", "")
                ));

        HarvestService.HarvestLogReport report = service.buildHarvestLogReport(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));
        assertThat(report.plainText()).contains("HARVEST LOG");
        assertThat(report.entryCount()).isEqualTo(3);
        assertThat(report.totalQuantity()).isEqualTo(75.0);
        assertThat(report.byCrop()).containsEntry("Nootka Rose", 60.0);
        // Chronological oldest → newest
        assertThat(report.entries().get(0).getHarvestDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(report.entries().get(2).getHarvestDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(report.toMap()).containsKeys("entries", "byCrop");

        byte[] pdf = service.generateHarvestLogPdf(report);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generateHarvestLogPdf(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.buildHarvestLogReport(
                        LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 10)));
    }

    @Test
    @DisplayName("dailyQuantitiesLast7Days buckets by day oldest→today")
    void dailyQuantities() {
        LocalDate today = LocalDate.now();
        when(repository.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(any(), any()))
                .thenReturn(List.of(
                        new HarvestEntry(today, "A", 10, "stems", "", ""),
                        new HarvestEntry(today.minusDays(2), "B", 7, "stems", "", ""),
                        new HarvestEntry(today.minusDays(2), "C", 3, "stems", "", "")
                ));
        double[] days = service.dailyQuantitiesLast7Days();
        assertThat(days).hasSize(7);
        assertThat(days[6]).isEqualTo(10.0); // today
        assertThat(days[4]).isEqualTo(10.0); // today-2
        assertThat(days[0]).isEqualTo(0.0);
    }

    @Test
    @DisplayName("exportToCsv writes header and filtered rows")
    void exportCsv() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("harvest-export", ".csv");
        HarvestEntry a = new HarvestEntry(LocalDate.of(2026, 7, 1), "Nootka Rose", 12.5, "stems", "Bed A", "am cut");
        a.setId(1L);
        when(repository.findAllByOrderByHarvestDateDescIdDesc()).thenReturn(List.of(a));

        service.exportToCsv(tmp.toString());
        String body = java.nio.file.Files.readString(tmp);
        assertThat(body).contains("Id,Date,Crop,Quantity,Unit,BedOrField,Notes");
        assertThat(body).contains("Nootka Rose");
        assertThat(body).contains("12.50");
        assertThat(body).contains("Bed A");

        java.nio.file.Path tmp2 = java.nio.file.Files.createTempFile("harvest-filtered", ".csv");
        service.exportToCsv(tmp2.toString(), List.of(a));
        assertThat(java.nio.file.Files.readString(tmp2)).contains("am cut");

        assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv("  "));
        java.nio.file.Files.deleteIfExists(tmp);
        java.nio.file.Files.deleteIfExists(tmp2);
    }

    @Test
    @DisplayName("totalsByCrop(list) aggregates subset")
    void totalsByCropList() {
        Map<String, Double> totals = service.totalsByCrop(List.of(
                new HarvestEntry(LocalDate.now(), "A", 3, "stems", "", ""),
                new HarvestEntry(LocalDate.now(), "A", 2, "stems", "", "")
        ));
        assertThat(totals.get("A")).isEqualTo(5.0);
        assertThat(service.totalsByCrop((List<HarvestEntry>) null)).isEmpty();
    }
}

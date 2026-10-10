package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.repository.HarvestRepository;
import org.junit.jupiter.api.Test;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HarvestReportQuantityTest {
    @Test void reportsKeepPreciseWeightsAndSeparateDifferentUnitsInTheSameBed() throws Exception {
        var day = LocalDate.of(2026, 10, 1);
        var flower = new HarvestEntry(day, "Blue Dream flower", 3.123456, "grams", "Room A", "");
        flower.setLossQuantity(0.123456);
        var clones = new HarvestEntry(day, "Blue Dream clones", 12, "plants", "Room A", "");
        var rows = List.of(flower, clones);
        var repository = mock(HarvestRepository.class);
        when(repository.findAllNewestFirst()).thenReturn(rows);
        when(repository.findBetween(any(), any())).thenReturn(rows);
        var service = new HarvestService(repository, null, null, new HarvestReportGenerator(new FarmProfile()),
                new HarvestCsvExporter(), new FarmProfile());
        var bed = service.productionByBed(day, day);
        assertThat(bed.beds()).singleElement().satisfies(row -> {
            assertThat(row.quantitySummary()).isEqualTo("3.123456 gram · 12 plant");
            assertThat(row.byProductUnit()).containsEntry("Blue Dream flower (gram)", 3.123456)
                    .containsEntry("Blue Dream clones (plant)", 12d);
        });
        assertThat(bed.plainText()).contains("3.123456 gram", "12 plant");
        var log = service.buildHarvestLogReport(day, day);
        assertThat(log.plainText()).contains("3.123456 gram", "12 plant", "0.123456");
        assertThat(service.generateHarvestLogPdf(log)).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        var csv = new StringWriter();
        new HarvestCsvExporter().writeCsv(csv, rows);
        assertThat(csv.toString()).contains("3.123456", "0.123456", "3.246912");
    }
}

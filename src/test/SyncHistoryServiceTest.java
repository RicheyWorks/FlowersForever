package com.flowerfarm.service;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.repository.SyncHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncHistoryService")
class SyncHistoryServiceTest {

    @Mock SyncHistoryJpaRepository repository;
    SyncHistoryService service;

    @BeforeEach
    void setUp() {
        service = new SyncHistoryService(repository);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("recordResult stores success with list size as count")
    void recordImportSuccess() {
        List<Item> items = List.of(
                new Item("A", "Other", 1, "u", 0.5, 1, ""),
                new Item("B", "Other", 1, "u", 0.5, 1, "")
        );
        ConnectorResult<List<Item>> result = ConnectorResult.ok(items, "Imported 2", "csv");

        service.recordResult("csv", "IMPORT", result);

        ArgumentCaptor<SyncHistoryEntry> cap = ArgumentCaptor.forClass(SyncHistoryEntry.class);
        verify(repository).save(cap.capture());
        SyncHistoryEntry e = cap.getValue();
        assertThat(e.isSuccess()).isTrue();
        assertThat(e.getOperation()).isEqualTo("IMPORT");
        assertThat(e.getConnectorName()).isEqualTo("csv");
        assertThat(e.getRecordCount()).isEqualTo(2);
        assertThat(e.getMessage()).contains("Imported");
    }

    @Test
    @DisplayName("recordResult stores SyncSummary total")
    void recordSyncSummary() {
        ConnectorResult<SyncSummary> result = ConnectorResult.ok(
                new SyncSummary(1, 2, 0, 3, 0), "done", "shopify");
        service.recordResult("shopify", "SYNC", result);

        ArgumentCaptor<SyncHistoryEntry> cap = ArgumentCaptor.forClass(SyncHistoryEntry.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getRecordCount()).isEqualTo(6);
        assertThat(cap.getValue().getOperation()).isEqualTo("SYNC");
    }

    @Test
    @DisplayName("recordResult stores failure without count")
    void recordFailure() {
        ConnectorResult<Integer> result = ConnectorResult.fail("boom", "detail", "square");
        service.recordResult("square", "EXPORT", result);

        ArgumentCaptor<SyncHistoryEntry> cap = ArgumentCaptor.forClass(SyncHistoryEntry.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().isSuccess()).isFalse();
        assertThat(cap.getValue().getRecordCount()).isNull();
        assertThat(cap.getValue().getDetail()).isEqualTo("detail");
    }

    @Test
    @DisplayName("recent clamps limit and queries repository")
    void recentClamps() {
        service.recent(9999);
        verify(repository).findAllByOrderByOccurredAtDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("filter applies connector, operation, success, message query")
    void filter() {
        SyncHistoryEntry ok = new SyncHistoryEntry("harvest", "HARVEST_LOG", true, "logged roses", "bed A", 10);
        SyncHistoryEntry fail = new SyncHistoryEntry("shopify", "IMPORT", false, "token expired", "401", null);
        SyncHistoryEntry crm = new SyncHistoryEntry("crm", "ORDER_FULFILL", true, "fulfilled #3", "", 1);
        when(repository.findAllByOrderByOccurredAtDesc(any(Pageable.class)))
                .thenReturn(List.of(ok, fail, crm));

        assertThat(service.filter("harvest", null, null, null, 50)).hasSize(1);
        assertThat(service.filter(null, "IMPORT", null, null, 50)).hasSize(1);
        assertThat(service.filter(null, null, false, null, 50)).hasSize(1);
        assertThat(service.filter(null, null, true, "fulfill", 50)).hasSize(1);
        assertThat(service.filter("ALL", "ALL", null, "token", 50)).hasSize(1);
    }

    @Test
    @DisplayName("stats counts ok/fail")
    void stats() {
        SyncHistoryEntry ok = new SyncHistoryEntry("a", "IMPORT", true, "ok", "", 1);
        SyncHistoryEntry fail = new SyncHistoryEntry("b", "EXPORT", false, "nope", "", null);
        SyncHistoryService.HistoryStats s = service.stats(List.of(ok, fail, ok));
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.ok()).isEqualTo(2);
        assertThat(s.fail()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportToCsv writes header and rows")
    void exportCsv() throws Exception {
        SyncHistoryEntry e = new SyncHistoryEntry("csv", "EXPORT", true, "done", "n/a", 4);
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("sync-hist", ".csv");
        try {
            service.exportToCsv(tmp.toString(), List.of(e));
            String body = java.nio.file.Files.readString(tmp);
            assertThat(body).contains("Id,When,Connector,Operation,Success");
            assertThat(body).contains("csv");
            assertThat(body).contains("EXPORT");
            assertThat(body).contains("done");
            assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv("  ", List.of()));
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}

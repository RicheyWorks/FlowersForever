package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.model.Item;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Durable AI farm-data exports")
class FarmDataExportServiceTest {

    @Mock FarmDataPageService pageService;
    @Mock EntityManager entityManager;

    @TempDir Path tempDirectory;

    ObjectMapper mapper;
    FarmDataExportService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().findAndRegisterModules();
        service = new FarmDataExportService(
                pageService, mapper, entityManager, tempDirectory.toString());
    }

    @Test
    @DisplayName("writes a complete immutable export and resumes it after recreation")
    void createsAndResumesCompleteExport() {
        Item first = item(1L, "Dahlia");
        Item second = item(2L, "Rose");
        Item third = item(3L, "Tulip");
        doReturn(new FarmDataPageService.RecordPage<>(
                List.of(first, second), 0, 2, 3))
                .when(pageService).list(
                        eq("inventory"), eq(0), eq(2), isNull(), isNull());
        doReturn(new FarmDataPageService.RecordPage<>(
                List.of(third), 2, 2, 3))
                .when(pageService).list(
                        eq("inventory"), eq(2), eq(2), isNull(), isNull());

        FarmDataExportService.CreatedExport created = service.create(
                "pilot-farm-complete-2026", List.of("inventory"), 2, null, null);

        assertThat(created.replayed()).isFalse();
        assertThat(created.export().recordCounts()).containsEntry("inventory", 3L);
        assertThat(created.export().totalRecords()).isEqualTo(3);
        assertThat(created.export().partCount()).isEqualTo(2);
        assertThat(created.export().snapshotSha256()).hasSize(64);
        InOrder paging = inOrder(pageService, entityManager);
        paging.verify(pageService).list("inventory", 0, 2, null, null);
        paging.verify(entityManager).clear();
        paging.verify(pageService).list("inventory", 2, 2, null, null);
        paging.verify(entityManager).clear();

        FarmDataExportService.ExportChunk firstPart = service.read(
                created.export().exportId(), 0);
        FarmDataExportService.ExportChunk secondPart = service.read(
                created.export().exportId(), firstPart.nextPart());
        assertThat(firstPart.records()).extracting(node -> node.path("name").asText())
                .containsExactly("Dahlia", "Rose");
        assertThat(firstPart.hasMore()).isTrue();
        assertThat(secondPart.records()).extracting(node -> node.path("name").asText())
                .containsExactly("Tulip");
        assertThat(secondPart.hasMore()).isFalse();
        assertThat(secondPart.nextPart()).isEqualTo(-1);

        reset(pageService);
        FarmDataExportService recreated = new FarmDataExportService(
                pageService, mapper, entityManager, tempDirectory.toString());
        FarmDataExportService.ExportSummary recovered = recreated.get(
                "pilot-farm-complete-2026");
        FarmDataExportService.CreatedExport replayed = recreated.create(
                "pilot-farm-complete-2026", List.of("inventory"), 2, null, null);

        assertThat(recovered.exportId()).isEqualTo(created.export().exportId());
        assertThat(recovered.snapshotSha256())
                .isEqualTo(created.export().snapshotSha256());
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.export()).isEqualTo(recovered);
        verifyNoInteractions(pageService);
    }

    @Test
    @DisplayName("defaults a snapshot to every canonical record type")
    void defaultsToAllCanonicalTypes() {
        doAnswer(invocation -> new FarmDataPageService.RecordPage<>(
                List.of(), invocation.getArgument(1), invocation.getArgument(2), 0))
                .when(pageService).list(
                        anyString(), anyInt(), anyInt(), isNull(), isNull());

        FarmDataExportService.CreatedExport created = service.create(
                "all-types-empty-farm", null, 100, null, null);

        assertThat(created.export().recordTypes())
                .containsExactlyElementsOf(FarmDataPageService.RECORD_TYPES);
        assertThat(created.export().recordCounts())
                .containsOnlyKeys(FarmDataPageService.RECORD_TYPES);
        assertThat(created.export().recordCounts().values()).containsOnly(0L);
        assertThat(created.export().partCount()).isZero();
        for (String type : FarmDataPageService.RECORD_TYPES) {
            verify(pageService).list(type, 0, 100, null, null);
        }
    }

    @Test
    @DisplayName("rejects reuse of an export key with different snapshot settings")
    void rejectsChangedRequestForExistingKey() {
        doReturn(new FarmDataPageService.RecordPage<>(List.of(), 0, 50, 0))
                .when(pageService).list(
                        eq("inventory"), eq(0), eq(50), isNull(), isNull());
        service.create("one-export-key", List.of("inventory"), 50, null, null);

        assertThatThrownBy(() -> service.create(
                "one-export-key", List.of("customers"), 50, null, null))
                .isInstanceOf(FarmDataExportConflictException.class)
                .hasMessageContaining("different completed snapshot");
    }

    @Test
    @DisplayName("detects a changed chunk before returning exported records")
    void rejectsCorruptChunk() throws Exception {
        doReturn(new FarmDataPageService.RecordPage<>(
                List.of(item(1L, "Dahlia")), 0, 10, 1))
                .when(pageService).list(
                        eq("inventory"), eq(0), eq(10), isNull(), isNull());
        FarmDataExportService.CreatedExport created = service.create(
                "corruption-check", List.of("inventory"), 10, null, null);
        Path part = tempDirectory.resolve("ai-snapshots")
                .resolve(created.export().exportId()).resolve("part-000000.json");
        Files.writeString(part, "[]");

        assertThatThrownBy(() -> service.read(created.export().exportId(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("corrupt", "size does not match");
    }

    private static Item item(Long id, String name) {
        Item item = new Item(name, "Cut flower", 2.5, "stem", 0.75, 20, "");
        item.setId(id);
        return item;
    }
}

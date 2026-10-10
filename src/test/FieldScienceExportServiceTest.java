package com.flowerfarm.service;

import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.reference.FieldScienceBundle;
import com.flowerfarm.repository.FieldScienceExportRepository;
import com.flowerfarm.repository.FieldScienceExportRepository.SummarySlice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldScienceExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:34:56Z");

    @Mock FieldScienceExportRepository repository;
    private FieldScienceExportService service;

    @BeforeEach
    void setUp() {
        service = new FieldScienceExportService(repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesExactTextWithCuratedSourceAndDigest() throws Exception {
        String content = "\uFEFF# Soil recipe\r\n  mix: café  \n\n";
        when(repository.save(any(FieldScienceExport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FieldScienceExport saved = service.save("soil-recipes", "Mix trial",
                "mix.ECO", "  For review  ", content);

        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getByteLength())
                .isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(saved.getSha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(content.getBytes(StandardCharsets.UTF_8))));
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getInstrumentId()).isEqualTo("soil-recipes");
        assertThat(saved.getInstrumentTitle()).contains("Subcool");
        assertThat(saved.getSourceCommit()).isEqualTo(FieldScienceBundle.SOURCE_COMMIT);
        assertThat(saved.getSourceUrl()).endsWith("/docs/soil-recipes.html");
        assertThat(saved.getTitle()).isEqualTo("Mix trial");
        assertThat(saved.getFilename()).isEqualTo("mix.ECO");
        assertThat(saved.getNote()).isEqualTo("For review");
        verify(repository).save(saved);
    }

    @Test
    void acceptsClipboardTextWithoutFilenameAndPreservesWhitespace() {
        when(repository.save(any(FieldScienceExport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        FieldScienceExport saved = service.save("farm-scout", "Counts", null,
                null, "  data: count=2\r\n\t");
        assertThat(saved.getFilename()).isNull();
        assertThat(saved.getContent()).isEqualTo("  data: count=2\r\n\t");
    }

    @Test
    void rejectsUnknownOrNonTextInputsBeforeWriting() {
        assertThatThrownBy(() -> service.save("unknown-tool", "Trial", null,
                null, "data: 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known field-science instrument");
        assertThatThrownBy(() -> service.save("farm-scout", "Trial", null,
                null, "\0binary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("binary control");
        assertThatThrownBy(() -> service.save("farm-scout", "Trial", null,
                null, "bad " + (char) 0xD800 + " surrogate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid UTF-8");
        assertThatThrownBy(() -> service.save("farm-scout", "Trial", null,
                null, "\n  \t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export text is required");
        assertThatThrownBy(() -> service.save("farm-scout", "Trial", null,
                null, "\uFEFF\n  \t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export text is required");
        assertThatThrownBy(() -> service.save("farm-scout", "Trial", null,
                null, "é".repeat(262_145)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512 KiB");
        verify(repository, never()).save(any());
    }

    @Test
    void restrictsFilenameAndMetadataWithoutChangingBody() {
        for (String filename : List.of("report.pdf", "../report.eco", "a\\b.txt", ".csv")) {
            assertThatThrownBy(() -> service.save("soil-bench", "Trial", filename,
                    null, "data: 1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.save("soil-bench", " ", null,
                null, "data: 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export title is required");
        assertThatThrownBy(() -> service.save("soil-bench", "Trial", null,
                "x".repeat(501), "data: 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export note cannot exceed");
        verifyNoInteractions(repository);
    }

    @Test
    void listsBoundedMetadataAndReadsOneFullExport() {
        FieldScienceExportSummary summary = new FieldScienceExportSummary(9L, NOW,
                "farm-scout", "Farm Scout", "source", FieldScienceBundle.SOURCE_COMMIT,
                "Counts", null, "", 4, "a".repeat(64));
        when(repository.summaryPage("", 0, FieldScienceExportService.LIST_LIMIT))
                .thenReturn(new SummarySlice(List.of(summary), false, 1));
        assertThat(service.list()).containsExactly(summary);
        assertThat(service.page(0).hasMore()).isFalse();
        assertThat(service.page(0).totalCount()).isEqualTo(1);
        FieldScienceExport export = new FieldScienceExport(NOW, "farm-scout",
                "Farm Scout", "source", FieldScienceBundle.SOURCE_COMMIT,
                "Counts", null, "", 4, "a".repeat(64), "abcd");
        when(repository.findById(9L)).thenReturn(Optional.of(export));
        assertThat(service.find(9)).contains(export);
        assertThatThrownBy(() -> service.find(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.page(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesAndBoundsLiteralMetadataSearch() {
        when(repository.summaryPage("o'brien %_", 1,
                FieldScienceExportService.LIST_LIMIT))
                .thenReturn(new SummarySlice(List.of(), false, 0));
        FieldScienceExportService.ExportPage result = service.page("  O'Brien %_  ", 1);
        assertThat(result.pageIndex()).isEqualTo(1);
        assertThat(result.totalCount()).isZero();
        verify(repository).summaryPage("o'brien %_", 1,
                FieldScienceExportService.LIST_LIMIT);

        assertThatThrownBy(() -> service.page("x".repeat(121), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120 characters");
        assertThatThrownBy(() -> service.page("one\ntwo", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }
}

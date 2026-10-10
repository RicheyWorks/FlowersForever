package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.flowerfarm.model.Item;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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

    @Test
    @ResourceLock(Resources.LOCALE)
    void writesAsciiPartFilenamesRegardlessOfFormatLocale() {
        stubInventoryPage();
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            for (Locale locale : List.of(Locale.US, Locale.forLanguageTag("ar-EG"),
                    Locale.forLanguageTag("fa-IR"))) {
                Locale.setDefault(Locale.Category.FORMAT, locale);
                var created = service.create("ascii-parts-" + locale.toLanguageTag(),
                        List.of("inventory"), 10, null, null);

                assertThat(exportPath(created.export().exportId()).resolve("part-000000.json"))
                        .as("Part filename under %s", locale).exists();
            }
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void resumesExportsAfterTheFormatLocaleChanges() {
        stubInventoryPage();
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            var created = service.create("locale-change", List.of("inventory"), 10, null, null);
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));

            assertThat(service.get("locale-change").snapshotSha256())
                    .isEqualTo(created.export().snapshotSha256());
            assertThat(service.read(created.export().exportId(), 0).records())
                    .singleElement().satisfies(record ->
                            assertThat(record.path("name").asText()).isEqualTo("Dahlia"));
            assertThat(service.create("locale-change", List.of("inventory"), 10, null, null)
                    .replayed()).isTrue();
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void readsPreviouslyCreatedLocalizedPartFilenames() throws Exception {
        stubInventoryPage();
        useTextDatesForManifestFixture();
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.US);
            for (String filename : List.of("part-٠٠٠٠٠٠.json", "part-۰۰۰۰۰۰.json")) {
                String key = "legacy-localized-" + filename;
                var created = service.create(key, List.of("inventory"), 10, null, null);
                Path directory = exportPath(created.export().exportId());
                Files.move(directory.resolve("part-000000.json"), directory.resolve(filename));
                replaceManifestPartFilename(directory, filename);

                assertThat(service.get(key).partCount()).isEqualTo(1);
                assertThat(service.read(created.export().exportId(), 0).records())
                        .singleElement().satisfies(record ->
                                assertThat(record.path("name").asText()).isEqualTo("Dahlia"));
                assertThat(service.create(key, List.of("inventory"), 10, null, null).replayed())
                        .isTrue();
            }
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    @Test
    void rejectsPartFilenamesThatDoNotExactlyIdentifyTheirManifestIndex() throws Exception {
        stubInventoryPage();
        useTextDatesForManifestFixture();
        var created = service.create("invalid-part-names", List.of("inventory"), 10, null, null);
        Path directory = exportPath(created.export().exportId());
        for (String filename : List.of("../part-000000.json", "part-000000/extra.json",
                "part-٠٠٠٠٠١.json", "part-٠٠٠٠٠.json", "part-٠٠٠٠٠٠٠.json",
                "part-00000٠.json", "part-000000.json.bak")) {
            replaceManifestPartFilename(directory, filename);

            assertThatThrownBy(() -> service.get("invalid-part-names"))
                    .as("Invalid part filename %s", filename)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("corrupt", "part metadata is invalid");
        }
    }

    @ParameterizedTest(name = "recovers legacy {0} metadata with current mapper settings")
    @MethodSource("legacyFormatting")
    void recoversLegacyMetadataAcrossApplicationFormattingChanges(
            String format, ObjectMapper legacyMapper) throws Exception {
        LegacyExport legacy = legacyExport(legacyMapper);
        byte[] storedManifest = Files.readAllBytes(legacy.manifestPath());

        var recovered = service.get(legacy.key());
        assertThat(recovered.snapshotSha256()).isEqualTo(legacy.snapshotSha256());
        assertThat(recovered.createdAt()).isEqualTo(Instant.parse("2026-09-13T20:30:40.123456780Z"));
        assertThat(service.read(legacy.exportId(), 0).records())
                .singleElement().satisfies(record ->
                        assertThat(record.path("name").asText()).isEqualTo("Dahlia"));
        assertThat(service.create(legacy.key(), List.of("production-tasks"), 10,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).replayed()).isTrue();
        assertThatThrownBy(() -> service.create(legacy.key(), List.of("production-tasks"), 11,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(FarmDataExportConflictException.class);
        assertThat(Files.readAllBytes(legacy.manifestPath())).isEqualTo(storedManifest);
    }

    @Test
    void recoversLegacyMillisecondTimestampsWithTheirStoredPrecision() throws Exception {
        LegacyExport legacy = legacyExport(metadataMapper()
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS));
        byte[] storedManifest = Files.readAllBytes(legacy.manifestPath());

        var recovered = service.get(legacy.key());
        assertThat(recovered.createdAt()).isEqualTo(Instant.parse("2026-09-13T20:30:40.123Z"));
        assertThat(recovered.snapshotSha256()).isEqualTo(legacy.snapshotSha256());
        assertThat(service.create(legacy.key(), List.of("production-tasks"), 10,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).replayed()).isTrue();
        assertThat(Files.readAllBytes(legacy.manifestPath())).isEqualTo(storedManifest);
    }

    @Test
    void keepsNewMetadataIndependentOfApplicationFormatting() throws Exception {
        stubInventoryPage();
        String expectedRequestFingerprint = null;
        for (ObjectMapper application : List.of(metadataMapper(),
                metadataMapper().enable(SerializationFeature.INDENT_OUTPUT),
                metadataMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))) {
            service = new FarmDataExportService(pageService, application, entityManager, tempDirectory.toString());
            String key = "stable-metadata-" + UUID.randomUUID();
            var created = service.create(key, List.of("inventory"), 10, null, null);
            ObjectNode manifest = (ObjectNode) mapper.readTree(Files.readAllBytes(
                    exportPath(created.export().exportId()).resolve("manifest.json")));
            assertThat(manifest.path("createdAt").isTextual()).isTrue();
            String fingerprint = manifest.path("requestFingerprint").asText();
            if (expectedRequestFingerprint == null) {
                expectedRequestFingerprint = fingerprint;
            } else {
                assertThat(fingerprint).isEqualTo(expectedRequestFingerprint);
            }
            ObjectMapper changedSettings = metadataMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            FarmDataExportService recreated = new FarmDataExportService(
                    pageService, changedSettings, entityManager, tempDirectory.toString());
            assertThat(recreated.get(key).snapshotSha256()).isEqualTo(created.export().snapshotSha256());
            assertThat(recreated.create(key, List.of("inventory"), 10, null, null).replayed()).isTrue();
        }
    }

    @Test
    void rejectsTamperedLegacyMetadataWithoutRewritingIt() throws Exception {
        LegacyExport legacy = legacyExport(metadataMapper().enable(SerializationFeature.INDENT_OUTPUT));
        byte[] original = Files.readAllBytes(legacy.manifestPath());
        for (String changedField : List.of("part", "date", "count")) {
            ObjectNode changed = (ObjectNode) mapper.readTree(original);
            switch (changedField) {
                case "part" -> ((ObjectNode) changed.path("parts").get(0)).put("recordOffset", 99);
                case "date" -> changed.put("createdAt", "2026-09-14T20:30:40.123456780Z");
                case "count" -> ((ObjectNode) changed.path("recordCounts")).put("production-tasks", -1);
                default -> throw new IllegalArgumentException(changedField);
            }
            byte[] tampered = mapper.writeValueAsBytes(changed);
            Files.write(legacy.manifestPath(), tampered);

            assertThatThrownBy(() -> service.get(legacy.key())).as("Changed %s", changedField)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("corrupt");
            assertThat(Files.readAllBytes(legacy.manifestPath())).isEqualTo(tampered);
        }
    }

    private static Stream<Arguments> legacyFormatting() {
        return Stream.of(
                Arguments.of("compact ISO", metadataMapper()),
                Arguments.of("pretty ISO", metadataMapper().enable(SerializationFeature.INDENT_OUTPUT)),
                Arguments.of("sorted maps", metadataMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)),
                Arguments.of("decimal timestamps", metadataMapper().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)),
                Arguments.of("escaped Unicode", metadataMapper().configure(
                        JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true)),
                Arguments.of("pretty sorted escaped timestamps", metadataMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true)));
    }

    private static ObjectMapper metadataMapper() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private LegacyExport legacyExport(ObjectMapper legacyMapper) throws Exception {
        useTextDatesForManifestFixture();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        doReturn(new FarmDataPageService.RecordPage<>(List.of(item(1L, "Dahlia")), 0, 10, 1))
                .when(pageService).list("production-tasks", 0, 10, from, to);
        String key = "legacy-metadata-" + UUID.randomUUID();
        var created = service.create(key, List.of("production-tasks"), 10, from, to);
        Path directory = exportPath(created.export().exportId());
        Path manifestPath = directory.resolve("manifest.json");
        ObjectNode original = (ObjectNode) mapper.readTree(Files.readAllBytes(manifestPath));
        String localized = "part-٠٠٠٠٠٠.json";
        Files.move(directory.resolve(original.path("parts").get(0).path("fileName").asText()),
                directory.resolve(localized));
        ((ObjectNode) original.path("parts").get(0)).put("fileName", localized);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("formatVersion", "1.0");
        request.put("recordTypes", List.of("production-tasks"));
        request.put("chunkRecords", 10);
        request.put("taskFrom", from);
        request.put("taskTo", to);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("formatVersion", "1.0");
        metadata.put("exportId", created.export().exportId());
        metadata.put("requestFingerprint", digest(legacyMapper.writeValueAsBytes(request)));
        metadata.put("createdAt", Instant.parse("2026-09-13T20:30:40.123456780Z"));
        metadata.put("recordTypes", List.of("production-tasks"));
        metadata.put("chunkRecords", 10);
        metadata.put("taskFrom", from);
        metadata.put("taskTo", to);
        metadata.put("recordCounts", Map.of("production-tasks", 1L));
        metadata.put("totalRecords", 1L);
        metadata.put("parts", original.path("parts"));
        String checksum = digest(legacyMapper.writeValueAsBytes(metadata));
        metadata.put("snapshotSha256", checksum);
        Files.write(manifestPath, legacyMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata));
        return new LegacyExport(key, created.export().exportId(), checksum, manifestPath);
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record LegacyExport(String key, String exportId, String snapshotSha256, Path manifestPath) { }

    private void stubInventoryPage() {
        doReturn(new FarmDataPageService.RecordPage<>(List.of(item(1L, "Dahlia")), 0, 10, 1))
                .when(pageService).list(eq("inventory"), eq(0), eq(10), isNull(), isNull());
    }

    private Path exportPath(String exportId) {
        return tempDirectory.resolve("ai-snapshots").resolve(exportId);
    }

    private void useTextDatesForManifestFixture() {
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new FarmDataExportService(pageService, mapper, entityManager, tempDirectory.toString());
    }

    private void replaceManifestPartFilename(Path directory, String filename) throws Exception {
        Path path = directory.resolve("manifest.json");
        ObjectNode manifest = (ObjectNode) mapper.readTree(Files.readAllBytes(path));
        ((ObjectNode) manifest.path("parts").get(0)).put("fileName", filename);
        ObjectNode unsigned = manifest.deepCopy();
        unsigned.remove("snapshotSha256");
        manifest.put("snapshotSha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(unsigned))));
        Files.write(path, mapper.writeValueAsBytes(manifest));
    }

    private static Item item(Long id, String name) {
        Item item = new Item(name, "Cut flower", 2.5, "stem", 0.75, 20, "");
        item.setId(id);
        return item;
    }
}

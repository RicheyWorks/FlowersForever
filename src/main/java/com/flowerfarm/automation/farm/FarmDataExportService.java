package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Creates immutable, restart-stable farm-data exports and reads verified chunks. */
@Service
public class FarmDataExportService {

    public static final int MAX_CHUNK_RECORDS = 200;
    public static final int MAX_CHUNK_BYTES = 1_000_000;

    private static final Logger log = LoggerFactory.getLogger(FarmDataExportService.class);
    private static final String FORMAT_VERSION = "1.0";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String EXPORT_ID_PATTERN = "farm-export-[0-9a-f]{32}";
    private static final long MAX_MANIFEST_BYTES = 32L * 1024 * 1024;
    // Durable metadata must not change with the application's record serializers or JSON settings.
    private static final ObjectMapper METADATA_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "formatVersion", "exportId", "requestFingerprint", "createdAt", "recordTypes",
            "chunkRecords", "taskFrom", "taskTo", "recordCounts", "totalRecords", "parts");
    private static final List<ObjectWriter> METADATA_DIGEST_WRITERS = metadataDigestWriters();

    private final FarmDataPageService pageService;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final Path exportRoot;

    public FarmDataExportService(
            FarmDataPageService pageService,
            ObjectMapper mapper,
            EntityManager entityManager,
            @Value("${flowerfarm.exports.directory:data/exports}") String exportDirectory) {
        this.pageService = pageService;
        this.mapper = mapper;
        this.entityManager = entityManager;
        String configured = exportDirectory == null || exportDirectory.isBlank()
                ? "data/exports" : exportDirectory.trim();
        this.exportRoot = Path.of(configured).toAbsolutePath().normalize()
                .resolve("ai-snapshots").normalize();
    }

    /**
     * Creates a complete immutable snapshot. A stable export key makes an exact retry durable
     * across gateway eviction, response loss, and application restart.
     */
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public synchronized CreatedExport create(
            String exportKey,
            List<String> requestedTypes,
            int chunkRecords,
            LocalDate taskFrom,
            LocalDate taskTo) {
        ExportRequest request = normalizeRequest(
                requestedTypes, chunkRecords, taskFrom, taskTo);
        String exportId = exportId(exportKey);
        String fingerprint = requestFingerprint(request);
        ensureExportRoot();

        Path destination = exportDirectory(exportId);
        if (Files.exists(destination)) {
            return replayExisting(exportId, request);
        }

        Path staging = null;
        try {
            staging = Files.createTempDirectory(exportRoot, "." + exportId + "-");
            Instant createdAt = Instant.now();
            List<ExportPart> parts = new ArrayList<>();
            Map<String, Long> counts = new LinkedHashMap<>();

            for (String type : request.recordTypes()) {
                long count = exportType(staging, exportId, type, request, parts);
                counts.put(type, count);
            }

            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            ExportManifest unsigned = new ExportManifest(
                    FORMAT_VERSION, exportId, fingerprint, createdAt,
                    request.recordTypes(), request.chunkRecords(),
                    request.taskFrom(), request.taskTo(), counts, total,
                    List.copyOf(parts), "");
            ExportManifest manifest = unsigned.withSnapshotSha256(
                    snapshotDigest(unsigned));
            writeManifest(staging, manifest);
            publish(staging, destination);
            staging = null;
            return new CreatedExport(summary(manifest), false);
        } catch (FarmDataExportConflictException | IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.error("Could not create record export {}", exportId, exception);
            throw new IllegalStateException(
                    "Could not create record export '" + exportId + "'.", exception);
        } finally {
            cleanup(staging);
        }
    }

    /** Finds a completed snapshot through the same stable key used to create it. */
    public ExportSummary get(String exportKey) {
        return summary(readManifest(exportId(exportKey)));
    }

    /** Reads one checksummed snapshot chunk without loading the complete export. */
    public ExportChunk read(String exportId, int partNumber) {
        ExportManifest manifest = readManifest(exportId);
        if (partNumber < 0 || partNumber >= manifest.parts().size()) {
            throw new IllegalArgumentException("Export part must be between 0 and "
                    + Math.max(0, manifest.parts().size() - 1) + ".");
        }
        ExportPart part = manifest.parts().get(partNumber);
        Path file = exportDirectory(exportId).resolve(part.fileName()).normalize();
        if (!file.startsWith(exportDirectory(exportId))) {
            throw corrupt(exportId, "part path escapes its export directory");
        }
        try {
            long actualSize = Files.size(file);
            if (actualSize != part.bytes() || actualSize > MAX_CHUNK_BYTES) {
                throw corrupt(exportId, "part " + partNumber + " size does not match");
            }
            byte[] bytes = Files.readAllBytes(file);
            if (!sha256(bytes).equals(part.sha256())) {
                throw corrupt(exportId, "part " + partNumber + " checksum does not match");
            }
            JsonNode content = mapper.readTree(bytes);
            if (!content.isArray() || content.size() != part.recordCount()) {
                throw corrupt(exportId, "part " + partNumber + " record count does not match");
            }
            List<JsonNode> records = new ArrayList<>();
            content.forEach(records::add);
            boolean hasMore = partNumber + 1 < manifest.parts().size();
            return new ExportChunk(
                    exportId, manifest.snapshotSha256(), partNumber,
                    part.recordType(), part.recordOffset(), part.sha256(),
                    List.copyOf(records), hasMore, hasMore ? partNumber + 1 : -1);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read record export '" + exportId + "'.", exception);
        }
    }

    private long exportType(
            Path staging,
            String exportId,
            String type,
            ExportRequest request,
            List<ExportPart> parts) throws IOException {
        int offset = 0;
        long expectedTotal = -1;
        long written = 0;
        while (true) {
            FarmDataPageService.RecordPage<?> page = pageService.list(
                    type, offset, request.chunkRecords(),
                    request.taskFrom(), request.taskTo());
            if (expectedTotal < 0) {
                expectedTotal = page.total();
            } else if (expectedTotal != page.total()) {
                throw new IllegalStateException(
                        "Record count changed inside export snapshot for " + type + ".");
            }
            if (page.records().isEmpty() && page.hasMore()) {
                throw new IllegalStateException(
                        "Database paging made no progress for " + type + ".");
            }
            writePage(staging, exportId, type, offset,
                    request.chunkRecords(), page.records(), parts);
            // The snapshot transaction spans every page. Release serialized entities so
            // Hibernate does not retain the whole farm in its persistence context.
            entityManager.clear();
            written += page.records().size();
            if (!page.hasMore()) {
                break;
            }
            offset = Math.addExact(offset, page.records().size());
        }
        if (written != expectedTotal) {
            throw new IllegalStateException("Exported " + written + " of "
                    + expectedTotal + " " + type + " records.");
        }
        return written;
    }

    private void writePage(
            Path staging,
            String exportId,
            String type,
            int pageOffset,
            int chunkRecords,
            List<?> records,
            List<ExportPart> parts) throws IOException {
        List<JsonNode> chunk = new ArrayList<>();
        int chunkBytes = 2;
        long chunkOffset = pageOffset;
        for (int index = 0; index < records.size(); index++) {
            JsonNode record = mapper.valueToTree(records.get(index));
            int recordBytes = jsonBytes(record).length;
            if (recordBytes + 2 > MAX_CHUNK_BYTES) {
                throw new IllegalStateException("One " + type
                        + " record exceeds the maximum export chunk size.");
            }
            int separatorBytes = chunk.isEmpty() ? 0 : 1;
            if (!chunk.isEmpty() && (chunk.size() >= chunkRecords
                    || chunkBytes + separatorBytes + recordBytes > MAX_CHUNK_BYTES)) {
                writePart(staging, exportId, type, chunkOffset, chunk, parts);
                chunk = new ArrayList<>();
                chunkBytes = 2;
                chunkOffset = (long) pageOffset + index;
                separatorBytes = 0;
            }
            chunk.add(record);
            chunkBytes += separatorBytes + recordBytes;
        }
        if (!chunk.isEmpty()) {
            writePart(staging, exportId, type, chunkOffset, chunk, parts);
        }
    }

    private void writePart(
            Path staging,
            String exportId,
            String type,
            long offset,
            List<JsonNode> records,
            List<ExportPart> parts) throws IOException {
        int partNumber = parts.size();
        String fileName = partFileName(partNumber);
        byte[] bytes = jsonBytes(records);
        if (bytes.length > MAX_CHUNK_BYTES) {
            throw new IllegalStateException(
                    "Generated export chunk exceeds its size limit.");
        }
        Files.write(staging.resolve(fileName), bytes,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        parts.add(new ExportPart(
                partNumber, type, offset, records.size(), fileName,
                bytes.length, sha256(bytes)));
        log.debug("Wrote export {} part {} ({} {} records)",
                exportId, partNumber, records.size(), type);
    }

    private CreatedExport replayExisting(String exportId, ExportRequest request) {
        ExportManifest existing = readManifest(exportId);
        ExportRequest stored = normalizeRequest(existing.recordTypes(), existing.chunkRecords(),
                existing.taskFrom(), existing.taskTo());
        if (!stored.equals(request)) {
            throw new FarmDataExportConflictException(
                    "Export key already identifies a different completed snapshot.");
        }
        return new CreatedExport(summary(existing), true);
    }

    private ExportManifest readManifest(String exportId) {
        validateExportId(exportId);
        Path manifestPath = exportDirectory(exportId).resolve(MANIFEST_FILE).normalize();
        if (!manifestPath.startsWith(exportDirectory(exportId))) {
            throw new IllegalArgumentException("Invalid record export id.");
        }
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException(
                    "Record export '" + exportId + "' was not found.");
        }
        try {
            if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
                throw corrupt(exportId, "manifest is too large");
            }
            JsonNode stored = METADATA_MAPPER.readTree(Files.readAllBytes(manifestPath));
            ExportManifest manifest = METADATA_MAPPER.treeToValue(stored, ExportManifest.class);
            validateManifest(exportId, manifest, stored);
            return manifest;
        } catch (JsonProcessingException exception) {
            throw corrupt(exportId, "manifest is not valid JSON", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read record export '" + exportId + "'.", exception);
        }
    }

    private void validateManifest(String exportId, ExportManifest manifest, JsonNode stored) {
        if (manifest == null
                || !FORMAT_VERSION.equals(manifest.formatVersion())
                || !exportId.equals(manifest.exportId())
                || manifest.createdAt() == null
                || manifest.requestFingerprint() == null
                || !manifest.requestFingerprint().matches("[0-9a-f]{64}")
                || manifest.snapshotSha256() == null
                || !manifest.snapshotSha256().matches("[0-9a-f]{64}")
                || manifest.chunkRecords() < 1
                || manifest.chunkRecords() > MAX_CHUNK_RECORDS
                || manifest.recordTypes() == null
                || manifest.recordCounts() == null
                || manifest.parts() == null) {
            throw corrupt(exportId, "manifest header is invalid");
        }
        ExportRequest request = normalizeRequest(
                manifest.recordTypes(), manifest.chunkRecords(),
                manifest.taskFrom(), manifest.taskTo());
        if (!request.recordTypes().equals(manifest.recordTypes())) {
            throw corrupt(exportId, "record types are not canonical");
        }

        Map<String, Long> counted = new LinkedHashMap<>();
        manifest.recordTypes().forEach(type -> counted.put(type, 0L));
        for (int index = 0; index < manifest.parts().size(); index++) {
            ExportPart part = manifest.parts().get(index);
            if (part == null || part.partNumber() != index
                    || !isPartFileName(part.fileName(), index)
                    || !counted.containsKey(part.recordType())
                    || part.recordOffset() < 0
                    || part.recordCount() < 1
                    || part.recordCount() > manifest.chunkRecords()
                    || part.bytes() < 2
                    || part.bytes() > MAX_CHUNK_BYTES
                    || part.sha256() == null
                    || !part.sha256().matches("[0-9a-f]{64}")) {
                throw corrupt(exportId, "part metadata is invalid");
            }
            counted.merge(part.recordType(), (long) part.recordCount(), Long::sum);
        }
        long countedTotal = counted.values().stream().mapToLong(Long::longValue).sum();
        if (!counted.equals(manifest.recordCounts())
                || countedTotal != manifest.totalRecords()
                || !matchesSnapshotDigest(manifest, stored)) {
            throw corrupt(exportId, "manifest totals or checksum do not match");
        }
    }

    private ExportRequest normalizeRequest(
            List<String> requestedTypes,
            int chunkRecords,
            LocalDate taskFrom,
            LocalDate taskTo) {
        if (chunkRecords < 1 || chunkRecords > MAX_CHUNK_RECORDS) {
            throw new IllegalArgumentException("chunk-records must be between 1 and "
                    + MAX_CHUNK_RECORDS + ".");
        }
        if ((taskFrom == null) != (taskTo == null)) {
            throw new IllegalArgumentException(
                    "task-from and task-to must be supplied together.");
        }
        Set<String> requested = new LinkedHashSet<>();
        if (requestedTypes == null) {
            requested.addAll(FarmDataPageService.RECORD_TYPES);
        } else {
            if (requestedTypes.isEmpty()) {
                throw new IllegalArgumentException("record-types must not be empty.");
            }
            for (String value : requestedTypes) {
                String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (!FarmDataPageService.RECORD_TYPES.contains(type)) {
                    throw new IllegalArgumentException("Unknown record type '" + value + "'.");
                }
                requested.add(type);
            }
        }
        List<String> types = FarmDataPageService.RECORD_TYPES.stream()
                .filter(requested::contains).toList();
        if (!types.contains("production-tasks") && taskFrom != null) {
            throw new IllegalArgumentException(
                    "Task dates require production-tasks in record-types.");
        }
        return new ExportRequest(types, chunkRecords, taskFrom, taskTo);
    }

    private String requestFingerprint(ExportRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("formatVersion", FORMAT_VERSION);
        canonical.put("recordTypes", request.recordTypes());
        canonical.put("chunkRecords", request.chunkRecords());
        canonical.put("taskFrom", request.taskFrom());
        canonical.put("taskTo", request.taskTo());
        return metadataDigest(METADATA_MAPPER.writer(), canonical);
    }

    private static String partFileName(int partNumber) {
        return String.format(Locale.ROOT, "part-%06d.json", partNumber);
    }

    private static boolean isPartFileName(String fileName, int partNumber) {
        String expected = partFileName(partNumber);
        if (expected.equals(fileName)) {
            return true;
        }
        int digitCount = expected.length() - "part-.json".length();
        if (fileName == null || !fileName.startsWith("part-") || !fileName.endsWith(".json")
                || fileName.length() < expected.length()
                || fileName.length() > "part-.json".length() + digitCount * 2) {
            return false;
        }
        // Legacy Formatter output used the host's decimal digits. Accept only
        // the same padded index in one digit alphabet, retaining its stored path.
        int[] digits = fileName.substring(5, fileName.length() - 5).codePoints().toArray();
        if (digits.length != digitCount) {
            return false;
        }
        int zeroDigit = -1;
        for (int index = 0; index < digits.length; index++) {
            int digit = Character.digit(digits[index], 10);
            if (Character.getType(digits[index]) != Character.DECIMAL_DIGIT_NUMBER
                    || digit != expected.charAt(5 + index) - '0') {
                return false;
            }
            if (zeroDigit < 0) {
                zeroDigit = digits[index] - digit;
            } else if (digits[index] - digit != zeroDigit) {
                return false;
            }
        }
        return true;
    }

    private String snapshotDigest(ExportManifest manifest) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        Map<String, Long> orderedCounts = new LinkedHashMap<>();
        manifest.recordTypes().forEach(type ->
                orderedCounts.put(type, manifest.recordCounts().get(type)));
        canonical.put("formatVersion", manifest.formatVersion());
        canonical.put("exportId", manifest.exportId());
        canonical.put("requestFingerprint", manifest.requestFingerprint());
        canonical.put("createdAt", manifest.createdAt());
        canonical.put("recordTypes", manifest.recordTypes());
        canonical.put("chunkRecords", manifest.chunkRecords());
        canonical.put("taskFrom", manifest.taskFrom());
        canonical.put("taskTo", manifest.taskTo());
        canonical.put("recordCounts", orderedCounts);
        canonical.put("totalRecords", manifest.totalRecords());
        canonical.put("parts", manifest.parts());
        return metadataDigest(METADATA_MAPPER.writer(), canonical);
    }

    private static boolean matchesSnapshotDigest(ExportManifest manifest, JsonNode stored) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        SNAPSHOT_FIELDS.forEach(field -> canonical.put(field, stored.get(field)));
        Map<String, JsonNode> orderedCounts = new LinkedHashMap<>();
        manifest.recordTypes().forEach(type ->
                orderedCounts.put(type, stored.path("recordCounts").get(type)));
        canonical.put("recordCounts", orderedCounts);
        // Older versions hashed with the application mapper. Preserve stored date/numeric
        // representations and try only its standard order, indentation, and escaping variants.
        // The first writer is the fixed format used by newly created exports.
        for (ObjectWriter writer : METADATA_DIGEST_WRITERS) {
            if (metadataDigest(writer, canonical).equals(manifest.snapshotSha256())) {
                return true;
            }
        }
        return false;
    }

    private static List<ObjectWriter> metadataDigestWriters() {
        List<ObjectWriter> writers = new ArrayList<>();
        for (boolean sorted : List.of(false, true)) {
            for (boolean pretty : List.of(false, true)) {
                for (boolean escaped : List.of(false, true)) {
                    ObjectWriter writer = METADATA_MAPPER.writer();
                    if (sorted) {
                        writer = writer.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
                    }
                    if (pretty) {
                        writer = writer.withDefaultPrettyPrinter();
                    }
                    if (escaped) {
                        writer = writer.with(JsonWriteFeature.ESCAPE_NON_ASCII);
                    }
                    writers.add(writer);
                }
            }
        }
        return List.copyOf(writers);
    }

    private static String metadataDigest(ObjectWriter writer, Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestOutputStream output = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                writer.writeValue(output, value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize record export metadata.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ExportSummary summary(ExportManifest manifest) {
        return new ExportSummary(
                manifest.exportId(), manifest.createdAt(), manifest.recordTypes(),
                manifest.recordCounts(), manifest.totalRecords(), manifest.parts().size(),
                manifest.chunkRecords(), manifest.taskFrom(), manifest.taskTo(),
                manifest.snapshotSha256());
    }

    private void writeManifest(Path staging, ExportManifest manifest) throws IOException {
        byte[] bytes = METADATA_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        if (bytes.length > MAX_MANIFEST_BYTES) {
            throw new IllegalStateException("Record export manifest exceeds its size limit.");
        }
        Files.write(staging.resolve(MANIFEST_FILE), bytes,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private void publish(Path staging, Path destination) throws IOException {
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException exception) {
            Files.move(staging, destination);
        }
    }

    private void ensureExportRoot() {
        try {
            Files.createDirectories(exportRoot);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create the AI export directory.", exception);
        }
    }

    private Path exportDirectory(String exportId) {
        validateExportId(exportId);
        Path resolved = exportRoot.resolve(exportId).normalize();
        if (!resolved.startsWith(exportRoot)) {
            throw new IllegalArgumentException("Invalid record export id.");
        }
        return resolved;
    }

    private static void validateExportId(String exportId) {
        if (exportId == null || !exportId.matches(EXPORT_ID_PATTERN)) {
            throw new IllegalArgumentException("Invalid record export id.");
        }
    }

    private static String exportId(String exportKey) {
        String normalized = exportKey == null ? "" : exportKey.trim();
        if (normalized.isEmpty() || normalized.length() > 160
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "export-key must contain 1 to 160 printable characters.");
        }
        String digest = sha256(("flowersforever-export-key\u0000" + normalized)
                .getBytes(StandardCharsets.UTF_8));
        return "farm-export-" + digest.substring(0, 32);
    }

    private byte[] jsonBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize record export.", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IllegalStateException corrupt(String exportId, String detail) {
        return new IllegalStateException(
                "Record export '" + exportId + "' is corrupt: " + detail + ".");
    }

    private static IllegalStateException corrupt(
            String exportId, String detail, Throwable cause) {
        return new IllegalStateException(
                "Record export '" + exportId + "' is corrupt: " + detail + ".", cause);
    }

    private void cleanup(Path staging) {
        if (staging == null) {
            return;
        }
        Path normalized = staging.normalize();
        Path fileName = normalized.getFileName();
        if (!normalized.startsWith(exportRoot) || fileName == null
                || !fileName.toString().startsWith(".farm-export-")) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException | UncheckedIOException exception) {
            log.warn("Could not remove incomplete AI export directory: {}",
                    staging.getFileName());
        }
    }

    private record ExportRequest(
            List<String> recordTypes,
            int chunkRecords,
            LocalDate taskFrom,
            LocalDate taskTo) {
    }

    private record ExportManifest(
            String formatVersion,
            String exportId,
            String requestFingerprint,
            Instant createdAt,
            List<String> recordTypes,
            int chunkRecords,
            LocalDate taskFrom,
            LocalDate taskTo,
            Map<String, Long> recordCounts,
            long totalRecords,
            List<ExportPart> parts,
            String snapshotSha256) {

        private ExportManifest withSnapshotSha256(String value) {
            return new ExportManifest(
                    formatVersion, exportId, requestFingerprint, createdAt,
                    List.copyOf(recordTypes), chunkRecords, taskFrom, taskTo,
                    java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(recordCounts)),
                    totalRecords, List.copyOf(parts), value);
        }
    }

    private record ExportPart(
            int partNumber,
            String recordType,
            long recordOffset,
            int recordCount,
            String fileName,
            long bytes,
            String sha256) {
    }

    public record CreatedExport(ExportSummary export, boolean replayed) {
    }

    public record ExportSummary(
            String exportId,
            Instant createdAt,
            List<String> recordTypes,
            Map<String, Long> recordCounts,
            long totalRecords,
            int partCount,
            int chunkRecords,
            LocalDate taskFrom,
            LocalDate taskTo,
            String snapshotSha256) {
    }

    public record ExportChunk(
            String exportId,
            String snapshotSha256,
            int partNumber,
            String recordType,
            long recordOffset,
            String partSha256,
            List<JsonNode> records,
            boolean hasMore,
            int nextPart) {
    }
}

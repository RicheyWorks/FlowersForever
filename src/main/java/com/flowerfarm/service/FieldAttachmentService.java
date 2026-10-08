package com.flowerfarm.service;

import com.flowerfarm.model.FieldAttachment;
import com.flowerfarm.model.FieldAttachmentTargetType;
import com.flowerfarm.repository.FieldAttachmentJpaRepository;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Stores immutable field photos and validates their database metadata against the bytes. */
@Service
public class FieldAttachmentService {

    public static final int MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024;
    public static final int MAX_CONTENT_CHUNK_BYTES = 256 * 1024;

    private final FieldAttachmentJpaRepository attachments;
    private final ProductionTaskJpaRepository tasks;
    private final Path storageRoot;

    public FieldAttachmentService(
            FieldAttachmentJpaRepository attachments,
            ProductionTaskJpaRepository tasks,
            @Value("${flowerfarm.attachments.directory:data/attachments}")
            String storageDirectory) {
        this.attachments = attachments;
        this.tasks = tasks;
        String configured = storageDirectory == null || storageDirectory.isBlank()
                ? "data/attachments" : storageDirectory.trim();
        storageRoot = Path.of(configured).toAbsolutePath().normalize();
    }

    /** Writes content by digest and persists only validated metadata in the current transaction. */
    @Transactional
    public AttachmentRecord add(
            String targetType,
            Long targetId,
            String originalFilename,
            String declaredMediaType,
            String caption,
            Instant capturedAt,
            byte[] content) {
        FieldAttachmentTargetType target = targetType(targetType);
        requireTarget(target, targetId);
        StoredContent stored = storeContent(originalFilename, declaredMediaType, content);
        FieldAttachment saved = attachments.saveAndFlush(new FieldAttachment(
                target, targetId, stored.originalFilename(), stored.mediaType(), stored.byteSize(),
                stored.sha256(), storageKey(stored.sha256()), caption, capturedAt));
        return AttachmentRecord.from(saved);
    }

    /**
     * Validates and stores a photo in the same digest-addressed, backed-up folder without a database row.
     * Records that keep their own metadata (the scouting log) reference the photo by its SHA-256 and size.
     */
    public StoredContent storeContent(String originalFilename, String declaredMediaType, byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content.clone();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Attachment file is empty.");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new AttachmentTooLargeException(
                    "Attachment file cannot exceed 15 MiB.");
        }
        String mediaType = detectedMediaType(bytes);
        verifyDeclaredMediaType(declaredMediaType, mediaType);
        String filename = filename(originalFilename, mediaType);
        String digest = sha256(bytes);
        persist(storageKey(digest), bytes, digest);
        return new StoredContent(filename, mediaType, bytes.length, digest);
    }

    /** Reads stored content by digest and size, verifying both against the bytes on disk. */
    public byte[] readContent(String sha256, long byteSize) {
        String digest = sha256 == null ? "" : sha256.trim();
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Attachment digest must be 64 lowercase hex characters.");
        }
        return verifiedBytes(storageKey(digest), byteSize, digest, "digest " + digest.substring(0, 12));
    }

    private static String storageKey(String digest) {
        return digest.substring(0, 2) + "/" + digest;
    }

    /** Reads and verifies complete content for an authenticated HTTP response. */
    @Transactional(readOnly = true)
    public AttachmentContent read(Long attachmentId) {
        FieldAttachment attachment = find(attachmentId);
        byte[] bytes = verifiedBytes(attachment);
        return new AttachmentContent(AttachmentRecord.from(attachment), bytes);
    }

    /** Reads one bounded verified slice for an AI client. */
    @Transactional(readOnly = true)
    public AttachmentContentChunk readChunk(Long attachmentId, int offset, int maxBytes) {
        if (offset < 0) {
            throw new IllegalArgumentException("Attachment offset cannot be negative.");
        }
        if (maxBytes < 1 || maxBytes > MAX_CONTENT_CHUNK_BYTES) {
            throw new IllegalArgumentException(
                    "Attachment chunk size must be between 1 and "
                            + MAX_CONTENT_CHUNK_BYTES + " bytes.");
        }
        FieldAttachment attachment = find(attachmentId);
        byte[] bytes = verifiedBytes(attachment);
        if (offset > bytes.length) {
            throw new IllegalArgumentException(
                    "Attachment offset cannot exceed the file size.");
        }
        int end = Math.min(bytes.length, offset + maxBytes);
        return new AttachmentContentChunk(
                AttachmentRecord.from(attachment), offset,
                Arrays.copyOfRange(bytes, offset, end),
                end < bytes.length ? end : -1);
    }

    /** Lists photos for a production task without exposing storage paths. */
    @Transactional(readOnly = true)
    public List<AttachmentRecord> listForTarget(String targetType, Long targetId) {
        FieldAttachmentTargetType target = targetType(targetType);
        requirePositive(targetId, "Attachment target id");
        return attachments.findByTargetTypeAndTargetRecordIdOrderByCapturedAtDescIdDesc(
                        target, targetId)
                .stream().map(AttachmentRecord::from).toList();
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private FieldAttachment find(Long attachmentId) {
        requirePositive(attachmentId, "Attachment id");
        return attachments.findById(attachmentId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No attachment with id=" + attachmentId));
    }

    private void requireTarget(FieldAttachmentTargetType targetType, Long targetId) {
        requirePositive(targetId, "Attachment target id");
        boolean exists = tasks.existsById(targetId);
        if (!exists) {
            throw new IndexOutOfBoundsException(
                    "No " + targetType.name().toLowerCase(Locale.ROOT)
                            .replace('_', ' ') + " with id=" + targetId);
        }
    }

    private byte[] verifiedBytes(FieldAttachment attachment) {
        return verifiedBytes(attachment.getStorageKey(), attachment.getByteSize(), attachment.getSha256(),
                "id=" + attachment.getId());
    }

    private byte[] verifiedBytes(String storageKey, long byteSize, String digest, String which) {
        Path contentPath = resolveStorageKey(storageKey);
        try {
            if (!Files.isRegularFile(contentPath)) {
                throw new IllegalStateException(
                        "Attachment content is missing for " + which);
            }
            long size = Files.size(contentPath);
            if (size != byteSize || size > MAX_ATTACHMENT_BYTES) {
                throw new IllegalStateException(
                        "Attachment content size does not match its metadata.");
            }
            byte[] bytes = Files.readAllBytes(contentPath);
            if (!sha256(bytes).equals(digest)) {
                throw new IllegalStateException(
                        "Attachment content hash does not match its metadata.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read attachment content.", exception);
        }
    }

    private void persist(String storageKey, byte[] bytes, String expectedDigest) {
        Path destination = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(storageRoot);
            Path destinationParent = destination.getParent();
            if (destinationParent == null) {
                throw new IllegalStateException(
                        "Attachment destination has no parent directory.");
            }
            Files.createDirectories(destinationParent);
            Path incoming = storageRoot.resolve(".incoming");
            Files.createDirectories(incoming);
            if (Files.exists(destination)) {
                verifyExisting(destination, bytes.length, expectedDigest);
                return;
            }
            Path temporary = Files.createTempFile(incoming, "attachment-", ".tmp");
            try {
                Files.write(temporary, bytes);
                com.flowerfarm.safety.SafeFiles.force(temporary);
                moveNewContent(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            verifyExisting(destination, bytes.length, expectedDigest);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not persist attachment content in the farm data folder.", exception);
        }
    }

    private static void moveNewContent(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(source, destination);
            } catch (FileAlreadyExistsException concurrentWrite) {
                // Another identical content-addressed upload won the race.
            }
        } catch (FileAlreadyExistsException exception) {
            // Another identical content-addressed upload won the race.
        }
    }

    private static void verifyExisting(Path path, long size, String digest) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != size
                || !sha256(Files.readAllBytes(path)).equals(digest)) {
            throw new IOException("Existing attachment content is corrupt.");
        }
    }

    private Path resolveStorageKey(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalStateException("Attachment storage key leaves its data directory.");
        }
        return resolved;
    }

    private static FieldAttachmentTargetType targetType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_');
        try {
            return FieldAttachmentTargetType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Attachment target type must be PRODUCTION_TASK.", exception);
        }
    }

    private static String filename(String value, String mediaType) {
        String normalized = value == null ? "" : value
                .replace('\\', '/').replaceAll("[\\p{Cntrl}]", "").trim();
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1).trim();
        }
        if (normalized.isEmpty()) {
            normalized = "field-photo." + extension(mediaType);
        }
        if (normalized.length() > 255) {
            normalized = normalized.substring(0, 255);
        }
        return normalized;
    }

    private static String extension(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/heic" -> "heic";
            default -> "jpg";
        };
    }

    private static void verifyDeclaredMediaType(String declared, String detected) {
        String normalized = declared == null ? "" : declared.trim().toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters >= 0) {
            normalized = normalized.substring(0, parameters).trim();
        }
        if (normalized.isEmpty() || normalized.equals("application/octet-stream")) {
            return;
        }
        boolean heifAlias = detected.equals("image/heic")
                && (normalized.equals("image/heic") || normalized.equals("image/heif"));
        if (!normalized.equals(detected) && !heifAlias) {
            throw new IllegalArgumentException(
                    "Attachment file content does not match its media type.");
        }
    }

    private static String detectedMediaType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12
                && ascii(bytes, 0, 4).equals("RIFF")
                && ascii(bytes, 8, 4).equals("WEBP")) {
            return "image/webp";
        }
        if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
            String brand = ascii(bytes, 8, 4);
            if (List.of("heic", "heix", "hevc", "hevx", "mif1", "msf1")
                    .contains(brand)) {
                return "image/heic";
            }
        }
        throw new IllegalArgumentException(
                "Attachment must contain a JPEG, PNG, WebP, HEIC, or HEIF image.");
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static void requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + " must be positive.");
        }
    }

    public record AttachmentRecord(
            Long id,
            FieldAttachmentTargetType targetType,
            Long targetRecordId,
            String originalFilename,
            String mediaType,
            long byteSize,
            String sha256,
            String caption,
            Instant capturedAt,
            Instant createdAt) {
        static AttachmentRecord from(FieldAttachment attachment) {
            return new AttachmentRecord(
                    attachment.getId(), attachment.getTargetType(),
                    attachment.getTargetRecordId(), attachment.getOriginalFilename(),
                    attachment.getMediaType(), attachment.getByteSize(),
                    attachment.getSha256(), attachment.getCaption(),
                    attachment.getCapturedAt(), attachment.getCreatedAt());
        }
    }

    /** Validated content stored without a database row. */
    public record StoredContent(String originalFilename, String mediaType, long byteSize, String sha256) {
    }

    public record AttachmentContent(AttachmentRecord attachment, byte[] bytes) {
        public AttachmentContent {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record AttachmentContentChunk(
            AttachmentRecord attachment,
            int offset,
            byte[] bytes,
            int nextOffset) {
        public AttachmentContentChunk {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public boolean hasMore() {
            return nextOffset >= 0;
        }
    }
}

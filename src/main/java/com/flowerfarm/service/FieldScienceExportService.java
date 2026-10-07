package com.flowerfarm.service;

import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.reference.FieldScienceBundle;
import com.flowerfarm.reference.FieldScienceCatalog;
import com.flowerfarm.reference.FieldScienceCatalog.Instrument;
import com.flowerfarm.repository.FieldScienceExportRepository;
import com.flowerfarm.repository.FieldScienceExportRepository.SummarySlice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Captures exact UTF-8 text exported by the pinned, curated CSRBT instruments. */
@Service
public class FieldScienceExportService {

    public static final int MAX_EXPORT_BYTES = 512 * 1024;
    public static final int LIST_LIMIT = 200;

    private final FieldScienceExportRepository repository;
    private final Clock clock;

    @Autowired
    public FieldScienceExportService(FieldScienceExportRepository repository) {
        this(repository, Clock.systemUTC());
    }

    FieldScienceExportService(FieldScienceExportRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Save the supplied text verbatim, including spaces, line endings, and a UTF-8 BOM.
     * Provenance comes from the curated catalog rather than caller-supplied labels.
     */
    @Transactional
    public FieldScienceExport save(String instrumentId, String title,
                                   String filename, String note, String content) {
        Instrument instrument = FieldScienceCatalog.byId(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Choose a known field-science instrument."));
        String safeTitle = requiredMetadata(title, 180, "Export title");
        String safeFilename = filename(filename);
        String safeNote = optionalMetadata(note, 500, "Export note");
        byte[] bytes = utf8Content(content);
        FieldScienceExport export = new FieldScienceExport(
                Instant.now(clock), instrument.id(), instrument.title(),
                instrument.sourceUrl(), FieldScienceBundle.SOURCE_COMMIT,
                safeTitle, safeFilename, safeNote, bytes.length, sha256(bytes),
                content);
        return repository.save(export);
    }

    /** Most recent metadata only, without materializing every export's text. */
    @Transactional(readOnly = true)
    public List<FieldScienceExportSummary> list() {
        return page(0).exports();
    }

    /** Zero-based, fixed-size archive page; the body CLOB is loaded only by find(). */
    @Transactional(readOnly = true)
    public ExportPage page(int pageIndex) {
        return page("", pageIndex);
    }

    /** Literal, case-insensitive metadata search with stable newest-first pages. */
    @Transactional(readOnly = true)
    public ExportPage page(String query, int pageIndex) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Export page index cannot be negative.");
        }
        String safeQuery = searchQuery(query);
        SummarySlice result = repository.summaryPage(safeQuery, pageIndex, LIST_LIMIT);
        return new ExportPage(result.exports(), pageIndex,
                result.hasMore(), result.totalCount());
    }

    @Transactional(readOnly = true)
    public Optional<FieldScienceExport> find(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Export id must be positive.");
        }
        return repository.findById(id);
    }

    public record ExportPage(List<FieldScienceExportSummary> exports,
                             int pageIndex, boolean hasMore, long totalCount) {
        public ExportPage {
            exports = List.copyOf(exports);
        }
    }

    private static String searchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException(
                    "Export search cannot exceed 120 characters.");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(
                        "Export search cannot contain control characters.");
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static byte[] utf8Content(String content) {
        if (content == null || (content.startsWith("\uFEFF")
                ? content.substring(1) : content).isBlank()) {
            throw new IllegalArgumentException("Export text is required.");
        }
        if (content.length() > MAX_EXPORT_BYTES) {
            throw new IllegalArgumentException("Export text cannot exceed 512 KiB of UTF-8.");
        }
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if ((character < 0x20 && character != '\t'
                    && character != '\r' && character != '\n')
                    || character == 0x7f) {
                throw new IllegalArgumentException(
                        "Export contains binary control characters; use a plain-text export.");
            }
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(content));
            if (encoded.remaining() > MAX_EXPORT_BYTES) {
                throw new IllegalArgumentException(
                        "Export text cannot exceed 512 KiB of UTF-8.");
            }
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Export text contains invalid UTF-8 characters.", exception);
        }
    }

    private static String filename(String value) {
        String normalized = optionalMetadata(value, 255, "Export filename");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0
                || normalized.chars().anyMatch(character -> character < 0x20
                || character == 0x7f)) {
            throw new IllegalArgumentException("Export filename must be a plain basename.");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".eco") || lower.endsWith(".txt")
                || lower.endsWith(".csv")) || normalized.lastIndexOf('.') <= 0) {
            throw new IllegalArgumentException(
                    "Export filename must end in .eco, .txt, or .csv.");
        }
        return normalized;
    }

    private static String requiredMetadata(String value, int max, String label) {
        String normalized = optionalMetadata(value, max, label);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String optionalMetadata(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(label + " cannot exceed " + max
                    + " characters.");
        }
        return normalized;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}

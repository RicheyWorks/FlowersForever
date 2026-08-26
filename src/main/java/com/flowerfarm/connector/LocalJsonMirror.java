package com.flowerfarm.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared local JSON mirror I/O for dual-mode connectors (Farmbrite, Floranext,
 * Shopify, Square, …). Stores an array of map records on disk.
 */
public final class LocalJsonMirror {

    private static final Logger log = LoggerFactory.getLogger(LocalJsonMirror.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final long MAX_MIRROR_BYTES = 10L * 1024 * 1024;

    private final Path path;
    private final String connectorLabel;

    public LocalJsonMirror(String filePath, String connectorLabel) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Local mirror path is required.");
        }
        this.path = Path.of(filePath.trim());
        this.connectorLabel = connectorLabel == null || connectorLabel.isBlank()
                ? "connector" : connectorLabel.trim();
    }

    public Path path() {
        return path;
    }

    public boolean exists() {
        return Files.exists(path);
    }

    /**
     * Read mirror rows. Missing file → empty list (not an error — export creates it).
     */
    public List<Map<String, Object>> readRows() throws IOException {
        if (!Files.exists(path)) {
            log.info("[{}] Local mirror '{}' not found — empty.", connectorLabel, path);
            return List.of();
        }
        long size = Files.size(path);
        if (size > MAX_MIRROR_BYTES) {
            throw new IOException("Local mirror exceeds the 10 MiB safety limit.");
        }
        List<Map<String, Object>> rows = MAPPER.readValue(
                path.toFile(), new TypeReference<List<Map<String, Object>>>() {});
        return rows == null ? List.of() : rows;
    }

    /** Write rows atomically with pretty-print; creates parent directories. */
    public void writeRows(List<Map<String, Object>> rows) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path fileName = target.getFileName();
        if (parent == null || fileName == null) {
            throw new IOException("Local mirror path must identify a file beneath a directory.");
        }
        Files.createDirectories(parent);
        List<Map<String, Object>> safe = rows == null ? List.of() : rows;
        Path temporary = Files.createTempFile(parent, fileName.toString(), ".tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), safe);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        log.info("[{}] Local mirror wrote {} row(s) → {}", connectorLabel, safe.size(), path.getFileName());
    }

    /** Convenience: write empty array (initialize mirror). */
    public void writeEmpty() throws IOException {
        writeRows(new ArrayList<>());
    }

    @Override
    public String toString() {
        return path.toString();
    }
}

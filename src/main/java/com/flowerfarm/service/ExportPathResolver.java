package com.flowerfarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Keeps REST-triggered server exports inside one configured directory.
 * GUI save dialogs continue to pass their explicitly approved paths directly
 * to the report services and do not use this resolver.
 */
@Component
public class ExportPathResolver {

    private final Path exportDirectory;

    public ExportPathResolver(
            @Value("${flowerfarm.exports.directory:data/exports}") String exportDirectory) {
        String configured = exportDirectory == null || exportDirectory.isBlank()
                ? "data/exports"
                : exportDirectory.trim();
        this.exportDirectory = Path.of(configured).toAbsolutePath().normalize();
    }

    /** Resolves one CSV leaf name beneath the export directory. */
    public Path resolveCsv(String requestedName, String defaultName) {
        String name = requestedName == null || requestedName.isBlank()
                ? defaultName
                : requestedName.trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Export filename is required.");
        }

        final Path leaf;
        try {
            leaf = Path.of(name);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid export filename.", e);
        }
        if (leaf.isAbsolute() || leaf.getNameCount() != 1
                || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException(
                    "Export filename must be a simple file name without directories.");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Export filename must end with .csv.");
        }

        Path resolved = exportDirectory.resolve(leaf).normalize();
        if (!resolved.startsWith(exportDirectory)) {
            throw new IllegalArgumentException("Export path escapes the configured directory.");
        }
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create export directory: " + exportDirectory, e);
        }
        return resolved;
    }

    Path exportDirectory() {
        return exportDirectory;
    }
}

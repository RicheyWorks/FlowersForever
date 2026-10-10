package com.flowerfarm.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Materializes the pinned CSRBT field-science pages as a complete offline site.
 * Pages navigate to one another with relative links, so a single file cannot be
 * opened directly from the application JAR.
 */
public final class FieldScienceBundle {

    public static final String SOURCE_COMMIT = "f4c9a25c287ac98b96efb38b99a9d95b281434de";
    private static final String VERSION = SOURCE_COMMIT.substring(0, 12);
    private static final String RESOURCE_ROOT = "/csrbt-field-kit/";
    private static final String MANIFEST = "manifest.sha256";
    /**
     * Serializes extraction. A private lock rather than {@code static synchronized}, so code that
     * happens to lock on {@code FieldScienceBundle.class} cannot stall or deadlock extraction.
     */
    private static final Object EXTRACTION_LOCK = new Object();

    private FieldScienceBundle() {
    }

    /**
     * Extract all bundled pages, then return one allowlisted page or metadata file.
     * Existing files with the bundled checksum are left untouched. A file whose
     * bytes differ from the bundled source is safely refreshed before it is used.
     *
     * @throws IllegalArgumentException if {@code fileName} is not in the bundle
     * @throws IOException if a resource is missing, corrupt, or cannot be written
     */
    public static Path materialize(String fileName) throws IOException {
        synchronized (EXTRACTION_LOCK) {
            return materializeLocked(fileName);
        }
    }

    private static Path materializeLocked(String fileName) throws IOException {
        List<Entry> entries = readManifest();
        if (fileName == null || entries.stream().noneMatch(entry -> entry.name().equals(fileName))) {
            throw new IllegalArgumentException("Unknown field-science bundle file: " + fileName);
        }

        Path dataDirectory = dataDirectory();
        Files.createDirectories(dataDirectory);
        Path canonicalData = dataDirectory.toRealPath();
        Path fieldScience = safeDirectory(canonicalData, "field-science");
        Path version = safeDirectory(fieldScience, VERSION);
        for (Entry entry : entries) {
            Path target = version.resolve(entry.name()).normalize();
            Path parent = target.getParent();
            if (parent == null || !parent.equals(version)) {
                throw new IOException("Unsafe field-science bundle destination: " + entry.name());
            }
            byte[] resource = readResource(entry.name());
            if (!sha256(resource).equals(entry.digest())) {
                throw new IOException("Field-science bundle resource failed checksum: " + entry.name());
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Field-science bundle destination is not a regular file: " + target);
                }
                if (sha256(Files.readAllBytes(target)).equals(entry.digest())) {
                    continue;
                }
            }
            writeAtomically(target, resource);
        }
        return version.resolve(fileName);
    }

    private static Path dataDirectory() {
        String configured = System.getProperty("flowerfarm.data-dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("FLOWERFARM_DATA_DIR");
        }
        return Path.of(configured == null || configured.isBlank() ? "data" : configured)
                .toAbsolutePath().normalize();
    }

    private static Path safeDirectory(Path parent, String name) throws IOException {
        Path directory = parent.resolve(name);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Field-science bundle path is not a directory: " + directory);
            }
        } else {
            Files.createDirectory(directory);
        }
        return directory;
    }

    private static List<Entry> readManifest() throws IOException {
        String manifest = new String(readResource(MANIFEST), StandardCharsets.US_ASCII);
        List<Entry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String line : manifest.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}")
                    || !parts[1].matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                    || !names.add(parts[1])) {
                throw new IOException("Invalid field-science bundle manifest entry.");
            }
            entries.add(new Entry(parts[1], parts[0]));
        }
        if (entries.isEmpty()) {
            throw new IOException("Field-science bundle manifest is empty.");
        }
        return List.copyOf(entries);
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream input = FieldScienceBundle.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (input == null) {
                throw new IOException("Missing field-science bundle resource: " + name);
            }
            return input.readAllBytes();
        }
    }

    private static void writeAtomically(Path target, byte[] source) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Field-science bundle file has no parent: " + target);
        }
        Path temporary = Files.createTempFile(parent, ".field-science-", ".tmp");
        try {
            Files.write(temporary, source);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record Entry(String name, String digest) {
    }
}

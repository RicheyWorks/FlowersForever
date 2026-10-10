package com.flowerfarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HTTP CSV exports use one configured directory, rather than client-selected server paths.
 * The directory and its ancestors must be controlled by the deployment operator, not
 * writable by untrusted local users. Directory-relative operations are used where the
 * filesystem supports them. Other providers (including Windows) additionally check
 * canonical path and directory metadata, using a stable file key when available.
 * Creation metadata is a best-effort replacement check, not an identity proof; it cannot
 * prevent a malicious local user replacing/spoofing the root between checks. These checks
 * do not replace the operator's directory and ancestor permissions.
 * Desktop/CLI exporters deliberately retain their separately selected local paths.
 */
@Service
public class ServerCsvExportService {
    private static final Pattern CSV_NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*\\.csv", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_NAME = Pattern.compile("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])", Pattern.CASE_INSENSITIVE);
    private static final Set<OpenOption> CREATE_OPTIONS = Set.of(
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    private final Path directory;

    public ServerCsvExportService(@Value("${flowerfarm.exports.directory:exports}") String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Server export directory must be configured.");
        }
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @FunctionalInterface
    public interface CsvWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    public void exportCsv(String filename, CsvWriter content) {
        validateFilename(filename);
        Objects.requireNonNull(content, "CSV content writer");
        try {
            BasicFileAttributes identity = prepareDirectory();
            Path realDirectory = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                if (stream instanceof SecureDirectoryStream<Path> secure) {
                    if (!sameDirectoryMetadata(identity, secure.getFileAttributeView(
                            BasicFileAttributeView.class).readAttributes())) {
                        throw new IOException("Server export directory changed.");
                    }
                    exportRelative(secure, Path.of(filename), content);
                } else {
                    exportWithProtectedDirectory(identity, realDirectory, filename, content);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Server CSV export failed: " + e.getMessage(), e);
        }
    }

    private static void validateFilename(String filename) {
        if (filename == null || filename.length() > 128 || !CSV_NAME.matcher(filename).matches()
                || filename.contains("..")
                || DEVICE_NAME.matcher(filename.substring(0, filename.indexOf('.')).toUpperCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Export filename must be a single CSV basename (letters, digits, underscore, hyphen or dot; no paths or reserved device names).");
        }
    }

    private BasicFileAttributes prepareDirectory() throws IOException {
        // Validate each existing ancestor before creating a child: createDirectories alone follows links.
        Path current = directory.getRoot();
        for (Path component : directory) {
            current = current.resolve(component);
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException missing) {
                try {
                    Files.createDirectory(current);
                } catch (FileAlreadyExistsException concurrentCreation) {
                    // Another export may have created the directory. Recheck the entry without following links.
                }
                attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            }
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
                throw new IOException("Server export directory must not contain links or non-directory entries.");
            }
        }
        BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes;
    }

    private static void requireRegularTarget(BasicFileAttributes attributes) throws IOException {
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Existing CSV export must be a regular file, not a link or directory.");
        }
    }

    private static void checkRelativeTarget(SecureDirectoryStream<Path> directory, Path target) throws IOException {
        try {
            requireRegularTarget(directory.getFileAttributeView(target,
                    BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes());
        } catch (NoSuchFileException absent) {
            // An absent leaf is valid; the staged file will create the entry.
        }
    }

    private static void exportRelative(SecureDirectoryStream<Path> directory, Path target,
                                       CsvWriter content) throws IOException {
        checkRelativeTarget(directory, target);
        Path temporary = Path.of(".export-" + UUID.randomUUID() + ".tmp");
        boolean created = false;
        boolean published = false;
        try {
            try (SeekableByteChannel channel = directory.newByteChannel(temporary, CREATE_OPTIONS)) {
                created = true;
                writeContent(channel, content);
            }
            checkRelativeTarget(directory, target);
            // This operation replaces an entry atomically on supporting providers; it never opens its target.
            directory.move(temporary, directory, target);
            published = true;
        } finally {
            if (created && !published) {
                directory.deleteFile(temporary);
            }
        }
    }

    private void exportWithProtectedDirectory(BasicFileAttributes identity, Path realDirectory, String filename,
                                              CsvWriter content) throws IOException {
        verifyDirectory(identity, realDirectory);
        Path target = directory.resolve(filename);
        checkTarget(target);
        Path temporary = directory.resolve(".export-" + UUID.randomUUID() + ".tmp");
        boolean created = false;
        boolean published = false;
        try {
            try (SeekableByteChannel channel = Files.newByteChannel(temporary, CREATE_OPTIONS)) {
                created = true;
                writeContent(channel, content);
            }
            verifyDirectory(identity, realDirectory);
            checkTarget(target);
            // Fail if atomic replacement is unsupported; never fall back to truncating a target/link.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
        } finally {
            if (created && !published) {
                // If a local process replaced the root, do not delete a similarly named entry in its new directory.
                verifyDirectory(identity, realDirectory);
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void verifyDirectory(BasicFileAttributes expected, Path realDirectory) throws IOException {
        BasicFileAttributes current = prepareDirectory();
        if (!realDirectory.equals(directory.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !sameDirectoryMetadata(expected, current)) {
            throw new IOException("Server export directory changed.");
        }
    }

    private static boolean sameDirectoryMetadata(BasicFileAttributes expected, BasicFileAttributes actual) {
        if (expected.fileKey() != null) {
            return expected.fileKey().equals(actual.fileKey());
        }
        // Windows' public BasicFileAttributes has no file key. Never treat null==null as identity.
        return expected.creationTime().equals(actual.creationTime());
    }

    private static void checkTarget(Path target) throws IOException {
        try {
            requireRegularTarget(Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        } catch (NoSuchFileException absent) {
            // An absent leaf is valid.
        }
    }

    private static void writeContent(SeekableByteChannel channel, CsvWriter content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8))) {
            content.write(writer);
        }
    }
}

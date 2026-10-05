package com.flowerfarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Java version of {@code scripts/backup-data.ps1} and {@code scripts/restore-data.ps1}.
 * The ZIP holds the data folder as its single top-level directory and gets a
 * {@code <zip>.sha256} sidecar in {@code sha256sum} format, so either the app or the
 * PowerShell restore script can restore it. A backup only counts once the ZIP is
 * re-read and every file's size and CRC match the live folder. Restoring never
 * deletes anything: the current folder is renamed to {@code data.before-restore-*}.
 */
public final class DataBackupService {

    public static final String DATABASE_FILE = "flowerfarm.mv.db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Clock clock;

    public DataBackupService(Clock clock) {
        this.clock = clock;
    }

    public VerifiedBackup backup(Path dataDirectory, Path destination, String label)
            throws UpdateVerificationException {
        Path data = dataDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(data)) {
            throw new UpdateVerificationException("The farm records folder was not found: " + data);
        }
        requireDatabaseClosed(data);
        Path rootName = data.getFileName();
        if (rootName == null) {
            throw new UpdateVerificationException("The farm records folder cannot be a drive root.");
        }
        Path archive;
        Path checksum;
        List<Path> files;
        try {
            Files.createDirectories(destination);
            String safeLabel = label.replaceAll("[^A-Za-z0-9._-]", "-");
            archive = uniqueArchive(destination.toAbsolutePath().normalize(), safeLabel);
            checksum = Path.of(archive + ".sha256");
            if (archive.startsWith(data)) {
                throw new UpdateVerificationException("The backup must be saved outside the records folder.");
            }
            try (var walk = Files.walk(data)) {
                files = walk.filter(Files::isRegularFile).filter(path -> !isTransient(path)).sorted().toList();
            }
            Path partial = Path.of(archive + ".part");
            try (OutputStream output = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW);
                 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(rootName + "/"));
                zip.closeEntry();
                for (Path file : files) {
                    zip.putNextEntry(new ZipEntry(rootName + "/" + entryName(data, file)));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            Files.move(partial, archive, StandardCopyOption.ATOMIC_MOVE);
            String sha256 = UpdateSignatures.sha256Hex(archive);
            Files.writeString(checksum, sha256 + "  " + archive.getFileName() + "\n", StandardCharsets.US_ASCII);
            long bytes = verifyArchiveMatches(archive, checksum, data, rootName.toString(), files);
            return new VerifiedBackup(archive, checksum, sha256, files.size(), bytes);
        } catch (IOException failure) {
            throw new UpdateVerificationException("The backup could not be written: " + failure.getMessage(),
                    failure);
        }
    }

    /** Re-checks an existing backup's SHA-256 sidecar. */
    public void verifyChecksum(Path archive) throws UpdateVerificationException {
        Path checksum = Path.of(archive + ".sha256");
        try {
            if (!Files.isRegularFile(archive) || !Files.isRegularFile(checksum)) {
                throw new UpdateVerificationException("The backup or its checksum file is missing: " + archive);
            }
            String expected = Files.readString(checksum, StandardCharsets.US_ASCII).trim().split("\\s+")[0]
                    .toLowerCase(Locale.ROOT);
            if (!expected.equals(UpdateSignatures.sha256Hex(archive))) {
                throw new UpdateVerificationException(
                        "The backup checksum does not match. The archive may be damaged or incomplete.");
            }
        } catch (IOException unreadable) {
            throw new UpdateVerificationException("The backup could not be read: " + archive, unreadable);
        }
    }

    /**
     * Restores a verified backup into {@code dataDirectory}. The current folder is
     * kept as {@code <data>.before-restore-<stamp>} and returned.
     */
    public Path restore(Path archive, Path dataDirectory) throws UpdateVerificationException {
        verifyChecksum(archive);
        Path data = dataDirectory.toAbsolutePath().normalize();
        Path parent = data.getParent();
        Path dataName = data.getFileName();
        if (parent == null || dataName == null) {
            throw new UpdateVerificationException("The farm records folder cannot be a drive root.");
        }
        if (Files.isDirectory(data)) {
            requireDatabaseClosed(data);
        }
        Path staging = parent.resolve(".restore-" + Long.toHexString(System.nanoTime()));
        Path keptAside = null;
        try {
            Files.createDirectories(staging);
            extract(archive, staging);
            Path source = singleTopLevelDirectory(staging);
            if (!Files.isRegularFile(source.resolve(DATABASE_FILE))) {
                throw new UpdateVerificationException("The backup does not contain a FlowersForever database.");
            }
            if (Files.exists(data)) {
                keptAside = parent.resolve(dataName + ".before-restore-" + stamp());
                Files.move(data, keptAside, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.move(source, data, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveFailed) {
                if (keptAside != null && !Files.exists(data)) {
                    Files.move(keptAside, data, StandardCopyOption.ATOMIC_MOVE);
                }
                throw moveFailed;
            }
            return keptAside;
        } catch (IOException failure) {
            throw new UpdateVerificationException("The backup could not be restored: " + failure.getMessage(),
                    failure);
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * Checks a backup's {@code .sha256} file and unpacks it into {@code staging} (which
     * must not exist yet or be empty) without touching the live records. Returns the
     * unpacked records folder, which always holds {@link #DATABASE_FILE}.
     */
    public Path unpack(Path archive, Path staging) throws UpdateVerificationException {
        verifyChecksum(archive);
        try {
            Files.createDirectories(staging);
            extract(archive, staging);
            Path source = singleTopLevelDirectory(staging);
            if (!Files.isRegularFile(source.resolve(DATABASE_FILE))) {
                throw new UpdateVerificationException("The backup does not contain a FlowersForever database.");
            }
            return source;
        } catch (IOException failure) {
            throw new UpdateVerificationException("The backup could not be unpacked: " + failure.getMessage(),
                    failure);
        }
    }

    /** Fails when another process (or this one) still has the H2 database open. */
    public static void requireDatabaseClosed(Path data) throws UpdateVerificationException {
        Path database = data.resolve(DATABASE_FILE);
        if (!Files.exists(database)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(database, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw databaseOpen();
            }
            lock.release();
        } catch (OverlappingFileLockException inThisProcess) {
            throw databaseOpen();
        } catch (IOException locked) {
            throw new UpdateVerificationException(databaseOpen().getMessage(), locked);
        }
    }

    private static UpdateVerificationException databaseOpen() {
        return new UpdateVerificationException(
                "FlowersForever or its AI helper is still using the farm records. Close every FlowersForever "
                        + "window and helper, then try again.");
    }

    private long verifyArchiveMatches(Path archive, Path checksum, Path data, String root, List<Path> files)
            throws IOException, UpdateVerificationException {
        verifyChecksum(archive);
        if (!String.valueOf(checksum.getFileName()).endsWith(".sha256")) {
            throw new UpdateVerificationException("Backup checksum file is misnamed.");
        }
        long bytes = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            int fileEntries = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (!entries.nextElement().isDirectory()) {
                    fileEntries++;
                }
            }
            if (fileEntries != files.size()) {
                throw new UpdateVerificationException("The backup is missing files.");
            }
            for (Path file : files) {
                ZipEntry entry = zip.getEntry(root + "/" + entryName(data, file));
                long size = Files.size(file);
                if (entry == null || entry.getSize() != size || entry.getCrc() != crc(file)) {
                    throw new UpdateVerificationException("The backup copy of " + data.relativize(file)
                            + " does not match the original.");
                }
                bytes += size;
            }
        }
        return bytes;
    }

    private static void extract(Path archive, Path destination) throws IOException, UpdateVerificationException {
        Path root = destination.toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = root.resolve(entry.getName().replace('\\', '/')).normalize();
                if (!target.startsWith(root) || target.equals(root)) {
                    throw new UpdateVerificationException("The backup contains an unsafe path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, target);
                }
            }
        }
    }

    private static Path singleTopLevelDirectory(Path staging) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (var list = Files.list(staging)) {
            list.forEach(entries::add);
        }
        if (entries.size() == 1 && Files.isDirectory(entries.get(0))) {
            return entries.get(0);
        }
        return staging;
    }

    private Path uniqueArchive(Path destination, String label) {
        String base = "FlowersForever-" + label + "-" + stamp();
        Path candidate = destination.resolve(base + ".zip");
        int counter = 2;
        while (Files.exists(candidate) || Files.exists(Path.of(candidate + ".part"))) {
            candidate = destination.resolve(base + "-" + counter++ + ".zip");
        }
        return candidate;
    }

    private String stamp() {
        return LocalDateTime.now(clock).format(STAMP);
    }

    private static boolean isTransient(Path file) {
        String name = String.valueOf(file.getFileName());
        return name.endsWith(".lock.db") || name.startsWith(".write-check-");
    }

    private static String entryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static long crc(Path file) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                crc.update(buffer, 0, read);
            }
        }
        return crc.getValue();
    }

    private static void deleteQuietly(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Staging leftovers are harmless; they never replace farm records.
        }
    }
}

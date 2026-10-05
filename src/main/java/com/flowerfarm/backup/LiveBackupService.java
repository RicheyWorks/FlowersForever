package com.flowerfarm.backup;

import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.UpdateVerificationException;
import com.flowerfarm.update.VerifiedBackup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * "Back up now" while FlowersForever keeps running. The database is copied with
 * H2's online backup (consistent, no need to close), the other record files
 * (attachments, exports) are copied after it, and the result is written with
 * {@link DataBackupService}: the same verified ZIP + {@code .sha256} layout as
 * {@code scripts/backup-data.ps1}, re-read and compared file by file.
 */
public final class LiveBackupService {

    private static final long SPACE_MARGIN_BYTES = 20L * 1024 * 1024;

    private final BackupSettings settings;
    private final DatabaseSnapshot database;
    private final DataBackupService archives;
    private final Object oneAtATime = new Object();

    public LiveBackupService(BackupSettings settings, DatabaseSnapshot database, DataBackupService archives) {
        this.settings = settings;
        this.database = database;
        this.archives = archives;
    }

    public BackupSettings settings() {
        return settings;
    }

    /** Writes a checked backup named {@code FlowersForever-<label>-<stamp>.zip} into {@code folder}. */
    public VerifiedBackup backUp(Path folder, String label) throws BackupException {
        Path data = settings.dataDirectory();
        Path target = folder.toAbsolutePath().normalize();
        if (settings.insideRecords(target)) {
            throw new BackupException(BackupMessages.FOLDER_INSIDE_RECORDS);
        }
        if (!Files.isDirectory(data)) {
            throw new BackupException(BackupMessages.recordsMissing(data));
        }
        Path dataName = data.getFileName();
        if (dataName == null) {
            throw new BackupException(BackupMessages.recordsMissing(data));
        }
        synchronized (oneAtATime) {
            try {
                Files.createDirectories(target);
            } catch (IOException cannotCreate) {
                throw new BackupException(BackupMessages.folderNotWritable(target), cannotCreate);
            }
            requireSpace(data, target);
            Path work = settings.stateDirectory().resolve("work-" + Long.toHexString(System.nanoTime()));
            Path snapshot = work.resolve(dataName.toString());
            try {
                Files.createDirectories(snapshot);
                Path databaseCopy = work.resolve("database.zip");
                database.writeTo(databaseCopy);
                extractDatabase(databaseCopy, snapshot);
                copyRecordFiles(data, snapshot);
                return archives.backup(snapshot, target, label);
            } catch (SQLException databaseBusy) {
                throw new BackupException(BackupMessages.databaseCopyFailed(databaseBusy.getMessage()), databaseBusy);
            } catch (IOException ioFailure) {
                throw new BackupException(BackupMessages.writeFailed(target, ioFailure.getMessage()), ioFailure);
            } catch (UpdateVerificationException notVerified) {
                throw new BackupException(notVerified.getMessage(), notVerified);
            } finally {
                deleteQuietly(work);
            }
        }
    }

    private static void extractDatabase(Path databaseCopy, Path snapshot) throws IOException, BackupException {
        boolean found = false;
        try (ZipFile zip = new ZipFile(databaseCopy.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (!entry.isDirectory() && DataBackupService.DATABASE_FILE.equals(fileName)) {
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, snapshot.resolve(DataBackupService.DATABASE_FILE));
                    }
                    found = true;
                }
            }
        }
        if (!found) {
            throw new BackupException(BackupMessages.databaseCopyFailed("the copy did not contain the records"));
        }
    }

    private static void copyRecordFiles(Path data, Path snapshot) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(data)) {
            files = walk.filter(Files::isRegularFile).filter(file -> !skipped(data, file)).sorted().toList();
        }
        for (Path file : files) {
            Path target = snapshot.resolve(data.relativize(file).toString());
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.copy(file, target);
            } catch (NoSuchFileException removedMeanwhile) {
                // A temporary file disappeared while copying; it was never a farm record.
            }
        }
    }

    /** The live database (copied separately), H2 lock/trace files, and scratch files. */
    static boolean skipped(Path data, Path file) {
        String name = String.valueOf(file.getFileName());
        boolean topLevel = data.equals(file.getParent());
        return (topLevel && name.equals(DataBackupService.DATABASE_FILE))
                || name.endsWith(".lock.db") || name.endsWith(".trace.db") || name.endsWith(".tempFile")
                || name.startsWith(".write-check-") || name.endsWith(".part");
    }

    private static void requireSpace(Path data, Path target) throws BackupException {
        try {
            long needed;
            try (Stream<Path> walk = Files.walk(data)) {
                needed = walk.filter(Files::isRegularFile).mapToLong(LiveBackupService::sizeOrZero).sum();
            }
            FileStore store = Files.getFileStore(target);
            if (store.getUsableSpace() < needed + SPACE_MARGIN_BYTES) {
                throw new BackupException(BackupMessages.notEnoughSpace(target, needed + SPACE_MARGIN_BYTES));
            }
        } catch (IOException cannotTell) {
            // Unknown free space: try anyway; a full disk still fails safely below.
        }
    }

    private static long sizeOrZero(Path file) {
        try {
            return Files.size(file);
        } catch (IOException gone) {
            return 0;
        }
    }

    static void deleteQuietly(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Scratch copies are harmless leftovers; they never replace farm records.
        }
    }
}

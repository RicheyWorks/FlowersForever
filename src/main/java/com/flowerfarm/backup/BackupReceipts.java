package com.flowerfarm.backup;

import com.flowerfarm.safety.SafeFiles;
import com.flowerfarm.update.UpdateSignatures;
import com.flowerfarm.update.VerifiedBackup;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/** Local proof that an exact verified archive was made from this records store. */
public final class BackupReceipts {
    private static final String FOLDER = "backup-receipts";

    private BackupReceipts() { }

    /** Offline backups keep receipts beside their original records directory. */
    public static void record(Path dataDirectory, VerifiedBackup backup) throws IOException {
        Path source = dataDirectory.toRealPath();
        record(source, defaultState(source), backup);
    }

    /** Live snapshots retain the original farm's identity and configured settings location. */
    public static void record(BackupSettings settings, VerifiedBackup backup) throws IOException {
        record(settings.dataDirectory().toRealPath(), settings.stateDirectory(), backup);
    }

    private static void record(Path source, Path state, VerifiedBackup backup) throws IOException {
        Properties receipt = new Properties();
        receipt.setProperty("source-directory", source.toString());
        receipt.setProperty("archive-sha256", backup.sha256());
        Path file = receiptFile(state, source, backup.sha256());
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("The backup receipt has no settings folder.");
        }
        Files.createDirectories(parent);
        StringWriter text = new StringWriter();
        receipt.store(text, "FlowersForever automatic recovery provenance; keep beside the original records");
        SafeFiles.writeString(file, text.toString());
    }

    /** Unknown, unreadable, moved-store, or changed archives remain manual-restore only. */
    public static boolean recognizes(BackupSettings settings, Path archive) {
        try {
            Path source = settings.dataDirectory().toRealPath();
            String checksum = UpdateSignatures.sha256Hex(archive);
            Set<Path> states = new LinkedHashSet<>();
            states.add(settings.stateDirectory());
            states.add(defaultState(source));
            for (Path state : states) {
                Path file = receiptFile(state, source, checksum);
                if (!Files.isRegularFile(file)) continue;
                Properties receipt = new Properties();
                try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    receipt.load(input);
                }
                if (source.toString().equals(receipt.getProperty("source-directory"))
                        && checksum.equals(receipt.getProperty("archive-sha256"))) {
                    return true;
                }
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            // A missing proof never authorizes replacing the current farm's records.
        }
        return false;
    }

    private static Path defaultState(Path source) {
        return source.resolveSibling(BackupSettings.STATE_FOLDER);
    }

    private static Path receiptFile(Path state, Path source, String checksum) {
        String storeKey = UpdateSignatures.sha256Hex(source.toString().getBytes(StandardCharsets.UTF_8));
        return state.resolve(FOLDER).resolve(storeKey).resolve(checksum + ".properties");
    }
}

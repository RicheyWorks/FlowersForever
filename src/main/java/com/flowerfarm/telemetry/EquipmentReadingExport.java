package com.flowerfarm.telemetry;

import com.flowerfarm.safety.SafeFiles;
import com.flowerfarm.util.CsvSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A chooser-approved, readable spreadsheet copy; full database backups retain raw frames. */
public final class EquipmentReadingExport {
    public static final List<String> HEADINGS = List.of("Equipment", "Growing area", "Measurement", "Value", "Unit",
            "Condition", "Measured at", "Received at", "Source", "Equipment reference", "Measurement reference",
            "Reading reference", "Saved reading number");

    private EquipmentReadingExport() { }

    /** Capture what the farmer is agreeing to replace; only the local chooser supplies this path. */
    public static Destination inspect(Path selected) throws IOException {
        Path absolute = selected.toAbsolutePath().normalize();
        Path name = absolute.getFileName();
        if (name == null || !name.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IOException("Choose a spreadsheet filename ending in .csv.");
        }
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Choose an existing folder for your spreadsheet.");
        Path target = parent.toRealPath().resolve(name);
        return new Destination(target, stamp(target));
    }

    /** Streams bounded pages into a staging file, then publishes only the complete reviewed copy. */
    public static long write(Destination destination, FarmReadingService.ReadingSnapshot snapshot,
            FarmReadingService service) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(service, "service");
        Path target = destination.path();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Choose an existing folder for your spreadsheet.");
        checkDestination(destination);
        Path temporary = Files.createTempFile(parent, ".equipment-readings-", ".part");
        boolean moved = false;
        long copied = 0;
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                writer.write('\ufeff');
                writer.write(CsvSupport.row(',', HEADINGS));
                writer.write("\r\n");
                long afterId = 0;
                while (afterId < snapshot.throughId()) {
                    List<FarmReading> page = service.copyPage(snapshot, afterId, FarmReadingService.MAX_PAGE);
                    if (page.isEmpty()) break;
                    for (FarmReading reading : page) {
                        if (reading.id() <= afterId || reading.id() > snapshot.throughId()) {
                            throw new IOException("Saved reading order changed. Try saving a new copy.");
                        }
                        writer.write(csvRow(reading));
                        writer.write("\r\n");
                        afterId = reading.id();
                        copied++;
                    }
                }
            }
            if (copied != snapshot.readingCount()) throw new IOException("Saved readings changed. Try saving a new copy.");
            SafeFiles.force(temporary);
            checkDestination(destination);
            if (destination.replacesExisting()) SafeFiles.replace(temporary, target);
            else Files.move(temporary, target);
            moved = true;
            return copied;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String csvRow(FarmReading reading) {
        return CsvSupport.row(',', text(ManualFarmReading.equipmentName(reading)), text(reading.location()),
                text(EquipmentSpreadsheet.measurementLabel(reading.metric())),
                reading.value() == null ? "" : reading.value().stripTrailingZeros().toPlainString(),
                text(reading.unit()), text(EquipmentSpreadsheet.conditionLabel(reading.quality())), reading.observedAt().toString(), reading.receivedAt().toString(),
                text(reading.sourceId()), text(reading.deviceId()), text(reading.metric()), text(reading.readingKey()), reading.id());
    }

    /** Neutralize formula-looking text while leaving genuine negative measurements numeric. */
    private static String text(String value) {
        if (value == null) return "";
        int start = 0;
        while (start < value.length() && (Character.isWhitespace(value.charAt(start))
                || Character.isSpaceChar(value.charAt(start)) || value.charAt(start) == '\ufeff')) start++;
        return start < value.length() && "=+-@".indexOf(value.charAt(start)) >= 0 ? "'" + value : value;
    }

    private static void checkDestination(Destination destination) throws IOException {
        if (!Objects.equals(destination.original, stamp(destination.path()))) {
            throw new IOException("The selected file changed. Choose it again and review a new copy.");
        }
    }

    private static FileStamp stamp(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw new IOException("Choose a regular spreadsheet file, not a folder or shortcut.");
            }
            return new FileStamp(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    private record FileStamp(Object key, long size, FileTime modified) { }

    public static final class Destination {
        private final Path path;
        private final FileStamp original;

        private Destination(Path path, FileStamp original) {
            this.path = path;
            this.original = original;
        }
        public Path path() { return path; }
        public boolean replacesExisting() { return original != null; }
    }
}

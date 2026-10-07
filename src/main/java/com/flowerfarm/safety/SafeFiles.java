package com.flowerfarm.safety;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes a file so a crash, power cut, or failed write never leaves a half-written
 * file in its place: the bytes go to a temporary file in the same folder, are
 * flushed to the disk, and only then replace the target in one step. If anything
 * fails, the previous file (if any) is untouched and the temporary file is removed.
 */
public final class SafeFiles {

    private SafeFiles() {
    }

    public static void write(Path target, byte[] bytes) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path folder = absolute.getParent();
        if (folder == null) {
            throw new IOException("A file cannot be saved at a drive root: " + target);
        }
        Path temporary = Files.createTempFile(folder, "." + absolute.getFileName() + "-", ".part");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            replace(temporary, absolute);
            moved = true;
            forceFolder(folder);
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static void writeString(Path target, CharSequence text) throws IOException {
        write(target, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Flushes an already-written file to the disk (before it is renamed into place). */
    public static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /** Moves {@code source} over {@code target} in one step where the file system allows it. */
    public static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best effort: makes the rename itself durable (works on Linux/macOS; a no-op on Windows). */
    private static void forceFolder(Path folder) {
        try (FileChannel channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | RuntimeException notSupported) {
            // Windows cannot open a folder as a channel; NTFS journals the rename itself.
        }
    }
}

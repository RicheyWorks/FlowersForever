package com.flowerfarm.safety;

import org.h2.store.fs.FilePath;
import org.h2.store.fs.FilePathWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * An H2 file system ({@code fulldisk:} URLs) that behaves exactly like the real disk
 * until {@link #FULL} is switched on; from then on every write fails with the same
 * message Windows gives when the disk is full.
 */
public final class FullDiskFilePath extends FilePathWrapper {

    public FullDiskFilePath() {
        // H2 creates instances reflectively
    }

    static volatile boolean full;
    static final String WINDOWS_MESSAGE = "There is not enough space on the disk";

    static void install() {
        FilePath.register(new FullDiskFilePath());
    }

    @Override
    public String getScheme() {
        return "fulldisk";
    }

    @Override
    public FileChannel open(String mode) throws IOException {
        return new Channel(super.open(mode));
    }

    private static void refuseIfFull() throws IOException {
        if (full) {
            throw new IOException(WINDOWS_MESSAGE);
        }
    }

    private static final class Channel extends FileChannel {
        private final FileChannel base;

        Channel(FileChannel base) {
            this.base = base;
        }

        @Override public int read(ByteBuffer dst) throws IOException { return base.read(dst); }
        @Override public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return base.read(dsts, offset, length);
        }
        @Override public int read(ByteBuffer dst, long position) throws IOException { return base.read(dst, position); }
        @Override public int write(ByteBuffer src) throws IOException { refuseIfFull(); return base.write(src); }
        @Override public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            refuseIfFull();
            return base.write(srcs, offset, length);
        }
        @Override public int write(ByteBuffer src, long position) throws IOException {
            refuseIfFull();
            return base.write(src, position);
        }
        @Override public long position() throws IOException { return base.position(); }
        @Override public FileChannel position(long newPosition) throws IOException {
            base.position(newPosition);
            return this;
        }
        @Override public long size() throws IOException { return base.size(); }
        @Override public FileChannel truncate(long size) throws IOException {
            refuseIfFull();
            base.truncate(size);
            return this;
        }
        @Override public void force(boolean metaData) throws IOException { base.force(metaData); }
        @Override public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            return base.transferTo(position, count, target);
        }
        @Override public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            refuseIfFull();
            return base.transferFrom(src, position, count);
        }
        @Override public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return base.map(mode, position, size);
        }
        @Override public FileLock lock(long position, long size, boolean shared) throws IOException {
            return base.lock(position, size, shared);
        }
        @Override public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return base.tryLock(position, size, shared);
        }
        @Override protected void implCloseChannel() throws IOException { base.close(); }
    }
}

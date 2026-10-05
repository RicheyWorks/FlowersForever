package com.flowerfarm.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/** Network boundary for update checks and downloads. Tests substitute a local server. */
public interface UpdateTransport {

    /** Fetches a small document, failing when it exceeds {@code maxBytes}. */
    byte[] fetch(URI uri, int maxBytes) throws IOException;

    /** Streams a file to {@code target}, failing when it exceeds {@code maxBytes}. */
    void download(URI uri, Path target, long maxBytes, LongConsumer progress) throws IOException;
}

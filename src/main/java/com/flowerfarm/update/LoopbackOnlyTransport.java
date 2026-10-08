package com.flowerfarm.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * Wraps a transport so it can only reach this computer. Used whenever a test key
 * is trusted: neither the manifest nor any installer it lists may come from the
 * network, so a test key can never be used against a real update feed.
 */
public final class LoopbackOnlyTransport implements UpdateTransport {

    private final UpdateTransport delegate;

    public LoopbackOnlyTransport(UpdateTransport delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] fetch(URI uri, int maxBytes) throws IOException {
        requireLoopback(uri);
        return delegate.fetch(uri, maxBytes);
    }

    @Override
    public void download(URI uri, Path target, long maxBytes, LongConsumer progress) throws IOException {
        requireLoopback(uri);
        delegate.download(uri, target, maxBytes, progress);
    }

    private static void requireLoopback(URI uri) throws IOException {
        if (!LoopbackAddresses.isLoopback(uri)) {
            throw new IOException("A test update may only use addresses on this computer; refused " + uri);
        }
    }
}

package com.flowerfarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * HTTPS-only transport. Redirects are followed only from HTTPS to HTTPS
 * (GitHub release assets redirect to a CDN). Plain HTTP is accepted only for
 * loopback addresses when explicitly allowed, which keeps tests local.
 */
public final class HttpUpdateTransport implements UpdateTransport {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final boolean allowLoopbackHttp;
    private final String userAgent;

    public HttpUpdateTransport(Duration connectTimeout, Duration requestTimeout,
                               boolean allowLoopbackHttp, String userAgent) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestTimeout = requestTimeout;
        this.allowLoopbackHttp = allowLoopbackHttp;
        this.userAgent = userAgent;
    }

    @Override
    public byte[] fetch(URI uri, int maxBytes) throws IOException {
        HttpResponse<InputStream> response = send(uri, requestTimeout);
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IOException("Update information is larger than expected");
            }
            return bytes;
        }
    }

    @Override
    public void download(URI uri, Path target, long maxBytes, LongConsumer progress) throws IOException {
        HttpResponse<InputStream> response = send(uri, requestTimeout.multipliedBy(30));
        try (InputStream body = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = body.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Download is larger than the published size");
                }
                out.write(buffer, 0, read);
                progress.accept(total);
            }
        }
    }

    private HttpResponse<InputStream> send(URI uri, Duration timeout) throws IOException {
        requireAllowed(uri, allowLoopbackHttp);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, application/octet-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Update request interrupted");
        }
        URI finalUri = response.uri();
        if (!finalUri.equals(uri)) {
            try {
                requireAllowed(finalUri, allowLoopbackHttp);
            } catch (IOException unsafe) {
                response.body().close();
                throw unsafe;
            }
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Update server answered HTTP " + response.statusCode());
        }
        return response;
    }

    static void requireAllowed(URI uri, boolean allowLoopbackHttp) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return;
        }
        if ("http".equals(scheme) && allowLoopbackHttp && isLoopback(uri.getHost())) {
            return;
        }
        throw new IOException("Updates are only downloaded over HTTPS: " + uri);
    }

    private static boolean isLoopback(String host) {
        return LoopbackAddresses.isLoopbackHost(host);
    }
}

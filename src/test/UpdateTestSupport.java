package com.flowerfarm.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared fixtures for update tests: a throwaway Ed25519 dev key (generated per
 * run, never stored), signed manifests, and a loopback-only HTTP server.
 */
final class UpdateTestSupport {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private UpdateTestSupport() {
    }

    static KeyPair devKey() {
        try {
            return UpdateSignatures.generateKeyPair();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    static UpdateSettings settings(Path root, String currentVersion, URI manifestUrl) {
        return settings(root, currentVersion, manifestUrl, "stable", Optional.of(root.resolve("app").resolve("FlowersForever.exe")));
    }

    static UpdateSettings settings(Path root, String currentVersion, URI manifestUrl, String channel,
                                   Optional<Path> launcher) {
        return new UpdateSettings(true, true, Optional.ofNullable(manifestUrl),
                UpdateChannel.fromName(channel).orElseThrow(), AppVersion.parse(currentVersion),
                root.resolve("FlowersForever").resolve("data"), root.resolve("FlowersForever").resolve("updates"),
                root.resolve("Backups"), launcher, true, true, Duration.ofSeconds(2), Duration.ofSeconds(5), "");
    }

    /** Builds a manifest payload; each release is {version, channels, packages map platform -> PackageInfo}. */
    static ObjectNode payload(boolean paused, List<ObjectNode> releases) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schema", 1);
        root.put("product", "FlowersForever");
        root.put("issuedAt", "2026-10-04T14:00:00Z");
        root.put("paused", paused);
        root.put("pauseMessage", paused ? "Holding while we fix a report bug." : "");
        ArrayNode array = root.putArray("releases");
        releases.forEach(array::add);
        return root;
    }

    static ObjectNode release(String version, String channels, String minimum, Map<String, ObjectNode> packages) {
        ObjectNode release = MAPPER.createObjectNode();
        release.put("version", version);
        ArrayNode list = release.putArray("channels");
        for (String channel : channels.split(",")) {
            list.add(channel);
        }
        if (minimum != null) {
            release.put("minimumVersion", minimum);
        }
        release.put("releasedOn", "2026-10-04");
        release.put("notes", "Faster harvest log and clearer invoices.");
        ObjectNode packageNodes = release.putObject("packages");
        packages.forEach(packageNodes::set);
        return release;
    }

    /** Signs an installer the same way the release tool does. */
    static ObjectNode packageNode(KeyPair key, String platform, String version, Path file, URI url)
            throws IOException, GeneralSecurityException {
        String fileName = file.getFileName().toString();
        long size = Files.size(file);
        String sha256 = UpdateSignatures.sha256Hex(file);
        ObjectNode node = MAPPER.createObjectNode();
        node.put("file", fileName);
        node.put("url", url.toString());
        node.put("size", size);
        node.put("sha256", sha256);
        node.put("keyId", UpdateSignatures.keyId(key.getPublic()));
        node.put("signature", Base64.getEncoder().encodeToString(UpdateSignatures.sign(key.getPrivate(),
                UpdateSignatures.packageStatement(platform, version, fileName, size, sha256))));
        return node;
    }

    static byte[] envelope(KeyPair key, ObjectNode payload) throws IOException, GeneralSecurityException {
        byte[] bytes = MAPPER.writeValueAsBytes(payload);
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("format", SignedManifestVerifier.FORMAT);
        envelope.put("keyId", UpdateSignatures.keyId(key.getPublic()));
        envelope.put("payload", Base64.getEncoder().encodeToString(bytes));
        envelope.put("signature", Base64.getEncoder().encodeToString(UpdateSignatures.sign(key.getPrivate(), bytes)));
        return MAPPER.writeValueAsBytes(envelope);
    }

    static Path fakeInstaller(Path directory, String name, String content) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** A small farm-records folder with a stand-in database file and an attachment. */
    static Path farmRecords(Path dataDirectory, String marker) throws IOException {
        Files.createDirectories(dataDirectory.resolve("attachments"));
        Files.writeString(dataDirectory.resolve(DataBackupService.DATABASE_FILE), "H2 store " + marker);
        Files.writeString(dataDirectory.resolve("attachments").resolve("bed-3.jpg"), "photo " + marker);
        return dataDirectory;
    }

    /** Loopback-only static file server; counts requests per path. */
    static final class LocalServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        final AtomicInteger requests = new AtomicInteger();

        LocalServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                byte[] body = files.get(exchange.getRequestURI().getPath());
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
        }

        void put(String path, byte[] body) {
            files.put(path, body);
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /** A port nobody is listening on, for offline tests. */
    static URI unreachable() throws IOException {
        try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/latest.json");
        }
    }

    /** Records installer hand-offs instead of starting anything. */
    static final class RecordingInstaller implements PlatformInstaller {
        final List<String> events;
        boolean installable = true;
        boolean failLaunch;

        RecordingInstaller(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean canInstall() {
            return installable;
        }

        @Override
        public Path launch(VerifiedPackage installer, Path workDirectory, Purpose purpose) throws IOException {
            if (failLaunch) {
                throw new IOException("simulated launch failure");
            }
            events.add(purpose + " " + installer.version() + " " + installer.file().getFileName());
            return workDirectory.resolve("apply.cmd");
        }
    }
}

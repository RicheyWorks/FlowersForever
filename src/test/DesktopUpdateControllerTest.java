package com.flowerfarm.gui;

import com.flowerfarm.update.AppVersion;
import com.flowerfarm.update.DataBackupService;
import com.flowerfarm.update.HttpUpdateTransport;
import com.flowerfarm.update.PackageDownloader;
import com.flowerfarm.update.PlatformInstaller;
import com.flowerfarm.update.PublisherVerifier;
import com.flowerfarm.update.SignedManifestVerifier;
import com.flowerfarm.update.TrustedKeys;
import com.flowerfarm.update.UpdateChannel;
import com.flowerfarm.update.UpdateChecker;
import com.flowerfarm.update.UpdateCoordinator;
import com.flowerfarm.update.UpdateLaunchGuard;
import com.flowerfarm.update.UpdatePlatform;
import com.flowerfarm.update.UpdateSettings;
import com.flowerfarm.update.UpdateSignatures;
import com.flowerfarm.update.UpdateStateStore;
import com.flowerfarm.update.VerifiedPackage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import javax.swing.Icon;
import java.awt.Component;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Drives the update prompts headlessly with scripted dialog answers and same-thread executors. */
@DisplayName("Desktop update prompts")
class DesktopUpdateControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final KeyPair publisher = generate();
    private final List<String> transcript = new ArrayList<>();
    private final List<String> status = new ArrayList<>();
    private final List<Integer> exits = new ArrayList<>();
    private final Deque<Integer> answers = new ArrayDeque<>();

    private static KeyPair generate() {
        try {
            return UpdateSignatures.generateKeyPair();
        } catch (Exception unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private ObjectNode pkg(String version) throws Exception {
        Path msi = Files.writeString(Files.createDirectories(temp.resolve("published")).resolve("FlowersForever-" + version + ".msi"),
                "msi " + version);
        files.put("/" + msi.getFileName(), Files.readAllBytes(msi));
        long size = Files.size(msi);
        String sha = UpdateSignatures.sha256Hex(msi);
        ObjectNode node = MAPPER.createObjectNode();
        node.put("file", msi.getFileName().toString());
        node.put("url", uri("/" + msi.getFileName()).toString());
        node.put("size", size);
        node.put("sha256", sha);
        node.put("keyId", UpdateSignatures.keyId(publisher.getPublic()));
        node.put("signature", Base64.getEncoder().encodeToString(UpdateSignatures.sign(publisher.getPrivate(),
                UpdateSignatures.packageStatement("windows-msi", version, msi.getFileName().toString(), size, sha))));
        return node;
    }

    private void publish(String... versions) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schema", 1);
        root.put("product", "FlowersForever");
        root.put("issuedAt", "2026-10-04T14:00:00Z");
        var releases = root.putArray("releases");
        for (String version : versions) {
            ObjectNode release = releases.addObject();
            release.put("version", version);
            release.putArray("channels").add("stable");
            release.put("notes", "Clearer invoices.");
            release.putObject("packages").set("windows-msi", pkg(version));
        }
        byte[] payload = MAPPER.writeValueAsBytes(root);
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("format", SignedManifestVerifier.FORMAT);
        envelope.put("keyId", UpdateSignatures.keyId(publisher.getPublic()));
        envelope.put("payload", Base64.getEncoder().encodeToString(payload));
        envelope.put("signature", Base64.getEncoder().encodeToString(UpdateSignatures.sign(publisher.getPrivate(), payload)));
        files.put("/latest.json", MAPPER.writeValueAsBytes(envelope));
    }

    private final List<String> handoffs = new ArrayList<>();

    private DesktopUpdateController controller(URI manifest) throws Exception {
        Path data = temp.resolve("FlowersForever/data");
        Files.createDirectories(data);
        Files.writeString(data.resolve(DataBackupService.DATABASE_FILE), "records");
        UpdateSettings settings = new UpdateSettings(true, true, Optional.of(manifest), UpdateChannel.STABLE,
                AppVersion.parse("1.2.0"), data, temp.resolve("FlowersForever/updates"), temp.resolve("Backups"),
                Optional.of(temp.resolve("FlowersForever.exe")), true, true, Duration.ofSeconds(2),
                Duration.ofSeconds(5), "");
        SignedManifestVerifier verifier = new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic()));
        HttpUpdateTransport transport = new HttpUpdateTransport(Duration.ofSeconds(2), Duration.ofSeconds(5), true, "t");
        UpdateStateStore store = new UpdateStateStore(settings.updatesDirectory());
        PlatformInstaller installer = new PlatformInstaller() {
            @Override
            public boolean canInstall() {
                return true;
            }

            @Override
            public Path launch(VerifiedPackage pkg, Path work, Purpose purpose) {
                handoffs.add(purpose + " " + pkg.version());
                return work;
            }
        };
        UpdateCoordinator coordinator = new UpdateCoordinator(settings, new UpdateChecker(settings, verifier, transport,
                Optional.of(UpdatePlatform.WINDOWS_MSI), store::failedVersions),
                new PackageDownloader(settings.updatesDirectory(), transport, verifier, PublisherVerifier.none()),
                new DataBackupService(Clock.systemUTC()), store, installer, Optional.of(UpdatePlatform.WINDOWS_MSI),
                (operation, success, message) -> { }, Clock.systemUTC());
        @SuppressWarnings("unchecked")
        ObjectProvider<UpdateLaunchGuard> noGuard = mock(ObjectProvider.class);
        DesktopUpdateController controller = new DesktopUpdateController(coordinator, noGuard,
                () -> transcript.add("database closed"), Runnable::run, Runnable::run, exits::add,
                () -> transcript.add("relaunch"));
        controller.attach(new DesktopUpdateController.Host(null, dialogs(), status::add, () -> true,
                () -> transcript.add("preferences saved")));
        return controller;
    }

    private FarmDialogs dialogs() {
        return new FarmDialogs() {
            @Override
            public int showOption(Component parent, Object message, String title, int optionType, int messageType,
                                  Icon icon, Object[] options, Object initialValue) {
                transcript.add("OPTION " + title + ": " + message);
                return answers.isEmpty() ? -1 : answers.pop();
            }

            @Override
            public int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
                transcript.add("CONFIRM " + title);
                return -1;
            }

            @Override
            public void showMessage(Component parent, Object message, String title, int messageType) {
                transcript.add("MESSAGE " + title + ": " + message);
            }
        };
    }

    @Test
    @DisplayName("launch check finds an update; Update now then Install now backs up and hands off, then exits")
    void updateNowFlow() throws Exception {
        publish("1.2.0", "1.2.1");
        answers.add(0);
        answers.add(0);

        controller(uri("/latest.json"));

        assertThat(transcript).anyMatch(line -> line.startsWith("OPTION New version ready: A new version of FlowersForever is ready: 1.2.1"));
        assertThat(transcript).anyMatch(line -> line.startsWith("OPTION Ready to install"));
        assertThat(transcript).containsSubsequence("preferences saved", "database closed");
        assertThat(handoffs).containsExactly("UPDATE 1.2.1");
        assertThat(exits).containsExactly(0);
        try (var backups = Files.list(temp.resolve("Backups"))) {
            assertThat(backups.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("FlowersForever-before-1.2.1-") && name.endsWith(".zip"));
        }
    }

    @Test
    @DisplayName("Later downloads nothing and changes nothing")
    void later() throws Exception {
        publish("1.2.0", "1.2.1");
        answers.add(1);

        controller(uri("/latest.json"));

        assertThat(handoffs).isEmpty();
        assertThat(exits).isEmpty();
        assertThat(temp.resolve("FlowersForever/updates/packages")).doesNotExist();
        assertThat(status).contains("Update postponed. FlowersForever will remind you next time.");
    }

    @Test
    @DisplayName("offline at launch: no dialog, no error")
    void offlineLaunchIsSilent() throws Exception {
        controller(uri("/not-there.json"));

        assertThat(transcript).isEmpty();
        assertThat(exits).isEmpty();
    }

    @Test
    @DisplayName("Check for updates when current: plain answer and a way to turn automatic checks off")
    void manualCheckAndToggle() throws Exception {
        publish("1.2.0");
        DesktopUpdateController controller = controller(uri("/latest.json"));
        transcript.clear();
        answers.add(1);

        controller.checkNow();

        assertThat(transcript).singleElement().asString()
                .contains("You have the latest version of FlowersForever (1.2.0).", "Automatic checks", "are on");
        assertThat(status).contains("Automatic update checks turned off.");
        assertThat(Files.readString(temp.resolve("FlowersForever/updates/update-preferences.properties")))
                .contains("automaticChecks=false");
    }
}

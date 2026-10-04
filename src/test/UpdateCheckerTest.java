package com.flowerfarm.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Update check: available, none, offline, paused, and rejected")
class UpdateCheckerTest {

    @TempDir
    Path temp;

    private final KeyPair publisher = UpdateTestSupport.devKey();
    private UpdateTestSupport.LocalServer server;

    @BeforeEach
    void start() throws IOException {
        server = new UpdateTestSupport.LocalServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private UpdateChecker checker(String current, URI manifest, String channel, Set<String> failed) {
        UpdateSettings settings = UpdateTestSupport.settings(temp, current, manifest, channel, Optional.empty());
        return new UpdateChecker(settings, new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic())),
                transport(), Optional.of(UpdatePlatform.WINDOWS_MSI), () -> failed);
    }

    private static HttpUpdateTransport transport() {
        return new HttpUpdateTransport(Duration.ofSeconds(2), Duration.ofSeconds(5), true, "test");
    }

    private void publish(boolean paused, String... versionsAndChannels) throws Exception {
        List<ObjectNode> releases = new java.util.ArrayList<>();
        for (int index = 0; index < versionsAndChannels.length; index += 2) {
            String version = versionsAndChannels[index];
            Path msi = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-" + version + ".msi",
                    "msi " + version);
            releases.add(UpdateTestSupport.release(version, versionsAndChannels[index + 1], "1.0.0",
                    Map.of("windows-msi", UpdateTestSupport.packageNode(publisher, "windows-msi", version, msi,
                            URI.create("https://updates.example/FlowersForever-" + version + ".msi")))));
        }
        server.put("/latest.json", UpdateTestSupport.envelope(publisher, UpdateTestSupport.payload(paused, releases)));
    }

    @Test
    @DisplayName("a newer signed release is offered with plain words and its notes")
    void updateAvailable() throws Exception {
        publish(false, "1.2.0", "stable", "1.2.1", "stable");

        UpdateCheckResult result = checker("1.2.0", server.uri("/latest.json"), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(result.release().orElseThrow().version()).isEqualTo(AppVersion.parse("1.2.1"));
        assertThat(result.message()).startsWith("A new version of FlowersForever is ready: 1.2.1 (you have 1.2.0).")
                .contains("What's new:", "saves a checked backup of your farm records", "goes back to 1.2.0");
    }

    @Test
    @DisplayName("no newer release means up to date")
    void noUpdate() throws Exception {
        publish(false, "1.2.0", "stable");

        UpdateCheckResult result = checker("1.2.0", server.uri("/latest.json"), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.UP_TO_DATE);
        assertThat(result.message()).isEqualTo("You have the latest version of FlowersForever (1.2.0).");
    }

    @Test
    @DisplayName("offline is a quiet, normal result and never an exception")
    void offline() throws Exception {
        UpdateCheckResult result = checker("1.2.0", UpdateTestSupport.unreachable(), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.OFFLINE);
        assertThat(result.message()).contains("could not reach the update server, which is fine", "keep working offline");
    }

    @Test
    @DisplayName("a missing manifest (HTTP 404) is treated like offline")
    void serverWithoutManifest() {
        UpdateCheckResult result = checker("1.2.0", server.uri("/missing.json"), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.OFFLINE);
    }

    @Test
    @DisplayName("a manifest signed by an unknown key is rejected and nothing is offered")
    void badSignatureRejected() throws Exception {
        KeyPair attacker = UpdateTestSupport.devKey();
        Path msi = UpdateTestSupport.fakeInstaller(temp, "FlowersForever-9.9.9.msi", "evil");
        server.put("/latest.json", UpdateTestSupport.envelope(attacker, UpdateTestSupport.payload(false, List.of(
                UpdateTestSupport.release("9.9.9", "stable", null, Map.of("windows-msi",
                        UpdateTestSupport.packageNode(attacker, "windows-msi", "9.9.9", msi,
                                URI.create("https://evil.example/x.msi"))))))));

        UpdateCheckResult result = checker("1.2.0", server.uri("/latest.json"), "stable", Set.of()).check(true);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.REJECTED);
        assertThat(result.release()).isEmpty();
    }

    @Test
    @DisplayName("staged rollout: an owner-only release is invisible to stable and early installs")
    void channels() throws Exception {
        publish(false, "1.2.1", "owner");

        assertThat(checker("1.2.0", server.uri("/latest.json"), "stable", Set.of()).check(false).status())
                .isEqualTo(UpdateCheckResult.Status.UP_TO_DATE);
        assertThat(checker("1.2.0", server.uri("/latest.json"), "early", Set.of()).check(false).status())
                .isEqualTo(UpdateCheckResult.Status.UP_TO_DATE);
        assertThat(checker("1.2.0", server.uri("/latest.json"), "owner", Set.of()).check(false).status())
                .isEqualTo(UpdateCheckResult.Status.UPDATE_AVAILABLE);
        publish(false, "1.2.1", "stable");
        assertThat(checker("1.2.0", server.uri("/latest.json"), "owner", Set.of()).check(false).status())
                .as("owner installs also receive stable releases")
                .isEqualTo(UpdateCheckResult.Status.UPDATE_AVAILABLE);
    }

    @Test
    @DisplayName("the pause switch stops every offer")
    void paused() throws Exception {
        publish(true, "1.2.1", "stable");

        UpdateCheckResult result = checker("1.2.0", server.uri("/latest.json"), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.PAUSED);
        assertThat(result.message()).contains("paused", "Holding while we fix a report bug.");
    }

    @Test
    @DisplayName("a version that failed its health check here is not offered again")
    void failedVersionSkipped() throws Exception {
        publish(false, "1.2.1", "stable");

        assertThat(checker("1.2.0", server.uri("/latest.json"), "stable", Set.of("1.2.1")).check(false).status())
                .isEqualTo(UpdateCheckResult.Status.UP_TO_DATE);
    }

    @Test
    @DisplayName("too-old installs are told to get a fresh installer")
    void minimumVersion() throws Exception {
        publish(false, "1.2.1", "stable");

        UpdateCheckResult result = checker("0.9.0", server.uri("/latest.json"), "stable", Set.of()).check(false);

        assertThat(result.status()).isEqualTo(UpdateCheckResult.Status.MANUAL_INSTALL_REQUIRED);
    }

    @Test
    @DisplayName("turned off or unconfigured: no network request at all")
    void disabledMakesNoRequest() throws Exception {
        publish(false, "1.2.1", "stable");
        UpdateSettings settings = UpdateTestSupport.settings(temp, "1.2.0", server.uri("/latest.json"));
        settings.setAutomaticChecksEnabled(false);
        UpdateChecker off = new UpdateChecker(settings, new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic())),
                transport(), Optional.of(UpdatePlatform.WINDOWS_MSI), Set::of);
        UpdateChecker noKeys = new UpdateChecker(settings, new SignedManifestVerifier(TrustedKeys.none()),
                transport(), Optional.of(UpdatePlatform.WINDOWS_MSI), Set::of);

        assertThat(off.check(false).status()).isEqualTo(UpdateCheckResult.Status.DISABLED);
        assertThat(noKeys.check(true).status()).isEqualTo(UpdateCheckResult.Status.NOT_CONFIGURED);
        assertThat(server.requests.get()).isZero();
        assertThat(off.check(true).status()).as("a manual check still works when automatic checks are off")
                .isEqualTo(UpdateCheckResult.Status.UPDATE_AVAILABLE);
    }

    @Test
    @DisplayName("only HTTPS is used, except literal loopback addresses in tests")
    void httpsOnly() throws Exception {
        HttpUpdateTransport.requireAllowed(URI.create("https://updates.example/latest.json"), false);
        HttpUpdateTransport.requireAllowed(URI.create("http://127.0.0.1:9/latest.json"), true);
        assertThatThrownBy(() -> HttpUpdateTransport.requireAllowed(URI.create("http://127.0.0.1:9/x"), false))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> HttpUpdateTransport.requireAllowed(URI.create("http://updates.example/x"), true))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> HttpUpdateTransport.requireAllowed(URI.create("file:///C:/x.json"), true))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("settings default to stable, on, and no source until a URL is configured")
    void settingsDefaults() {
        Map<String, String> properties = Map.of("flowerfarm.data-dir", temp.resolve("FlowersForever/data").toString());
        UpdateSettings settings = UpdateSettings.from(properties::get);

        assertThat(settings.enabledByConfiguration()).isTrue();
        assertThat(settings.manifestUrl()).isEmpty();
        assertThat(settings.channel()).isEqualTo(UpdateChannel.STABLE);
        assertThat(settings.updatesDirectory()).isEqualTo(temp.resolve("FlowersForever/updates").toAbsolutePath());
        assertThat(settings.configured(TrustedKeys.of(publisher.getPublic()))).isFalse();
    }
}

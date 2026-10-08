package com.flowerfarm.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadlessUpdateCommandTest {

    @TempDir
    Path root;

    private final KeyPair testKey = UpdateTestSupport.devKey();
    private final List<String> events = new ArrayList<>();
    private final AtomicReference<List<String>> relaunch = new AtomicReference<>();
    private final AtomicInteger networkCalls = new AtomicInteger();
    private final ByteArrayOutputStream console = new ByteArrayOutputStream();
    private UpdateTestSupport.LocalServer server;
    private Path keyFile;
    private Path logFile;

    @BeforeEach
    void setUp() throws Exception {
        server = new UpdateTestSupport.LocalServer();
        keyFile = root.resolve("keys").resolve("test-update.pub");
        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, UpdateSignatures.encodePublicKey(testKey.getPublic()) + "\n");
        logFile = root.resolve("evidence").resolve("check-15.log");
        UpdateTestSupport.farmRecords(data(), "before");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("without the test flag nothing runs: no network, no backup, no installer, exit 64")
    void refusesWhenNotEnabled() throws Exception {
        publish(testKey, true);

        int exit = command(false, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));

        assertThat(exit).isEqualTo(HeadlessUpdateCommand.Exit.REFUSED.code()).isEqualTo(64);
        assertThat(server.requests).hasValue(0);
        assertThat(networkCalls).hasValue(0);
        assertThat(events).isEmpty();
        assertThat(root.resolve("FlowersForever").resolve("updates")).doesNotExist();
        assertThat(root.resolve("Backups")).doesNotExist();
        assertThat(log()).contains("not enabled", "RESULT  exit=64 REFUSED");

        AtomicBoolean started = new AtomicBoolean();
        HeadlessUpdateCommand closed = command(false, "1.3.0", TrustedKeys.none());
        int verify = closed.verifyLaunch(closed.guard(), () -> {
            started.set(true);
            return "context";
        }, () -> { }, context -> "records", context -> { });
        assertThat(verify).isEqualTo(64);
        assertThat(started).isFalse();
    }

    @Test
    @DisplayName("the gate opens only for an explicit true in the JVM property or the environment")
    void gateNeedsAnExplicitTrue() {
        Map<String, String> none = Map.of();
        assertThat(HeadlessUpdateCommand.gateOpen(none::get, none::get)).isFalse();
        for (String value : List.of("false", "1", "yes", "on", "")) {
            Map<String, String> set = Map.of(HeadlessUpdateCommand.GATE_PROPERTY, value);
            Map<String, String> env = Map.of(HeadlessUpdateCommand.GATE_ENVIRONMENT, value);
            assertThat(HeadlessUpdateCommand.gateOpen(set::get, none::get)).as(value).isFalse();
            assertThat(HeadlessUpdateCommand.gateOpen(none::get, env::get)).as(value).isFalse();
        }
        Map<String, String> property = Map.of(HeadlessUpdateCommand.GATE_PROPERTY, "true");
        Map<String, String> environment = Map.of(HeadlessUpdateCommand.GATE_ENVIRONMENT, " TRUE ");
        assertThat(HeadlessUpdateCommand.gateOpen(property::get, none::get)).isTrue();
        assertThat(HeadlessUpdateCommand.gateOpen(none::get, environment::get)).isTrue();
    }

    @Test
    @DisplayName("any headless flag is recognised so the desktop never starts; bad arguments are refused")
    void argumentsAreStrict() {
        assertThat(HeadlessUpdateCommand.requested(new String[] {"--trust-test-key", "k.pub"})).isTrue();
        assertThat(HeadlessUpdateCommand.requested(new String[] {"--verify-update-launch"})).isTrue();
        assertThat(HeadlessUpdateCommand.requested(new String[] {"--cli"})).isFalse();
        assertThatThrownBy(() -> HeadlessUpdateCommand.parse(new String[] {"--trust-test-key", "k.pub"}))
                .hasMessageContaining("only used with --apply-update-from");
        assertThatThrownBy(() -> HeadlessUpdateCommand.parse(new String[] {
                "--apply-update-from", "http://127.0.0.1/x.json", "--verify-update-launch"}))
                .hasMessageContaining("not both");
        assertThatThrownBy(() -> HeadlessUpdateCommand.parse(new String[] {"--verify-update-launch", "--surprise"}))
                .hasMessageContaining("Unexpected argument");
        assertThatThrownBy(() -> HeadlessUpdateCommand.parse(new String[] {"--apply-update-from", "--update-log"}))
                .hasMessageContaining("Missing value");
        assertThatThrownBy(() -> HeadlessUpdateCommand.parse(new String[] {
                "--verify-update-launch", "--trust-test-key", "k.pub"}))
                .hasMessageContaining("only used with --apply-update-from");
        HeadlessUpdateCommand.Request request = HeadlessUpdateCommand.parse(new String[] {
                "--apply-update-from", "http://127.0.0.1:8123/latest.json", "--trust-test-key", "k.pub",
                "--update-log", "c.log", "--update-backup-dir", "b"});
        assertThat(request.mode()).isEqualTo(HeadlessUpdateCommand.Mode.APPLY);
        assertThat(request.feed()).contains(URI.create("http://127.0.0.1:8123/latest.json"));
        assertThat(request.testKey()).contains(Path.of("k.pub"));
    }

    @Test
    @DisplayName("a test key is refused with any feed that is not on this computer, before any network use")
    void refusesRemoteFeedWithTestKey() throws Exception {
        for (String feed : List.of("https://richeyworks.github.io/FlowersForever-updates/latest.json",
                "http://192.168.1.20:8000/latest.json", "http://127.0.0.1.example.com/latest.json",
                "https://localhost.richeyworks.dev/latest.json")) {
            int exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(URI.create(feed)));
            assertThat(exit).as(feed).isEqualTo(64);
        }
        assertThat(networkCalls).hasValue(0);
        assertThat(events).isEmpty();
        assertThat(log()).contains("can only be used with a feed on this computer");
    }

    @Test
    @DisplayName("apply always needs a test key; the built-in release keys are never used by this path")
    void refusesApplyWithoutTestKey() throws Exception {
        HeadlessUpdateCommand.Request noKey = new HeadlessUpdateCommand.Request(HeadlessUpdateCommand.Mode.APPLY,
                Optional.of(server.uri("/latest.json")), Optional.empty(), Optional.of(logFile), Optional.empty());

        assertThat(command(true, "1.3.0", TrustedKeys.none()).apply(noKey)).isEqualTo(64);
        assertThat(networkCalls).hasValue(0);
        assertThat(log()).contains("needs --trust-test-key");
    }

    @Test
    @DisplayName("a built-in release key passed as the test key is refused")
    void refusesReleaseKeyAsTestKey() throws Exception {
        publish(testKey, true);

        int exit = command(true, "1.3.0", TrustedKeys.of(testKey.getPublic())).apply(apply(server.uri("/latest.json")));

        assertThat(exit).isEqualTo(64);
        assertThat(networkCalls).hasValue(0);
        assertThat(log()).contains("is a built-in release key");
    }

    @Test
    @DisplayName("a key file holding a private key is refused and its secret never reaches the log")
    void refusesPrivateKeyFile() throws Exception {
        String secret = UpdateSignatures.encodePrivateKey(testKey.getPrivate());
        Files.writeString(keyFile, "# FlowersForever update signing key. SECRET\nprivate=" + secret + "\npublic="
                + UpdateSignatures.encodePublicKey(testKey.getPublic()) + "\n");

        int exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));

        assertThat(exit).isEqualTo(64);
        assertThat(log()).contains("holds a private key").doesNotContain(secret);
        assertThat(console.toString(StandardCharsets.UTF_8)).doesNotContain(secret);
    }

    @Test
    @DisplayName("public key files: bare, label=key, keygen public line, and PEM are read; two keys are refused")
    void readsPublicKeyFormats() throws Exception {
        String encoded = UpdateSignatures.encodePublicKey(testKey.getPublic());
        String id = UpdateSignatures.keyId(testKey.getPublic());
        Path file = root.resolve("k.pub");
        for (String content : List.of(encoded, "test-2026-10-08=" + encoded, "# key\npublic=" + encoded,
                "-----BEGIN PUBLIC KEY-----\n" + encoded.substring(0, 20) + "\n" + encoded.substring(20)
                        + "\n-----END PUBLIC KEY-----\n")) {
            Files.writeString(file, content);
            assertThat(UpdateSignatures.keyId(HeadlessUpdateCommand.readTestKey(file))).as(content).isEqualTo(id);
        }
        Files.writeString(file, encoded + "\n" + encoded + "\n");
        assertThatThrownBy(() -> HeadlessUpdateCommand.readTestKey(file)).hasMessageContaining("exactly one");
        Files.writeString(file, "not-a-key");
        assertThatThrownBy(() -> HeadlessUpdateCommand.readTestKey(file)).hasMessageContaining("valid Ed25519");
    }

    @Test
    @DisplayName("a manifest with a bad signature is rejected: nothing downloaded, backed up, or installed")
    void rejectsBadSignature() throws Exception {
        KeyPair attacker = UpdateTestSupport.devKey();
        publish(attacker, true);

        int exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));

        assertThat(exit).isEqualTo(HeadlessUpdateCommand.Exit.REJECTED.code()).isEqualTo(66);
        assertThat(server.requests).hasValue(1);
        assertThat(events).isEmpty();
        assertThat(root.resolve("Backups")).doesNotExist();
        assertThat(Files.readString(data().resolve(DataBackupService.DATABASE_FILE))).isEqualTo("H2 store before");
        assertThat(log()).contains("CHECK  REJECTED", "RESULT  exit=66 REJECTED");
    }

    @Test
    @DisplayName("an installer address off this computer is refused even inside a correctly signed local feed")
    void refusesRemoteInstallerInLocalFeed() throws Exception {
        Path next = UpdateTestSupport.fakeInstaller(root.resolve("published"), "FlowersForever-1.3.1.msi", "msi 1.3.1");
        List<com.fasterxml.jackson.databind.node.ObjectNode> releases = List.of(UpdateTestSupport.release("1.3.1",
                "stable", null, Map.of("windows-msi", UpdateTestSupport.packageNode(testKey, "windows-msi", "1.3.1",
                        next, URI.create("https://github.com/RicheyWorks/FlowersForever-updates/releases/download/"
                                + "v1.3.1/FlowersForever-1.3.1.msi")))));
        server.put("/latest.json", UpdateTestSupport.envelope(testKey, UpdateTestSupport.payload(false, releases)));

        int exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));

        assertThat(exit).isEqualTo(66);
        assertThat(events).isEmpty();
        assertThat(log()).contains("could not be downloaded");
    }

    @Test
    @DisplayName("records still open in another FlowersForever: verified but not installed, records untouched")
    void refusesWhileRecordsAreOpen() throws Exception {
        publish(testKey, true);
        Path database = data().resolve(DataBackupService.DATABASE_FILE);
        int exit;
        try (FileChannel channel = FileChannel.open(database, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));
        }

        assertThat(exit).isEqualTo(HeadlessUpdateCommand.Exit.NOT_INSTALLED.code()).isEqualTo(67);
        assertThat(events).isEmpty();
        assertThat(Files.readString(database)).isEqualTo("H2 store before");
    }

    @Test
    @DisplayName("apply runs the desktop's steps and hands off to the silent installer that reopens the headless check")
    void appliesAndHandsOffSilently() throws Exception {
        publish(testKey, true);

        int exit = command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")));

        assertThat(exit).as(log()).isZero();
        assertThat(events).containsExactly("UPDATE 1.3.1 FlowersForever-1.3.1.msi");
        assertThat(relaunch.get()).containsExactly("--verify-update-launch", "--update-log",
                logFile.toAbsolutePath().normalize().toString());
        try (Stream<Path> backups = Files.list(root.resolve("evidence-backups"))) {
            assertThat(backups.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.contains("before-1.3.1") && name.endsWith(".zip"));
        }
        UpdateState state = new UpdateStateStore(updates()).load();
        assertThat(state.phase).isEqualTo(UpdateState.Phase.INSTALL_HANDED_OFF);
        assertThat(state.fromVersion).isEqualTo("1.3.0");
        assertThat(state.toVersion).isEqualTo("1.3.1");
        assertThat(state.rollbackInstaller).endsWith("FlowersForever-1.3.0.msi");
        assertThat(log()).contains("TRUST  test key id " + UpdateSignatures.keyId(testKey.getPublic()),
                "CHECK  UPDATE_AVAILABLE", "VERIFIED  new installer", "BACKUP  ", "RESULT  exit=0 OK");
    }

    @Test
    @DisplayName("rollback: a failed first launch restores the records and silently reinstalls the previous version")
    void failedFirstLaunchRollsBack() throws Exception {
        publish(testKey, true);
        assertThat(command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")))).isZero();
        events.clear();
        // The broken 1.3.1 "installed" and changed the records before its start failed.
        Files.writeString(data().resolve(DataBackupService.DATABASE_FILE), "H2 store half-migrated by 1.3.1");

        HeadlessUpdateCommand reopened = command(true, "1.3.1", TrustedKeys.none());
        int exit = reopened.verifyLaunch(reopened.guard(), () -> {
            throw new IllegalStateException("Migration V999__broken.sql failed");
        }, () -> { }, context -> "records", context -> { });

        assertThat(exit).as(log()).isEqualTo(HeadlessUpdateCommand.Exit.ROLLED_BACK.code()).isEqualTo(3);
        assertThat(events).containsExactly("ROLLBACK 1.3.0 FlowersForever-1.3.0.msi");
        assertThat(relaunch.get()).startsWith("--verify-update-launch");
        assertThat(Files.readString(data().resolve(DataBackupService.DATABASE_FILE))).isEqualTo("H2 store before");
        try (Stream<Path> siblings = Files.list(data().getParent())) {
            assertThat(siblings.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.startsWith("data.before-restore-"));
        }
        assertThat(log()).contains("NOTICE  ", "went back to 1.3.0", "RESULT  exit=3 ROLLED_BACK");

        // The silently reinstalled 1.3.0 reopens headless, starts, and reports its records.
        HeadlessUpdateCommand previous = command(true, "1.3.0", TrustedKeys.none());
        AtomicBoolean closed = new AtomicBoolean();
        int restarted = previous.verifyLaunch(previous.guard(), () -> "context", () -> { },
                context -> "tables=1 rows=5", context -> closed.set(true));
        assertThat(restarted).isZero();
        assertThat(closed).isTrue();
        assertThat(log()).contains("RECORDS  tables=1 rows=5", "RESULT  exit=0 OK Version 1.3.0 started");

        // 1.3.1 is remembered as failed and is not offered again.
        assertThat(command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json"))))
                .isEqualTo(HeadlessUpdateCommand.Exit.NO_UPDATE.code());
    }

    @Test
    @DisplayName("a healthy first launch after the update is confirmed and reported")
    void healthyFirstLaunchIsConfirmed() throws Exception {
        publish(testKey, true);
        assertThat(command(true, "1.3.0", TrustedKeys.none()).apply(apply(server.uri("/latest.json")))).isZero();

        HeadlessUpdateCommand reopened = command(true, "1.3.1", TrustedKeys.none());
        UpdateLaunchGuard guard = reopened.guard();
        int exit = reopened.verifyLaunch(guard, () -> {
            assertThat(guard.decision()).isEqualTo(UpdateState.Decision.VERIFY_FIRST_LAUNCH);
            guard.confirmHealthy(); // what UpdateFirstLaunchVerifier does inside Spring after the health check
            return "context";
        }, () -> { }, context -> "tables=1 rows=5", context -> { });

        assertThat(exit).isZero();
        assertThat(new UpdateStateStore(updates()).load().phase).isEqualTo(UpdateState.Phase.IDLE);
        assertThat(log()).contains("RECORDS  tables=1 rows=5", "RESULT  exit=0 OK Version 1.3.1 started");
    }

    @Test
    @DisplayName("silent hand-off script: msiexec /qn, marks the reopened app as the headless check, quoted arguments")
    void silentHandOffScript() throws Exception {
        Path launcher = root.resolve("Program Files").resolve("FlowersForever").resolve("FlowersForever.exe");
        Path log = root.resolve("evidence").resolve("check-15.log");
        List<List<String>> started = new ArrayList<>();
        WindowsMsiInstaller silent = new WindowsMsiInstaller(Optional.of(launcher), (command, dir) -> started.add(command),
                4242, root.resolve("Windows"), true, List.of("--verify-update-launch", "--update-log", log.toString()));

        String script = silent.script(root.resolve("FlowersForever-1.3.1.msi"), root.resolve("msiexec.log"),
                root.resolve("msiexec.exitcode"));

        assertThat(script).contains("/qn /norestart /l*v").doesNotContain("/passive");
        assertThat(script).contains("set \"FLOWERFARM_UPDATES_HEADLESS_TEST=true\"");
        assertThat(script).contains("start \"\" \"" + launcher.toAbsolutePath() + "\" \"--verify-update-launch\" "
                + "\"--update-log\" \"" + log + "\"");

        String desktop = new WindowsMsiInstaller(Optional.of(launcher), (command, dir) -> started.add(command), 4242,
                root.resolve("Windows")).script(root.resolve("FlowersForever-1.3.1.msi"), root.resolve("msiexec.log"),
                root.resolve("msiexec.exitcode"));
        assertThat(desktop).contains("/passive /norestart /l*v").doesNotContain("FLOWERFARM_UPDATES_HEADLESS_TEST")
                .contains("start \"\" \"" + launcher.toAbsolutePath() + "\"\r\n");

        WindowsMsiInstaller unsafe = new WindowsMsiInstaller(Optional.of(launcher), (command, dir) -> started.add(command),
                4242, root.resolve("Windows"), true, List.of("--update-log", "a.log & calc"));
        assertThatThrownBy(() -> unsafe.script(root.resolve("x.msi"), root.resolve("l.log"), root.resolve("r")))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot use");
        assertThat(PlatformInstallers.silentForThisProcess(Optional.of(UpdatePlatform.MACOS_DMG),
                Optional.of(launcher), List.of()).canInstall()).isFalse();
    }

    /** Publishes 1.3.1 (new) and, optionally, the 1.3.0 installer kept for rollback, signed by {@code signer}. */
    private void publish(KeyPair signer, boolean withPrevious) throws Exception {
        Path published = root.resolve("published");
        Path next = UpdateTestSupport.fakeInstaller(published, "FlowersForever-1.3.1.msi", "msi 1.3.1");
        server.put("/FlowersForever-1.3.1.msi", Files.readAllBytes(next));
        List<com.fasterxml.jackson.databind.node.ObjectNode> releases = new ArrayList<>();
        if (withPrevious) {
            Path previous = UpdateTestSupport.fakeInstaller(published, "FlowersForever-1.3.0.msi", "msi 1.3.0");
            server.put("/FlowersForever-1.3.0.msi", Files.readAllBytes(previous));
            releases.add(UpdateTestSupport.release("1.3.0", "stable", null, Map.of("windows-msi",
                    UpdateTestSupport.packageNode(signer, "windows-msi", "1.3.0", previous,
                            server.uri("/FlowersForever-1.3.0.msi")))));
        }
        releases.add(UpdateTestSupport.release("1.3.1", "stable", "1.3.0", Map.of("windows-msi",
                UpdateTestSupport.packageNode(signer, "windows-msi", "1.3.1", next,
                        server.uri("/FlowersForever-1.3.1.msi")))));
        server.put("/latest.json", UpdateTestSupport.envelope(signer, UpdateTestSupport.payload(false, releases)));
    }

    private HeadlessUpdateCommand.Request apply(URI feed) {
        return new HeadlessUpdateCommand.Request(HeadlessUpdateCommand.Mode.APPLY, Optional.of(feed),
                Optional.of(keyFile), Optional.of(logFile), Optional.of(root.resolve("evidence-backups")));
    }

    private HeadlessUpdateCommand command(boolean enabled, String installedVersion, TrustedKeys releaseKeys) {
        UpdateSettings settings = UpdateTestSupport.settings(root, installedVersion, null);
        HeadlessUpdateLog log = new HeadlessUpdateLog(Optional.of(logFile),
                new PrintStream(console, true, StandardCharsets.UTF_8), Clock.systemUTC(), "TEST");
        return new HeadlessUpdateCommand(enabled, settings, releaseKeys, Optional.of(UpdatePlatform.WINDOWS_MSI),
                testSettings -> new CountingTransport(new HttpUpdateTransport(Duration.ofSeconds(2),
                        Duration.ofSeconds(5), true, "headless-test")),
                arguments -> {
                    relaunch.set(arguments);
                    return new UpdateTestSupport.RecordingInstaller(events);
                }, log);
    }

    private Path data() {
        return root.resolve("FlowersForever").resolve("data");
    }

    private Path updates() {
        return root.resolve("FlowersForever").resolve("updates");
    }

    private String log() throws IOException {
        return Files.isRegularFile(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
    }

    /** Counts every network attempt so refusal tests can prove nothing was fetched. */
    private final class CountingTransport implements UpdateTransport {
        private final UpdateTransport delegate;

        CountingTransport(UpdateTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public byte[] fetch(URI uri, int maxBytes) throws IOException {
            networkCalls.incrementAndGet();
            return delegate.fetch(uri, maxBytes);
        }

        @Override
        public void download(URI uri, Path target, long maxBytes, java.util.function.LongConsumer progress)
                throws IOException {
            networkCalls.incrementAndGet();
            delegate.download(uri, target, maxBytes, progress);
        }
    }
}

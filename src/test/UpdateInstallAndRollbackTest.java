package com.flowerfarm.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Backup before install, hand-off, first-launch check, and rollback")
class UpdateInstallAndRollbackTest {

    @TempDir
    Path temp;

    private final KeyPair publisher = UpdateTestSupport.devKey();
    private final List<String> events = new ArrayList<>();
    private final List<String> activity = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC);
    private UpdateTestSupport.LocalServer server;
    private UpdateTestSupport.RecordingInstaller installer;

    @BeforeEach
    void start() throws Exception {
        server = new UpdateTestSupport.LocalServer();
        installer = new UpdateTestSupport.RecordingInstaller(events);
        publishBoth(true);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    /** Publishes 1.2.1 (new) and, optionally, the 1.2.0 installer kept for rollback. */
    private void publishBoth(boolean includeCurrent) throws Exception {
        Path published = temp.resolve("published");
        Path next = UpdateTestSupport.fakeInstaller(published, "FlowersForever-1.2.1.msi", "msi 1.2.1");
        Path previous = UpdateTestSupport.fakeInstaller(published, "FlowersForever-1.2.0.msi", "msi 1.2.0");
        server.put("/FlowersForever-1.2.1.msi", Files.readAllBytes(next));
        server.put("/FlowersForever-1.2.0.msi", Files.readAllBytes(previous));
        var releases = new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        if (includeCurrent) {
            releases.add(UpdateTestSupport.release("1.2.0", "stable", null, Map.of("windows-msi",
                    UpdateTestSupport.packageNode(publisher, "windows-msi", "1.2.0", previous,
                            server.uri("/FlowersForever-1.2.0.msi")))));
        }
        releases.add(UpdateTestSupport.release("1.2.1", "stable", "1.2.0", Map.of("windows-msi",
                UpdateTestSupport.packageNode(publisher, "windows-msi", "1.2.1", next,
                        server.uri("/FlowersForever-1.2.1.msi")))));
        server.put("/latest.json", UpdateTestSupport.envelope(publisher, UpdateTestSupport.payload(false, releases)));
    }

    private UpdateSettings settings(String version) {
        return UpdateTestSupport.settings(temp, version, server.uri("/latest.json"));
    }

    private UpdateCoordinator coordinator(UpdateSettings settings) {
        SignedManifestVerifier verifier = new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic()));
        HttpUpdateTransport transport = new HttpUpdateTransport(Duration.ofSeconds(2), Duration.ofSeconds(5), true, "t");
        UpdateStateStore store = new UpdateStateStore(settings.updatesDirectory());
        return new UpdateCoordinator(settings, new UpdateChecker(settings, verifier, transport,
                Optional.of(UpdatePlatform.WINDOWS_MSI), store::failedVersions),
                new PackageDownloader(settings.updatesDirectory(), transport, verifier, PublisherVerifier.none()),
                new DataBackupService(clock), store, installer, Optional.of(UpdatePlatform.WINDOWS_MSI),
                (operation, success, message) -> activity.add(operation + " " + success), clock);
    }

    private UpdateLaunchGuard guard(UpdateSettings settings) {
        return new UpdateLaunchGuard(settings, new UpdateStateStore(settings.updatesDirectory()),
                new DataBackupService(clock), installer, Optional.of(UpdatePlatform.WINDOWS_MSI), clock);
    }

    /** Runs the 1.2.0 side: check, download, close database, back up, hand off. */
    private UpdateCoordinator.InstallOutcome installFrom120() throws Exception {
        UpdateSettings old = settings("1.2.0");
        UpdateTestSupport.farmRecords(old.dataDirectory(), "season-2026");
        UpdateCoordinator coordinator = coordinator(old);
        UpdateCheckResult result = coordinator.check(false);
        assertThat(result.updateAvailable()).isTrue();
        UpdateCoordinator.PreparedUpdate prepared = coordinator.prepare(result, bytes -> { });
        return coordinator.install(prepared, () -> {
            events.add("database closed");
            assertThat(old.backupDirectory()).as("no backup is attempted before the database closes").doesNotExist();
        });
    }

    @Test
    @DisplayName("order is: close database, verified backup, record progress, then start the installer")
    void backupBeforeInstall() throws Exception {
        UpdateCoordinator.InstallOutcome outcome = installFrom120();

        assertThat(outcome.handedOff()).isTrue();
        VerifiedBackup backup = outcome.backup().orElseThrow();
        assertThat(events).containsExactly("database closed", "UPDATE 1.2.1 FlowersForever-1.2.1.msi");
        assertThat(backup.archive()).exists().hasParent(temp.resolve("Backups"));
        new DataBackupService(clock).verifyChecksum(backup.archive());
        UpdateState state = new UpdateStateStore(settings("1.2.0").updatesDirectory()).load();
        assertThat(state.phase).isEqualTo(UpdateState.Phase.INSTALL_HANDED_OFF);
        assertThat(state.backupArchive).isEqualTo(backup.archive().toString());
        assertThat(state.rollbackInstaller).endsWith("FlowersForever-1.2.0.msi");
        assertThat(activity).contains("UPDATE_AVAILABLE true", "UPDATE_DOWNLOADED true", "UPDATE_STARTED true");
    }

    @Test
    @DisplayName("if the backup cannot be verified, nothing is installed")
    void backupFailureStopsInstall() throws Exception {
        UpdateSettings old = settings("1.2.0");
        UpdateCoordinator coordinator = coordinator(old);
        UpdateCoordinator.PreparedUpdate prepared = coordinator.prepare(coordinator.check(true), bytes -> { });
        // No records folder exists, so the backup cannot be made.
        UpdateCoordinator.InstallOutcome outcome = coordinator.install(prepared, () -> events.add("database closed"));

        assertThat(outcome.handedOff()).isFalse();
        assertThat(outcome.databaseClosed()).isTrue();
        assertThat(outcome.message()).contains("stopped before anything was installed", "records were not changed");
        assertThat(events).containsExactly("database closed");
        assertThat(new UpdateStateStore(old.updatesDirectory()).load().phase).isEqualTo(UpdateState.Phase.IDLE);
    }

    @Test
    @DisplayName("an installer altered after verification is refused before the database is closed")
    void tamperedAfterDownload() throws Exception {
        UpdateSettings old = settings("1.2.0");
        UpdateTestSupport.farmRecords(old.dataDirectory(), "x");
        UpdateCoordinator coordinator = coordinator(old);
        UpdateCoordinator.PreparedUpdate prepared = coordinator.prepare(coordinator.check(true), bytes -> { });
        Files.writeString(prepared.next().file(), "msi 6.6.6");

        UpdateCoordinator.InstallOutcome outcome = coordinator.install(prepared, () -> events.add("database closed"));

        assertThat(outcome.handedOff()).isFalse();
        assertThat(outcome.databaseClosed()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("without a checked installer for the current version, the update is not installed")
    void rollbackInstallerRequired() throws Exception {
        publishBoth(false);
        UpdateCoordinator coordinator = coordinator(settings("1.2.0"));

        assertThatThrownBy(() -> coordinator.prepare(coordinator.check(true), bytes -> { }))
                .hasMessageContaining("so it can go back if an update fails");
    }

    @Test
    @DisplayName("first launch passes its health check: update confirmed, notice and activity recorded")
    void healthyFirstLaunch() throws Exception {
        installFrom120();
        UpdateLaunchGuard guard = guard(settings("1.2.1"));
        AtomicReference<String> told = new AtomicReference<>();

        UpdateStartup.Result<String> result = UpdateStartup.start(guard, () -> {
            assertThat(guard.decision()).isEqualTo(UpdateState.Decision.VERIFY_FIRST_LAUNCH);
            guard.confirmHealthy();
            return "context";
        }, () -> events.add("shutdown"), told::set);

        assertThat(result.started()).contains("context");
        assertThat(told.get()).isNull();
        assertThat(guard.takeNotice()).hasValueSatisfying(notice ->
                assertThat(notice).contains("updated to 1.2.1", "records were checked and are safe"));
        List<String> logged = new ArrayList<>();
        guard.flushActivity((operation, success, message) -> logged.add(operation + ": " + message));
        assertThat(logged).singleElement().asString().contains("UPDATE_RESULT", "health check passed");
        assertThat(new UpdateStateStore(settings("1.2.1").updatesDirectory()).load().phase)
                .isEqualTo(UpdateState.Phase.IDLE);
    }

    @Test
    @DisplayName("first launch fails its health check: backup restored, previous version reinstalled, nothing lost")
    void rollbackOnFailedHealthCheck() throws Exception {
        installFrom120();
        UpdateSettings next = settings("1.2.1");
        // The new version starts migrating, writes to the records, then fails its check.
        Files.writeString(next.dataDirectory().resolve(DataBackupService.DATABASE_FILE), "half-migrated by 1.2.1");
        UpdateLaunchGuard guard = guard(next);
        AtomicReference<String> told = new AtomicReference<>();

        UpdateStartup.Result<String> result = UpdateStartup.start(guard, () -> {
            throw new IllegalStateException("context failed",
                    new UpdateHealthCheckFailedException("the database check failed: V22 failed", null));
        }, () -> events.add("shutdown"), told::set);

        assertThat(result.started()).isEmpty();
        assertThat(result.exitCode()).isZero();
        assertThat(events).containsSubsequence("shutdown", "ROLLBACK 1.2.0 FlowersForever-1.2.0.msi");
        assertThat(next.dataDirectory().resolve(DataBackupService.DATABASE_FILE)).hasContent("H2 store season-2026");
        assertThat(next.dataDirectory().resolve("attachments/bed-3.jpg")).hasContent("photo season-2026");
        UpdateState state = new UpdateStateStore(next.updatesDirectory()).load();
        assertThat(state.phase).isEqualTo(UpdateState.Phase.ROLLED_BACK);
        assertThat(state.failedVersions).containsExactly("1.2.1");
        assertThat(Path.of(state.keptAside).resolve(DataBackupService.DATABASE_FILE)).hasContent("half-migrated by 1.2.1");
        assertThat(told.get()).contains("did not start correctly", "went back to 1.2.0", "Nothing was lost");

        // The reinstalled 1.2.0 then starts normally, shows the notice once, and will not offer 1.2.1 again.
        UpdateLaunchGuard restored = guard(settings("1.2.0"));
        assertThat(restored.beforeStart()).isEqualTo(UpdateState.Decision.NORMAL);
        assertThat(restored.takeNotice()).isPresent();
        assertThat(restored.takeNotice()).isEmpty();
        assertThat(coordinator(settings("1.2.0")).check(false).status()).isEqualTo(UpdateCheckResult.Status.UP_TO_DATE);
    }

    @Test
    @DisplayName("a first launch that never finished (crash) rolls back on the next start")
    void crashedFirstLaunchRollsBack() throws Exception {
        installFrom120();
        UpdateSettings next = settings("1.2.1");
        assertThat(guard(next).beforeStart()).isEqualTo(UpdateState.Decision.VERIFY_FIRST_LAUNCH);
        // Process dies here without confirming. Next start:
        UpdateStartup.Result<String> result = UpdateStartup.start(guard(next), () -> "must not start",
                () -> { }, message -> events.add("told"));

        assertThat(result.started()).isEmpty();
        assertThat(events).contains("ROLLBACK 1.2.0 FlowersForever-1.2.0.msi", "told");
    }

    @Test
    @DisplayName("if the backup cannot be restored, the app is blocked and nothing is deleted")
    void restoreFailureBlocks() throws Exception {
        UpdateCoordinator.InstallOutcome outcome = installFrom120();
        Files.write(outcome.backup().orElseThrow().archive(), new byte[] {9}, java.nio.file.StandardOpenOption.APPEND);
        UpdateSettings next = settings("1.2.1");
        UpdateLaunchGuard guard = guard(next);
        guard.beforeStart();

        UpdateLaunchGuard.RollbackOutcome rollback = guard.rollBack("V22 failed");

        assertThat(rollback.installerStarted()).isFalse();
        assertThat(rollback.message()).contains("could not finish", "contact RicheyWorks");
        assertThat(outcome.backup().get().archive()).exists();
        assertThat(guard(next).beforeStart()).isEqualTo(UpdateState.Decision.BLOCKED);
        assertThat(events).noneMatch(event -> event.startsWith("ROLLBACK"));
    }

    @Test
    @DisplayName("a cancelled install leaves the old version running with a plain notice")
    void cancelledInstall() throws Exception {
        installFrom120();
        UpdateSettings old = settings("1.2.0");
        Path exitCode = old.updatesDirectory().resolve("handoff").resolve("msiexec-update-1.2.1.exitcode");
        Files.createDirectories(exitCode.getParent());
        Files.writeString(exitCode, "1602");
        Files.setLastModifiedTime(exitCode, java.nio.file.attribute.FileTime.from(Instant.parse("2026-10-04T15:01:00Z")));

        UpdateLaunchGuard guard = guard(old);
        assertThat(guard.beforeStart()).isEqualTo(UpdateState.Decision.NORMAL);
        assertThat(guard.takeNotice()).hasValueSatisfying(notice ->
                assertThat(notice).contains("was not installed", "still on 1.2.0"));
    }

    @Test
    @DisplayName("an installer that cannot be started is reported and the records stay as they were")
    void launchFailure() throws Exception {
        installer.failLaunch = true;
        UpdateCoordinator.InstallOutcome outcome = installFrom120();

        assertThat(outcome.handedOff()).isFalse();
        assertThat(outcome.message()).contains("could not be started", "were not changed");
        assertThat(new UpdateStateStore(settings("1.2.0").updatesDirectory()).load().phase)
                .isEqualTo(UpdateState.Phase.IDLE);
    }

    @Test
    @DisplayName("a copy without an installer (developer JAR) never tries to install")
    void unsupportedCopy() throws IOException {
        installer.installable = false;
        assertThat(coordinator(settings("1.2.0")).canInstall()).isFalse();
        assertThatThrownBy(() -> PlatformInstaller.unsupported().launch(null, temp, PlatformInstaller.Purpose.UPDATE))
                .hasMessageContaining("cannot install updates by itself");
        assertThat(URI.create("https://x").getScheme()).isEqualTo("https");
    }
}

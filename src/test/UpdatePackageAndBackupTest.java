package com.flowerfarm.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Installer download verification and verified farm-record backups")
class UpdatePackageAndBackupTest {

    @TempDir
    Path temp;

    private final KeyPair publisher = UpdateTestSupport.devKey();
    private final SignedManifestVerifier verifier = new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic()));
    private final DataBackupService backups = new DataBackupService(
            Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC));
    private UpdateTestSupport.LocalServer server;

    @BeforeEach
    void start() throws IOException {
        server = new UpdateTestSupport.LocalServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private PackageDownloader downloader(PublisherVerifier publisherCheck) {
        return new PackageDownloader(temp.resolve("updates"),
                new HttpUpdateTransport(Duration.ofSeconds(2), Duration.ofSeconds(5), true, "test"),
                verifier, publisherCheck);
    }

    private UpdateManifest.PackageInfo info(Path signedFile, URI url) throws Exception {
        ObjectNode node = UpdateTestSupport.packageNode(publisher, "windows-msi", "1.2.1", signedFile, url);
        return new UpdateManifest.PackageInfo("windows-msi", node.get("file").asText(), url, node.get("size").asLong(),
                node.get("sha256").asText(), node.get("keyId").asText(), node.get("signature").asText());
    }

    @Test
    @DisplayName("a matching download is kept, re-verifiable, and found again from the cache")
    void goodDownload() throws Exception {
        Path published = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-1.2.1.msi", "msi bytes");
        server.put("/FlowersForever-1.2.1.msi", Files.readAllBytes(published));
        UpdateManifest.PackageInfo info = info(published, server.uri("/FlowersForever-1.2.1.msi"));

        VerifiedPackage pkg = downloader(PublisherVerifier.none()).obtain(UpdatePlatform.WINDOWS_MSI,
                AppVersion.parse("1.2.1"), info, bytes -> { });

        assertThat(pkg.file()).hasContent("msi bytes");
        assertThat(pkg.sha256()).isEqualTo(UpdateSignatures.sha256Hex(published));
        assertThat(downloader(PublisherVerifier.none()).cached(UpdatePlatform.WINDOWS_MSI, AppVersion.parse("1.2.1")))
                .hasValueSatisfying(cached -> assertThat(cached.file()).isEqualTo(pkg.file()));
        assertThat(pkg.file().resolveSibling("FlowersForever-1.2.1.msi.part")).doesNotExist();
    }

    @Test
    @DisplayName("bad checksum: different bytes of the same size are rejected and deleted")
    void badChecksumRejected() throws Exception {
        Path published = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-1.2.1.msi", "msi bytes");
        server.put("/FlowersForever-1.2.1.msi", "MSI BYTES".getBytes(StandardCharsets.UTF_8));
        UpdateManifest.PackageInfo info = info(published, server.uri("/FlowersForever-1.2.1.msi"));

        assertThatThrownBy(() -> downloader(PublisherVerifier.none()).obtain(UpdatePlatform.WINDOWS_MSI,
                AppVersion.parse("1.2.1"), info, bytes -> { }))
                .isInstanceOf(UpdateVerificationException.class)
                .hasMessageContaining("checksum does not match")
                .hasMessageContaining("Nothing was installed");
        try (var files = Files.list(temp.resolve("updates/packages/1.2.1"))) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("a larger download than published is cut off and rejected")
    void oversizeRejected() throws Exception {
        Path published = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-1.2.1.msi", "msi bytes");
        server.put("/FlowersForever-1.2.1.msi", "msi bytes plus a hidden payload".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> downloader(PublisherVerifier.none()).obtain(UpdatePlatform.WINDOWS_MSI,
                AppVersion.parse("1.2.1"), info(published, server.uri("/FlowersForever-1.2.1.msi")), bytes -> { }))
                .isInstanceOf(UpdateVerificationException.class);
    }

    @Test
    @DisplayName("bad package signature: rejected before anything is downloaded")
    void badPackageSignatureRejected() throws Exception {
        Path published = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-1.2.1.msi", "msi bytes");
        server.put("/FlowersForever-1.2.1.msi", Files.readAllBytes(published));
        UpdateManifest.PackageInfo good = info(published, server.uri("/FlowersForever-1.2.1.msi"));
        KeyPair attacker = UpdateTestSupport.devKey();
        ObjectNode forged = UpdateTestSupport.packageNode(attacker, "windows-msi", "1.2.1", published, good.url());
        UpdateManifest.PackageInfo bad = new UpdateManifest.PackageInfo("windows-msi", good.fileName(), good.url(),
                good.size(), good.sha256(), good.keyId(), forged.get("signature").asText());

        assertThatThrownBy(() -> downloader(PublisherVerifier.none()).obtain(UpdatePlatform.WINDOWS_MSI,
                AppVersion.parse("1.2.1"), bad, bytes -> { }))
                .hasMessageContaining("valid publisher signature");
        assertThat(server.requests.get()).isZero();
    }

    @Test
    @DisplayName("an operating-system publisher check (future Authenticode) can veto a package")
    void publisherVeto() throws Exception {
        Path published = UpdateTestSupport.fakeInstaller(temp.resolve("published"), "FlowersForever-1.2.1.msi", "msi bytes");
        server.put("/FlowersForever-1.2.1.msi", Files.readAllBytes(published));

        assertThatThrownBy(() -> downloader((file, platform) -> {
            throw new UpdateVerificationException("not signed by RicheyWorks");
        }).obtain(UpdatePlatform.WINDOWS_MSI, AppVersion.parse("1.2.1"),
                info(published, server.uri("/FlowersForever-1.2.1.msi")), bytes -> { }))
                .hasMessage("not signed by RicheyWorks");
        assertThatThrownBy(() -> new AuthenticodePublisherVerifier(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("backup: one top-level data folder, sha256sum sidecar, every file checked")
    void backupMatchesScriptFormat() throws Exception {
        Path data = UpdateTestSupport.farmRecords(temp.resolve("FlowersForever/data"), "v1");

        VerifiedBackup backup = backups.backup(data, temp.resolve("Backups"), "before-1.2.1");

        assertThat(backup.archive().getFileName().toString()).isEqualTo("FlowersForever-before-1.2.1-20261004-150000.zip");
        assertThat(backup.fileCount()).isEqualTo(2);
        assertThat(Files.readString(backup.checksumFile()).trim())
                .isEqualTo(backup.sha256() + "  " + backup.archive().getFileName());
        try (ZipFile zip = new ZipFile(backup.archive().toFile())) {
            assertThat(zip.getEntry("data/flowerfarm.mv.db")).isNotNull();
            assertThat(zip.getEntry("data/attachments/bed-3.jpg")).isNotNull();
        }
    }

    @Test
    @DisplayName("backup refuses while the database is open, and never inside the records folder")
    void backupRefusesOpenDatabase() throws Exception {
        Path data = UpdateTestSupport.farmRecords(temp.resolve("FlowersForever/data"), "v1");
        try (FileChannel channel = FileChannel.open(data.resolve(DataBackupService.DATABASE_FILE),
                StandardOpenOption.READ, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> backups.backup(data, temp.resolve("Backups"), "x"))
                    .hasMessageContaining("still using the farm records");
        }
        assertThatThrownBy(() -> backups.backup(data, data.resolve("backups"), "x"))
                .hasMessageContaining("outside the records folder");
    }

    @Test
    @DisplayName("restore checks the checksum and keeps the current folder aside")
    void restoreKeepsCurrentFolder() throws Exception {
        Path data = UpdateTestSupport.farmRecords(temp.resolve("FlowersForever/data"), "before");
        VerifiedBackup backup = backups.backup(data, temp.resolve("Backups"), "before-1.2.1");
        Files.writeString(data.resolve(DataBackupService.DATABASE_FILE), "half-migrated by 1.2.1");

        Path keptAside = backups.restore(backup.archive(), data);

        assertThat(data.resolve(DataBackupService.DATABASE_FILE)).hasContent("H2 store before");
        assertThat(keptAside.resolve(DataBackupService.DATABASE_FILE)).hasContent("half-migrated by 1.2.1");
        assertThat(keptAside.getFileName().toString()).startsWith("data.before-restore-");
    }

    @Test
    @DisplayName("a damaged backup is never restored")
    void damagedBackupRejected() throws Exception {
        Path data = UpdateTestSupport.farmRecords(temp.resolve("FlowersForever/data"), "before");
        VerifiedBackup backup = backups.backup(data, temp.resolve("Backups"), "before-1.2.1");
        Files.write(backup.archive(), new byte[] {1, 2, 3}, StandardOpenOption.APPEND);

        assertThatThrownBy(() -> backups.restore(backup.archive(), data)).hasMessageContaining("checksum does not match");
        assertThat(data.resolve(DataBackupService.DATABASE_FILE)).hasContent("H2 store before");
    }
}

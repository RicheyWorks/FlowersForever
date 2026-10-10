package com.flowerfarm.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.util.PathNames;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * Downloads installers into {@code <updates>/packages/<version>/} and only
 * keeps a file once its size, SHA-256, publisher signature, and optional OS
 * publisher check all pass. A failed file is deleted. Each kept installer has
 * a {@code .package.json} sidecar holding the signed details so it can be
 * re-verified later, for example when it is needed for a rollback.
 */
public final class PackageDownloader {

    private static final String SIDECAR = ".package.json";

    private final Path packagesDirectory;
    private final UpdateTransport transport;
    private final SignedManifestVerifier verifier;
    private final PublisherVerifier publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public PackageDownloader(Path updatesDirectory, UpdateTransport transport,
                             SignedManifestVerifier verifier, PublisherVerifier publisher) {
        this.packagesDirectory = updatesDirectory.resolve("packages");
        this.transport = transport;
        this.verifier = verifier;
        this.publisher = publisher;
    }

    public VerifiedPackage obtain(UpdatePlatform platform, AppVersion version,
                                  UpdateManifest.PackageInfo info, LongConsumer progress)
            throws UpdateVerificationException {
        if (!verifier.packageSignatureValid(info, version)) {
            throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                    "The installer listed for " + version + " does not carry a valid publisher signature."));
        }
        Path directory = packagesDirectory.resolve(version.toString());
        Path target = directory.resolve(info.fileName());
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(target)) {
                try {
                    return verifyFile(platform, version, info, target);
                } catch (UpdateVerificationException stale) {
                    Files.deleteIfExists(target);
                }
            }
            Path partial = directory.resolve(info.fileName() + ".part");
            Files.deleteIfExists(partial);
            try {
                transport.download(info.url(), partial, info.size(), progress);
                VerifiedPackage verified = verifyFile(platform, version, info, partial);
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                writeSidecar(target, info);
                return new VerifiedPackage(platform, version, target, verified.sha256(), verified.size());
            } finally {
                Files.deleteIfExists(partial);
            }
        } catch (IOException offline) {
            throw new UpdateVerificationException(
                    "The update could not be downloaded right now. Nothing was installed; "
                            + "FlowersForever will try again later.", offline);
        }
    }

    /** Re-checks an installer already on disk against signed details. */
    public VerifiedPackage verifyFile(UpdatePlatform platform, AppVersion version,
                                      UpdateManifest.PackageInfo info, Path file)
            throws UpdateVerificationException {
        try {
            long size = Files.size(file);
            if (size != info.size()) {
                throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                        "The downloaded file is a different size than the publisher listed."));
            }
            String sha256 = UpdateSignatures.sha256Hex(file);
            if (!MessageDigest.isEqual(sha256.getBytes(StandardCharsets.US_ASCII),
                    info.sha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                        "The downloaded file's checksum does not match the publisher's checksum."));
            }
        } catch (IOException unreadable) {
            throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                    "The downloaded file could not be read."), unreadable);
        }
        if (!verifier.packageSignatureValid(info, version)) {
            throw new UpdateVerificationException(UpdateMessages.downloadRejected(
                    "The installer does not carry a valid publisher signature."));
        }
        publisher.verify(file, platform);
        return new VerifiedPackage(platform, version, file, info.sha256(), info.size());
    }

    /** A previously downloaded installer for {@code version}, re-verified from its signed sidecar. */
    public Optional<VerifiedPackage> cached(UpdatePlatform platform, AppVersion version) {
        Path directory = packagesDirectory.resolve(version.toString());
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (var files = Files.list(directory)) {
            for (Path sidecar : files.filter(path -> PathNames.fileName(path).endsWith(SIDECAR)).toList()) {
                Optional<UpdateManifest.PackageInfo> info = readSidecar(sidecar);
                if (info.isEmpty() || !platform.key().equals(info.get().platformKey())) {
                    continue;
                }
                Path file = directory.resolve(info.get().fileName());
                if (Files.isRegularFile(file)) {
                    try {
                        return Optional.of(verifyFile(platform, version, info.get(), file));
                    } catch (UpdateVerificationException damaged) {
                        // Ignore damaged cache entries; the caller will report no rollback installer.
                    }
                }
            }
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private void writeSidecar(Path target, UpdateManifest.PackageInfo info) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("platform", info.platformKey());
        node.put("file", info.fileName());
        node.put("url", info.url().toString());
        node.put("size", info.size());
        node.put("sha256", info.sha256());
        node.put("keyId", info.keyId());
        node.put("signature", info.signature());
        Path fileName = target.getFileName();
        Path parent = target.getParent();
        if (fileName == null || parent == null) {
            throw new IOException("Installer path has no parent: " + target);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                parent.resolve(fileName + SIDECAR).toFile(), node);
    }

    private Optional<UpdateManifest.PackageInfo> readSidecar(Path sidecar) {
        try {
            var node = mapper.readTree(sidecar.toFile());
            return Optional.of(new UpdateManifest.PackageInfo(node.path("platform").asText(),
                    node.path("file").asText(), URI.create(node.path("url").asText()),
                    node.path("size").asLong(), node.path("sha256").asText(),
                    node.path("keyId").asText(), node.path("signature").asText()));
        } catch (IOException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}

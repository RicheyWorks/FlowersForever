package com.flowerfarm.update.release;

import com.flowerfarm.update.SignedManifestVerifier;
import com.flowerfarm.update.TrustedKeys;
import com.flowerfarm.update.UpdateManifest;
import com.flowerfarm.update.UpdatePlatform;
import com.flowerfarm.update.UpdateSignatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Release tool: dev key, signed manifest, staged rollout, verification")
class UpdateReleaseToolTest {

    @TempDir
    Path temp;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private int run(Map<String, String> environment, String... args) {
        output.reset();
        return new UpdateReleaseTool(new PrintStream(output, true, StandardCharsets.UTF_8), environment::get,
                Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC)).run(args);
    }

    private String out() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("keygen refuses to write a private key inside a git checkout")
    void keygenRefusesRepository() throws Exception {
        Path repo = Files.createDirectories(temp.resolve("FlowersForever"));
        Files.createDirectories(repo.resolve(".git"));

        assertThat(run(Map.of(), "keygen", "--out", repo.resolve("keys/update.key").toString())).isEqualTo(1);
        assertThat(out()).contains("Refusing to write a private key inside the git checkout");
        assertThat(repo.resolve("keys/update.key")).doesNotExist();
    }

    @Test
    @DisplayName("full flow: keygen, add-release, promote channel, sign, verify; private key never published")
    void fullFlow() throws Exception {
        Path key = temp.resolve("secrets/flowersforever-update.key");
        assertThat(run(Map.of(), "keygen", "--out", key.toString())).isZero();
        assertThat(out()).contains("Key id:", "trusted-update-keys.properties", "richeyworks-2026-10-04=");
        Properties keyFile = new Properties();
        keyFile.load(new java.io.StringReader(Files.readString(key)));
        String publicKey = keyFile.getProperty("public");
        String privateKey = keyFile.getProperty("private");

        Path msi = Files.writeString(temp.resolve("FlowersForever-1.2.1.msi"), "msi 1.2.1");
        Path unsigned = temp.resolve("hosting/manifest.unsigned.json");
        assertThat(run(Map.of(), "add-release", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--version", "1.2.1", "--channels", "owner", "--minimum-version", "1.2.0",
                "--notes", "Clearer invoices.",
                "--package", "windows-msi=" + msi + "@https://github.com/RicheyWorks/FlowersForever-updates/releases/download/v1.2.1/FlowersForever-1.2.1.msi"))
                .as(out()).isZero();
        assertThat(run(Map.of(), "channels", "--manifest", unsigned.toString(), "--version", "1.2.1",
                "--channels", "owner,early")).isZero();
        Path signed = temp.resolve("hosting/latest.json");
        assertThat(run(Map.of("FF_UPDATE_KEY", Files.readString(key)), "sign", "--manifest", unsigned.toString(),
                "--private-key", "env:FF_UPDATE_KEY", "--out", signed.toString())).as(out()).isZero();

        assertThat(run(Map.of(), "verify", "--signed", signed.toString(), "--public-key", publicKey,
                "--package", "windows-msi=" + msi)).as(out()).isZero();
        assertThat(out()).contains("Manifest signature OK", "signature OK", "matches the manifest");
        assertThat(Files.readString(signed)).doesNotContain(privateKey);
        assertThat(Files.readString(unsigned)).doesNotContain(privateKey);

        UpdateManifest manifest = new SignedManifestVerifier(TrustedKeys.of(UpdateSignatures.decodePublicKey(publicKey)))
                .verify(Files.readAllBytes(signed));
        assertThat(manifest.releases()).singleElement().satisfies(release -> {
            assertThat(release.channels()).containsExactly("owner", "early");
            assertThat(release.packageFor(UpdatePlatform.WINDOWS_MSI).orElseThrow().sha256())
                    .isEqualTo(UpdateSignatures.sha256Hex(msi));
        });

        // Pause switch round trip.
        assertThat(run(Map.of(), "pause", "--manifest", unsigned.toString(), "--message", "Fixing reports")).isZero();
        assertThat(run(Map.of(), "sign", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--out", signed.toString())).isZero();
        assertThat(new SignedManifestVerifier(TrustedKeys.of(UpdateSignatures.decodePublicKey(publicKey)))
                .verify(Files.readAllBytes(signed)).paused()).isTrue();
    }

    @Test
    @DisplayName("verify fails for a tampered installer or the wrong publisher key")
    void verifyCatchesProblems() throws Exception {
        Path key = temp.resolve("secrets/k.key");
        run(Map.of(), "keygen", "--out", key.toString());
        Path otherKey = temp.resolve("secrets/other.key");
        run(Map.of(), "keygen", "--out", otherKey.toString());
        Properties other = new Properties();
        other.load(new java.io.StringReader(Files.readString(otherKey)));
        Path msi = Files.writeString(temp.resolve("FlowersForever-1.2.1.msi"), "msi 1.2.1");
        Path unsigned = temp.resolve("m.json");
        Path signed = temp.resolve("latest.json");
        run(Map.of(), "add-release", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--version", "1.2.1", "--channels", "stable", "--package", "windows-msi=" + msi + "@https://x.example/a.msi");
        run(Map.of(), "sign", "--manifest", unsigned.toString(), "--private-key", key.toString(), "--out", signed.toString());
        Files.writeString(msi, "msi 1.2.1 tampered");

        assertThat(run(Map.of(), "verify", "--signed", signed.toString(), "--public-key", other.getProperty("public")))
                .isEqualTo(1);
        Properties mine = new Properties();
        mine.load(new java.io.StringReader(Files.readString(key)));
        assertThat(run(Map.of(), "verify", "--signed", signed.toString(), "--public-key", mine.getProperty("public"),
                "--package", "windows-msi=" + msi)).isEqualTo(1);
        assertThat(out()).contains("DOES NOT match");
        assertThat(run(Map.of(), "add-release", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--version", "1.2.2", "--channels", "stable", "--package", "windows-msi=" + msi + "@http://x.example/a.msi"))
                .isEqualTo(1);
        assertThat(run(Map.of(), "nonsense")).isEqualTo(2);
    }
}

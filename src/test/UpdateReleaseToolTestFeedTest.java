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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code --test-feed loopback}: local installer addresses, throwaway keys only. */
class UpdateReleaseToolTestFeedTest {

    @TempDir
    Path temp;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private int run(TrustedKeys releaseKeys, String... args) {
        output.reset();
        return new UpdateReleaseTool(new PrintStream(output, true, StandardCharsets.UTF_8), Map.<String, String>of()::get,
                Clock.fixed(Instant.parse("2026-10-08T19:00:00Z"), ZoneOffset.UTC), releaseKeys).run(args);
    }

    private String out() {
        return output.toString(StandardCharsets.UTF_8);
    }

    private PublicKey keygen(Path key) throws Exception {
        assertThat(run(TrustedKeys.none(), "keygen", "--out", key.toString())).isZero();
        Properties file = new Properties();
        file.load(new StringReader(Files.readString(key)));
        return UpdateSignatures.decodePublicKey(file.getProperty("public"));
    }

    @Test
    @DisplayName("a loopback test feed is built and signed with a throwaway key, and verifies under that key")
    void buildsLoopbackTestFeed() throws Exception {
        Path key = temp.resolve("test-keys/throwaway.key");
        PublicKey publicKey = keygen(key);
        Path msi = Files.writeString(temp.resolve("FlowersForever-1.3.1.msi"), "msi 1.3.1");
        Path unsigned = temp.resolve("feed/manifest.unsigned.json");
        Path signed = temp.resolve("feed/latest.json");

        assertThat(run(TrustedKeys.none(), "add-release", "--manifest", unsigned.toString(), "--private-key",
                key.toString(), "--version", "1.3.1", "--channels", "stable", "--notes", "Release check build.",
                "--package", "windows-msi=" + msi + "@http://127.0.0.1:8765/FlowersForever-1.3.1.msi",
                "--test-feed", "loopback")).as(out()).isZero();
        assertThat(run(TrustedKeys.none(), "sign", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--out", signed.toString(), "--test-feed", "loopback")).as(out()).isZero();

        UpdateManifest manifest = new SignedManifestVerifier(TrustedKeys.of(publicKey)).verify(Files.readAllBytes(signed));
        assertThat(manifest.releases()).singleElement().satisfies(release -> assertThat(
                release.packageFor(UpdatePlatform.WINDOWS_MSI).orElseThrow().url().toString())
                .isEqualTo("http://127.0.0.1:8765/FlowersForever-1.3.1.msi"));
    }

    @Test
    @DisplayName("without --test-feed, http installer addresses are refused as before, also when signing")
    void httpNeedsTestFeed() throws Exception {
        Path key = temp.resolve("test-keys/throwaway.key");
        keygen(key);
        Path msi = Files.writeString(temp.resolve("FlowersForever-1.3.1.msi"), "msi 1.3.1");
        Path unsigned = temp.resolve("feed/manifest.unsigned.json");

        assertThat(run(TrustedKeys.none(), "add-release", "--manifest", unsigned.toString(), "--private-key",
                key.toString(), "--version", "1.3.1", "--channels", "stable", "--package",
                "windows-msi=" + msi + "@http://127.0.0.1:8765/FlowersForever-1.3.1.msi")).isEqualTo(1);
        assertThat(out()).contains("Package URLs must use https");

        assertThat(run(TrustedKeys.none(), "add-release", "--manifest", unsigned.toString(), "--private-key",
                key.toString(), "--version", "1.3.1", "--channels", "stable", "--package",
                "windows-msi=" + msi + "@http://127.0.0.1:8765/FlowersForever-1.3.1.msi",
                "--test-feed", "loopback")).isZero();
        assertThat(run(TrustedKeys.none(), "sign", "--manifest", unsigned.toString(), "--private-key", key.toString(),
                "--out", temp.resolve("feed/latest.json").toString())).isEqualTo(1);
        assertThat(out()).contains("Package URLs must use https");
        assertThat(temp.resolve("feed/latest.json")).doesNotExist();
    }

    @Test
    @DisplayName("a test feed refuses installer addresses that are not on this computer")
    void testFeedRefusesRemoteAddresses() throws Exception {
        Path key = temp.resolve("test-keys/throwaway.key");
        keygen(key);
        Path msi = Files.writeString(temp.resolve("FlowersForever-1.3.1.msi"), "msi 1.3.1");

        assertThat(run(TrustedKeys.none(), "add-release", "--manifest", temp.resolve("m.json").toString(),
                "--private-key", key.toString(), "--version", "1.3.1", "--channels", "stable", "--package",
                "windows-msi=" + msi + "@https://github.com/RicheyWorks/FlowersForever-updates/releases/download/"
                        + "v1.3.1/FlowersForever-1.3.1.msi", "--test-feed", "loopback")).isEqualTo(1);
        assertThat(out()).contains("must be on this computer");
        assertThat(run(TrustedKeys.none(), "add-release", "--manifest", temp.resolve("m.json").toString(),
                "--private-key", key.toString(), "--version", "1.3.1", "--channels", "stable", "--package",
                "windows-msi=" + msi + "@http://127.0.0.1:8765/x.msi", "--test-feed", "remote")).isEqualTo(1);
        assertThat(out()).contains("only accepts the value loopback");
    }

    @Test
    @DisplayName("a built-in release key can never sign a test feed")
    void releaseKeyCannotSignTestFeed() throws Exception {
        Path key = temp.resolve("test-keys/stand-in-release.key");
        PublicKey standInReleaseKey = keygen(key);
        Path msi = Files.writeString(temp.resolve("FlowersForever-1.3.1.msi"), "msi 1.3.1");

        assertThat(run(TrustedKeys.of(standInReleaseKey), "add-release", "--manifest",
                temp.resolve("m.json").toString(), "--private-key", key.toString(), "--version", "1.3.1",
                "--channels", "stable", "--package", "windows-msi=" + msi + "@http://127.0.0.1:8765/x.msi",
                "--test-feed", "loopback")).isEqualTo(1);
        assertThat(out()).contains("never a built-in release key");
        assertThat(temp.resolve("m.json")).doesNotExist();
    }
}

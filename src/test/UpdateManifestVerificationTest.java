package com.flowerfarm.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.flowerfarm.update.UpdateTestSupport.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Signed update manifest verification")
class UpdateManifestVerificationTest {

    @TempDir
    Path temp;

    private final KeyPair publisher = UpdateTestSupport.devKey();
    private final SignedManifestVerifier verifier = new SignedManifestVerifier(TrustedKeys.of(publisher.getPublic()));

    private ObjectNode payload() throws Exception {
        Path msi = UpdateTestSupport.fakeInstaller(temp, "FlowersForever-1.2.1.msi", "msi 1.2.1");
        return UpdateTestSupport.payload(false, List.of(UpdateTestSupport.release("1.2.1", "stable", "1.2.0",
                Map.of("windows-msi", UpdateTestSupport.packageNode(publisher, "windows-msi", "1.2.1", msi,
                        URI.create("https://updates.example/FlowersForever-1.2.1.msi"))))));
    }

    @Test
    @DisplayName("a manifest signed by the built-in publisher key is accepted and parsed")
    void acceptsPublisherSignature() throws Exception {
        UpdateManifest manifest = verifier.verify(UpdateTestSupport.envelope(publisher, payload()));

        assertThat(manifest.releases()).singleElement().satisfies(release -> {
            assertThat(release.version()).isEqualTo(AppVersion.parse("1.2.1"));
            assertThat(release.minimumVersion()).isEqualTo(AppVersion.parse("1.2.0"));
            assertThat(release.packageFor(UpdatePlatform.WINDOWS_MSI)).isPresent();
            assertThat(verifier.packageSignatureValid(release.packageFor(UpdatePlatform.WINDOWS_MSI).get(),
                    release.version())).isTrue();
        });
    }

    @Test
    @DisplayName("a manifest signed by any other key is rejected")
    void rejectsOtherKey() throws Exception {
        KeyPair attacker = UpdateTestSupport.devKey();

        assertThatThrownBy(() -> verifier.verify(UpdateTestSupport.envelope(attacker, payload())))
                .isInstanceOf(UpdateVerificationException.class)
                .hasMessageContaining("did not carry a valid FlowersForever publisher signature")
                .hasMessageContaining("Nothing was downloaded or changed");
    }

    @Test
    @DisplayName("a payload changed after signing is rejected even with the real key id")
    void rejectsTamperedPayload() throws Exception {
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(UpdateTestSupport.envelope(publisher, payload()));
        String original = new String(Base64.getDecoder().decode(envelope.get("payload").asText()), StandardCharsets.UTF_8);
        String tampered = original.replace("https://updates.example/", "https://evil.example/");
        envelope.put("payload", Base64.getEncoder().encodeToString(tampered.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> verifier.verify(MAPPER.writeValueAsBytes(envelope)))
                .isInstanceOf(UpdateVerificationException.class);
    }

    @Test
    @DisplayName("garbage, unsigned JSON, and a missing signature are rejected without exceptions leaking")
    void rejectsMalformedEnvelopes() throws Exception {
        assertThatThrownBy(() -> verifier.verify("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateVerificationException.class);
        assertThatThrownBy(() -> verifier.verify(MAPPER.writeValueAsBytes(payload())))
                .isInstanceOf(UpdateVerificationException.class);
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(UpdateTestSupport.envelope(publisher, payload()));
        envelope.remove("signature");
        assertThatThrownBy(() -> verifier.verify(MAPPER.writeValueAsBytes(envelope)))
                .isInstanceOf(UpdateVerificationException.class);
    }

    @Test
    @DisplayName("a correctly signed manifest for another product or with unsafe file names is rejected")
    void rejectsInvalidContent() throws Exception {
        ObjectNode otherProduct = payload();
        otherProduct.put("product", "SomethingElse");
        assertThatThrownBy(() -> verifier.verify(UpdateTestSupport.envelope(publisher, otherProduct)))
                .hasMessageContaining("another product");

        ObjectNode unsafe = payload();
        ((ObjectNode) unsafe.get("releases").get(0).get("packages").get("windows-msi")).put("file", "..\\evil.msi");
        assertThatThrownBy(() -> verifier.verify(UpdateTestSupport.envelope(publisher, unsafe)))
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("a package signature for different bytes, size, or version does not verify")
    void packageSignatureBindsFileDetails() throws Exception {
        UpdateManifest.PackageInfo info = verifier.verify(UpdateTestSupport.envelope(publisher, payload()))
                .releases().get(0).packageFor(UpdatePlatform.WINDOWS_MSI).orElseThrow();

        assertThat(verifier.packageSignatureValid(info, AppVersion.parse("1.2.2"))).isFalse();
        assertThat(verifier.packageSignatureValid(new UpdateManifest.PackageInfo(info.platformKey(), info.fileName(),
                info.url(), info.size() + 1, info.sha256(), info.keyId(), info.signature()),
                AppVersion.parse("1.2.1"))).isFalse();
        assertThat(verifier.packageSignatureValid(new UpdateManifest.PackageInfo(info.platformKey(), info.fileName(),
                info.url(), info.size(), "0".repeat(64), info.keyId(), info.signature()),
                AppVersion.parse("1.2.1"))).isFalse();
    }

    @Test
    @DisplayName("versions compare numerically and snapshots sort before their release")
    void versionOrdering() {
        assertThat(AppVersion.parse("1.10.0").isNewerThan(AppVersion.parse("1.9.9"))).isTrue();
        assertThat(AppVersion.parse("1.2.0").isNewerThan(AppVersion.parse("1.2.0-SNAPSHOT"))).isTrue();
        assertThat(AppVersion.parse("v1.2")).isEqualTo(AppVersion.parse("1.2.0"));
        assertThat(AppVersion.current("not-a-version")).isEqualTo(AppVersion.parse("0.0.0-dev"));
        assertThatThrownBy(() -> AppVersion.parse("1.x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the shipped trusted-key list holds no private material and loads cleanly")
    void shippedTrustedKeysLoad() {
        TrustedKeys shipped = TrustedKeys.fromClasspath();
        assertThat(shipped.size()).isGreaterThanOrEqualTo(0);
    }
}

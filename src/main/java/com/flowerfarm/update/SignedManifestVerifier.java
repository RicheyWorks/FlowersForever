package com.flowerfarm.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Optional;

/**
 * Checks the publisher signature on a signed manifest envelope and only then
 * parses the payload. The envelope is:
 * <pre>{"format":"flowersforever-signed-manifest-v1","keyId":"…","payload":"base64","signature":"base64"}</pre>
 * The signature covers the exact decoded payload bytes, so no JSON
 * canonicalisation is needed.
 */
public final class SignedManifestVerifier {

    public static final String FORMAT = "flowersforever-signed-manifest-v1";
    private static final String REJECTED =
            "The update information did not carry a valid FlowersForever publisher signature, "
                    + "so it was ignored. Nothing was downloaded or changed.";

    private final TrustedKeys trustedKeys;
    private final ObjectMapper mapper;

    public SignedManifestVerifier(TrustedKeys trustedKeys) {
        this(trustedKeys, new ObjectMapper());
    }

    SignedManifestVerifier(TrustedKeys trustedKeys, ObjectMapper mapper) {
        this.trustedKeys = trustedKeys;
        this.mapper = mapper;
    }

    public UpdateManifest verify(byte[] envelopeBytes) throws UpdateVerificationException {
        JsonNode envelope;
        try {
            envelope = mapper.readTree(envelopeBytes);
        } catch (IOException unreadable) {
            throw new UpdateVerificationException(REJECTED, unreadable);
        }
        if (envelope == null || !FORMAT.equals(envelope.path("format").asText())) {
            throw new UpdateVerificationException(REJECTED);
        }
        Optional<PublicKey> key = trustedKeys.find(envelope.path("keyId").asText(null));
        if (key.isEmpty()) {
            throw new UpdateVerificationException(REJECTED);
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = UpdateSignatures.decodeBase64(envelope.path("payload").asText(null), "payload");
            signature = UpdateSignatures.decodeBase64(envelope.path("signature").asText(null),
                    "signature");
        } catch (GeneralSecurityException malformed) {
            throw new UpdateVerificationException(REJECTED, malformed);
        }
        if (!UpdateSignatures.verify(key.get(), payload, signature)) {
            throw new UpdateVerificationException(REJECTED);
        }
        return UpdateManifest.parse(payload, mapper);
    }

    /** Verifies one installer's publisher signature against the trusted keys. */
    public boolean packageSignatureValid(UpdateManifest.PackageInfo info, AppVersion version) {
        Optional<PublicKey> key = trustedKeys.find(info.keyId());
        if (key.isEmpty()) {
            return false;
        }
        byte[] signature;
        try {
            signature = UpdateSignatures.decodeBase64(info.signature(), "package signature");
        } catch (GeneralSecurityException malformed) {
            return false;
        }
        return UpdateSignatures.verify(key.get(), UpdateSignatures.packageStatement(
                info.platformKey(), version.toString(), info.fileName(), info.size(), info.sha256()),
                signature);
    }

    public boolean hasTrustedKeys() {
        return !trustedKeys.isEmpty();
    }
}

package com.flowerfarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Ed25519 publisher signatures and SHA-256 helpers shared by the app and the
 * release tool. The JDK provides Ed25519 since Java 15, so no extra library is needed.
 */
public final class UpdateSignatures {

    public static final String ALGORITHM = "Ed25519";
    private static final String STATEMENT_HEADER = "FlowersForever package v1";

    private UpdateSignatures() {
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    }

    public static byte[] sign(PrivateKey key, byte[] message) throws GeneralSecurityException {
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(message);
        return signer.sign();
    }

    public static boolean verify(PublicKey key, byte[] message, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        byte[] encoded = decodeBase64(base64, "public key");
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static PrivateKey decodePrivateKey(String base64) throws GeneralSecurityException {
        byte[] encoded = decodeBase64(base64, "private key");
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encodePrivateKey(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Short stable identifier: the first 16 hex characters of SHA-256 over the encoded public key. */
    public static String keyId(PublicKey key) {
        return sha256Hex(key.getEncoded()).substring(0, 16);
    }

    /**
     * Canonical text a package signature covers. Binding the platform, version,
     * file name, size, and digest means a signature for one file cannot be
     * reused for another file or version.
     */
    public static byte[] packageStatement(String platformKey, String version, String fileName,
                                          long size, String sha256) {
        String statement = STATEMENT_HEADER + "\n"
                + "platform=" + platformKey + "\n"
                + "version=" + version + "\n"
                + "file=" + fileName + "\n"
                + "size=" + size + "\n"
                + "sha256=" + sha256.toLowerCase(Locale.ROOT) + "\n";
        return statement.getBytes(StandardCharsets.UTF_8);
    }

    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }

    static byte[] decodeBase64(String value, String label) throws GeneralSecurityException {
        if (value == null || value.isBlank()) {
            throw new GeneralSecurityException("Missing " + label);
        }
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new GeneralSecurityException("The " + label + " is not valid base64", invalid);
        }
    }
}

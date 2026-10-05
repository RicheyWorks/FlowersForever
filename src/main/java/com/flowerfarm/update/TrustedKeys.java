package com.flowerfarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Publisher public keys built into the app. Only manifests signed by one of
 * these keys are accepted. Keys are listed in
 * {@code classpath:update/trusted-update-keys.properties}; the property name
 * is a label, the value is the base64 X.509 public key. Listing more than one
 * key allows a planned key rotation.
 */
public final class TrustedKeys {

    public static final String RESOURCE = "update/trusted-update-keys.properties";

    private final Map<String, PublicKey> byKeyId;

    private TrustedKeys(Map<String, PublicKey> byKeyId) {
        this.byKeyId = Collections.unmodifiableMap(new LinkedHashMap<>(byKeyId));
    }

    public static TrustedKeys of(PublicKey... keys) {
        Map<String, PublicKey> map = new LinkedHashMap<>();
        for (PublicKey key : keys) {
            map.put(UpdateSignatures.keyId(key), key);
        }
        return new TrustedKeys(map);
    }

    public static TrustedKeys none() {
        return new TrustedKeys(Map.of());
    }

    /** Loads the built-in keys. A malformed entry is a build defect, so it fails loudly. */
    public static TrustedKeys fromClasspath() {
        ClassLoader loader = TrustedKeys.class.getClassLoader();
        try (InputStream input = loader == null ? null : loader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return none();
            }
            Properties properties = new Properties();
            properties.load(input);
            Map<String, PublicKey> map = new LinkedHashMap<>();
            for (String label : properties.stringPropertyNames()) {
                String value = properties.getProperty(label, "").trim();
                if (value.isEmpty()) {
                    continue;
                }
                PublicKey key = UpdateSignatures.decodePublicKey(value);
                map.put(UpdateSignatures.keyId(key), key);
            }
            return new TrustedKeys(map);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, failure);
        } catch (GeneralSecurityException invalid) {
            throw new IllegalStateException("A trusted update key in " + RESOURCE + " is invalid",
                    invalid);
        }
    }

    public Optional<PublicKey> find(String keyId) {
        return Optional.ofNullable(keyId == null ? null : byKeyId.get(keyId));
    }

    public boolean isEmpty() {
        return byKeyId.isEmpty();
    }

    public int size() {
        return byKeyId.size();
    }
}

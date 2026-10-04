package com.flowerfarm.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Verified update manifest payload. Only {@link SignedManifestVerifier}
 * creates instances, after the publisher signature has been checked.
 */
public record UpdateManifest(int schema, String product, Instant issuedAt, boolean paused,
                             String pauseMessage, List<Release> releases) {

    public static final String PRODUCT = "FlowersForever";
    public static final int SCHEMA = 1;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9._-]{1,120}");
    private static final long MAX_PACKAGE_BYTES = 2L * 1024 * 1024 * 1024;

    public UpdateManifest {
        releases = List.copyOf(releases);
    }

    /** One published version and its installers. */
    public record Release(AppVersion version, List<String> channels, AppVersion minimumVersion,
                          String releasedOn, String notes, Map<String, PackageInfo> packages) {
        public Release {
            channels = List.copyOf(channels);
            packages = Map.copyOf(packages);
        }

        public Optional<PackageInfo> packageFor(UpdatePlatform platform) {
            return Optional.ofNullable(packages.get(platform.key()));
        }

        public boolean offeredTo(UpdateChannel channel) {
            return channels.stream().anyMatch(channel::accepts);
        }
    }

    /** A downloadable installer. The signature covers {@link UpdateSignatures#packageStatement}. */
    public record PackageInfo(String platformKey, String fileName, URI url, long size,
                              String sha256, String keyId, String signature) {
    }

    static UpdateManifest parse(byte[] payload, ObjectMapper mapper) throws UpdateVerificationException {
        JsonNode root;
        try {
            root = mapper.readTree(payload);
        } catch (IOException invalid) {
            throw new UpdateVerificationException("The update information is not readable.", invalid);
        }
        if (root == null || !root.isObject()) {
            throw invalid("the manifest is not a JSON object");
        }
        int schema = root.path("schema").asInt(-1);
        if (schema != SCHEMA) {
            throw invalid("unsupported manifest schema " + schema);
        }
        String product = text(root, "product");
        if (!PRODUCT.equals(product)) {
            throw invalid("the manifest is for another product");
        }
        Instant issuedAt;
        try {
            issuedAt = Instant.parse(text(root, "issuedAt"));
        } catch (DateTimeParseException badTime) {
            throw invalid("issuedAt is not an ISO-8601 instant");
        }
        boolean paused = root.path("paused").asBoolean(false);
        String pauseMessage = root.path("pauseMessage").asText("");
        JsonNode releaseNodes = root.path("releases");
        if (!releaseNodes.isArray()) {
            throw invalid("releases must be a list");
        }
        List<Release> releases = new ArrayList<>();
        for (JsonNode node : releaseNodes) {
            releases.add(parseRelease(node));
        }
        return new UpdateManifest(schema, product, issuedAt, paused, pauseMessage, releases);
    }

    private static Release parseRelease(JsonNode node) throws UpdateVerificationException {
        AppVersion version = version(node, "version");
        if (!version.isRelease()) {
            throw invalid("release " + version + " is not a final version");
        }
        List<String> channels = new ArrayList<>();
        for (JsonNode channel : node.path("channels")) {
            String value = channel.asText("").trim().toLowerCase(Locale.ROOT);
            if (UpdateChannel.fromName(value).isEmpty()) {
                throw invalid("release " + version + " lists an unknown channel '" + value + "'");
            }
            channels.add(value);
        }
        if (channels.isEmpty()) {
            throw invalid("release " + version + " has no channels");
        }
        AppVersion minimum = node.hasNonNull("minimumVersion") ? version(node, "minimumVersion") : null;
        Map<String, PackageInfo> packages = new LinkedHashMap<>();
        JsonNode packageNodes = node.path("packages");
        if (!packageNodes.isObject()) {
            throw invalid("release " + version + " has no packages");
        }
        for (Map.Entry<String, JsonNode> field : packageNodes.properties()) {
            if (UpdatePlatform.fromKey(field.getKey()).isEmpty()) {
                continue; // Future platforms are ignored by this version.
            }
            packages.put(field.getKey(), parsePackage(field.getKey(), field.getValue(), version));
        }
        return new Release(version, channels, minimum, node.path("releasedOn").asText(""),
                node.path("notes").asText(""), packages);
    }

    private static PackageInfo parsePackage(String platformKey, JsonNode node, AppVersion version)
            throws UpdateVerificationException {
        String file = text(node, "file");
        if (!SAFE_FILE.matcher(file).matches() || file.startsWith(".")) {
            throw invalid("package file name '" + file + "' is not allowed");
        }
        URI url;
        try {
            url = new URI(text(node, "url"));
        } catch (URISyntaxException badUrl) {
            throw invalid("package URL for " + version + " is not valid");
        }
        if (url.getScheme() == null || url.getHost() == null) {
            throw invalid("package URL for " + version + " is not absolute");
        }
        long size = node.path("size").asLong(-1);
        if (size <= 0 || size > MAX_PACKAGE_BYTES) {
            throw invalid("package size for " + version + " is not valid");
        }
        String sha256 = text(node, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw invalid("package checksum for " + version + " is not a SHA-256 value");
        }
        return new PackageInfo(platformKey, file, url, size, sha256, text(node, "keyId"),
                text(node, "signature"));
    }

    private static AppVersion version(JsonNode node, String field) throws UpdateVerificationException {
        try {
            return AppVersion.parse(text(node, field));
        } catch (IllegalArgumentException invalid) {
            throw invalid(field + " is not a version");
        }
    }

    private static String text(JsonNode node, String field) throws UpdateVerificationException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("missing " + field);
        }
        return value.asText().trim();
    }

    private static UpdateVerificationException invalid(String detail) {
        return new UpdateVerificationException(
                "The update information was rejected because it is incomplete (" + detail + ").");
    }
}

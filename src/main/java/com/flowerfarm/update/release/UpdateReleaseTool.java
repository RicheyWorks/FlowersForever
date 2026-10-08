package com.flowerfarm.update.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.update.AppVersion;
import com.flowerfarm.update.LoopbackAddresses;
import com.flowerfarm.update.SignedManifestVerifier;
import com.flowerfarm.update.TrustedKeys;
import com.flowerfarm.update.UpdateChannel;
import com.flowerfarm.update.UpdateManifest;
import com.flowerfarm.update.UpdatePlatform;
import com.flowerfarm.update.UpdateSignatures;
import com.flowerfarm.update.UpdateVerificationException;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Release-side tool for the signed update manifest. Run it from the release JAR:
 * <pre>
 * java -cp flowerfarm-manager-X.jar -Dloader.main=com.flowerfarm.update.release.UpdateReleaseTool \
 *      org.springframework.boot.loader.launch.PropertiesLauncher &lt;command&gt; [options]
 * </pre>
 * or with {@code scripts/update-release.ps1}. Commands: {@code keygen}, {@code public-key},
 * {@code add-release}, {@code channels}, {@code pause}, {@code resume}, {@code sign},
 * {@code verify}. The private key is read from a file outside any git checkout or from
 * an environment variable ({@code env:NAME}); it is never written into a manifest.
 */
public final class UpdateReleaseTool {

    private final PrintStream out;
    private final Function<String, String> environment;
    private final Clock clock;
    private final TrustedKeys releaseKeys;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public UpdateReleaseTool(PrintStream out, Function<String, String> environment, Clock clock) {
        this(out, environment, clock, TrustedKeys.fromClasspath());
    }

    /** @param releaseKeys the built-in release keys; a test feed may never be signed with one of them */
    public UpdateReleaseTool(PrintStream out, Function<String, String> environment, Clock clock,
                             TrustedKeys releaseKeys) {
        this.releaseKeys = releaseKeys;
        this.out = out;
        this.environment = environment;
        this.clock = clock;
    }

    public static void main(String[] args) {
        int code = new UpdateReleaseTool(System.out, System::getenv, Clock.systemUTC()).run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    public int run(String[] args) {
        if (args.length == 0) {
            usage();
            return 2;
        }
        Map<String, List<String>> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException invalid) {
            out.println("ERROR: " + invalid.getMessage());
            return 2;
        }
        try {
            switch (args[0]) {
                case "keygen" -> keygen(options);
                case "public-key" -> publicKey(options);
                case "add-release" -> addRelease(options);
                case "channels" -> channels(options);
                case "pause" -> pause(options, true);
                case "resume" -> pause(options, false);
                case "sign" -> sign(options);
                case "verify" -> {
                    return verify(options);
                }
                default -> {
                    usage();
                    return 2;
                }
            }
            return 0;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | UpdateVerificationException failure) {
            out.println("ERROR: " + failure.getMessage());
            return 1;
        }
    }

    private void keygen(Map<String, List<String>> options) throws IOException, GeneralSecurityException {
        Path target = Path.of(required(options, "out")).toAbsolutePath().normalize();
        Path inside = gitWorkingTree(target);
        if (inside != null) {
            throw new IllegalArgumentException("Refusing to write a private key inside the git checkout " + inside
                    + ". Choose a folder outside any repository (see docs/AUTO_UPDATES.md).");
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Refusing to overwrite an existing key: " + target);
        }
        KeyPair pair = UpdateSignatures.generateKeyPair();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String publicKeyText = UpdateSignatures.encodePublicKey(pair.getPublic());
        Files.writeString(target, "# FlowersForever update signing key. SECRET: never commit or share.\n"
                        + "# Key id " + UpdateSignatures.keyId(pair.getPublic()) + "\n"
                        + "private=" + UpdateSignatures.encodePrivateKey(pair.getPrivate()) + "\n"
                        + "public=" + publicKeyText + "\n",
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        restrictPermissions(target);
        String publicKey = publicKeyText;
        out.println("Private key written to " + target + " (keep it secret and backed up offline).");
        out.println("Key id: " + UpdateSignatures.keyId(pair.getPublic()));
        out.println("Add this line to src/main/resources/" + TrustedKeys.RESOURCE + ":");
        out.println("richeyworks-" + LocalDate.now(clock) + "=" + publicKey);
    }

    private void publicKey(Map<String, List<String>> options) throws IOException, GeneralSecurityException {
        PublicKey key = signingKey(options).publicKey();
        out.println("Key id: " + UpdateSignatures.keyId(key));
        out.println(UpdateSignatures.encodePublicKey(key));
    }

    private void addRelease(Map<String, List<String>> options) throws IOException, GeneralSecurityException {
        Path manifestPath = Path.of(required(options, "manifest"));
        SigningKey signingKey = signingKey(options);
        PrivateKey key = signingKey.privateKey();
        PublicKey publicKey = signingKey.publicKey();
        boolean testFeed = testFeed(options, publicKey);
        String version = AppVersion.parse(required(options, "version")).toString();
        if (!AppVersion.parse(version).isRelease()) {
            throw new IllegalArgumentException("Only final versions can be published: " + version);
        }
        ObjectNode root = loadOrCreate(manifestPath);
        ObjectNode release = mapper.createObjectNode();
        release.put("version", version);
        release.set("channels", channelArray(required(options, "channels")));
        if (options.containsKey("minimum-version")) {
            release.put("minimumVersion", AppVersion.parse(required(options, "minimum-version")).toString());
        }
        release.put("releasedOn", options.containsKey("released-on") ? required(options, "released-on")
                : LocalDate.now(clock).toString());
        release.put("notes", notes(options));
        ObjectNode packages = mapper.createObjectNode();
        for (String spec : options.getOrDefault("package", List.of())) {
            int equals = spec.indexOf('=');
            int at = spec.lastIndexOf('@');
            if (equals <= 0 || at <= equals) {
                throw new IllegalArgumentException("--package must look like windows-msi=path\\to\\file.msi@https://host/file.msi");
            }
            String platform = spec.substring(0, equals);
            if (UpdatePlatform.fromKey(platform).isEmpty()) {
                throw new IllegalArgumentException("Unknown package platform: " + platform);
            }
            Path file = Path.of(spec.substring(equals + 1, at));
            URI url = URI.create(spec.substring(at + 1));
            requireAllowedPackageUrl(url, testFeed);
            Path fileName = file.getFileName();
            if (fileName == null || !Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Package file not found: " + file);
            }
            long size = Files.size(file);
            String sha256 = UpdateSignatures.sha256Hex(file);
            byte[] signature = UpdateSignatures.sign(key, UpdateSignatures.packageStatement(
                    platform, version, fileName.toString(), size, sha256));
            ObjectNode pkg = mapper.createObjectNode();
            pkg.put("file", fileName.toString());
            pkg.put("url", url.toString());
            pkg.put("size", size);
            pkg.put("sha256", sha256);
            pkg.put("keyId", UpdateSignatures.keyId(publicKey));
            pkg.put("signature", Base64.getEncoder().encodeToString(signature));
            packages.set(platform, pkg);
            out.println("Signed " + platform + " " + fileName + " (" + size + " bytes, sha256 " + sha256 + ")");
        }
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("At least one --package is required");
        }
        release.set("packages", packages);
        ArrayNode releases = (ArrayNode) root.withArray("releases");
        for (int index = releases.size() - 1; index >= 0; index--) {
            if (version.equals(releases.get(index).path("version").asText())) {
                releases.remove(index);
            }
        }
        releases.add(release);
        write(manifestPath, root);
        out.println("Release " + version + " recorded in " + manifestPath + " for channels "
                + release.get("channels") + ". Next: sign.");
    }

    private void channels(Map<String, List<String>> options) throws IOException {
        Path manifestPath = Path.of(required(options, "manifest"));
        String version = AppVersion.parse(required(options, "version")).toString();
        ObjectNode root = loadOrCreate(manifestPath);
        boolean found = false;
        for (JsonNode release : root.withArray("releases")) {
            if (version.equals(release.path("version").asText())) {
                ((ObjectNode) release).set("channels", channelArray(required(options, "channels")));
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Release " + version + " is not in " + manifestPath);
        }
        write(manifestPath, root);
        out.println("Release " + version + " now offered to " + required(options, "channels") + ". Next: sign.");
    }

    private void pause(Map<String, List<String>> options, boolean paused) throws IOException {
        Path manifestPath = Path.of(required(options, "manifest"));
        ObjectNode root = loadOrCreate(manifestPath);
        root.put("paused", paused);
        root.put("pauseMessage", paused && options.containsKey("message") ? required(options, "message") : "");
        write(manifestPath, root);
        out.println((paused ? "Updates paused" : "Updates resumed") + " in " + manifestPath + ". Next: sign.");
    }

    private void sign(Map<String, List<String>> options)
            throws IOException, GeneralSecurityException, UpdateVerificationException {
        Path manifestPath = Path.of(required(options, "manifest"));
        Path output = Path.of(required(options, "out"));
        SigningKey signingKey = signingKey(options);
        PrivateKey key = signingKey.privateKey();
        PublicKey publicKey = signingKey.publicKey();
        ObjectNode root = loadOrCreate(manifestPath);
        boolean testFeed = testFeed(options, publicKey);
        for (JsonNode release : root.path("releases")) {
            for (JsonNode pkg : release.path("packages")) {
                requireAllowedPackageUrl(URI.create(pkg.path("url").asText("")), testFeed);
            }
        }
        root.put("issuedAt", Instant.now(clock).toString());
        byte[] payload = mapper.writeValueAsBytes(root);
        // Self-check before anything is published.
        UpdateManifest manifest = new SignedManifestVerifier(TrustedKeys.of(publicKey))
                .verify(envelope(payload, key, publicKey));
        for (UpdateManifest.Release release : manifest.releases()) {
            for (UpdateManifest.PackageInfo info : release.packages().values()) {
                if (!info.keyId().equals(UpdateSignatures.keyId(publicKey))) {
                    throw new IllegalArgumentException("Release " + release.version()
                            + " was signed with a different key; run add-release again with this key.");
                }
            }
        }
        Files.write(output, envelope(payload, key, publicKey));
        out.println("Signed manifest written to " + output + " (" + manifest.releases().size()
                + " releases, key id " + UpdateSignatures.keyId(publicKey) + ").");
    }

    /**
     * {@code --test-feed loopback} builds a feed for the headless release check: installer
     * addresses must then be on this computer, and the signing key must be a throwaway
     * key, never one of the built-in release keys.
     */
    private boolean testFeed(Map<String, List<String>> options, PublicKey publicKey) {
        if (!options.containsKey("test-feed")) {
            return false;
        }
        if (!"loopback".equals(required(options, "test-feed"))) {
            throw new IllegalArgumentException("--test-feed only accepts the value loopback");
        }
        if (releaseKeys.find(UpdateSignatures.keyId(publicKey)).isPresent()) {
            throw new IllegalArgumentException("A test feed must be signed with a throwaway key, never a built-in "
                    + "release key.");
        }
        return true;
    }

    private static void requireAllowedPackageUrl(URI url, boolean testFeed) {
        if (testFeed) {
            if (!LoopbackAddresses.isLoopback(url)) {
                throw new IllegalArgumentException("Test-feed package URLs must be on this computer "
                        + "(127.0.0.1, [::1] or localhost): " + url);
            }
            return;
        }
        if (!"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException("Package URLs must use https: " + url);
        }
    }

    private byte[] envelope(byte[] payload, PrivateKey key, PublicKey publicKey)
            throws GeneralSecurityException, IOException {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("format", SignedManifestVerifier.FORMAT);
        envelope.put("keyId", UpdateSignatures.keyId(publicKey));
        envelope.put("payload", Base64.getEncoder().encodeToString(payload));
        envelope.put("signature", Base64.getEncoder().encodeToString(UpdateSignatures.sign(key, payload)));
        return mapper.writeValueAsBytes(envelope);
    }

    private int verify(Map<String, List<String>> options)
            throws IOException, GeneralSecurityException, UpdateVerificationException {
        byte[] signed = Files.readAllBytes(Path.of(required(options, "signed")));
        TrustedKeys keys = options.containsKey("public-key")
                ? TrustedKeys.of(UpdateSignatures.decodePublicKey(required(options, "public-key")))
                : TrustedKeys.fromClasspath();
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("No trusted public key: pass --public-key or build one into the app.");
        }
        SignedManifestVerifier verifier = new SignedManifestVerifier(keys);
        UpdateManifest manifest = verifier.verify(signed);
        out.println("Manifest signature OK. Issued " + manifest.issuedAt() + (manifest.paused() ? " (PAUSED)" : ""));
        int failures = 0;
        for (UpdateManifest.Release release : manifest.releases()) {
            out.println("  " + release.version() + " channels=" + release.channels());
            for (UpdateManifest.PackageInfo info : release.packages().values()) {
                boolean ok = verifier.packageSignatureValid(info, release.version());
                failures += ok ? 0 : 1;
                out.println("    " + info.platformKey() + " " + info.fileName() + " signature "
                        + (ok ? "OK" : "INVALID") + " " + info.url());
            }
        }
        for (String spec : options.getOrDefault("package", List.of())) {
            int equals = spec.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("--package must look like windows-msi=path\\to\\file.msi");
            }
            Path file = Path.of(spec.substring(equals + 1));
            Path fileName = file.getFileName();
            String sha = UpdateSignatures.sha256Hex(file);
            boolean matched = manifest.releases().stream().flatMap(release -> release.packages().values().stream())
                    .anyMatch(info -> fileName != null && info.fileName().equals(fileName.toString())
                            && info.sha256().equals(sha) && info.size() == sizeOf(file));
            failures += matched ? 0 : 1;
            out.println("  local " + file + " " + (matched ? "matches the manifest" : "DOES NOT match the manifest"));
        }
        return failures == 0 ? 0 : 1;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException unreadable) {
            return -1;
        }
    }

    /** A private/public key pair read from a key file or an environment variable holding the same text. */
    record SigningKey(PrivateKey privateKey, PublicKey publicKey) {
    }

    private SigningKey signingKey(Map<String, List<String>> options) throws IOException, GeneralSecurityException {
        String source = required(options, "private-key");
        String text;
        if (source.startsWith("env:")) {
            text = environment.apply(source.substring(4));
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Environment variable " + source.substring(4) + " is empty");
            }
        } else {
            text = Files.readString(Path.of(source), StandardCharsets.US_ASCII);
        }
        java.util.Properties properties = new java.util.Properties();
        properties.load(new java.io.StringReader(text));
        PrivateKey privateKey = UpdateSignatures.decodePrivateKey(properties.getProperty("private"));
        PublicKey publicKey = UpdateSignatures.decodePublicKey(properties.getProperty("public"));
        byte[] probe = "FlowersForever key check".getBytes(StandardCharsets.US_ASCII);
        if (!UpdateSignatures.verify(publicKey, probe, UpdateSignatures.sign(privateKey, probe))) {
            throw new GeneralSecurityException("The key file's private and public keys do not belong together");
        }
        return new SigningKey(privateKey, publicKey);
    }

    private ArrayNode channelArray(String csv) {
        ArrayNode array = mapper.createArrayNode();
        for (String channel : csv.split(",")) {
            String trimmed = channel.trim().toLowerCase(Locale.ROOT);
            if (UpdateChannel.fromName(trimmed).isEmpty()) {
                throw new IllegalArgumentException("Unknown channel '" + trimmed + "' (use owner, early, stable)");
            }
            array.add(trimmed);
        }
        return array;
    }

    private String notes(Map<String, List<String>> options) throws IOException {
        if (options.containsKey("notes-file")) {
            return Files.readString(Path.of(required(options, "notes-file")), StandardCharsets.UTF_8).trim();
        }
        return options.containsKey("notes") ? required(options, "notes") : "";
    }

    private ObjectNode loadOrCreate(Path manifest) throws IOException {
        if (Files.isRegularFile(manifest)) {
            JsonNode node = mapper.readTree(manifest.toFile());
            if (node instanceof ObjectNode object) {
                return object;
            }
            throw new IllegalArgumentException(manifest + " is not a JSON object");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("schema", UpdateManifest.SCHEMA);
        root.put("product", UpdateManifest.PRODUCT);
        root.put("issuedAt", Instant.now(clock).toString());
        root.put("paused", false);
        root.put("pauseMessage", "");
        root.putArray("releases");
        return root;
    }

    private void write(Path manifest, ObjectNode root) throws IOException {
        Path parent = manifest.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writeValue(manifest.toFile(), root);
    }

    static Path gitWorkingTree(Path target) {
        for (Path current = target.getParent(); current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
        }
        return null;
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException windows) {
            // On Windows the file inherits the user profile ACL; keep it in a private folder.
        }
    }

    private static Map<String, List<String>> parse(String[] args) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String arg = args[index];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String name = arg.substring(2);
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            options.computeIfAbsent(name, key -> new ArrayList<>()).add(args[++index]);
        }
        return options;
    }

    private static String required(Map<String, List<String>> options, String name) {
        List<String> values = options.get(name);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            throw new IllegalArgumentException("Missing --" + name);
        }
        return values.get(values.size() - 1);
    }

    private void usage() {
        out.println("""
                FlowersForever update release tool
                  keygen      --out <file outside any git checkout>
                  public-key  --private-key <file|env:NAME>
                  add-release --manifest unsigned.json --private-key K --version 1.2.1 --channels owner
                              --package windows-msi=<file.msi>@<https url> [--package macos-dmg=...]
                              [--notes "text" | --notes-file f] [--minimum-version 1.2.0]
                              [--test-feed loopback]  (throwaway key, http://127.0.0.1 URLs; release checks only)
                  channels    --manifest unsigned.json --version 1.2.1 --channels owner,early,stable
                  pause       --manifest unsigned.json [--message "text"]
                  resume      --manifest unsigned.json
                  sign        --manifest unsigned.json --private-key K --out latest.json [--test-feed loopback]
                  verify      --signed latest.json [--public-key B64] [--package windows-msi=<file>]
                """);
    }
}

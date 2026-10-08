package com.flowerfarm.update;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test-only, headless update path for the real-install release check (#15).
 *
 * <p>{@value #APPLY} runs the desktop's own update steps without a window:
 * check the signed manifest, download and verify the new installer and the
 * current version's rollback installer, require the records closed, make the
 * verified backup, record progress, and hand off to a <em>silent</em> MSI
 * install. The installed app then reopens with {@value #VERIFY}, which runs the
 * same first-launch guard as the desktop: a healthy start is confirmed, a failed
 * start restores the pre-update backup and silently reinstalls the previous
 * version.
 *
 * <p>Safety rails: nothing runs unless {@value #GATE_PROPERTY}=true or
 * {@value #GATE_ENVIRONMENT}=true is set; {@value #APPLY} always needs
 * {@value #TRUST_TEST_KEY}, trusts only that key (never the built-in release
 * keys), refuses a built-in release key passed as the test key, and refuses any
 * feed or installer address that is not on this computer. Every step and the
 * result go to a log file; the process exits with an {@link Exit} code.
 */
public final class HeadlessUpdateCommand {

    public static final String APPLY = "--apply-update-from";
    public static final String TRUST_TEST_KEY = "--trust-test-key";
    public static final String VERIFY = "--verify-update-launch";
    public static final String LOG = "--update-log";
    public static final String BACKUP_DIRECTORY = "--update-backup-dir";
    public static final String GATE_PROPERTY = "flowerfarm.updates.headless-test";
    public static final String GATE_ENVIRONMENT = "FLOWERFARM_UPDATES_HEADLESS_TEST";
    public static final String DEFAULT_LOG = "headless-update-check.log";

    private static final int MAX_KEY_FILE_BYTES = 64 * 1024;
    private static final String RECORD_COUNTS = "SELECT TABLE_NAME, ROW_COUNT_ESTIMATE FROM INFORMATION_SCHEMA.TABLES "
            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";

    /** Process exit codes. */
    public enum Exit {
        /** Apply: handed off to the silent installer. Verify: started and passed its checks. */
        OK(0),
        /** Verify: update recovery needs help; nothing more was attempted. */
        BLOCKED(2),
        /** Verify: the new version failed its first launch; records restored and the previous installer started. */
        ROLLED_BACK(3),
        /** Not enabled, bad arguments, a non-local address, or an unacceptable key. Nothing was changed. */
        REFUSED(64),
        /** The feed offers nothing newer for this install. Nothing was changed. */
        NO_UPDATE(65),
        /** The manifest or an installer failed verification (signature, size, checksum). Nothing was changed. */
        REJECTED(66),
        /** Verified, but not installed (not an installed copy, records still open, backup or hand-off failed). */
        NOT_INSTALLED(67),
        /** The local test feed could not be reached. Nothing was changed. */
        OFFLINE(68),
        /** Verify: an ordinary (not first-after-update) start failed. */
        START_FAILED(70);

        private final int code;

        Exit(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public enum Mode { APPLY, VERIFY }

    public record Request(Mode mode, Optional<URI> feed, Optional<Path> testKey, Optional<Path> log,
                          Optional<Path> backupDirectory) {
    }

    private final boolean enabled;
    private final UpdateSettings settings;
    private final TrustedKeys releaseKeys;
    private final Optional<UpdatePlatform> platform;
    private final Function<UpdateSettings, UpdateTransport> transports;
    private final Function<List<String>, PlatformInstaller> installers;
    private final HeadlessUpdateLog log;

    public HeadlessUpdateCommand(boolean enabled, UpdateSettings settings, TrustedKeys releaseKeys,
                                 Optional<UpdatePlatform> platform,
                                 Function<UpdateSettings, UpdateTransport> transports,
                                 Function<List<String>, PlatformInstaller> installers, HeadlessUpdateLog log) {
        this.enabled = enabled;
        this.settings = settings;
        this.releaseKeys = releaseKeys;
        this.platform = platform;
        this.transports = transports;
        this.installers = installers;
        this.log = log;
    }

    /** The real wiring: HTTP (loopback only), the built-in release keys for the refusal check, silent MSI. */
    public static HeadlessUpdateCommand forThisProcess(boolean enabled, UpdateSettings settings,
                                                       HeadlessUpdateLog log) {
        Optional<UpdatePlatform> platform = UpdatePlatform.forOperatingSystem(System.getProperty("os.name"));
        return new HeadlessUpdateCommand(enabled, settings, TrustedKeys.fromClasspath(), platform,
                testSettings -> new HttpUpdateTransport(testSettings.connectTimeout(), testSettings.requestTimeout(),
                        true, "FlowersForever/" + testSettings.currentVersion() + " (headless release check)"),
                arguments -> PlatformInstallers.silentForThisProcess(platform, settings.launcherPath(), arguments),
                log);
    }

    /** True when the arguments ask for this path; such a run never falls through to the desktop window. */
    public static boolean requested(String[] args) {
        for (String arg : args) {
            if (APPLY.equals(arg) || VERIFY.equals(arg) || TRUST_TEST_KEY.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /** True only for an explicit {@code true} in the JVM property or the environment variable. */
    public static boolean gateOpen(Function<String, String> properties, Function<String, String> environment) {
        return isTrue(properties.apply(GATE_PROPERTY)) || isTrue(environment.apply(GATE_ENVIRONMENT));
    }

    private static boolean isTrue(String value) {
        return value != null && "true".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    /** Parses {@code --flag value} pairs. Unknown arguments are refused rather than ignored. */
    public static Request parse(String[] args) {
        Mode mode = null;
        URI feed = null;
        Path key = null;
        Path logFile = null;
        Path backups = null;
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case VERIFY -> mode = setMode(mode, Mode.VERIFY);
                case APPLY -> {
                    mode = setMode(mode, Mode.APPLY);
                    feed = uri(value(args, ++index, arg));
                }
                case TRUST_TEST_KEY -> key = Path.of(value(args, ++index, arg));
                case LOG -> logFile = Path.of(value(args, ++index, arg));
                case BACKUP_DIRECTORY -> backups = Path.of(value(args, ++index, arg));
                default -> throw new IllegalArgumentException("Unexpected argument for the headless update check: "
                        + arg);
            }
        }
        if (mode == null) {
            throw new IllegalArgumentException(key != null ? TRUST_TEST_KEY + " is only used with " + APPLY
                    : "Use " + APPLY + " <feed address> or " + VERIFY + ".");
        }
        if (mode == Mode.VERIFY && key != null) {
            throw new IllegalArgumentException(TRUST_TEST_KEY + " is only used with " + APPLY);
        }
        return new Request(mode, Optional.ofNullable(feed), Optional.ofNullable(key), Optional.ofNullable(logFile),
                Optional.ofNullable(backups));
    }

    private static Mode setMode(Mode current, Mode next) {
        if (current != null) {
            throw new IllegalArgumentException("Use either " + APPLY + " or " + VERIFY + ", not both.");
        }
        return next;
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static URI uri(String value) {
        try {
            URI uri = new URI(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("The feed address must be a full http(s) address: " + value);
            }
            return uri;
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("The feed address is not valid: " + value, invalid);
        }
    }

    /** Message used whenever the gate is closed. */
    public static String notEnabledMessage() {
        return "The headless update check is a test-only tool and is not enabled. Set " + GATE_ENVIRONMENT
                + "=true (or -D" + GATE_PROPERTY + "=true) for a release check; nothing was changed.";
    }

    /** Downloads, verifies, backs up, and hands off to the silent installer. Returns the exit code. */
    public int apply(Request request) {
        log.line("START", "apply update; feed " + request.feed().map(URI::toString).orElse("(none)")
                + "; test key file " + request.testKey().map(Path::toString).orElse("(none)")
                + "; installed version " + settings.currentVersion() + "; records " + settings.dataDirectory());
        if (!enabled) {
            return log.finish(Exit.REFUSED, notEnabledMessage());
        }
        if (request.mode() != Mode.APPLY || request.feed().isEmpty()) {
            return log.finish(Exit.REFUSED, "Missing " + APPLY + " <feed address>.");
        }
        URI feed = request.feed().get();
        if (request.testKey().isEmpty()) {
            return log.finish(Exit.REFUSED, APPLY + " needs " + TRUST_TEST_KEY + " <public key file>. This "
                    + "test-only path never uses the built-in release keys; nothing was changed.");
        }
        if (!LoopbackAddresses.isLoopback(feed)) {
            return log.finish(Exit.REFUSED, "A test key can only be used with a feed on this computer "
                    + "(127.0.0.1, [::1] or localhost). Refused " + feed + "; nothing was changed.");
        }
        PublicKey testKey;
        try {
            testKey = readTestKey(request.testKey().get());
        } catch (IOException | GeneralSecurityException unusable) {
            return log.finish(Exit.REFUSED, "The test key could not be used: " + unusable.getMessage());
        }
        String keyId = UpdateSignatures.keyId(testKey);
        if (releaseKeys.find(keyId).isPresent()) {
            return log.finish(Exit.REFUSED, "The key in " + request.testKey().get() + " is a built-in release key. "
                    + "A release check must use a throwaway test key; nothing was changed.");
        }
        log.line("TRUST", "test key id " + keyId + "; the built-in release keys are not trusted in this run");
        UpdateSettings testSettings = settings.forTestFeed(feed, request.backupDirectory());
        UpdateTransport transport = new LoopbackOnlyTransport(transports.apply(testSettings));
        PlatformInstaller installer = installers.apply(relaunchArguments());
        UpdateCoordinator coordinator = UpdateWiring.coordinator(testSettings, TrustedKeys.of(testKey), transport,
                platform, installer, log);
        UpdateCheckResult result = coordinator.check(true);
        log.line("CHECK", result.status() + ": " + result.message());
        switch (result.status()) {
            case UPDATE_AVAILABLE -> {
                // Continue below.
            }
            case REJECTED -> {
                return log.finish(Exit.REJECTED, result.message());
            }
            case OFFLINE -> {
                return log.finish(Exit.OFFLINE, "The test feed could not be reached: " + feed);
            }
            default -> {
                return log.finish(Exit.NO_UPDATE, result.message());
            }
        }
        if (!coordinator.canInstall()) {
            return log.finish(Exit.NOT_INSTALLED, UpdateMessages.CANNOT_INSTALL_HERE + " (launcher: "
                    + settings.launcherPath().map(Path::toString).orElse("none; not an installed copy") + ")");
        }
        UpdateCoordinator.PreparedUpdate prepared;
        try {
            prepared = coordinator.prepare(result, bytes -> { });
        } catch (UpdateVerificationException rejected) {
            return log.finish(Exit.REJECTED, rejected.getMessage());
        }
        log.line("VERIFIED", "new installer " + prepared.next().file() + " (sha256 " + prepared.next().sha256()
                + "); rollback installer " + prepared.rollback().map(pkg -> pkg.file() + " (sha256 "
                + pkg.sha256() + ")").orElse("none"));
        UpdateCoordinator.InstallOutcome outcome = coordinator.install(prepared,
                () -> DataBackupService.requireDatabaseClosed(testSettings.dataDirectory()));
        outcome.backup().ifPresent(backup -> log.line("BACKUP", backup.archive() + " (" + backup.fileCount()
                + " files, sha256 " + backup.sha256() + ")"));
        if (!outcome.handedOff()) {
            return log.finish(Exit.NOT_INSTALLED, outcome.message());
        }
        return log.finish(Exit.OK, "Handed off to the silent installer for " + prepared.next().version()
                + ". This process exits now; the installed app reopens with " + VERIFY
                + " and adds its result to this log.");
    }

    /** The first-launch guard with the silent hand-off, for {@link #verifyLaunch}. */
    public UpdateLaunchGuard guard() {
        return new UpdateLaunchGuard(settings, new UpdateStateStore(settings.updatesDirectory()),
                new DataBackupService(Clock.systemDefaultZone()), installers.apply(relaunchArguments()), platform,
                Clock.systemUTC());
    }

    /**
     * Starts the app headless through the same guard as the desktop. A failed first
     * launch after an update rolls back (exit {@link Exit#ROLLED_BACK}).
     *
     * @param describe summarises the records after a successful start
     */
    public <T> int verifyLaunch(UpdateLaunchGuard guard, Callable<T> startSpring, Runnable closeDatabase,
                                Function<T, String> describe, Consumer<T> close) {
        log.line("START", "first-launch check; installed version " + settings.currentVersion() + "; records "
                + settings.dataDirectory());
        if (!enabled) {
            return log.finish(Exit.REFUSED, notEnabledMessage());
        }
        UpdateStartup.Result<T> result;
        try {
            result = UpdateStartup.start(guard, startSpring, closeDatabase, notice -> log.line("NOTICE", notice));
        } catch (Exception | Error failure) {
            return log.finish(Exit.START_FAILED, "FlowersForever did not start: " + UpdateStartup.reason(failure));
        }
        if (result.started().isEmpty()) {
            if (result.exitCode() == 0) {
                return log.finish(Exit.ROLLED_BACK, "The new version failed its first-launch check. The records "
                        + "were restored from the pre-update backup and the previous version's installer was "
                        + "started silently; it reopens with " + VERIFY + ".");
            }
            return log.finish(Exit.BLOCKED, "Update recovery needs help; see the NOTICE line above.");
        }
        T started = result.started().get();
        try {
            log.line("RECORDS", describe.apply(started));
            guard.takeNotice().ifPresent(notice -> log.line("NOTICE", notice));
        } finally {
            close.accept(started);
        }
        return log.finish(Exit.OK, "Version " + settings.currentVersion() + " started and passed its checks.");
    }

    /** Arguments for the app the installer reopens: the headless check, writing to the same log. */
    List<String> relaunchArguments() {
        List<String> arguments = new ArrayList<>(List.of(VERIFY));
        log.file().ifPresent(file -> {
            arguments.add(LOG);
            arguments.add(file.toString());
        });
        return List.copyOf(arguments);
    }

    /**
     * Reads a public key file: one base64 X.509 key, optionally as {@code label=key},
     * or a PEM public key. A file holding a private key is refused without echoing it.
     */
    static PublicKey readTestKey(Path file) throws IOException, GeneralSecurityException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("the test key file was not found: " + file);
        }
        if (Files.size(file) > MAX_KEY_FILE_BYTES) {
            throw new IOException("the test key file is too large to be a public key: " + file);
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("private key") || lower.lines().anyMatch(line -> line.strip().startsWith("private="))) {
            throw new GeneralSecurityException("the file " + file + " holds a private key; pass a file with only "
                    + "the public key");
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-----")) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            throw new GeneralSecurityException("the file " + file + " does not contain a public key");
        }
        boolean pem = text.contains("-----BEGIN PUBLIC KEY-----");
        if (!pem && lines.size() != 1) {
            throw new GeneralSecurityException("the file " + file + " must contain exactly one public key");
        }
        String candidate = String.join("", lines);
        try {
            return UpdateSignatures.decodePublicKey(candidate);
        } catch (GeneralSecurityException | IllegalArgumentException notBare) {
            int equals = candidate.indexOf('=');
            if (pem || equals <= 0 || equals == candidate.length() - 1) {
                throw new GeneralSecurityException("the file " + file + " does not contain a valid Ed25519 public key");
            }
            try {
                return UpdateSignatures.decodePublicKey(candidate.substring(equals + 1).strip());
            } catch (GeneralSecurityException | IllegalArgumentException invalid) {
                throw new GeneralSecurityException("the file " + file
                        + " does not contain a valid Ed25519 public key", invalid);
            }
        }
    }

    /** Row counts per table from H2's catalogue, so a log shows the records before and after. */
    public static String recordCounts(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(RECORD_COUNTS)) {
            long total = 0;
            int tables = 0;
            StringBuilder nonEmpty = new StringBuilder();
            while (rows.next()) {
                String table = rows.getString(1);
                if (table == null || table.toLowerCase(Locale.ROOT).startsWith("flyway_schema_history")) {
                    continue;
                }
                long count = rows.getLong(2);
                tables++;
                total += count;
                if (count > 0) {
                    nonEmpty.append(' ').append(table).append('=').append(count);
                }
            }
            return "tables=" + tables + " rows=" + total + nonEmpty;
        } catch (SQLException unavailable) {
            return "record counts unavailable: " + unavailable.getMessage();
        }
    }
}

package com.flowerfarm.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Requires a valid Windows Authenticode signature whose certificate subject
 * contains the expected publisher text. Enabled by setting
 * {@code flowerfarm.updates.authenticode-subject} once Microsoft Artifact
 * Signing is in place. Not used for macOS packages.
 */
public final class AuthenticodePublisherVerifier implements PublisherVerifier {
    static final String FILE_ENVIRONMENT = "FLOWERSFOREVER_SIGNATURE_FILE";
    static final int MAX_OUTPUT_BYTES = 16384;
    // Installer paths are data in the child environment, never PowerShell source.
    private static final String SIGNATURE_SCRIPT = "$ErrorActionPreference = 'Stop'; "
            + "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); "
            + "$s = Get-AuthenticodeSignature -LiteralPath $env:FLOWERSFOREVER_SIGNATURE_FILE; "
            + "if ($s.Status -ne 'Valid' -or $null -eq $s.SignerCertificate) "
            + "{ 'INVALID ' + $s.Status; exit 1 } "
            + "else { 'VALID ' + $s.SignerCertificate.Subject; exit 0 }";

    @FunctionalInterface
    interface ProcessFactory {
        Process start(Path installer) throws IOException;
    }
    private final String expectedSubject;
    private final ProcessFactory processes;
    private final Duration timeout;

    public AuthenticodePublisherVerifier(String expectedSubject) {
        this(expectedSubject, installer -> signatureCommand(installer,
                TrustedSystemCommands.windowsPowerShell()).start(), Duration.ofSeconds(60));
    }

    AuthenticodePublisherVerifier(String expectedSubject, ProcessFactory processes, Duration timeout) {
        if (expectedSubject == null || expectedSubject.isBlank()) {
            throw new IllegalArgumentException("Expected publisher subject is required");
        }
        if (processes == null || timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("A bounded signature-check process is required");
        }
        this.expectedSubject = expectedSubject.trim();
        this.processes = processes;
        this.timeout = timeout;
    }

    static ProcessBuilder signatureCommand(Path installer, Path powershell) {
        ProcessBuilder command = new ProcessBuilder(List.of(powershell.toString(), "-NoProfile", "-NonInteractive",
                "-Command", SIGNATURE_SCRIPT)).redirectErrorStream(true);
        command.environment().put(FILE_ENVIRONMENT, installer.toAbsolutePath().normalize().toString());
        return command;
    }

    @Override
    public void verify(Path file, UpdatePlatform platform) throws UpdateVerificationException {
        if (platform != UpdatePlatform.WINDOWS_MSI) {
            return;
        }
        Process process = null;
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "flowersforever-signature-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> output = null;
        try {
            if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The installer file is unavailable.");
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            process = processes.start(file.toAbsolutePath().normalize());
            Process started = process;
            output = reader.submit(() -> {
                byte[] bytes = started.getInputStream().readNBytes(MAX_OUTPUT_BYTES + 1);
                if (bytes.length > MAX_OUTPUT_BYTES) {
                    throw new IOException("The signature check returned too much output.");
                }
                return new String(bytes, StandardCharsets.UTF_8).trim();
            });
            if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException();
            }
            String result = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (process.exitValue() != 0 || !result.startsWith("VALID ") || !result.contains(expectedSubject)) {
                throw new UpdateVerificationException(
                        "The installer is not signed by the expected publisher, so it was not used.");
            }
        } catch (TimeoutException timedOut) {
            throw new UpdateVerificationException("The installer's Windows signature check timed out.");
        } catch (IOException | ExecutionException failure) {
            throw new UpdateVerificationException("The installer's Windows signature could not be checked.", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UpdateVerificationException("The installer's Windows signature check was interrupted.");
        } finally {
            if (process != null) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                    // Destruction is the primary cancellation of a blocked process pipe.
                }
            }
            if (output != null) {
                output.cancel(true);
            }
            reader.shutdownNow();
        }
    }

    private static long remaining(long deadline) throws TimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new TimeoutException();
        }
        return nanos;
    }
}

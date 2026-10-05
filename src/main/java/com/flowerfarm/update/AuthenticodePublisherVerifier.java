package com.flowerfarm.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Requires a valid Windows Authenticode signature whose certificate subject
 * contains the expected publisher text. Enabled by setting
 * {@code flowerfarm.updates.authenticode-subject} once Microsoft Artifact
 * Signing is in place. Not used for macOS packages.
 */
public final class AuthenticodePublisherVerifier implements PublisherVerifier {

    private final String expectedSubject;

    public AuthenticodePublisherVerifier(String expectedSubject) {
        if (expectedSubject == null || expectedSubject.isBlank()) {
            throw new IllegalArgumentException("Expected publisher subject is required");
        }
        this.expectedSubject = expectedSubject.trim();
    }

    @Override
    public void verify(Path file, UpdatePlatform platform) throws UpdateVerificationException {
        if (platform != UpdatePlatform.WINDOWS_MSI) {
            return;
        }
        String literal = file.toAbsolutePath().toString().replace("'", "''");
        String script = "$s = Get-AuthenticodeSignature -LiteralPath '" + literal + "'; "
                + "if ($s.Status -ne 'Valid' -or $null -eq $s.SignerCertificate) { 'INVALID ' + $s.Status } "
                + "else { 'VALID ' + $s.SignerCertificate.Subject }";
        String output;
        try {
            Process process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-Command", script)).redirectErrorStream(true).start();
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new UpdateVerificationException("The installer's Windows signature check timed out.");
            }
        } catch (IOException failure) {
            throw new UpdateVerificationException("The installer's Windows signature could not be checked.",
                    failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UpdateVerificationException("The installer's Windows signature check was interrupted.");
        }
        if (!output.startsWith("VALID ") || !output.contains(expectedSubject)) {
            throw new UpdateVerificationException(
                    "The installer is not signed by the expected publisher, so it was not used.");
        }
    }
}

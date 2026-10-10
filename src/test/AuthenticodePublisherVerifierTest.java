package com.flowerfarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** All processes are fakes; no PowerShell or signature tools are started. */
class AuthenticodePublisherVerifierTest {
    @TempDir Path temp;

    @Test
    void signatureScriptIsConstantAndInstallerMetacharactersRemainEnvironmentData() throws Exception {
        Path normal = Files.writeString(temp.resolve("normal.msi"), "fixture");
        Path unusual = Files.writeString(temp.resolve("file's;$PWNED=1.msi"), "fixture");
        Path powershell = Path.of("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe");
        ProcessBuilder first = AuthenticodePublisherVerifier.signatureCommand(normal, powershell);
        ProcessBuilder second = AuthenticodePublisherVerifier.signatureCommand(unusual, powershell);
        assertThat(second.command()).isEqualTo(first.command());
        assertThat(second.command().get(4)).contains("-LiteralPath $env:FLOWERSFOREVER_SIGNATURE_FILE")
                .doesNotContain(unusual.toString());
        assertThat(second.environment()).containsEntry(AuthenticodePublisherVerifier.FILE_ENVIRONMENT,
                unusual.toAbsolutePath().normalize().toString());
        AtomicReference<Path> checked = new AtomicReference<>();
        new AuthenticodePublisherVerifier("CN=Flower Farm", installer -> {
            checked.set(installer);
            return new FakeProcess("VALID CN=Flower Farm", 0, true);
        }, Duration.ofSeconds(1)).verify(unusual, UpdatePlatform.WINDOWS_MSI);
        assertThat(checked).hasValue(unusual.toAbsolutePath().normalize());
    }

    @Test
    void refusesInvalidPublisherAndNonzeroToolExit() throws Exception {
        Path file = Files.writeString(temp.resolve("installer.msi"), "fixture");
        for (FakeProcess process : new FakeProcess[] {
                new FakeProcess("INVALID NotSigned", 0, true),
                new FakeProcess("VALID CN=Someone Else", 0, true),
                new FakeProcess("VALID CN=Flower Farm", 1, true)}) {
            assertThatThrownBy(() -> verifier(process).verify(file, UpdatePlatform.WINDOWS_MSI))
                    .isInstanceOf(UpdateVerificationException.class).hasMessageContaining("expected publisher");
        }
    }

    @Test
    void refusesOversizedOutputAndCancelsTimedOutProcess() throws Exception {
        Path file = Files.writeString(temp.resolve("installer.msi"), "fixture");
        FakeProcess oversized = new FakeProcess("VALID CN=Flower Farm "
                + "x".repeat(AuthenticodePublisherVerifier.MAX_OUTPUT_BYTES), 0, true);
        assertThatThrownBy(() -> verifier(oversized).verify(file, UpdatePlatform.WINDOWS_MSI))
                .hasMessageContaining("could not be checked");
        FakeProcess stalled = new FakeProcess("", 0, false);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> verifier(stalled).verify(file, UpdatePlatform.WINDOWS_MSI))
                        .hasMessageContaining("timed out"));
        assertThat(stalled.destroyed).isTrue();
    }

    private AuthenticodePublisherVerifier verifier(FakeProcess process) {
        return new AuthenticodePublisherVerifier("CN=Flower Farm", installer -> process, Duration.ofSeconds(2));
    }

    @Test
    void outputReadIsCoveredByTheDeadlineEvenAfterTheToolExits() throws Exception {
        Path file = Files.writeString(temp.resolve("installer.msi"), "fixture");
        BlockingOutput output = new BlockingOutput();
        FakeProcess process = new FakeProcess(output, 0, true);
        AuthenticodePublisherVerifier verifier = new AuthenticodePublisherVerifier("CN=Flower Farm",
                installer -> process, Duration.ofMillis(50));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> verifier.verify(file, UpdatePlatform.WINDOWS_MSI))
                        .hasMessageContaining("timed out"));
        assertThat(output.closed).isTrue();
    }

    private static final class BlockingOutput extends InputStream {
        private boolean closed;
        @Override public synchronized int read() throws IOException {
            while (!closed) {
                try { wait(); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            return -1;
        }
        @Override public synchronized void close() { closed = true; notifyAll(); }
    }

    private static final class FakeProcess extends Process {
        private final InputStream output;
        private final int exit;
        private final boolean completes;
        private boolean destroyed;
        FakeProcess(String text, int exit, boolean completes) {
            this(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), exit, completes);
        }
        FakeProcess(InputStream output, int exit, boolean completes) {
            this.output = output;
            this.exit = exit;
            this.completes = completes;
        }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long time, TimeUnit unit) { return completes; }
        @Override public int exitValue() { return exit; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return !completes && !destroyed; }
    }
}

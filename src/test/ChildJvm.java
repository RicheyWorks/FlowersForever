package com.flowerfarm.safety;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Starts {@link CrashChild} in its own JVM and kills it without any chance to clean up. */
final class ChildJvm implements AutoCloseable {

    private final Process process;
    private final BufferedReader output;
    private String last = "";
    private final StringBuilder seen = new StringBuilder();

    private ChildJvm(Process process) {
        this.process = process;
        this.output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    static ChildJvm start(String... args) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-Djava.awt.headless=true",
                "-cp", System.getProperty("java.class.path"), CrashChild.class.getName()));
        command.addAll(List.of(args));
        return new ChildJvm(new ProcessBuilder(command).redirectErrorStream(true).start());
    }

    /** Reads output until a line matches; returns that line. Fails if the child ends first. */
    String awaitLine(Predicate<String> wanted) throws IOException {
        String line;
        while ((line = output.readLine()) != null) {
            if (seen.length() < 4000) {
                seen.append(line).append('\n');
            }
            if (line.startsWith("SAVED ") || line.startsWith("WROTE ") || line.equals("OPEN")) {
                last = line;
            }
            if (wanted.test(line)) {
                return line;
            }
        }
        throw new IllegalStateException("The child JVM stopped early; output:\n" + seen);
    }

    /** The newest progress line the child printed before it was killed. */
    String lastProgress() {
        return last;
    }

    /** Failure context after a hard kill; includes whether termination was observed. */
    String processStatus() {
        return "child pid=" + process.pid() + ", alive=" + process.isAlive()
                + ", exit=" + (process.isAlive() ? "pending" : process.exitValue())
                + ", last progress=" + last;
    }

    /** Hard kill (TerminateProcess on Windows, SIGKILL elsewhere), then drain what was already printed. */
    void kill() throws Exception {
        process.destroyForcibly();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The child JVM did not stop");
        }
        String line;
        while ((line = output.readLine()) != null) {
            if (line.startsWith("SAVED ") || line.startsWith("WROTE ")) {
                last = line;
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (process.isAlive()) {
            kill();
        }
    }
}

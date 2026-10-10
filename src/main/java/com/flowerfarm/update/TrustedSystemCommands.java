package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves fixed operating-system tools without searching the application PATH. */
final class TrustedSystemCommands {
    private TrustedSystemCommands() { }

    static Path windowsCommandInterpreter() throws IOException {
        return windowsTool("cmd.exe");
    }

    static Path windowsPowerShell() throws IOException {
        return windowsTool("WindowsPowerShell", "v1.0", "powershell.exe");
    }

    private static Path windowsTool(String... parts) throws IOException {
        String configuredRoot = System.getenv("SystemRoot");
        Path root = Path.of(configuredRoot == null || configuredRoot.isBlank() ? "C:\\Windows" : configuredRoot);
        if (!root.isAbsolute()) {
            throw new IOException("The Windows system directory is unavailable.");
        }
        Path tool = root.normalize().resolve("System32");
        for (String part : parts) {
            tool = tool.resolve(part);
        }
        if (!Files.isRegularFile(tool) || !Files.isExecutable(tool)) {
            throw new IOException("The required Windows system tool is unavailable.");
        }
        return tool.toRealPath();
    }
}

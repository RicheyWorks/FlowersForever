package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;

/** Resolves fixed operating-system tools without searching the application PATH. */
final class TrustedSystemCommands {
    private TrustedSystemCommands() { }

    static Path windowsCommandInterpreter() throws IOException {
        return windowsTool("cmd.exe");
    }

    static Path windowsPowerShell() throws IOException {
        return windowsTool("WindowsPowerShell", "v1.0", "powershell.exe");
    }

    static Path posixShell() throws IOException {
        // A POSIX filesystem has one system root. Resolve its standard bin tool
        // structurally, with no application setting, working directory or PATH.
        if (!FileSystems.getDefault().getSeparator().equals("/")) {
            throw new IOException("The POSIX system directory is unavailable.");
        }
        Iterator<Path> roots = FileSystems.getDefault().getRootDirectories().iterator();
        if (!roots.hasNext()) {
            throw new IOException("The POSIX system directory is unavailable.");
        }
        Path root = roots.next();
        if (roots.hasNext() || !root.isAbsolute() || root.getNameCount() != 0) {
            throw new IOException("The POSIX system directory is unavailable.");
        }
        return requiredTool(root.resolve("bin").resolve("sh"));
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
        return requiredTool(tool);
    }

    private static Path requiredTool(Path tool) throws IOException {
        if (!Files.isRegularFile(tool) || !Files.isExecutable(tool)) {
            throw new IOException("The required operating-system tool is unavailable.");
        }
        return tool.toRealPath();
    }
}

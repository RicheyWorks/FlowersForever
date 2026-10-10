package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Hands a verified installer to the operating system. Implementations write a
 * small script into the updates folder that waits for this process to exit,
 * installs the package, and opens FlowersForever again. Only the script is
 * started here; the app exits right after.
 */
public interface PlatformInstaller {

    enum Purpose { UPDATE, ROLLBACK }

    /** True when this copy was installed by a FlowersForever installer and can update itself. */
    boolean canInstall();

    /** Writes and starts the hand-off script. Returns the script path. */
    Path launch(VerifiedPackage installer, Path workDirectory, Purpose purpose) throws IOException;

    /** Process boundary so tests can capture commands instead of running them. */
    @FunctionalInterface
    interface ProcessStarter {
        void start(List<String> command, Path workingDirectory) throws IOException;

        static ProcessStarter system() {
            return (command, workingDirectory) -> {
                InstallerProcessCommand admitted = InstallerProcessCommand.forThisSystem(command, workingDirectory);
                new ProcessBuilder(admitted.arguments())
                    .directory(admitted.directory().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            };
        }
    }

    static PlatformInstaller unsupported() {
        return new PlatformInstaller() {
            @Override
            public boolean canInstall() {
                return false;
            }

            @Override
            public Path launch(VerifiedPackage installer, Path workDirectory, Purpose purpose) throws IOException {
                throw new IOException(UpdateMessages.CANNOT_INSTALL_HERE);
            }
        };
    }

    /** Rejects characters that could change the meaning of a generated script line. */
    static String scriptSafe(Path path) throws IOException {
        String value = path.toAbsolutePath().normalize().toString();
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '%' || c == '!' || c == '`' || c == '$' || c == '\n' || c == '\r' || c == '^') {
                throw new IOException("Path contains a character the installer hand-off cannot use: " + value);
            }
        }
        return value;
    }

    /** cmd /c also interprets these operators in an unquoted single script argument. */
    static String windowsScriptSafe(Path path) throws IOException {
        String value = scriptSafe(path);
        for (char c : value.toCharArray()) {
            if (c == '&' || c == '|' || c == '<' || c == '>' || c == '(' || c == ')') {
                throw new IOException("Path contains a character the installer hand-off cannot use: " + value);
            }
        }
        return value;
    }
}

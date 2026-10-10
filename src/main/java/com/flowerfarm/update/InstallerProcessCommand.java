package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** An admitted OS interpreter and one generated, local installer hand-off. */
record InstallerProcessCommand(List<String> arguments, Path directory) {
    private static final long MAX_SCRIPT_BYTES = 65536;

    static InstallerProcessCommand forThisSystem(List<String> command, Path directory) throws IOException {
        UpdatePlatform platform = UpdatePlatform.forOperatingSystem(System.getProperty("os.name"))
                .orElseThrow(() -> new IOException(UpdateMessages.CANNOT_INSTALL_HERE));
        Path executable = platform == UpdatePlatform.WINDOWS_MSI
                ? TrustedSystemCommands.windowsCommandInterpreter() : Path.of("/bin/sh");
        return validate(command, directory, platform, executable);
    }

    static InstallerProcessCommand validate(List<String> command, Path directory,
                                             UpdatePlatform platform, Path executable) throws IOException {
        boolean windows = platform == UpdatePlatform.WINDOWS_MSI;
        int expectedSize = windows ? 4 : 2;
        if (command == null || command.size() != expectedSize || directory == null || !directory.isAbsolute()
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !executable.isAbsolute() || !Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("The installer hand-off command is unavailable.");
        }
        for (String argument : command) {
            if (argument == null || argument.length() > 8192 || argument.indexOf('\0') >= 0) {
                throw new IOException("The installer hand-off command is invalid.");
            }
        }
        Path requestedExecutable;
        Path script;
        try {
            requestedExecutable = Path.of(command.get(0));
            script = Path.of(command.get(expectedSize - 1));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("The installer hand-off command is invalid.", invalid);
        }
        if (!requestedExecutable.isAbsolute() || !Files.isSameFile(requestedExecutable, executable)
                || (windows && !(command.get(1).equals("/d") && command.get(2).equals("/c")))) {
            throw new IOException("The installer hand-off interpreter is not recognized.");
        }
        Path work = directory.toRealPath();
        if (!script.isAbsolute() || !Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)
                || !script.normalize().getParent().toRealPath().equals(work) || Files.size(script) > MAX_SCRIPT_BYTES) {
            throw new IOException("The installer hand-off must be a generated script in its updates folder.");
        }
        String name = script.getFileName().toString();
        String extension = windows ? ".cmd" : ".sh";
        String prefix = name.startsWith("apply-update-") ? "apply-update-" : "apply-rollback-";
        if (!name.startsWith(prefix) || !name.endsWith(extension)) {
            throw new IOException("The installer hand-off script name is invalid.");
        }
        try {
            AppVersion.parse(name.substring(prefix.length(), name.length() - extension.length()));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("The installer hand-off script version is invalid.", invalid);
        }
        String admittedScript = windows ? PlatformInstaller.windowsScriptSafe(script)
                : PlatformInstaller.scriptSafe(script);
        List<String> admitted = windows
                ? List.of(executable.toString(), "/d", "/c", admittedScript)
                : List.of(executable.toString(), admittedScript);
        return new InstallerProcessCommand(admitted, work);
    }
}

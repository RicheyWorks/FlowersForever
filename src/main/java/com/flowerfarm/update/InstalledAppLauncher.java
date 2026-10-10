package com.flowerfarm.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Reopens only the installed native launcher that owns this running process. */
public final class InstalledAppLauncher {
    private InstalledAppLauncher() { }

    public static boolean canRelaunch(Path launcher) {
        try {
            validatedLauncher(launcher, currentCommand());
            return true;
        } catch (IOException | IllegalArgumentException unavailable) {
            return false;
        }
    }

    public static void relaunch(Path launcher) throws IOException {
        Path executable = validatedLauncher(launcher, currentCommand());
        Path directory = executable.getParent();
        if (directory == null) {
            throw new IOException("The installed launcher directory is unavailable.");
        }
        // One executable, no arguments and no shell. The startup property alone
        // cannot select a different program from the running packaged app.
        new ProcessBuilder(List.of(executable.toString()))
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static Optional<Path> currentCommand() {
        return ProcessHandle.current().info().command().map(Path::of);
    }

    static Path validatedLauncher(Path launcher, Optional<Path> runningCommand) throws IOException {
        if (launcher == null || !launcher.isAbsolute()) {
            throw new IOException("The installed launcher is unavailable.");
        }
        Path fileName = launcher.getFileName();
        if (fileName == null) {
            throw new IOException("The installed launcher is unavailable.");
        }
        String name = fileName.toString();
        if (!(name.equalsIgnoreCase("FlowersForever.exe") || name.equals("FlowersForever"))) {
            throw new IOException("The installed launcher name is not recognized.");
        }
        Path executable = launcher.normalize();
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(executable)
                || runningCommand.isEmpty() || !Files.isSameFile(executable, runningCommand.get())) {
            throw new IOException("The installed launcher does not own this process.");
        }
        return executable.toRealPath();
    }
}

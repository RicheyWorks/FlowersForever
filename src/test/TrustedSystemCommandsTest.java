package com.flowerfarm.update;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Filesystem validation only; no system tool is executed. */
class TrustedSystemCommandsTest {
    @Test
    void posixShellUsesTheStandardSystemRootOrRefusesThePlatform() throws Exception {
        if (FileSystems.getDefault().getSeparator().equals("/")) {
            Path tool = TrustedSystemCommands.posixShell();
            assertThat(Files.isSameFile(tool, Path.of("/bin/sh"))).isTrue();
            assertThat(tool.isAbsolute()).isTrue();
            assertThat(Files.isExecutable(tool)).isTrue();
        } else {
            assertThatThrownBy(TrustedSystemCommands::posixShell).isInstanceOf(IOException.class)
                    .hasMessageContaining("POSIX system directory");
        }
    }
}

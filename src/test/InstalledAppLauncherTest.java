package com.flowerfarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validation only: never relaunches an application. */
class InstalledAppLauncherTest {
    @TempDir Path temp;

    @Test
    void startupPathMustBeTheRecognizedExecutableOwningThisProcess() throws Exception {
        Path launcher = Files.writeString(temp.resolve("FlowersForever.exe"), "fixture");
        launcher.toFile().setExecutable(true);
        assertThat(InstalledAppLauncher.validatedLauncher(launcher, Optional.of(launcher)))
                .isEqualTo(launcher.toRealPath());
        Path other = Files.writeString(temp.resolve("other.exe"), "fixture");
        other.toFile().setExecutable(true);
        assertThatThrownBy(() -> InstalledAppLauncher.validatedLauncher(launcher, Optional.of(other)))
                .hasMessageContaining("does not own");
        assertThatThrownBy(() -> InstalledAppLauncher.validatedLauncher(other, Optional.of(other)))
                .hasMessageContaining("name");
        assertThatThrownBy(() -> InstalledAppLauncher.validatedLauncher(launcher, Optional.empty()))
                .hasMessageContaining("does not own");
        assertThatThrownBy(() -> InstalledAppLauncher.validatedLauncher(Path.of("FlowersForever.exe"),
                Optional.of(launcher))).hasMessageContaining("unavailable");
    }
}

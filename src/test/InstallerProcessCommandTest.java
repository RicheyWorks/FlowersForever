package com.flowerfarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admission only: neither OS interpreters nor installer scripts are executed. */
class InstallerProcessCommandTest {
    @TempDir Path temp;

    @Test
    void admitsOnlyTheFixedInterpreterAndOneGeneratedScriptInsideItsFolder() throws Exception {
        Path interpreter = Files.writeString(temp.resolve("cmd.exe"), "fixture");
        interpreter.toFile().setExecutable(true);
        Path work = Files.createDirectory(temp.resolve("update folder"));
        Path script = Files.writeString(work.resolve("apply-update-1.2.1.cmd"), "@echo off\n");
        List<String> valid = List.of(interpreter.toString(), "/d", "/c", script.toString());
        assertThat(InstallerProcessCommand.validate(valid, work, UpdatePlatform.WINDOWS_MSI, interpreter)
                .arguments()).containsExactlyElementsOf(valid);
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(interpreter.toString(), "/d", "/c", script.toString(), "extra"), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("unavailable");
        Path other = Files.writeString(temp.resolve("other.exe"), "fixture");
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(other.toString(), "/d", "/c", script.toString()), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("not recognized");
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(interpreter.toString(), "/c", "/c", script.toString()), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("not recognized");
        Path outside = Files.writeString(temp.resolve("apply-update-1.2.1.cmd"), "fixture");
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(interpreter.toString(), "/d", "/c", outside.toString()), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("updates folder");
        Path arbitrary = Files.writeString(work.resolve("other.cmd"), "fixture");
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(interpreter.toString(), "/d", "/c", arbitrary.toString()), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("name");
        Path oversized = Files.writeString(work.resolve("apply-rollback-1.2.1.cmd"), "x".repeat(65537));
        assertThatThrownBy(() -> InstallerProcessCommand.validate(
                List.of(interpreter.toString(), "/d", "/c", oversized.toString()), work,
                UpdatePlatform.WINDOWS_MSI, interpreter)).hasMessageContaining("updates folder");
    }

    @Test
    void refusesCmdOperatorsBeforeTheHandOffCanBeStarted() throws Exception {
        for (String folder : List.of("farm&whoami", "farm(parentheses)")) {
            Path path = temp.resolve(folder).resolve("apply-update-1.2.1.cmd");
            assertThatThrownBy(() -> PlatformInstaller.windowsScriptSafe(path)).hasMessageContaining("cannot use");
        }
        assertThat(PlatformInstaller.scriptSafe(temp.resolve("mac(farm)&notes"))).contains("mac(farm)&notes");
    }
}

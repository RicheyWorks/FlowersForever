package com.flowerfarm.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Installer hand-off scripts (generated, never run in tests)")
class UpdateInstallerHandoffTest {

    @TempDir
    Path temp;

    private final List<List<String>> started = new ArrayList<>();

    @Test
    @DisplayName("Windows: waits for this process, runs msiexec /passive with a log, then reopens the app")
    void windowsScript() throws Exception {
        Path launcher = temp.resolve("Program Files/FlowersForever/FlowersForever.exe");
        WindowsMsiInstaller installer = new WindowsMsiInstaller(Optional.of(launcher),
                (command, directory) -> started.add(command), 4242, Path.of("C:\\Windows"));
        Path msi = UpdateTestSupport.fakeInstaller(temp.resolve("packages"), "FlowersForever-1.2.1.msi", "msi");
        VerifiedPackage pkg = new VerifiedPackage(UpdatePlatform.WINDOWS_MSI, AppVersion.parse("1.2.1"), msi,
                UpdateSignatures.sha256Hex(msi), Files.size(msi));

        Path script = installer.launch(pkg, temp.resolve("handoff"), PlatformInstaller.Purpose.UPDATE);

        String content = Files.readString(script);
        assertThat(installer.canInstall()).isTrue();
        assertThat(script.getFileName().toString()).isEqualTo("apply-update-1.2.1.cmd");
        assertThat(content).contains("PID eq 4242", "msiexec.exe\" /i \"" + msi.toAbsolutePath(),
                "/passive /norestart /l*v", "msiexec-update-1.2.1.exitcode", "start \"\" \"" + launcher.toAbsolutePath());
        assertThat(content).doesNotContain("/quiet", "powershell", "-ExecutionPolicy");
        assertThat(started).singleElement().satisfies(command ->
                assertThat(command).containsExactly("C:\\Windows" + java.io.File.separator + "System32"
                        + java.io.File.separator + "cmd.exe", "/d", "/c", script.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("Windows: no launcher (developer run) means no install, and unsafe paths are refused")
    void windowsRefusals() throws IOException {
        assertThat(new WindowsMsiInstaller(Optional.empty(), (c, d) -> started.add(c), 1, temp).canInstall()).isFalse();
        Path root = temp.toAbsolutePath().getRoot();
        assertThat(new WindowsMsiInstaller(Optional.of(root), (c, d) -> started.add(c), 1, temp).canInstall())
                .as("a launcher path with no file name").isFalse();
        assertThat(new WindowsMsiInstaller(Optional.of(temp.resolve("flowerfarm.jar")), (c, d) -> started.add(c), 1,
                temp).canInstall()).as("a launcher that is not an .exe").isFalse();
        assertThat(new WindowsMsiInstaller(Optional.of(temp.resolve("FLOWERSFOREVER.EXE")), (c, d) -> started.add(c),
                1, temp).canInstall()).as("the .exe check ignores case").isTrue();
        WindowsMsiInstaller installer = new WindowsMsiInstaller(Optional.of(temp.resolve("FlowersForever.exe")),
                (c, d) -> started.add(c), 1, temp);
        Path odd = UpdateTestSupport.fakeInstaller(temp.resolve("100%"), "FlowersForever-1.2.1.msi", "msi");
        VerifiedPackage pkg = new VerifiedPackage(UpdatePlatform.WINDOWS_MSI, AppVersion.parse("1.2.1"), odd, "x", 3);

        assertThatThrownBy(() -> installer.launch(pkg, temp.resolve("handoff"), PlatformInstaller.Purpose.UPDATE))
                .hasMessageContaining("cannot use");
        assertThat(started).isEmpty();
    }

    @Test
    @DisplayName("macOS: swaps the .app bundle only after the new copy succeeds, then reopens it")
    void macScript() throws Exception {
        Path launcher = temp.resolve("Applications/FlowersForever.app/Contents/MacOS/FlowersForever");
        MacDmgInstaller installer = new MacDmgInstaller(Optional.of(launcher), (c, d) -> started.add(c), 77);
        Path dmg = UpdateTestSupport.fakeInstaller(temp.resolve("packages"), "FlowersForever-1.2.1.dmg", "dmg");
        VerifiedPackage pkg = new VerifiedPackage(UpdatePlatform.MACOS_DMG, AppVersion.parse("1.2.1"), dmg,
                UpdateSignatures.sha256Hex(dmg), Files.size(dmg));

        Path script = installer.launch(pkg, temp.resolve("handoff"), PlatformInstaller.Purpose.ROLLBACK);

        assertThat(installer.canInstall()).isTrue();
        assertThat(Files.readString(script)).contains("kill -0 77", "hdiutil attach -nobrowse -readonly",
                "ditto", ".ff-previous", "open \"$APP\"");
        assertThat(started).singleElement().satisfies(command -> assertThat(command.get(0)).isEqualTo("/bin/sh"));
        assertThat(new MacDmgInstaller(Optional.of(temp.resolve("flowerfarm.jar")), (c, d) -> { }, 1).canInstall())
                .isFalse();
    }

    @Test
    @DisplayName("the platform comes from the operating system name")
    void platformDetection() {
        assertThat(UpdatePlatform.forOperatingSystem("Windows 11")).contains(UpdatePlatform.WINDOWS_MSI);
        assertThat(UpdatePlatform.forOperatingSystem("Mac OS X")).contains(UpdatePlatform.MACOS_DMG);
        assertThat(UpdatePlatform.forOperatingSystem("Linux")).isEmpty();
        assertThat(PlatformInstallers.forThisProcess(Optional.empty(), Optional.empty()).canInstall()).isFalse();
    }
}

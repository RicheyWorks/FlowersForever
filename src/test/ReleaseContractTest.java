package com.flowerfarm.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Release artifact contracts")
class ReleaseContractTest {

    @Test
    @DisplayName("CycloneDX outputs match the files published by release automation")
    void sbomOutputMatchesReleaseWorkflow() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String release = Files.readString(Path.of(".github", "workflows", "release.yml"));
        String ci = Files.readString(Path.of(".github", "workflows", "ci.yml"));

        assertThat(pom).contains(
                "<outputDirectory>${project.build.directory}</outputDirectory>",
                "<outputName>bom</outputName>",
                "<outputFormat>all</outputFormat>");
        assertThat(release).contains(
                "test -s target/bom.json", "test -s target/bom.xml",
                "target/bom.json", "target/bom.xml", "target/SHA256SUMS.txt");
        assertThat(ci).contains("target/bom.*", "if-no-files-found: error");
    }

    @Test
    @DisplayName("installer staging accepts only the executable fat JAR")
    void installerStagesOneFatJar() throws Exception {
        String workflow = Files.readString(
                Path.of(".github", "workflows", "installers.yml"));

        assertThat(workflow)
                .contains("grep -v '\\.original$'", "target/jpackage-input/",
                        "find target/jpackage-input -maxdepth 1 -type f");
        assertThat(workflow.lines().map(String::trim).toList())
                .doesNotContain("--input target \\");
    }

    @Test
    @DisplayName("packaged runtime uses durable app data and release recovery artifacts")
    void packagedRuntimeHasDataAndRecoveryContracts() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "installers.yml"));
        String build = Files.readString(Path.of("scripts", "build-release.ps1"));
        String backup = Files.readString(Path.of("scripts", "backup-data.ps1"));
        String restore = Files.readString(Path.of("scripts", "restore-data.ps1"));
        String application = Files.readString(Path.of("src", "main", "java", "com",
                "flowerfarm", "FlowerFarmApp.java"));

        assertThat(workflow).contains("-Dflowerfarm.packaged=true", "SHA256SUMS-installers.txt");
        assertThat(build).contains("--type app-image", "--type msi", "SHA256SUMS.txt",
                "bom.json", "bom.xml", "-Dflowerfarm.packaged=true");
        assertThat(backup).contains("FlowersForever\\data", "Get-FileHash", "SHA256");
        assertThat(restore).contains("Get-FileHash", ".before-restore-", "flowerfarm.mv.db");
        assertThat(application).contains("--release-smoke", "release-smoke.ok", "context.close()");
        assertThat(Path.of("docs", "DEPLOYMENT.md")).isRegularFile();
        assertThat(Path.of("docs", "BACKUP_RESTORE.md")).isRegularFile();
        assertThat(Path.of("docs", "KNOWN_LIMITATIONS.md")).isRegularFile();
        assertThat(Path.of("docs", "RELEASE_NOTES.md")).isRegularFile();
    }
}

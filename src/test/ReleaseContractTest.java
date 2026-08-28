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
                "release-assets/bom.json", "release-assets/bom.xml",
                "release-assets/SHA256SUMS.txt");
        assertThat(ci).contains("target/bom.*", "if-no-files-found: error");
    }

    @Test
    @DisplayName("installers consume only the already-verified executable JAR")
    void installersConsumeVerifiedJar() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));

        assertThat(workflow)
                .contains("needs: verify", "name: verified-jar", "target/jpackage-input",
                        "find target/jpackage-input -maxdepth 1 -type f",
                        "--input target/jpackage-input");
        assertThat(workflow).doesNotContain("mvn -B clean package -DskipTests");
    }

    @Test
    @DisplayName("release tag, POM, JAR, and installer versions must agree")
    void releaseVersionIsLocked() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));

        assertThat(workflow).contains(
                "POM_VERSION=\"$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)\"",
                "test \"$POM_VERSION\" = \"$TAG_VERSION\"",
                "flowerfarm-manager-${VERSION}.jar",
                "--app-version \"$VERSION\"");
    }

    @Test
    @DisplayName("installer candidates are launched on both platforms without publishing")
    void installerCandidatesAreSmokeTestedWithoutPublishing() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));

        assertThat(workflow).contains(
                "pull_request:",
                "workflow_dispatch:",
                "Validate complete candidate bundle (no release)",
                "--type app-image",
                "--release-smoke",
                "release-smoke.ok",
                "FlowersForever.exe",
                "FlowersForever.app/Contents/MacOS/FlowersForever",
                "if: github.event_name != 'push'",
                "if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')");
    }

    @Test
    @DisplayName("tagged publishing requires Windows signing and Apple notarization")
    void taggedPublishingRequiresPlatformTrust() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));

        assertThat(workflow).contains(
                "environment: release-signing",
                "azure/login@v3",
                "azure/artifact-signing-action@v2",
                "Get-AuthenticodeSignature",
                "--mac-sign",
                "xcrun notarytool submit",
                "xcrun stapler staple",
                "spctl --assess --type execute",
                "needs: [verify, windows-release, macos-release]");
        assertThat(workflow).doesNotContain("The native installers are not yet code-signed");
        assertThat(Path.of("docs", "SIGNING_SETUP.md")).isRegularFile();
    }

    @Test
    @DisplayName("packaged runtime uses durable app data and release recovery artifacts")
    void packagedRuntimeHasDataAndRecoveryContracts() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));
        String build = Files.readString(Path.of("scripts", "build-release.ps1"));
        String backup = Files.readString(Path.of("scripts", "backup-data.ps1"));
        String restore = Files.readString(Path.of("scripts", "restore-data.ps1"));
        String application = Files.readString(Path.of("src", "main", "java", "com",
                "flowerfarm", "FlowerFarmApp.java"));

        assertThat(workflow).contains("-Dflowerfarm.packaged=true", "SHA256SUMS.txt",
                "THIRD-PARTY.txt", "needs: [verify, installers]");
        assertThat(build).contains("--type app-image", "--type msi", "SHA256SUMS.txt",
                "bom.json", "bom.xml", "THIRD-PARTY.txt", "*-SNAPSHOT",
                "Remove-Item -LiteralPath $resolvedDist -Recurse -Force",
                "-Dflowerfarm.packaged=true");
        assertThat(backup).contains("FlowersForever\\data", "Get-FileHash", "SHA256");
        assertThat(restore).contains("Get-FileHash", ".before-restore-", "flowerfarm.mv.db");
        assertThat(application).contains("--release-smoke", "release-smoke.ok", "context.close()");
        assertThat(Path.of("docs", "DEPLOYMENT.md")).isRegularFile();
        assertThat(Path.of("docs", "BACKUP_RESTORE.md")).isRegularFile();
        assertThat(Path.of("docs", "KNOWN_LIMITATIONS.md")).isRegularFile();
        assertThat(Path.of("docs", "RELEASE_NOTES.md")).isRegularFile();
        assertThat(Path.of("docs", "SIGNING_SETUP.md")).isRegularFile();
    }

    @Test
    @DisplayName("customer H2 schema changes are versioned and Hibernate only validates")
    void databaseSchemaIsVersioned() throws Exception {
        String application = Files.readString(
                Path.of("src", "main", "resources", "application.properties"));

        assertThat(application).contains(
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.locations=classpath:db/migration/h2",
                "spring.flyway.baseline-on-migrate=true");
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V1__legacy_1_0_schema.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V2__add_order_handoff_type.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V3__farm_knowledge_log.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V4__season_plan.sql")).isRegularFile();
    }

    @Test
    @DisplayName("authenticated demo instructions never depend on built-in weak passwords")
    void authenticatedDemoUsesGeneratedCredentials() throws Exception {
        String demo = Files.readString(Path.of("docs", "DEMO.md"));

        assertThat(demo).contains(
                "FLOWERFARM_AUTH_USERS",
                "RandomNumberGenerator]::GetBytes(18)",
                "--server.address=127.0.0.1",
                "owner:$ownerPassword");
        assertThat(demo).doesNotContain(
                "farm:kitsap",
                "`kitsap`",
                "`harvest`",
                "FLOWERFARM_ALLOW_DEMO_CREDENTIALS");
    }
}

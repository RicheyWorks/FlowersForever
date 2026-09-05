package com.flowerfarm.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    @DisplayName("each native job checks out launcher assets before packaging or smoke")
    void nativeJobsHaveTheirLocalInputs() throws Exception {
        Map<?, ?> workflow = new Yaml().load(Files.readString(
                Path.of(".github", "workflows", "release.yml")));
        Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
        for (String jobName : List.of("installers", "windows-release", "macos-release")) {
            Map<?, ?> job = (Map<?, ?>) jobs.get(jobName);
            List<?> steps = (List<?>) job.get("steps");
            boolean checkedOut = false;
            boolean downloadedJar = false;
            for (Object entry : steps) {
                Map<?, ?> step = (Map<?, ?>) entry;
                String uses = String.valueOf(step.get("uses"));
                if (uses.startsWith("actions/checkout@")) {
                    checkedOut = true;
                }
                if (uses.startsWith("actions/download-artifact@")
                        && step.get("with") instanceof Map<?, ?> inputs
                        && "verified-jar".equals(inputs.get("name"))) {
                    downloadedJar = true;
                }
                String run = String.valueOf(step.get("run"));
                if (run.contains("jpackage ") || run.contains("scripts/test-mcp-launcher.ps1")) {
                    assertThat(checkedOut).as("%s / %s has source assets",
                            jobName, step.get("name")).isTrue();
                    assertThat(downloadedJar).as("%s uses the verified executable", jobName).isTrue();
                }
            }
            assertThat(checkedOut).as("%s obtains launcher assets", jobName).isTrue();
        }
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
    @DisplayName("candidate installers contain the app image exercised by the smoke gates")
    void candidateInstallersReuseTheSmokeTestedImage() throws Exception {
        Map<?, ?> workflow = new Yaml().load(Files.readString(
                Path.of(".github", "workflows", "release.yml")));
        Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
        Map<?, ?> installers = (Map<?, ?>) jobs.get("installers");
        Map<?, ?> strategy = (Map<?, ?>) installers.get("strategy");
        Map<?, ?> matrix = (Map<?, ?>) strategy.get("matrix");
        for (Object entry : (List<?>) matrix.get("include")) {
            Map<?, ?> platform = (Map<?, ?>) entry;
            String image = String.valueOf(platform.get("app_image"));
            assertThat(image).as("%s app image", platform.get("type"))
                    .startsWith("smoke-dist/");
            assertThat(String.valueOf(platform.get("launcher"))).startsWith(image + "/");
            assertThat(String.valueOf(platform.get("ai_launcher"))).startsWith(image + "/");
        }

        List<?> steps = (List<?>) installers.get("steps");
        int smokeIndex = -1;
        int packageIndex = -1;
        int imageBuilds = 0;
        for (int index = 0; index < steps.size(); index++) {
            Map<?, ?> step = (Map<?, ?>) steps.get(index);
            String run = String.valueOf(step.get("run"));
            if (run.contains("--type app-image")) {
                imageBuilds++;
                assertThat(run).contains("--dest smoke-dist");
            }
            if (run.contains("--release-smoke")) {
                smokeIndex = index;
                assertThat(run).contains("${{ matrix.launcher }}", "${{ matrix.ai_launcher }}",
                        "scripts/test-mcp-launcher.ps1", "release-smoke.ok");
            }
            if (run.contains("--type ${{ matrix.type }}")) {
                packageIndex = index;
                assertThat(run).contains("--app-image \"${{ matrix.app_image }}\"")
                        .doesNotContain("--input ", "--main-jar ", "--add-launcher ");
            }
        }
        assertThat(imageBuilds).isEqualTo(1);
        assertThat(smokeIndex).as("a smoke gate exists").isGreaterThanOrEqualTo(0);
        assertThat(packageIndex).as("packaging follows the successful smoke gate")
                .isGreaterThan(smokeIndex);
    }

    @Test
    @DisplayName("local Windows MSI packaging preserves the smoke-tested launcher bytes")
    void localMsiReusesTheSmokeTestedImage() throws Exception {
        String build = Files.readString(Path.of("scripts", "build-release.ps1"));
        int smokeIndex = build.indexOf("test-mcp-launcher.ps1");
        int packageIndex = build.indexOf("& jpackage --type msi");

        assertThat(smokeIndex).isGreaterThanOrEqualTo(0);
        assertThat(packageIndex).isGreaterThan(smokeIndex);
        String msiCommand = build.substring(packageIndex,
                build.indexOf("if ($LASTEXITCODE", packageIndex));
        assertThat(msiCommand).contains("--app-image $appImage", "--vendor RicheyWorks")
                .doesNotContain("--input ", "--main-jar ", "--add-launcher ");
        assertThat(build).contains("Compress-Archive -LiteralPath $appImage");
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
    @DisplayName("tagged MSI and both Windows launchers require one verified signing publisher")
    void windowsReleaseRequiresConsistentPublisher() throws Exception {
        Map<?, ?> workflow = new Yaml().load(Files.readString(
                Path.of(".github", "workflows", "release.yml")));
        Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
        Map<?, ?> windows = (Map<?, ?>) jobs.get("windows-release");
        boolean packagesSignedImage = false;
        boolean verifiesPublishers = false;
        for (Object entry : (List<?>) windows.get("steps")) {
            Map<?, ?> step = (Map<?, ?>) entry;
            String run = String.valueOf(step.get("run"));
            if (run.contains("--type msi")) {
                packagesSignedImage = true;
                assertThat(run).contains("--app-image signed-app-image/FlowersForever",
                        "--vendor RicheyWorks", "--app-version \"$VERSION\"")
                        .doesNotContain("--input ", "--main-jar ", "--add-launcher ");
            }
            if (run.contains("Get-AuthenticodeSignature -LiteralPath $msi")) {
                verifiesPublishers = true;
                assertThat(run).contains("$signature.Status -ne 'Valid'",
                        "$publisher = $signature.SignerCertificate.Subject",
                        "@('FlowersForever.exe', 'FlowersForeverAI.exe')",
                        "$launcherSignature.Status -ne 'Valid'",
                        "$launcherSignature.SignerCertificate.Subject -cne $publisher",
                        "throw \"$name and the MSI have different signing publishers.\"");
            }
        }
        assertThat(packagesSignedImage).isTrue();
        assertThat(verifiesPublishers).isTrue();
    }

    @Test
    @DisplayName("packaged runtime uses durable app data and release recovery artifacts")
    void packagedRuntimeHasDataAndRecoveryContracts() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));
        String build = Files.readString(Path.of("scripts", "build-release.ps1"));
        String backup = Files.readString(Path.of("scripts", "backup-data.ps1"));
        String restore = Files.readString(Path.of("scripts", "restore-data.ps1"));
        String recoveryGuide = Files.readString(
                Path.of("docs", "BACKUP_RESTORE.md"));
        String application = Files.readString(Path.of("src", "main", "java", "com",
                "flowerfarm", "FlowerFarmApp.java"));

        assertThat(workflow).contains("-Dflowerfarm.packaged=true", "SHA256SUMS.txt",
                "THIRD-PARTY.txt", "needs: [verify, installers]");
        assertThat(build).contains("--type app-image", "--type msi", "SHA256SUMS.txt",
                "bom.json", "bom.xml", "THIRD-PARTY.txt", "*-SNAPSHOT",
                "Remove-Item -LiteralPath $resolvedDist -Recurse -Force",
                "-Dflowerfarm.packaged=true");
        assertThat(backup).contains("FlowersForever\\data", "Get-FileHash", "SHA256",
                "Compress-Archive -LiteralPath $resolvedData");
        assertThat(restore).contains("Get-FileHash", ".before-restore-", "flowerfarm.mv.db");
        assertThat(recoveryGuide).contains("attachments", "photo metadata", "verified bytes");
        assertThat(application).contains("--release-smoke", "release-smoke.ok", "context.close()");
        assertThat(Path.of("docs", "DEPLOYMENT.md")).isRegularFile();
        assertThat(Path.of("docs", "BACKUP_RESTORE.md")).isRegularFile();
        assertThat(Path.of("docs", "KNOWN_LIMITATIONS.md")).isRegularFile();
        assertThat(Path.of("docs", "RELEASE_NOTES.md")).isRegularFile();
        assertThat(Path.of("docs", "SIGNING_SETUP.md")).isRegularFile();
    }

    @Test
    @DisplayName("native releases include and exercise a dedicated MCP stdio launcher")
    void nativeReleasesIncludeAiLauncher() throws Exception {
        String workflow = Files.readString(Path.of(".github", "workflows", "release.yml"));
        String build = Files.readString(Path.of("scripts", "build-release.ps1"));
        String smoke = Files.readString(Path.of("scripts", "test-mcp-launcher.ps1"));
        String generic = Files.readString(Path.of(
                "packaging", "FlowersForeverAI.properties"));
        String windows = Files.readString(Path.of(
                "packaging", "FlowersForeverAI-windows.properties"));

        assertThat(generic).contains(
                "arguments=--mcp", "-Djava.awt.headless=true",
                "-Dflowerfarm.packaged=true", "-Djdk.console=java.base");
        assertThat(windows).contains(generic.trim(), "win-console=true");
        assertThat(build).contains(
                "--add-launcher \"FlowersForeverAI=$aiLauncher\"",
                "FlowersForeverAI.exe");
        assertThat(workflow).contains(
                "--add-launcher FlowersForeverAI=",
                "FlowersForeverAI.exe",
                "Contents/MacOS/FlowersForeverAI",
                "scripts/test-mcp-launcher.ps1",
                "mcp-doctor",
                "mcp-ping");
        assertThat(smoke).contains(
                "RedirectStandardInput = $true",
                "RedirectStandardOutput = $true",
                "--mcp-doctor",
                "FlowersForever MCP stdio adapter ready",
                "FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ",
                "FLOWERFARM_HARNESS_ALLOW_MUTATIONS");
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
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V5__widen_activity_operation.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V6__production_studio.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V7__production_integrity.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V13__production_task_actuals.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V14__farm_data_import_receipts.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V15__worker_action_receipts.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V16__worker_inventory_counts.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V17__worker_order_handoffs.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V18__field_attachments.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V19__harvest_lot_traceability.sql")).isRegularFile();
        assertThat(Path.of("src", "main", "resources", "db", "migration", "h2",
                "V20__season_production_costs.sql")).isRegularFile();
    }

    @Test
    @DisplayName("harvest lot traceability ships with real QR label support")
    void harvestTraceabilityHasQrSupport() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String migration = Files.readString(Path.of("src", "main", "resources",
                "db", "migration", "h2", "V19__harvest_lot_traceability.sql"));

        assertThat(pom).contains(
                "<groupId>com.google.zxing</groupId>",
                "<artifactId>core</artifactId>");
        assertThat(migration).contains(
                "lot_code", "harvest_grade", "loss_quantity",
                "storage_location", "CREATE UNIQUE INDEX uq_harvest_lot_code");
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

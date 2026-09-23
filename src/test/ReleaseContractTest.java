package com.flowerfarm.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Release artifact contracts")
class ReleaseContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"candidate", "publish"})
    @DisplayName("release checksum gates exclude their manifest, survive reruns, and detect corruption")
    void checksumGatesVerifyPayloads(String jobName, @TempDir Path tempDir) throws Exception {
        Map<?, ?> workflow = new Yaml().load(Files.readString(
                Path.of(".github", "workflows", "release.yml")));
        Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
        Map<?, ?> job = (Map<?, ?>) jobs.get(jobName);
        String run = ((List<?>) job.get("steps")).stream()
                .map(entry -> (Map<?, ?>) entry)
                .filter(step -> String.valueOf(step.get("name")).contains("generate checksums"))
                .map(step -> String.valueOf(step.get("run")))
                .findFirst().orElseThrow()
                .replace("${{ needs.verify.outputs.version }}", "1.2.0");
        assertThat(run).contains("sha256sum --check SHA256SUMS.txt");

        Path assets = Files.createDirectory(tempDir.resolve(
                "candidate".equals(jobName) ? "candidate-assets" : "release-assets"));
        List<String> expected = new ArrayList<>();
        for (String name : List.of("flowerfarm-manager-1.2.0.jar", "bom.json", "bom.xml",
                "THIRD-PARTY.txt", "Flowers Forever-1.2.0.msi", "FlowersForever-1.2.0.dmg")) {
            Path asset = Files.writeString(assets.resolve(name), "fixture contents: " + name);
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(asset)));
            expected.add(digest + " *./" + name);
        }

        BashResult initial = runBash(tempDir, run);
        assertThat(initial.exitCode()).withFailMessage(initial.output()).isZero();
        Path manifest = assets.resolve("SHA256SUMS.txt");
        List<String> checksums = Files.readAllLines(manifest);
        assertThat(checksums).containsExactlyInAnyOrderElementsOf(expected);

        BashResult repeated = runBash(tempDir, run);
        assertThat(repeated.exitCode()).withFailMessage(repeated.output()).isZero();
        assertThat(Files.readAllLines(manifest)).isEqualTo(checksums);

        Files.writeString(assets.resolve("Flowers Forever-1.2.0.msi"), "corrupted installer");
        BashResult corrupted = runBash(assets, "sha256sum --check SHA256SUMS.txt");
        assertThat(corrupted.exitCode()).isNotZero();
        assertThat(corrupted.output()).contains("Flowers Forever-1.2.0.msi: FAILED");
    }

    private static BashResult runBash(Path directory, String script) throws Exception {
        String bash = "bash";
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Use Git Bash, not the Windows System32 WSL launcher.
            Path gitBash = Path.of(System.getenv("ProgramFiles"), "Git", "bin", "bash.exe");
            assertThat(gitBash).as("Git Bash is required to exercise release shell gates")
                    .isRegularFile();
            bash = gitBash.toString();
        }
        Path output = Files.createTempFile(directory, "bash-output-", ".log");
        Process process = new ProcessBuilder(bash, "--noprofile", "--norc", "-e",
                "-o", "pipefail", "-s")
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(script.getBytes(StandardCharsets.UTF_8));
            }
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("release shell gate completes")
                    .isTrue();
            return new BashResult(process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private record BashResult(int exitCode, String output) { }

    @Test
    @DisplayName("private draft storage requires an explicit manual choice and leaves the PR route active")
    void privateDraftTransportRequiresAnExplicitManualChoice() throws Exception {
        Map<?, ?> release = readWorkflow("release.yml");
        assertThat(release.get("permissions")).isEqualTo(Map.of("contents", "read"));
        Map<?, ?> events = workflowEvents(release);
        Map<?, ?> dispatch = (Map<?, ?>) events.get("workflow_dispatch");
        Map<?, ?> dispatchInputs = (Map<?, ?>) dispatch.get("inputs");
        Map<?, ?> releaseId = (Map<?, ?>) dispatchInputs.get("preview_release_id");
        assertThat(releaseId.get("required")).isEqualTo(false);
        assertThat(releaseId.get("type")).isEqualTo("string");
        assertThat(releaseId.get("default")).isNull();

        Map<?, ?> jobs = (Map<?, ?>) release.get("jobs");
        Map<?, ?> draft = (Map<?, ?>) jobs.get("private-preview");
        assertThat(draft.get("if")).isEqualTo(
                "github.event_name == 'workflow_dispatch' && inputs.preview_release_id != ''");
        assertThat(draft.get("permissions")).isEqualTo(Map.of("contents", "write"));
        assertThat(draft.get("uses")).isEqualTo("./.github/workflows/private-preview.yml");
        assertThat(draft.get("with")).isEqualTo(Map.of(
                "release_id", "${{ inputs.preview_release_id }}",
                "expected_sha", "${{ github.sha }}",
                "version", "${{ inputs.version }}"));
        assertThat(draft.get("secrets")).isNull();

        Map<?, ?> verify = (Map<?, ?>) jobs.get("verify");
        assertThat(verify.get("if")).isEqualTo(
                "github.event_name != 'workflow_dispatch' || inputs.preview_release_id == ''");
        for (String ordinaryJob : List.of("verify", "installers", "candidate")) {
            assertThat(((Map<?, ?>) jobs.get(ordinaryJob)).get("permissions"))
                    .as("%s inherits contents read for ordinary PRs", ordinaryJob).isNull();
        }
        assertThat(((Map<?, ?>) jobs.get("installers")).get("needs")).isEqualTo("verify");
        assertThat(((Map<?, ?>) jobs.get("candidate")).get("needs"))
                .isEqualTo(List.of("verify", "installers"));
        assertThat(workflowStep(verify, "Build and run every verification gate").get("run"))
                .isEqualTo("mvn -B clean verify");
    }

    @Test
    @DisplayName("draft jobs use exact source without artifact storage or signing credentials")
    void privateDraftJobsPinSourceAndLimitCredentials() throws Exception {
        String source = Files.readString(Path.of(".github", "workflows", "private-preview.yml"));
        assertThat(source).doesNotContain("actions/upload-artifact@", "actions/download-artifact@",
                "secrets.", "secrets:", "environment:", "azure/", "xcrun notarytool");
        Map<?, ?> preview = readWorkflow("private-preview.yml");
        assertThat(preview.get("permissions")).isEqualTo(Map.of("contents", "read"));
        assertThat(workflowEvents(preview).keySet().stream().map(String::valueOf).toList())
                .containsExactly("workflow_call");
        Map<?, ?> call = (Map<?, ?>) workflowEvents(preview).get("workflow_call");
        assertThat(call.get("secrets")).isNull();
        Map<?, ?> jobs = (Map<?, ?>) preview.get("jobs");
        assertThat(jobs.keySet().stream().map(String::valueOf).toList())
                .containsExactly("verify", "installers", "candidate");
        for (Object value : jobs.values()) {
            Map<?, ?> job = (Map<?, ?>) value;
            assertThat(job.get("if")).isEqualTo("github.event_name == 'workflow_dispatch'");
            assertThat(job.get("permissions")).isEqualTo(Map.of("contents", "write"));
            List<?> steps = (List<?>) job.get("steps");
            List<? extends Map<?, ?>> checkouts = steps.stream().map(entry -> (Map<?, ?>) entry)
                    .filter(step -> String.valueOf(step.get("uses")).startsWith("actions/checkout@"))
                    .toList();
            assertThat(checkouts).hasSize(1);
            assertThat(steps.get(0)).isEqualTo(checkouts.get(0));
            assertThat(checkouts.get(0).get("with")).isEqualTo(Map.of(
                    "ref", "${{ inputs.expected_sha }}", "persist-credentials", false));
        }
        Map<?, ?> verify = (Map<?, ?>) jobs.get("verify");
        assertThat(String.valueOf(workflowStep(verify, "Lock the source and candidate version").get("run")))
                .contains("test \"$GITHUB_SHA\" = \"$EXPECTED_SHA\"",
                        "test \"$(git rev-parse HEAD)\" = \"$EXPECTED_SHA\"");
    }

    @Test
    @DisplayName("draft transport preserves the canonical native gates and complete candidate checksum gate")
    void privateDraftPreservesCanonicalVerification() throws Exception {
        Map<?, ?> releaseJobs = (Map<?, ?>) readWorkflow("release.yml").get("jobs");
        Map<?, ?> previewJobs = (Map<?, ?>) readWorkflow("private-preview.yml").get("jobs");
        Map<?, ?> canonicalInstallers = (Map<?, ?>) releaseJobs.get("installers");
        Map<?, ?> draftInstallers = (Map<?, ?>) previewJobs.get("installers");
        assertThat(draftInstallers.get("strategy")).isEqualTo(canonicalInstallers.get("strategy"));
        assertThat(draftInstallers.get("needs")).isEqualTo("verify");
        for (String gate : List.of("Build and smoke-test the candidate app image",
                "Package the smoke-tested app image")) {
            assertThat(workflowStep(draftInstallers, gate))
                    .as("%s must exercise the same native bytes", gate)
                    .isEqualTo(workflowStep(canonicalInstallers, gate));
        }
        Map<?, ?> canonicalCandidate = (Map<?, ?>) releaseJobs.get("candidate");
        Map<?, ?> draftCandidate = (Map<?, ?>) previewJobs.get("candidate");
        assertThat(draftCandidate.get("needs")).isEqualTo(List.of("verify", "installers"));
        assertThat(workflowStep(draftCandidate, "Require the complete candidate and generate checksums"))
                .isEqualTo(workflowStep(canonicalCandidate,
                        "Require the complete candidate and generate checksums"));
        Map<?, ?> canonicalVerify = (Map<?, ?>) releaseJobs.get("verify");
        Map<?, ?> draftVerify = (Map<?, ?>) previewJobs.get("verify");
        assertThat(workflowStep(draftVerify, "Build and run every application verification gate").get("run"))
                .isEqualTo(workflowStep(canonicalVerify, "Build and run every verification gate").get("run"));
        assertThat(workflowStep(draftVerify, "Verify field forms and offline replay"))
                .isEqualTo(workflowStep(canonicalVerify, "Verify field forms and offline replay"));
        List<?> verifySteps = (List<?>) draftVerify.get("steps");
        assertThat(verifySteps.indexOf(workflowStep(draftVerify, "Store the verified inputs in the private draft")))
                .isGreaterThan(verifySteps.indexOf(workflowStep(draftVerify,
                        "Build and run every application verification gate")));
        List<?> candidateSteps = (List<?>) draftCandidate.get("steps");
        assertThat(candidateSteps.indexOf(workflowStep(draftCandidate,
                "Record the completed preview in the private draft")))
                .isGreaterThan(candidateSteps.indexOf(workflowStep(draftCandidate,
                        "Require the complete candidate and generate checksums")));
    }

    private static Map<?, ?> readWorkflow(String file) throws Exception {
        return new Yaml().load(Files.readString(Path.of(".github", "workflows", file)));
    }

    private static Map<?, ?> workflowEvents(Map<?, ?> workflow) {
        // SnakeYAML's YAML 1.1 resolver treats an unquoted "on" key as boolean true.
        return (Map<?, ?>) (workflow.containsKey("on") ? workflow.get("on") : workflow.get(true));
    }

    private static Map<?, ?> workflowStep(Map<?, ?> job, String name) {
        return ((List<?>) job.get("steps")).stream().map(entry -> (Map<?, ?>) entry)
                .filter(step -> name.equals(step.get("name"))).findFirst().orElseThrow();
    }

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
                "cd release-assets", "SHA256SUMS.txt");
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
                "scripts/test-desktop-launcher.ps1",
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
            if (run.contains("scripts/test-desktop-launcher.ps1")) {
                smokeIndex = index;
                assertThat(run).contains("${{ matrix.launcher }}", "${{ matrix.ai_launcher }}",
                        "scripts/test-mcp-launcher.ps1", "-DataDir \"$SMOKE_DATA\"");
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
        assertThat(build.indexOf("test-desktop-launcher.ps1")).isGreaterThan(smokeIndex)
                .isLessThan(packageIndex);
        assertThat(packageIndex).isGreaterThan(smokeIndex);
        String msiCommand = build.substring(packageIndex,
                build.indexOf("if ($LASTEXITCODE", packageIndex));
        assertThat(msiCommand).contains("--app-image $appImage", "--vendor RicheyWorks")
                .doesNotContain("--input ", "--main-jar ", "--add-launcher ");
        assertThat(build).contains("Compress-Archive -LiteralPath $appImage");
        assertThat(build).contains("target\\release-$buildId", "dist\\release-$buildId",
                "-Dflowerfarm.build.directory=$target").doesNotContain("Remove-Item");
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
                        "throw \"$name and the MSI have different signing publishers.\"",
                        "scripts/test-desktop-launcher.ps1 -Launcher $launcher -DataDir $smokeData");
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
                "test-desktop-launcher.ps1",
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
                "scripts/test-macos-launchers.sh",
                "mcp-doctor");
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

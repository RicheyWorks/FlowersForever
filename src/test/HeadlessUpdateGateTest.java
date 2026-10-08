package com.flowerfarm;

import com.flowerfarm.update.HeadlessUpdateCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The launcher entry point refuses the test-only update path unless it is explicitly enabled. */
class HeadlessUpdateGateTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("without the test flag the launcher refuses with exit 64, logs why, and starts nothing")
    void launcherRefusesWithoutTheFlag() throws Exception {
        assumeTrue(System.getenv(HeadlessUpdateCommand.GATE_ENVIRONMENT) == null, "gate variable set on this machine");
        assumeTrue(System.getProperty(HeadlessUpdateCommand.GATE_PROPERTY) == null, "gate property set");
        String dataBefore = System.getProperty("flowerfarm.data-dir");
        Path log = temp.resolve("refused.log");

        int apply = FlowerFarmApp.runHeadlessUpdateCheck(new String[] {
                "--apply-update-from", "http://127.0.0.1:1/latest.json", "--trust-test-key",
                temp.resolve("missing.pub").toString(), "--update-log", log.toString()});
        int verify = FlowerFarmApp.runHeadlessUpdateCheck(new String[] {"--verify-update-launch"});

        assertThat(apply).isEqualTo(64);
        assertThat(verify).isEqualTo(64);
        assertThat(Files.readString(log, StandardCharsets.UTF_8)).contains("not enabled", "exit=64 REFUSED");
        // Refused before the data folder bootstrap: no data directory was chosen or created.
        assertThat(System.getProperty("flowerfarm.data-dir")).isEqualTo(dataBefore);
    }

    @Test
    @DisplayName("malformed headless arguments are refused with exit 64")
    void launcherRefusesBadArguments() {
        assertThat(FlowerFarmApp.runHeadlessUpdateCheck(new String[] {"--trust-test-key", "k.pub"})).isEqualTo(64);
        assertThat(FlowerFarmApp.runHeadlessUpdateCheck(new String[] {"--verify-update-launch", "--gui"}))
                .isEqualTo(64);
    }
}

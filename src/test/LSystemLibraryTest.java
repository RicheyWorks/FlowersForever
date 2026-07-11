package com.flowerfarm.lsystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LSystemLibrary")
class LSystemLibraryTest {

    @TempDir Path temp;

    @Test
    @DisplayName("save and load round-trips a ruleset")
    void saveLoad() throws Exception {
        LSystemLibrary lib = new LSystemLibrary(temp);
        LSystemDefinition def = LSystemDefinition.from(RoseLSystemPresets.nootka());
        def.setName("My Nootka Cross");
        def.setLinkedSku("Nootka Rose");
        def.setDepth(5);

        Path file = lib.save(def);
        assertThat(file).exists();

        LSystemDefinition loaded = lib.load(file);
        assertThat(loaded.getName()).isEqualTo("My Nootka Cross");
        assertThat(loaded.getLinkedSku()).isEqualTo("Nootka Rose");
        assertThat(loaded.toLSystem().expand(2)).isNotBlank();
        assertThat(lib.listFiles()).hasSize(1);
    }
}

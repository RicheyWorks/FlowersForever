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

    @Test
    @DisplayName("listFiles keeps only .json rulesets, sorted by name without regard to case")
    void listFilesFiltersAndSorts() throws Exception {
        java.nio.file.Files.writeString(temp.resolve("b-rose.json"), "{}");
        java.nio.file.Files.writeString(temp.resolve("A-Rose.JSON"), "{}");
        java.nio.file.Files.writeString(temp.resolve("notes.txt"), "not a ruleset");
        java.nio.file.Files.createDirectories(temp.resolve("folder"));

        assertThat(new LSystemLibrary(temp).listFiles())
                .extracting(p -> p.getFileName().toString())
                .containsExactly("A-Rose.JSON", "b-rose.json");
    }

    @Test
    @DisplayName("listFiles on a missing directory is empty")
    void listFilesMissingDirectory() throws Exception {
        assertThat(new LSystemLibrary(temp.resolve("missing")).listFiles()).isEmpty();
    }
}

package com.flowerfarm.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("LocalJsonMirror")
class LocalJsonMirrorTest {

    @Test
    @DisplayName("read missing file returns empty list")
    void missingFileEmpty() throws Exception {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "no-such-mirror-" + System.nanoTime() + ".json");
        LocalJsonMirror mirror = new LocalJsonMirror(missing.toString(), "test");
        assertThat(mirror.exists()).isFalse();
        assertThat(mirror.readRows()).isEmpty();
    }

    @Test
    @DisplayName("write then read round-trips rows")
    void writeRead() throws Exception {
        Path tmp = Files.createTempFile("mirror-test", ".json");
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(tmp.toString(), "test");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", "Nootka Rose");
            row.put("quantity", 10);
            mirror.writeRows(List.of(row));
            assertThat(mirror.exists()).isTrue();
            List<Map<String, Object>> back = mirror.readRows();
            assertThat(back).hasSize(1);
            assertThat(back.get(0).get("name")).isEqualTo("Nootka Rose");
            assertThat(back.get(0).get("quantity")).isEqualTo(10);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("blank path rejected")
    void blankPath() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalJsonMirror("  ", "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalJsonMirror(null, "x"));
    }
}

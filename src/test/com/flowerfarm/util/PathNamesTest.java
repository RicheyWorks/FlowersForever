package com.flowerfarm.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathNamesTest {

    @Test
    void returnsTheLastPathElement() {
        assertThat(PathNames.fileName(Path.of("data", "rulesets", "Rose.JSON"))).isEqualTo("Rose.JSON");
        assertThat(PathNames.fileName(Path.of("FlowersForever.exe"))).isEqualTo("FlowersForever.exe");
    }

    @Test
    void rootsEmptyPathsAndNullGiveAnEmptyName() {
        Path root = Path.of("").toAbsolutePath().getRoot();
        assertThat(root.getFileName()).isNull();
        assertThat(PathNames.fileName(root)).isEmpty();
        assertThat(PathNames.fileName(Path.of(""))).isEmpty();
        assertThat(PathNames.fileName(null)).isEmpty();
    }
}

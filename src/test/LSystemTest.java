package com.flowerfarm.lsystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LSystem")
class LSystemTest {

    @Test
    @DisplayName("expand grows string under production rules")
    void expandGrows() {
        LSystem nootka = RoseLSystemPresets.nootka();
        String d0 = nootka.expand(0);
        String d3 = nootka.expand(3);
        assertThat(d0).isEqualTo("X");
        assertThat(d3.length()).isGreaterThan(d0.length());
        assertThat(d3).contains("F");
    }

    @Test
    @DisplayName("depth is capped at MAX_DEPTH")
    void depthCapped() {
        LSystem system = RoseLSystemPresets.rugosa();
        String a = system.expand(LSystem.MAX_DEPTH);
        String b = system.expand(LSystem.MAX_DEPTH + 5);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("mutate returns a distinct system with finite angle")
    void mutate() {
        LSystem base = RoseLSystemPresets.bourbon();
        LSystem mutant = base.mutate(new Random(42));
        assertThat(mutant.getName()).contains("mutant");
        assertThat(mutant.getDefaultAngleDegrees()).isBetween(8.0, 55.0);
        assertThat(mutant.expand(3)).isNotBlank();
    }

    @Test
    @DisplayName("all presets expand without throwing")
    void allPresets() {
        for (LSystem system : RoseLSystemPresets.all()) {
            assertThat(system.getName()).isNotBlank();
            assertThat(system.expand(system.getDefaultDepth())).isNotBlank();
        }
    }
}

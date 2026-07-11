package com.flowerfarm.gui.tabs;

import com.flowerfarm.lsystem.LSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoseVisualizer SKU → preset mapping")
class RoseVisualizerSkuMatchTest {

    @Test
    @DisplayName("maps inventory names to growth habits")
    void mapping() {
        assertThat(RoseVisualizerTab.matchPresetForSku("Nootka Rose").getName()).contains("Nootka");
        assertThat(RoseVisualizerTab.matchPresetForSku("Rosa rugosa Hansa").getName()).containsIgnoringCase("rugosa");
        assertThat(RoseVisualizerTab.matchPresetForSku("New Dawn Climber").getName()).containsIgnoringCase("Climb");
        assertThat(RoseVisualizerTab.matchPresetForSku("Damask Mix").getName()).containsIgnoringCase("Damask");
        assertThat(RoseVisualizerTab.matchPresetForSku("Zephirine Bourbon").getName()).containsIgnoringCase("Bourbon");
        LSystem fallback = RoseVisualizerTab.matchPresetForSku("Mystery Bloom");
        assertThat(fallback.getName()).contains("Nootka");
    }
}

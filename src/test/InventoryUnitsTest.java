package com.flowerfarm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryUnitsTest {

    @Test
    void agriculturalPackLabelsMatchOnlyTheirOwnUnit() {
        assertThat(InventoryUnits.equivalent("bins", "Per Bin")).isTrue();
        assertThat(InventoryUnits.equivalent("bushels", "bushel")).isTrue();
        assertThat(InventoryUnits.equivalent("cwt", "hundredweight")).isTrue();
        assertThat(InventoryUnits.equivalent("lbs", "bags")).isFalse();
        assertThat(InventoryUnits.equivalent("bins", "cases")).isFalse();
    }
}

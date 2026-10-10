package com.flowerfarm.util;

import com.flowerfarm.model.Item;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class InventoryQuantityTest {
    @Test void measuredStockNeverLosesSmallWeights() {
        double stock = 0;
        for (int i = 0; i < 100; i++) stock = InventoryQuantity.add(stock, 0.000001);
        assertThat(stock).isEqualTo(0.0001);
        assertThat(InventoryQuantity.subtract(3.5, 0.125)).isEqualTo(3.375);
        assertThat(InventoryQuantity.requireForUnit(3.5, "Per Weight (lb)")).isEqualTo(3.5);
        assertThat(InventoryQuantity.requireForUnit(0.000001, "grams")).isEqualTo(0.000001);
        assertThat(InventoryQuantity.format(3.5)).isEqualTo("3.5");
    }

    @Test void invalidMeasurementsAndFractionalCountsAreRejected() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0.0000001,
                (double) Integer.MAX_VALUE + 1}) {
            assertThatThrownBy(() -> InventoryQuantity.decimal(value)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String unit : List.of("stems", "plants", "clones", "bales", "head", "each")) {
            assertThatThrownBy(() -> InventoryQuantity.requireForUnit(1.5, unit))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("whole number");
        }
        assertThatThrownBy(() -> InventoryQuantity.parse("bad weight"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no stock value was substituted");
    }

    @Test void receiptNumbersRetainLegacyWholeCountIdentity() {
        assertThat(InventoryQuantity.wireNumber(20)).isEqualTo(Integer.valueOf(20));
        assertThat(InventoryQuantity.wireNumber(3.5).toString()).isEqualTo("3.5");
    }

    @Test void quantitiesAreSummarizedWithinTheirOwnUnits() {
        List<Item> rows = List.of(new Item("Blue Dream", "Cannabis", 1, "g", 0, 3.5, ""),
                new Item("OG Kush", "Cannabis", 1, "grams", 0, 0.125, ""),
                new Item("Clones", "Cannabis", 1, "plants", 0, 12, ""));
        assertThat(InventoryQuantity.summary(rows, Item::getQuantity, Item::getUnit))
                .isEqualTo("3.625 gram · 12 plant");
    }
}

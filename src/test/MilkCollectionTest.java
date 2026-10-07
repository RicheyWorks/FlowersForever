package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Milk collection")
class MilkCollectionTest {

    @Test
    void normalizesOptionalTextAndRetainsTheEnteredUnitWithoutConversion() {
        MilkCollection collection = new MilkCollection(date(), " Milking herd ",
                " Morning ", null, new BigDecimal("125.5"),
                MilkCollectionUnit.POUNDS, null);

        assertThat(collection.getGroupName()).isEqualTo("Milking herd");
        assertThat(collection.getSessionName()).isEqualTo("Morning");
        assertThat(collection.getTankLocation()).isEmpty();
        assertThat(collection.getNotes()).isEmpty();
        assertThat(collection.getAmount()).isEqualByComparingTo("125.50");
        assertThat(collection.getUnit()).isEqualTo(MilkCollectionUnit.POUNDS);
        assertThat(MilkCollectionUnit.POUNDS.symbol()).isEqualTo("lb");
        assertThat(MilkCollectionUnit.GALLONS.symbol()).isEqualTo("gal");
        assertThat(MilkCollectionUnit.LITERS.symbol()).isEqualTo("L");

        MilkCollection snapshot = collection.snapshot();
        snapshot.setGroupName("Other herd");
        assertThat(collection.getGroupName()).isEqualTo("Milking herd");
    }

    @Test
    void requiresDateGroupSessionPositiveAmountAndExplicitUnit() {
        assertThatThrownBy(() -> new MilkCollection(null, "Herd", "Morning", "",
                amount("1"), MilkCollectionUnit.POUNDS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collection date");
        assertThatThrownBy(() -> new MilkCollection(date(), " ", "Morning", "",
                amount("1"), MilkCollectionUnit.POUNDS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group name");
        assertThatThrownBy(() -> new MilkCollection(date(), "Herd", " ", "",
                amount("1"), MilkCollectionUnit.POUNDS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session name");
        assertThatThrownBy(() -> new MilkCollection(date(), "Herd", "Morning", "",
                null, MilkCollectionUnit.POUNDS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount");
        for (String invalid : new String[]{"0", "-1", "1000000.01"}) {
            assertThatThrownBy(() -> new MilkCollection(date(), "Herd", "Morning", "",
                    amount(invalid), MilkCollectionUnit.POUNDS, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount");
        }
        assertThatThrownBy(() -> new MilkCollection(date(), "Herd", "Morning", "",
                amount("1.001"), MilkCollectionUnit.POUNDS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> new MilkCollection(date(), "Herd", "Morning", "",
                amount("1"), null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit");
        assertThat(new MilkCollection(date(), "Herd", "Morning", "",
                amount("1000000.00"), MilkCollectionUnit.LITERS, "")
                .getAmount()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void editCopiesBusinessFieldsWithoutTyingRecordsTogether() {
        MilkCollection stored = collection();
        MilkCollection edit = new MilkCollection(LocalDate.of(2026, 10, 2),
                "North herd", "Evening", "Tank B", amount("86.25"),
                MilkCollectionUnit.GALLONS, "After rain");

        stored.copyBusinessFieldsFrom(edit);
        edit.setTankLocation("Tank C");

        assertThat(stored.getCollectionDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(stored.getGroupName()).isEqualTo("North herd");
        assertThat(stored.getSessionName()).isEqualTo("Evening");
        assertThat(stored.getTankLocation()).isEqualTo("Tank B");
        assertThat(stored.getAmount()).isEqualByComparingTo("86.25");
        assertThat(stored.getUnit()).isEqualTo(MilkCollectionUnit.GALLONS);
        assertThat(stored.getNotes()).isEqualTo("After rain");
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static MilkCollection collection() {
        return new MilkCollection(date(), "Milking herd", "Morning", "Tank A",
                amount("125.50"), MilkCollectionUnit.POUNDS, "");
    }
}

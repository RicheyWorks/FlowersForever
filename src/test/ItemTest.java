package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Item")
class ItemTest {

    // ── Happy-path construction ─────────────────────────────────────────────

    @Test
    @DisplayName("stores all fields when valid")
    void storesAllFields() {
        Item item = new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "PNW native");

        assertThat(item.getName()).isEqualTo("Nootka Rose");
        assertThat(item.getCategory()).isEqualTo("Flowers/Plants");
        assertThat(item.getPrice()).isEqualTo(3.50);
        assertThat(item.getUnit()).isEqualTo("Per Stem");
        assertThat(item.getCost()).isEqualTo(2.00);
        assertThat(item.getQuantity()).isEqualTo(50);
        assertThat(item.getNotes()).isEqualTo("PNW native");
    }

    @Test
    @DisplayName("trims whitespace from name, category, unit, notes")
    void trimsWhitespace() {
        Item item = new Item("  Rosa rugosa  ", "  Shrubs  ", 2.00, "  Per Unit  ", 1.00, 10, "  notes  ");

        assertThat(item.getName()).isEqualTo("Rosa rugosa");
        assertThat(item.getCategory()).isEqualTo("Shrubs");
        assertThat(item.getUnit()).isEqualTo("Per Unit");
        assertThat(item.getNotes()).isEqualTo("notes");
    }

    // ── Default fallbacks ───────────────────────────────────────────────────

    @Test
    @DisplayName("defaults category to 'Other' when null")
    void defaultCategoryOnNull() {
        Item item = new Item("Seed", null, 1.00, "Per Unit", 0.50, 5, "");
        assertThat(item.getCategory()).isEqualTo("Other");
    }

    @Test
    @DisplayName("defaults category to 'Other' when blank")
    void defaultCategoryOnBlank() {
        Item item = new Item("Seed", "   ", 1.00, "Per Unit", 0.50, 5, "");
        assertThat(item.getCategory()).isEqualTo("Other");
    }

    @Test
    @DisplayName("defaults unit to 'Per Unit' when null")
    void defaultUnitOnNull() {
        Item item = new Item("Seed", "Other", 1.00, null, 0.50, 5, "");
        assertThat(item.getUnit()).isEqualTo("Per Unit");
    }

    @Test
    @DisplayName("defaults notes to empty string when null")
    void defaultNotesOnNull() {
        Item item = new Item("Seed", "Other", 1.00, "Per Unit", 0.50, 5, null);
        assertThat(item.getNotes()).isEmpty();
    }

    // ── Validation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("constructor throws")
    class ValidationTests {

        @Test
        @DisplayName("when name is null")
        void throwsOnNullName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Item(null, "Other", 1.00, "Per Unit", 0.50, 5, ""))
                    .withMessageContaining("name");
        }

        @Test
        @DisplayName("when name is blank")
        void throwsOnBlankName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Item("   ", "Other", 1.00, "Per Unit", 0.50, 5, ""));
        }

        @ParameterizedTest(name = "price = {0}")
        @ValueSource(doubles = {-0.01, -1.0, -999.99})
        @DisplayName("when price is negative")
        void throwsOnNegativePrice(double price) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Item("Rose", "Other", price, "Per Unit", 0.00, 5, ""))
                    .withMessageContaining("Price");
        }

        @ParameterizedTest(name = "cost = {0}")
        @ValueSource(doubles = {-0.01, -5.0})
        @DisplayName("when cost is negative")
        void throwsOnNegativeCost(double cost) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Item("Rose", "Other", 1.00, "Per Unit", cost, 5, ""))
                    .withMessageContaining("Cost");
        }

        @ParameterizedTest(name = "quantity = {0}")
        @ValueSource(ints = {-1, -100})
        @DisplayName("when quantity is negative")
        void throwsOnNegativeQuantity(int qty) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Item("Rose", "Other", 1.00, "Per Unit", 0.50, qty, ""))
                    .withMessageContaining("Quantity");
        }
    }

    // ── Zero-value edge cases ───────────────────────────────────────────────

    @Test
    @DisplayName("allows price = 0 (complimentary / sample item)")
    void allowsZeroPrice() {
        assertThatCode(() -> new Item("Sample", "Other", 0.0, "Per Unit", 0.0, 0, ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows quantity = 0 (out-of-stock)")
    void allowsZeroQuantity() {
        assertThatCode(() -> new Item("Out of Stock Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 0, ""))
                .doesNotThrowAnyException();
    }

    // ── CSV serialization ───────────────────────────────────────────────────

    @Test
    @DisplayName("toCsv() produces correct comma-separated line")
    void toCsvHappyPath() {
        Item item = new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "PNW native");
        assertThat(item.toCsv()).isEqualTo("Nootka Rose,Flowers/Plants,3.50,Per Stem,2.00,50,\"PNW native\"");
    }

    @Test
    @DisplayName("toCsv() escapes internal double-quotes in notes")
    void toCsvEscapesQuotes() {
        Item item = new Item("Rosa", "Other", 1.00, "Per Unit", 0.50, 1, "Has \"thorns\"");
        assertThat(item.toCsv()).contains("\"Has \"\"thorns\"\"\"");
    }

    @Test
    @DisplayName("toCsv() formats price and cost to 2 decimal places")
    void toCsvFormatsDecimals() {
        Item item = new Item("Rose", "Other", 1.5, "Per Unit", 0.5, 10, "");
        assertThat(item.toCsv()).contains("1.50").contains("0.50");
    }

    // ── Setters ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setters mutate fields correctly")
    void settersWork() {
        Item item = new Item("Rose", "Other", 1.00, "Per Unit", 0.50, 5, "");

        item.setName("Damask Rose");
        item.setCategory("Flowers/Plants");
        item.setPrice(4.00);
        item.setUnit("Per Stem");
        item.setCost(2.50);
        item.setQuantity(100);
        item.setNotes("Fragrant");

        assertThat(item.getName()).isEqualTo("Damask Rose");
        assertThat(item.getCategory()).isEqualTo("Flowers/Plants");
        assertThat(item.getPrice()).isEqualTo(4.00);
        assertThat(item.getUnit()).isEqualTo("Per Stem");
        assertThat(item.getCost()).isEqualTo(2.50);
        assertThat(item.getQuantity()).isEqualTo(100);
        assertThat(item.getNotes()).isEqualTo("Fragrant");
    }

    @Test
    @DisplayName("setters trim and apply the same defaults as the constructor")
    void settersNormalizeLikeConstructor() {
        Item item = new Item("Rose", "Other", 1.00, "Per Unit", 0.50, 5, "");

        item.setName("  Damask  ");
        item.setCategory("   ");
        item.setUnit(null);
        item.setNotes(null);

        assertThat(item.getName()).isEqualTo("Damask");
        assertThat(item.getCategory()).isEqualTo("Other");
        assertThat(item.getUnit()).isEqualTo("Per Unit");
        assertThat(item.getNotes()).isEmpty();
    }

    @Nested
    @DisplayName("setters throw")
    class SetterValidationTests {

        private final Item item = new Item("Rose", "Other", 1.00, "Per Unit", 0.50, 5, "");

        @Test
        @DisplayName("when setName is blank")
        void throwsOnBlankName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> item.setName("   "))
                    .withMessageContaining("name");
        }

        @Test
        @DisplayName("when setPrice is negative")
        void throwsOnNegativePrice() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> item.setPrice(-1.0))
                    .withMessageContaining("Price");
        }

        @Test
        @DisplayName("when setCost is negative")
        void throwsOnNegativeCost() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> item.setCost(-0.01))
                    .withMessageContaining("Cost");
        }

        @Test
        @DisplayName("when setQuantity is negative")
        void throwsOnNegativeQuantity() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> item.setQuantity(-1))
                    .withMessageContaining("Quantity");
        }
    }
}

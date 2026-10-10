package com.flowerfarm.automation;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessCommandTest {

    @Test
    void freezesNestedArgumentsSoReplayIdentityCannotChange() {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("quantity", 1));
        List<Object> rows = new ArrayList<>(List.of(row));
        HarnessCommand command = new HarnessCommand("immutable", "import", Map.of("rows", rows));

        row.put("quantity", 2);
        rows.clear();

        assertThat(command.arguments()).containsEntry("rows", List.of(Map.of("quantity", 1)));
        assertThat(command).isEqualTo(new HarnessCommand(
                "immutable", "import", Map.of("rows", new Object[]{Map.of("quantity", 1)})));
        assertThatThrownBy(() -> ((List<?>) command.arguments().get("rows")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsJsonNullsAndRejectsNonJsonOrCyclicInputs() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("optional", null);
        assertThat(new HarnessCommand("null", "read", arguments).arguments())
                .containsKey("optional");

        arguments.put("cycle", arguments);
        assertThatThrownBy(() -> new HarnessCommand("cycle", "read", arguments))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cyclic");
        assertThatThrownBy(() -> new HarnessCommand("nan", "move", Map.of("x", Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON values");
    }

    @Test
    void rejectsOverflowAndFractionsInsteadOfTruncatingIntegers() {
        for (Object bad : List.of(1L + Integer.MAX_VALUE, BigInteger.ONE.shiftLeft(70), 1.5, "1")) {
            HarnessCommand command = new HarnessCommand("number", "set", Map.of("value", bad));
            assertThatThrownBy(() -> command.integer("value"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("integer");
        }
        HarnessCommand array = new HarnessCommand("array", "select",
                Map.of("rows", List.of(1, 1L + Integer.MAX_VALUE)));
        assertThatThrownBy(() -> array.integers("rows"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32-bit");
        assertThat(new HarnessCommand("valid", "set", Map.of("value", (long) Integer.MAX_VALUE))
                .integer("value")).isEqualTo(Integer.MAX_VALUE);
    }
}

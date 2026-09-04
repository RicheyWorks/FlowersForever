package com.flowerfarm.automation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessArgumentSpecTest {

    private final HarnessArgumentSpec records = new HarnessArgumentSpec(
            "records", HarnessValueType.ARRAY, "Canonical records", true,
            HarnessValueType.OBJECT, Map.of(
                    "type", "object", "required", List.of("lines"), "additionalProperties", false,
                    "properties", Map.of("lines", Map.of("type", "array", "items", Map.of(
                            "type", "object", "required", List.of("quantity"),
                            "additionalProperties", false,
                            "properties", Map.of("quantity", Map.of("type", "integer"),
                                    "unit", Map.of("type", "string", "enum", List.of("stems"))))))));

    @Test
    void validatesNestedRequiredFieldsAndTypesBeforePluginExecution() {
        assertThatThrownBy(() -> records.validate(List.of(Map.of()), "import"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires field 'lines'");
        assertThatThrownBy(() -> records.validate(
                List.of(Map.of("lines", List.of(Map.of("quantity", 1.5)))), "import"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("records[0]")
                .hasMessageContaining("lines[0].quantity")
                .hasMessageContaining("JSON integer");
        assertThatThrownBy(() -> records.validate(
                List.of(Map.of("lines", List.of(Map.of("quantity", 1, "unexpected", true)))), "import"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not accept field");
        assertThatThrownBy(() -> records.validate(
                List.of(Map.of("lines", List.of(Map.of("quantity", 1, "unit", "boxes")))), "import"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be one of");

        records.validate(List.of(Map.of("lines", List.of(Map.of("quantity", 1, "unit", "stems")))), "import");
    }

    @Test
    void keepsArbitrarySourceRecordsOpenForMapping() {
        HarnessArgumentSpec source = new HarnessArgumentSpec("source-records", HarnessValueType.ARRAY,
                "Raw provider records", true, HarnessValueType.OBJECT,
                Map.of("type", "object", "additionalProperties", true));
        source.validate(List.of(Map.of("Their Quantity", "12", "Unrelated", true)), "preview");
    }
}

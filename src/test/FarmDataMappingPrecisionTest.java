package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FarmDataMappingPrecisionTest {

    private final FarmDataMappingService service = new FarmDataMappingService(
            new ObjectMapper().findAndRegisterModules());

    @ParameterizedTest
    @ValueSource(strings = {"5.0000000000000001", "4.9999999999999999"})
    void rejectsFractionalPackSizesInBothJsonCellForms(String packSize) {
        String ingredient = "{\"itemName\":\"Tulip\",\"packSize\":" + packSize + "}";
        for (Object cell : List.of("[" + ingredient + "]", List.of(ingredient))) {
            var preview = previewIngredients(cell);

            assertThat(preview.ready()).as("Cell: %s", cell).isFalse();
            assertThat(preview.invalidCount()).isEqualTo(1);
            assertThat(preview.issues()).anySatisfy(issue -> {
                assertThat(issue.row()).isEqualTo(1);
                assertThat(issue.field()).isEqualTo("ingredients");
                assertThat(issue.message()).contains("packSize", "whole number");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"9007199254740993", "9223372036854775807"})
    void preservesExactWholeNumbersInJsonCells(String number) {
        var preview = previewIngredients(
                "[{\"itemName\":\"Tulip\",\"packSize\":" + number + ".0}]");

        assertThat(preview.ready()).isTrue();
        assertThat(preview.records().get(0).get("ingredients"))
                .isEqualTo(List.of(Map.of("itemName", "Tulip", "packSize", Long.parseLong(number))));
    }

    @Test
    void keepsWholeDecimalCellsAndOrdinaryNumberValuesCompatible() {
        var preview = previewIngredients(
                "[{\"itemName\":\"Tulip\",\"packSize\":5.0000,\"packCost\":1.25}]");

        assertThat(preview.ready()).isTrue();
        assertThat(preview.records().get(0).get("ingredients"))
                .isEqualTo(List.of(Map.of("itemName", "Tulip", "packSize", 5L, "packCost", 1.25)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"46000.0000000000000001", "45999.9999999999999999"})
    void rejectsFractionalSpreadsheetDatesWithoutRounding(String serial) {
        var preview = previewDate(new BigDecimal(serial));

        assertThat(preview.ready()).isFalse();
        assertThat(preview.records().get(0)).doesNotContainKey("harvestDate");
        assertThat(preview.issues()).anySatisfy(issue -> {
            assertThat(issue.row()).isEqualTo(1);
            assertThat(issue.field()).isEqualTo("harvestDate");
            assertThat(issue.message()).contains("spreadsheet date");
        });
    }

    @Test
    void preservesWholeSpreadsheetDatesAcrossNumericTypes() {
        for (Number serial : List.of(46000, 46000L, 46000.0,
                new BigInteger("46000"), new BigDecimal("46000.0000"))) {
            var preview = previewDate(serial);

            assertThat(preview.ready()).as("%s: %s", serial.getClass(), serial).isTrue();
            assertThat(preview.records().get(0)).containsEntry("harvestDate", "2025-12-09");
        }
    }

    private FarmDataMappingService.MappingPreview previewIngredients(Object cell) {
        Map<String, Object> ingredient = objectSchema(List.of("itemName", "packSize"), Map.of(
                "itemName", Map.of("type", "string"),
                "packSize", Map.of("type", "integer"),
                "packCost", Map.of("type", "number")));
        Map<String, Object> schema = objectSchema(List.of("ingredients"), Map.of(
                "ingredients", Map.of("type", "array", "items", ingredient)));
        return service.preview("floral-recipes", schema, Map.of("ingredients", "Materials"),
                List.of(Map.of("Materials", cell)), Map.of(), List.of());
    }

    private FarmDataMappingService.MappingPreview previewDate(Number serial) {
        Map<String, Object> schema = objectSchema(List.of("harvestDate"), Map.of(
                "harvestDate", Map.of("type", "string", "format", "date")));
        return service.preview("harvests", schema, Map.of(),
                List.of(Map.of("harvestDate", serial)), Map.of(), List.of());
    }

    private static Map<String, Object> objectSchema(
            List<String> required, Map<String, Object> properties) {
        return Map.of("type", "object", "required", required,
                "properties", properties, "additionalProperties", false);
    }
}

package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Provider-neutral farm data mapping")
class FarmDataMappingServiceTest {

    FarmDataMappingService service;

    @BeforeEach
    void setUp() {
        service = new FarmDataMappingService(
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("suggests common spreadsheet aliases with explicit confidence")
    void suggestsCommonAliases() {
        FarmDataMappingService.MappingSuggestion suggestion = service.suggest(
                inventorySchema(), List.of(
                        "Legacy ID", "Product Name", "On Hand", "Retail Price", "Unused"));

        assertThat(suggestion.fieldMapping())
                .containsEntry("sourceKey", "Legacy ID")
                .containsEntry("name", "Product Name")
                .containsEntry("quantity", "On Hand")
                .containsEntry("price", "Retail Price");
        assertThat(suggestion.matches())
                .extracting(FarmDataMappingService.FieldMatch::reason)
                .contains("known farm-data alias");
        assertThat(suggestion.requiredUnmapped()).isEmpty();
        assertThat(suggestion.unmappedSourceFields()).containsExactly("Unused");
    }

    @Test
    @DisplayName("suggests harvest lot, grade, loss, and storage aliases")
    void suggestsHarvestTraceabilityAliases() {
        FarmDataMappingService.MappingSuggestion suggestion = service.suggest(
                harvestSchema(), List.of(
                        "Cut Date", "Crop", "Count", "Batch", "Quality",
                        "Culls", "Cooler"));

        assertThat(suggestion.fieldMapping())
                .containsEntry("harvestDate", "Cut Date")
                .containsEntry("cropName", "Crop")
                .containsEntry("quantity", "Count")
                .containsEntry("lotCode", "Batch")
                .containsEntry("grade", "Quality")
                .containsEntry("lossQuantity", "Culls")
                .containsEntry("storageLocation", "Cooler");
        assertThat(suggestion.requiredUnmapped()).isEmpty();
    }

    @Test
    @DisplayName("coerces money, counts, booleans, dates, and delimited tag cells")
    void coercesSpreadsheetValues() {
        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", richSchema(), Map.of(), List.of(Map.of(
                        "Legacy ID", "row-9",
                        "Product Name", "Dahlia",
                        "On Hand", "1,250",
                        "Retail Price", "$3.50",
                        "Available", "yes",
                        "Cut Date", "9/2/2026",
                        "Labels", "market; focal")),
                Map.of("unit", "stem"), List.of());

        assertThat(preview.ready()).isTrue();
        assertThat(preview.durableSourceKeysReady()).isTrue();
        assertThat(preview.invalidCount()).isZero();
        assertThat(preview.records()).singleElement().satisfies(row -> {
            assertThat(row)
                    .containsEntry("sourceKey", "row-9")
                    .containsEntry("name", "Dahlia")
                    .containsEntry("quantity", 1_250L)
                    .containsEntry("price", 3.5)
                    .containsEntry("active", true)
                    .containsEntry("harvestDate", "2026-09-02")
                    .containsEntry("unit", "stem");
            assertThat(row.get("tags")).isEqualTo(List.of("market", "focal"));
        });
    }

    @Test
    @DisplayName("rejects impossible spreadsheet dates instead of silently changing them")
    void rejectsImpossibleDates() {
        for (String date : List.of("2/29/2025", "2/30/2024", "4-31-2026", "2/29/25")) {
            FarmDataMappingService.MappingPreview preview = service.preview(
                    "harvests", harvestSchema(), Map.of(),
                    List.of(Map.of("Cut Date", date, "Crop", "Rose", "Count", 5)),
                    Map.of(), List.of());

            assertThat(preview.ready()).as(date).isFalse();
            assertThat(preview.issues())
                    .extracting(FarmDataMappingService.MappingIssue::field)
                    .contains("harvestDate");
        }
        FarmDataMappingService.MappingPreview leapDay = service.preview(
                "harvests", harvestSchema(), Map.of(),
                List.of(Map.of("Cut Date", "2/29/24", "Crop", "Rose", "Count", 5)),
                Map.of(), List.of());
        assertThat(leapDay.ready()).isTrue();
        assertThat(leapDay.records().get(0)).containsEntry("harvestDate", "2024-02-29");
    }

    @Test
    @DisplayName("explicit composite keys take precedence over a suggested legacy id")
    void honorsExplicitSourceKeyFields() {
        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", inventorySchema(), Map.of(),
                List.of(Map.of("Legacy ID", "7", "Workbook", "Spring",
                        "Product Name", "Dahlia")),
                Map.of(), List.of("Workbook", "Legacy ID"));

        assertThat(preview.ready()).isTrue();
        assertThat(preview.durableSourceKeysReady()).isTrue();
        assertThat(preview.fieldMapping()).doesNotContainKey("sourceKey");
        assertThat(preview.records().get(0)).containsEntry(
                "sourceKey", "composite-8:Workbook=6:Spring|9:Legacy ID=1:7");
    }

    @Test
    @DisplayName("reports duplicate and oversized source keys before import can overwrite rows")
    void rejectsUnusableSourceKeys() {
        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", inventorySchema(), Map.of(),
                List.of(Map.of("Legacy ID", "same-row", "Product Name", "Dahlia"),
                        Map.of("Legacy ID", " same-row ", "Product Name", "Rose"),
                        Map.of("Legacy ID", "k".repeat(241), "Product Name", "Tulip")),
                Map.of(), List.of());

        assertThat(preview.ready()).isFalse();
        assertThat(preview.durableSourceKeysReady()).isFalse();
        assertThat(preview.invalidCount()).isEqualTo(2);
        assertThat(preview.issues())
                .extracting(FarmDataMappingService.MappingIssue::row)
                .containsExactly(2, 3);
        assertThat(preview.issues())
                .extracting(FarmDataMappingService.MappingIssue::message)
                .anySatisfy(message -> assertThat(message).contains("duplicates row 1"))
                .anySatisfy(message -> assertThat(message).contains("240"));
    }

    @Test
    @DisplayName("nested mappings prefer exact field names and reject ambiguous aliases")
    void resolvesNestedPathsWithoutGuessing() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Product Name", "Wrong crop");
        payload.put("productName", "Dahlia");
        List<Map<String, Object>> rows = List.of(Map.of("payload", payload));

        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", inventorySchema(), Map.of("name", "payload.productName"),
                rows, Map.of(), List.of());
        assertThat(preview.records().get(0)).containsEntry("name", "Dahlia");

        assertThatThrownBy(() -> service.preview(
                "inventory", inventorySchema(), Map.of("name", "payload.product-name"),
                rows, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous", "exact source field name");
    }

    @Test
    @DisplayName("does not stringify objects into farm names or durable source keys")
    void rejectsStructuredScalarValues() {
        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", inventorySchema(), Map.of("name", "Crop"),
                List.of(Map.of("Crop", Map.of("name", "Dahlia"),
                        "Identity", List.of("sheet", "row"))),
                Map.of(), List.of("Identity"));

        assertThat(preview.ready()).isFalse();
        assertThat(preview.durableSourceKeysReady()).isFalse();
        assertThat(preview.issues())
                .extracting(FarmDataMappingService.MappingIssue::field)
                .contains("name", "sourceKey");
    }

    @Test
    @DisplayName("maps nested provider paths and JSON object arrays")
    void mapsNestedProviderValues() {
        Map<String, Object> ingredient = objectSchema(
                List.of("itemName", "stemsPerDesign", "packSize"), Map.of(
                        "itemName", field("string"),
                        "stemsPerDesign", field("number"),
                        "packSize", field("integer")));
        Map<String, Object> recipe = objectSchema(List.of("name", "ingredients"), Map.of(
                "sourceKey", field("string"),
                "name", field("string"),
                "ingredients", Map.of("type", "array", "items", ingredient)));
        Map<String, Object> mapping = Map.of(
                "name", "payload.title",
                "ingredients", "Materials");
        Map<String, Object> source = Map.of(
                "Workbook", "Spring recipes",
                "Row", 14,
                "payload", Map.of("title", "Mapped bouquet"),
                "Materials", "[{\"itemName\":\"Tulip\","
                        + "\"stemsPerDesign\":\"5\",\"packSize\":\"10\"}]");

        FarmDataMappingService.MappingPreview preview = service.preview(
                "floral-recipes", recipe, mapping, List.of(source), Map.of(),
                List.of("Workbook", "Row"));

        assertThat(preview.ready()).isTrue();
        assertThat(preview.records()).singleElement().satisfies(row -> {
            assertThat(row.get("sourceKey")).isEqualTo(
                    "composite-8:Workbook=14:Spring recipes|3:Row=2:14");
            assertThat(row.get("name")).isEqualTo("Mapped bouquet");
            assertThat((List<?>) row.get("ingredients")).singleElement()
                    .isInstanceOf(Map.class);
        });
    }

    @Test
    @DisplayName("returns row-specific coercion and required-field issues")
    void reportsInvalidRows() {
        FarmDataMappingService.MappingPreview preview = service.preview(
                "inventory", inventorySchema(), Map.of(
                        "name", "Crop", "quantity", "Count"),
                List.of(
                        Map.of("Crop", "Cosmos", "Count", "12"),
                        Map.of("Crop", "", "Count", "many")),
                Map.of(), List.of("Crop"));

        assertThat(preview.ready()).isFalse();
        assertThat(preview.rowCount()).isEqualTo(2);
        assertThat(preview.validCount()).isEqualTo(1);
        assertThat(preview.invalidCount()).isEqualTo(1);
        assertThat(preview.issues())
                .extracting(FarmDataMappingService.MappingIssue::row)
                .containsOnly(2);
        assertThat(preview.issues())
                .extracting(FarmDataMappingService.MappingIssue::field)
                .contains("name", "quantity", "sourceKey");
    }

    @Test
    @DisplayName("rejects unknown destinations, missing source paths, and mapping ambiguity")
    void rejectsInvalidMappingContracts() {
        assertThatThrownBy(() -> service.preview(
                "inventory", inventorySchema(), Map.of("typo", "Crop"),
                List.of(Map.of("Crop", "Dahlia")), Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown canonical field 'typo'");

        assertThatThrownBy(() -> service.preview(
                "inventory", inventorySchema(), Map.of("name", "Missing"),
                List.of(Map.of("Crop", "Dahlia")), Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable source field 'Missing'");

        assertThatThrownBy(() -> service.preview(
                "inventory", inventorySchema(), Map.of("sourceKey", "Legacy ID"),
                List.of(Map.of("Legacy ID", "1", "Product Name", "Dahlia")),
                Map.of(), List.of("Legacy ID")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be combined");
    }

    private static Map<String, Object> inventorySchema() {
        return objectSchema(List.of("name"), Map.of(
                "sourceKey", field("string"),
                "name", field("string"),
                "price", field("number"),
                "quantity", field("integer"),
                "unit", field("string")));
    }

    private static Map<String, Object> richSchema() {
        return objectSchema(List.of("name", "quantity"), Map.ofEntries(
                Map.entry("sourceKey", field("string")),
                Map.entry("name", field("string")),
                Map.entry("price", field("number")),
                Map.entry("quantity", field("integer")),
                Map.entry("unit", field("string")),
                Map.entry("active", field("boolean")),
                Map.entry("harvestDate", Map.of(
                        "type", "string", "format", "date")),
                Map.entry("tags", Map.of(
                        "type", "array", "items", field("string")))));
    }

    private static Map<String, Object> harvestSchema() {
        return objectSchema(List.of("harvestDate", "cropName", "quantity"),
                Map.ofEntries(
                        Map.entry("harvestDate", Map.of(
                                "type", "string", "format", "date")),
                        Map.entry("cropName", field("string")),
                        Map.entry("quantity", field("number")),
                        Map.entry("lotCode", field("string")),
                        Map.entry("grade", field("string")),
                        Map.entry("lossQuantity", field("number")),
                        Map.entry("storageLocation", field("string"))));
    }

    private static Map<String, Object> objectSchema(
            List<String> required, Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "required", required,
                "properties", properties,
                "additionalProperties", false);
    }

    private static Map<String, Object> field(String type) {
        return Map.of("type", type);
    }
}

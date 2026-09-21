package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FarmDataMappingNumericTextTest {

    private final FarmDataMappingService service = new FarmDataMappingService(new ObjectMapper());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1200.50 | 1200.5", "1,200.50 | 1200.5", "$1,200.50 | 1200.5",
            "-$1,200.50 | -1200.5", "$-1,200.50 | -1200.5", "+$1,200 | 1200",
            "50% | 50", "12.5% | 12.5", "-0.5% | -0.5", "+5e1% | 50",
            ".5 | 0.5", "1. | 1", "1.e2 | 100", "1.25e+3 | 1250",
            "-2.5E-2 | -0.025", "1,200e-2 | 12", "00012.50 | 12.5",
            "0e2147483647 | 0", "0e-2147483647 | 0", "4.9e-324 | 4.9e-324"
    })
    void acceptsUnambiguousNumberCellsWithoutChangingPercentageUnits(String input, double expected) {
        var preview = preview("number", " \t" + input + " \n");

        assertThat(preview.ready()).as(input).isTrue();
        assertThat(preview.records().get(0)).containsEntry("value", expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1,5", "12,34", "1234,567", "1,,200", ",1200", "1200,", "1,200.5,0",
            "1$2", "12$", "$$12", "%50", "5%%", "1%2", "$12%", "-$12%",
            "1 200", "$ 12", "12 %", "1_200", "1e1,2", "--12", "+-12",
            ".", "$", "%", "NaN", "Infinity", "1\n2", "1e2147483649"
    })
    void rejectsMalformedNumericPunctuationInsteadOfRemovingIt(String input) {
        assertRejected("number", input, "numeric");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1,250 | 1250", "+1,250.000 | 1250", "-1.2e2 | -120", "1200e-2 | 12",
            "9,223,372,036,854,775,807 | 9223372036854775807",
            "-9,223,372,036,854,775,808 | -9223372036854775808",
            "9223372036854775807.000 | 9223372036854775807",
            "-9223372036854775808.000 | -9223372036854775808"
    })
    void preservesExactWholeNumbersAndLongBounds(String input, long expected) {
        var preview = preview("integer", input);

        assertThat(preview.ready()).as(input).isTrue();
        assertThat(preview.records().get(0)).containsEntry("value", expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$12", "-$12", "$-12", "12%", "1,5", "1$2", "%50", "5%%"})
    void integerCountsAndIdentifiersRejectMoneyAndPercentageDecorations(String input) {
        assertRejected("integer", input, "numeric");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9223372036854775808", "-9223372036854775809",
            "5.0000000000000001", "4.9999999999999999", "1e-2147483647", "1e2147483647"
    })
    void rejectsFractionsAndOverflowBeforeExpandingIntegerExponents(String input) {
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertRejected("integer", input, "whole number"));
    }

    @Test
    void handlesTheFullBigDecimalScaleRangeWithoutExpandingIt() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int scale : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                assertRejected("integer", new BigDecimal(BigInteger.ONE, scale), "whole number");
                assertRejected("integer", new BigDecimal(BigInteger.ONE.negate(), scale), "whole number");
                assertRejected("number", new BigDecimal(BigInteger.ONE, scale),
                        scale < 0 ? "finite" : "too small");
                var zero = preview("integer", new BigDecimal(BigInteger.ZERO, scale));
                assertThat(zero.ready()).isTrue();
                assertThat(zero.records().get(0)).containsEntry("value", 0L);
            }
        });
    }

    @Test
    void rejectsOverflowAndUnderflowInsteadOfTurningThemIntoInfinityOrZero() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (String input : List.of("1e309", "-1e309", "1e2147483647")) {
                assertRejected("number", input, "finite");
            }
            for (String input : List.of("1e-400", "-1e-400", "1e-2147483647")) {
                assertRejected("number", input, "too small");
            }
            for (Number input : List.of(Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Float.NaN)) {
                assertRejected("number", input, "numeric");
            }
        });
    }

    @Test
    void boundsExtremeNumericSpreadsheetDatesBeforeConvertingThemToIntegers() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "harvestDate", Map.of("type", "string", "format", "date")));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int scale : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                var preview = service.preview("harvests", schema, Map.of(), List.of(Map.of(
                        "harvestDate", new BigDecimal(BigInteger.ONE, scale))), Map.of(), List.of());
                assertThat(preview.ready()).isFalse();
                assertThat(preview.issues()).singleElement().satisfies(issue ->
                        assertThat(issue.message()).contains("spreadsheet date"));
            }
        });
    }

    @Test
    void enforcesTheSameGrammarForDefaultsAndNestedJsonCells() {
        Map<String, Object> ingredient = Map.of("type", "object", "properties", Map.of(
                "packSize", Map.of("type", "integer"), "packCost", Map.of("type", "number")));
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "price", Map.of("type", "number"),
                "ingredients", Map.of("type", "array", "items", ingredient)));
        assertThatThrownBy(() -> service.preview("floral-recipes", schema, Map.of(),
                List.of(Map.of("ingredients", "[]")), Map.of("price", "1,5"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price", "numeric");
        for (String cell : List.of("[{\"packSize\":\"$12\"}]", "[{\"packCost\":\"1$2\"}]")) {
            var preview = service.preview("floral-recipes", schema, Map.of(),
                    List.of(Map.of("ingredients", cell)), Map.of("price", "$1,200.50"), List.of());

            assertThat(preview.ready()).isFalse();
            assertThat(preview.issues()).extracting(FarmDataMappingService.MappingIssue::field)
                    .containsExactly("ingredients");
            assertThat(preview.records().get(0)).containsExactlyEntriesOf(Map.of("price", 1200.5));
        }
    }

    private void assertRejected(String type, Object input, String reason) {
        var preview = preview(type, input);

        assertThat(preview.ready()).as("%s: %s", type, input).isFalse();
        assertThat(preview.invalidCount()).isEqualTo(1);
        assertThat(preview.records().get(0)).doesNotContainKey("value");
        assertThat(preview.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.row()).isEqualTo(1);
            assertThat(issue.field()).isEqualTo("value");
            assertThat(issue.message()).contains(reason);
        });
    }

    private FarmDataMappingService.MappingPreview preview(String type, Object input) {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "value", Map.of("type", type)));
        return service.preview("numeric-contract", schema, Map.of(),
                List.of(Map.of("value", input)), Map.of(), List.of());
    }
}

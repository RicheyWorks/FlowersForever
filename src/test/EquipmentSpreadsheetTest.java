package com.flowerfarm.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EquipmentSpreadsheetTest {
    @TempDir Path directory;
    private final FarmReadingService validator = mock(FarmReadingService.class);
    private static final String HEADER = String.join(",", EquipmentSpreadsheet.HEADINGS) + "\n";
    private static final String ROW = "North probe,Tomato house,Air temperature,21.125000000001,°C,Measured,2026-10-01T08:30:00-07:00\n";

    @Test void exactDecimalsNamesAndOffsetsSurviveBomQuotedCommaAndReorderedHeaders() {
        var row = EquipmentSpreadsheet.parse("\uFEFFUnit,Equipment,Measured at,Value,Measurement,Condition,Growing area\r\n"
                + "°C,\"North, probe\",2026-10-01T08:30:00-07:00,21.125000000001,Air temperature,Measured,Tomato house\r\n", validator).get(0);
        assertThat(row.get("value").toString()).isEqualTo("21.125000000001");
        assertThat(row.get("observedAt")).isEqualTo("2026-10-01T15:30:00Z");
        assertThat(row.get("deviceId")).isEqualTo(ManualFarmReading.draft("x", "North, probe", "Tomato house",
                "air.temperature", "°C", "1", "GOOD", java.time.Instant.EPOCH).get("deviceId"));
    }

    @Test void retriesAndEquivalentOffsetsKeepIdentityWhileChangedValueKeepsConflictIdentity() {
        var original = EquipmentSpreadsheet.parse(HEADER + ROW, validator).get(0);
        var changed = EquipmentSpreadsheet.parse(HEADER + ROW.replace("21.125000000001", "22"), validator).get(0);
        var utc = EquipmentSpreadsheet.parse(HEADER + ROW.replace("08:30:00-07:00", "15:30:00Z"), validator).get(0);
        assertThat(original.get("readingKey")).isEqualTo(changed.get("readingKey")).isEqualTo(utc.get("readingKey"));
        assertThat(original).isEqualTo(utc).isNotEqualTo(changed);
    }

    @Test void explicitReferenceKeepsIdentityAcrossTimeCorrectionsAndDistinctEventsStayDistinct() {
        String header = HEADER.stripTrailing() + ",Reading reference\n";
        String row = ROW.stripTrailing() + ",event-17\n";
        var first = EquipmentSpreadsheet.parse(header + row, validator).get(0);
        assertThat(EquipmentSpreadsheet.parse(header + row.replace("08:30:00", "08:31:00"), validator).get(0).get("readingKey"))
                .isEqualTo(first.get("readingKey"));
        assertThat(EquipmentSpreadsheet.parse(header + row.replace("event-17", "event-18"), validator).get(0).get("readingKey"))
                .isNotEqualTo(first.get("readingKey"));
    }

    @Test void missingOmitsValueAndMeasuredZeroRemainsZero() {
        var missing = EquipmentSpreadsheet.parse(HEADER + ROW.replace("21.125000000001", "").replace("Measured,", "No reading,"), validator).get(0);
        assertThat(missing).doesNotContainKey("value");
        assertThat(EquipmentSpreadsheet.parse(HEADER + ROW.replace("21.125000000001", "0"), validator).get(0).get("value").toString()).isEqualTo("0");
    }

    @Test void malformedQuotesAreRejectedRatherThanSilentlyChangingCells() {
        for (String bad : List.of("\"North\"garbage", "No\"rth", "\"North")) {
            assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.replace("North probe", bad), validator))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageMatching(".*quot.*");
        }
    }

    @Test void duplicateUnknownAndMissingHeadingsAreRejected() {
        for (String header : List.of(HEADER.replace("Growing area", "Equipment"), HEADER.replace("Unit", "Other"), HEADER.replace("Condition,", ""))) {
            assertThatThrownBy(() -> EquipmentSpreadsheet.parse(header + ROW, validator)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER.stripTrailing() + ",Other\n" + ROW.stripTrailing() + ",x\n", validator))
                .hasMessageContaining("extra columns");
    }

    @Test void invalidRowReportsReadingNumberAndNoOffsetIsNotGuessed() {
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW + ROW.replace("08:30:00-07:00", "08:31:00"), validator))
                .hasMessageContaining("Reading 2:").hasMessageContaining("offset");
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.replace("Air temperature", "Bananas"), validator))
                .hasMessageContaining("measurement");
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.replace("Measured,", "Maybe,"), validator))
                .hasMessageContaining("condition");
    }

    @Test void conflictingEventsInsideSheetAreRejectedButExactRepeatsRemainRetryable() {
        assertThat(EquipmentSpreadsheet.parse(HEADER + ROW + ROW, validator)).hasSize(2);
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW + ROW.replace("21.125000000001", "25"), validator))
                .hasMessageContaining("conflicting readings");
    }

    @Test void equivalentDecimalsSharePreviewValuesAndExtremeZeroExponentCannotExpandPreviewText() {
        var rows = EquipmentSpreadsheet.parse(HEADER + ROW.replace("21.125000000001", "1.0")
                + ROW.replace("21.125000000001", "1.00"), validator);
        assertThat(rows.get(0)).isEqualTo(rows.get(1));
        var zero = EquipmentSpreadsheet.parse(HEADER + ROW.replace("21.125000000001", "0E-999999999"), validator).get(0);
        assertThat(zero.get("value")).isEqualTo(java.math.BigDecimal.ZERO);
    }

    @Test void rowColumnAndCellBoundsApplyDuringParsing() {
        assertThat(EquipmentSpreadsheet.parse(HEADER + ROW.repeat(500), validator)).hasSize(500);
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.repeat(501), validator)).hasMessageContaining("500");
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.replace("North probe", "a".repeat(1025)), validator)).hasMessageContaining("1,024");
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse("a,b,c,d,e,f,g,h,i\n", validator)).hasMessageContaining("eight");
        assertThatThrownBy(() -> EquipmentSpreadsheet.parse(HEADER + ROW.replace(",°C,", ","), validator)).hasMessageContaining("columns");
    }

    @Test void emptySheetsAreRejectedAndBlankPhysicalLinesAreIgnored() {
        for (String empty : List.of("", HEADER, "\n\r\n")) {
            assertThatThrownBy(() -> EquipmentSpreadsheet.parse(empty, validator)).hasMessageContaining("at least one");
        }
        assertThat(EquipmentSpreadsheet.parse("\n" + HEADER + "\n" + ROW + "\n", validator)).hasSize(1);
    }

    @Test void selectedUtf8CsvIsReadAndMalformedEncodingIsRejected() throws IOException {
        Path file = directory.resolve("equipment.csv");
        Files.writeString(file, HEADER + ROW, StandardCharsets.UTF_8);
        assertThat(EquipmentSpreadsheet.read(file, validator)).hasSize(1);
        Files.write(file, new byte[] {(byte) 0xC3, 0x28});
        assertThatThrownBy(() -> EquipmentSpreadsheet.read(file, validator)).hasMessageContaining("CSV UTF-8");
    }

    @Test void oversizedFilesFoldersWrongExtensionsAndMissingFilesAreRejected() throws IOException {
        Path file = directory.resolve("big.csv");
        Files.write(file, new byte[EquipmentSpreadsheet.MAX_BYTES + 1]);
        assertThatThrownBy(() -> EquipmentSpreadsheet.read(file, validator)).hasMessageContaining("1 MB");
        for (Path bad : List.of(directory, directory.resolve("missing.csv"), directory.resolve("sheet.xlsx"))) {
            assertThatThrownBy(() -> EquipmentSpreadsheet.read(bad, validator)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}

package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Durable farm data import receipt")
class FarmDataImportReceiptTest {

    private static final String FIRST_HASH = "a".repeat(64);
    private static final String SECOND_HASH = "b".repeat(64);

    @Test
    @DisplayName("normalizes source identity and compares exact action content")
    void normalizesAndMatches() {
        FarmDataImportReceipt receipt = new FarmDataImportReceipt(
                " Legacy Excel ", "harvests", " Row-17 ",
                "import-harvest-history", FIRST_HASH, 42L);

        assertThat(receipt.getSourceSystem()).isEqualTo("legacy excel");
        assertThat(receipt.getSourceKey()).isEqualTo("Row-17");
        assertThat(receipt.matches("import-harvest-history", FIRST_HASH)).isTrue();
        assertThat(receipt.matches("import-harvest-history", SECOND_HASH)).isFalse();

        receipt.updateTarget("import-harvest-history", SECOND_HASH, 43L);
        assertThat(receipt.getTargetRecordId()).isEqualTo(43L);
        assertThat(receipt.matches("import-harvest-history", SECOND_HASH)).isTrue();
    }

    @Test
    @DisplayName("rejects blank, oversized, invalid, and non-positive fields")
    void validatesReceiptIdentity() {
        assertThatThrownBy(() -> new FarmDataImportReceipt(
                "", "harvests", "row", "import-harvest-history", FIRST_HASH, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source system");
        assertThatThrownBy(() -> new FarmDataImportReceipt(
                "excel", "Harvest Rows", "row", "import-harvest-history",
                FIRST_HASH, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Record type");
        assertThatThrownBy(() -> new FarmDataImportReceipt(
                "excel", "harvests", "row", "import-harvest-history", "short", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> new FarmDataImportReceipt(
                "excel", "harvests", "row", "import-harvest-history", FIRST_HASH, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}

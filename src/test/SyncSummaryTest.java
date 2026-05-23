package com.flowerfarm.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SyncSummary + SyncDirection")
class SyncSummaryTest {

    // ── SyncSummary ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("total() sums all counters")
    void totalSumsAllCounters() {
        SyncSummary s = new SyncSummary(2, 3, 1, 4, 0);
        assertThat(s.total()).isEqualTo(10);
    }

    @Test
    @DisplayName("total() is 0 for an all-zero summary")
    void totalZero() {
        assertThat(new SyncSummary(0, 0, 0, 0, 0).total()).isZero();
    }

    @Test
    @DisplayName("isClean() is true when changes occurred and no errors")
    void isCleanHappyPath() {
        assertThat(new SyncSummary(1, 0, 0, 5, 0).isClean()).isTrue();
        assertThat(new SyncSummary(0, 1, 0, 0, 0).isClean()).isTrue();
        assertThat(new SyncSummary(0, 0, 1, 0, 0).isClean()).isTrue();
    }

    @Test
    @DisplayName("isClean() is false when errors exist")
    void isCleanFalseOnErrors() {
        assertThat(new SyncSummary(3, 0, 0, 0, 1).isClean()).isFalse();
    }

    @Test
    @DisplayName("isClean() is false when no changes occurred (skipped only)")
    void isCleanFalseWhenNoChanges() {
        assertThat(new SyncSummary(0, 0, 0, 10, 0).isClean()).isFalse();
    }

    @Test
    @DisplayName("toString() contains all field names and values")
    void toStringContainsFields() {
        SyncSummary s = new SyncSummary(2, 3, 1, 4, 0);
        String str = s.toString();

        assertThat(str)
                .contains("created=2")
                .contains("updated=3")
                .contains("deleted=1")
                .contains("skipped=4")
                .contains("errors=0")
                .contains("total=10");
    }

    // ── SyncDirection ────────────────────────────────────────────────────────

    @Test
    @DisplayName("IMPORT_ONLY can import, cannot export or sync")
    void importOnly() {
        assertThat(SyncDirection.IMPORT_ONLY.canImport()).isTrue();
        assertThat(SyncDirection.IMPORT_ONLY.canExport()).isFalse();
        assertThat(SyncDirection.IMPORT_ONLY.canSync()).isFalse();
    }

    @Test
    @DisplayName("EXPORT_ONLY can export, cannot import or sync")
    void exportOnly() {
        assertThat(SyncDirection.EXPORT_ONLY.canImport()).isFalse();
        assertThat(SyncDirection.EXPORT_ONLY.canExport()).isTrue();
        assertThat(SyncDirection.EXPORT_ONLY.canSync()).isFalse();
    }

    @Test
    @DisplayName("BIDIRECTIONAL supports import, export, and sync")
    void bidirectional() {
        assertThat(SyncDirection.BIDIRECTIONAL.canImport()).isTrue();
        assertThat(SyncDirection.BIDIRECTIONAL.canExport()).isTrue();
        assertThat(SyncDirection.BIDIRECTIONAL.canSync()).isTrue();
    }

    @ParameterizedTest(name = "{0} has a non-null name")
    @EnumSource(SyncDirection.class)
    @DisplayName("every enum constant has a non-blank name()")
    void allConstantsHaveName(SyncDirection dir) {
        assertThat(dir.name()).isNotBlank();
    }
}

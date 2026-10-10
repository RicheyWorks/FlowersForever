package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cooler lot shelf-life review")
class CoolerShelfLifeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("board sorts overdue lots first and ages from harvest date")
    void boardAgingOrder() {
        HarvestEntry overdue = lot(1L, "LOT-OLD", "Zinnia", DAY.minusDays(10), "Cooler A", 40);
        HarvestEntry due = lot(2L, "LOT-DUE", "Dahlia", DAY.minusDays(5), "Cooler B", 20);
        HarvestEntry fresh = lot(3L, "LOT-NEW", "Sunflower", DAY.minusDays(1), "Cooler A", 12);
        CoolerShelfLife.Board board = CoolerShelfLife.board(DAY,
                List.of(fresh, overdue, due), CoolerShelfLife.Settings.defaults());
        assertThat(board.lots()).extracting(CoolerShelfLife.AgedLot::lotCode)
                .containsExactly("LOT-OLD", "LOT-DUE", "LOT-NEW");
        assertThat(board.lots().get(0).status()).isEqualTo(CoolerShelfLife.Status.OVERDUE);
        assertThat(board.lots().get(0).ageDays()).isEqualTo(10);
        assertThat(board.lots().get(0).packFirstHint()).contains("Pack first");
        assertThat(board.lots().get(1).status()).isEqualTo(CoolerShelfLife.Status.DUE);
        assertThat(board.lots().get(2).status()).isEqualTo(CoolerShelfLife.Status.OK);
        assertThat(board.overdueCount()).isEqualTo(1);
        assertThat(board.headline()).contains("overdue");
    }

    @Test
    @DisplayName("crop interval and conditioning override the default review window")
    void cropIntervals() {
        CoolerShelfLife.Settings settings = CoolerShelfLife.Settings.defaults()
                .withCropInterval("Nootka Rose", 3)
                .withCropConditioning("Woody lilac", CoolerShelfLife.Conditioning.WOODY);
        assertThat(CoolerShelfLife.intervalDays(settings, "Nootka Rose")).isEqualTo(3);
        assertThat(CoolerShelfLife.intervalDays(settings, "Woody lilac")).isEqualTo(10);
        assertThat(CoolerShelfLife.intervalDays(settings, "Other")).isEqualTo(5);

        HarvestEntry rose = lot(9L, "ROSE-1", "Nootka Rose", DAY.minusDays(3), "Left cooler", 30);
        CoolerShelfLife.AgedLot aged = CoolerShelfLife.age(rose, DAY, settings).orElseThrow();
        assertThat(aged.status()).isEqualTo(CoolerShelfLife.Status.DUE);
        assertThat(aged.reviewIntervalDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("last review date resets the due window without changing harvest age")
    void lastReviewResetsDue() {
        HarvestEntry entry = lot(4L, "LOT-R", "Zinnia", DAY.minusDays(12), "Cooler", 18);
        CoolerShelfLife.Settings settings = CoolerShelfLife.Settings.defaults()
                .withReviewed("LOT-R", DAY.minusDays(1));
        CoolerShelfLife.AgedLot aged = CoolerShelfLife.age(entry, DAY, settings).orElseThrow();
        assertThat(aged.ageDays()).isEqualTo(12);
        assertThat(aged.status()).isEqualTo(CoolerShelfLife.Status.OK);
        assertThat(aged.reviewDueDate()).isEqualTo(DAY.plusDays(4));
    }

    @Test
    @DisplayName("review applies count, grade, and additional loss while leaving marketable stems")
    void applyReviewMutations() {
        HarvestEntry existing = lot(5L, "LOT-Q", "Ranunculus", DAY.minusDays(2), "Shelf 2", 50);
        existing.setLossQuantity(2);
        existing.setGrade(HarvestGrade.UNGRADED);
        existing.setNotes("Field note");
        CoolerShelfLife.AppliedReview applied = CoolerShelfLife.applyReview(existing,
                new CoolerShelfLife.ReviewAction(45.0, HarvestGrade.SELECT, 3.0, "soft stems"));
        assertThat(applied.marketableQuantity()).isEqualTo(42.0);
        assertThat(applied.lossQuantity()).isEqualTo(5.0);
        assertThat(applied.grade()).isEqualTo(HarvestGrade.SELECT);
        assertThat(applied.notes()).contains("Field note", "Cooler review: soft stems");
        assertThat(applied.summary()).contains("count", "loss", "grade");
    }

    @Test
    @DisplayName("review rejects writing off the whole lot through additional loss")
    void rejectFullWriteOff() {
        HarvestEntry existing = lot(6L, "LOT-Z", "Zinnia", DAY, "Cooler", 10);
        assertThatThrownBy(() -> CoolerShelfLife.applyReview(existing,
                new CoolerShelfLife.ReviewAction(null, null, 10, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leave marketable");
    }

    @Test
    @DisplayName("blank storage is labeled Unassigned cooler; empty qty lots are skipped")
    void storageAndEmptySkipped() {
        HarvestEntry blankStorage = lot(7L, "LOT-B", "Cosmos", DAY.minusDays(1), "", 8);
        HarvestEntry noId = new HarvestEntry(DAY, "Skip", 5, "stems", "Bed 1", "");
        noId.setLotCode("SKIP");
        CoolerShelfLife.Board board = CoolerShelfLife.board(DAY,
                List.of(blankStorage, noId), CoolerShelfLife.Settings.defaults());
        assertThat(board.lots()).hasSize(1);
        assertThat(board.lots().get(0).storageLocation()).isEqualTo("Unassigned cooler");
    }

    @Test
    @DisplayName("preferences round-trip crop intervals, conditioning, and last-reviewed lots")
    void preferencesRoundTrip() throws Exception {
        MemoryPrefs prefs = new MemoryPrefs();
        CoolerShelfLifePreferences store = new CoolerShelfLifePreferences(prefs);
        CoolerShelfLife.Settings saved = CoolerShelfLife.Settings.defaults()
                .withDefaultInterval(6)
                .withCropInterval("Dahlia", 4)
                .withCropConditioning("Lilac", CoolerShelfLife.Conditioning.WOODY)
                .withReviewed("ABC-1", DAY);
        store.save(saved);
        CoolerShelfLife.Settings loaded = store.load();
        assertThat(loaded.defaultIntervalDays()).isEqualTo(6);
        assertThat(loaded.cropIntervals()).containsEntry("dahlia", 4);
        assertThat(loaded.cropConditioning()).containsEntry("lilac", CoolerShelfLife.Conditioning.WOODY);
        assertThat(loaded.lastReviewedByLot()).containsEntry("ABC-1", DAY);
        assertThat(CoolerShelfLife.statusSavedInterval("Dahlia", 4)).contains("Dahlia");
        assertThat(CoolerShelfLife.menuDescription()).containsIgnoringCase("QR");
    }

    private static HarvestEntry lot(Long id, String code, String crop, LocalDate harvested,
                                    String storage, double qty) {
        HarvestEntry entry = new HarvestEntry(id, harvested, crop, qty, "stems", "Bed 1", "",
                code, HarvestGrade.STANDARD, 0, storage);
        return entry;
    }

    /** Tiny in-memory Preferences for tests (no user node side effects). */
    private static final class MemoryPrefs extends AbstractPreferences {
        private final java.util.Map<String, String> values = new java.util.LinkedHashMap<>();

        MemoryPrefs() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return this;
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}

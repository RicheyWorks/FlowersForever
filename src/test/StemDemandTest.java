package com.flowerfarm.service;

import com.flowerfarm.service.StemDemand.Action;
import com.flowerfarm.service.StemDemand.Board;
import com.flowerfarm.service.StemDemand.Line;
import com.flowerfarm.service.StemDemand.Need;
import com.flowerfarm.service.StemDemand.NotCounted;
import com.flowerfarm.service.StemDemand.Planting;
import com.flowerfarm.service.StemDemand.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Stem demand board — totals by crop and date, cooler first, forecast second, shortages flagged")
class StemDemandTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 16);
    private static final LocalDate SAT = LocalDate.of(2026, 10, 17);
    private static final LocalDate END = MON.plusDays(13);

    @Test
    @DisplayName("orders for the same crop and date become one line; cooler covers first, the rest is short")
    void totalsAndShortage() {
        Board board = plan(List.of(
                        new Need(FRI, "Nootka Rose", 40, true, "Order #1 Bloom Studio"),
                        new Need(FRI, "nootka  rose", 30, true, "Order #2 Kitsap Weddings"),
                        new Need(SAT, "Dahlia Cafe au Lait", 24, true, "Order #3 Market")),
                List.of(new Stock("Nootka Rose", 50)), List.of());

        assertThat(board.lines()).hasSize(2);
        Line roses = board.lines().get(0);
        assertThat(roses.date()).isEqualTo(FRI);
        assertThat(roses.crop()).isEqualTo("Nootka Rose");
        assertThat(roses.needed()).isEqualTo(70);
        assertThat(roses.fromCooler()).isEqualTo(50);
        assertThat(roses.shortBy()).isEqualTo(20);
        assertThat(roses.action()).isEqualTo(Action.BUY);
        assertThat(roses.neededFor()).isEqualTo("Order #1 Bloom Studio; Order #2 Kitsap Weddings");
        Line dahlias = board.lines().get(1);
        assertThat(dahlias.shortBy()).isEqualTo(24);
        assertThat(board.totalNeeded()).isEqualTo(94);
        assertThat(board.totalShort()).isEqualTo(44);
        assertThat(board.crops()).extracting(StemDemand.CropTotal::crop)
                .containsExactly("Dahlia Cafe au Lait", "Nootka Rose");
    }

    @Test
    @DisplayName("cooler stock is used once across dates, earliest date first")
    void coolerIsNotDoubleCounted() {
        Board board = plan(List.of(
                        new Need(MON, "Zinnia", 30, true, "Order #1"),
                        new Need(FRI, "Zinnia", 30, true, "Order #2")),
                List.of(new Stock("Zinnia", 40)), List.of());

        assertThat(board.lines()).extracting(Line::fromCooler).containsExactly(30, 10);
        assertThat(board.lines()).extracting(Line::shortBy).containsExactly(0, 20);
        assertThat(board.lines()).extracting(Line::action).containsExactly(Action.FROM_COOLER, Action.BUY);
        assertThat(board.crops().get(0).fromCooler()).isEqualTo(40);
    }

    @Test
    @DisplayName("a draft order never takes stems a later confirmed order needs")
    void confirmedBeforeDraft() {
        Board board = StemDemand.plan(MON, END, true, List.of(
                        new Need(MON, "Zinnia", 30, false, "Order #1 (draft)"),
                        new Need(FRI, "Zinnia", 30, true, "Order #2")),
                List.of(new Stock("Zinnia", 30)), List.of(), List.of(), List.of());

        Line draftDay = board.lines().get(0);
        Line confirmedDay = board.lines().get(1);
        assertThat(confirmedDay.fromCooler()).isEqualTo(30);
        assertThat(confirmedDay.shortBy()).isZero();
        assertThat(draftDay.draftStems()).isEqualTo(30);
        assertThat(draftDay.fromCooler()).isZero();
        assertThat(draftDay.shortBy()).isEqualTo(30);
    }

    @Test
    @DisplayName("planned harvest counts only what is projected by each need date, and only once")
    void forecastByDateAndOnlyOnce() {
        // 10 days of harvest from Monday, 100 stems: about 10 stems a day.
        Planting zinnias = new Planting("Zinnia", MON, MON.plusDays(9), 100);
        Board board = plan(List.of(
                        new Need(MON.plusDays(1), "Zinnia Queen Lime", 30, true, "Order #1"),
                        new Need(FRI, "Zinnia Queen Lime", 30, true, "Order #2")),
                List.of(), List.of(zinnias));

        Line tuesday = board.lines().get(0);
        Line friday = board.lines().get(1);
        assertThat(tuesday.toCut()).isEqualTo(20);       // Mon + Tue projected
        assertThat(tuesday.shortBy()).isEqualTo(10);
        assertThat(friday.toCut()).isEqualTo(30);        // 50 projected by Fri, 20 already promised
        assertThat(friday.shortBy()).isZero();
        assertThat(friday.action()).isEqualTo(Action.CUT);
        assertThat(board.notes()).contains(StemDemand.FORECAST_NOTE);
        assertThat(board.crops().get(0).projectedHarvest()).isEqualTo(100);
    }

    @Test
    @DisplayName("a harvest that starts after the event date does not cover it")
    void lateCropExposesShortage() {
        Planting late = new Planting("Dahlia", SAT.plusDays(1), SAT.plusDays(20), 400);

        Board board = plan(List.of(new Need(SAT, "Dahlia", 60, true, "Order #9")), List.of(), List.of(late));

        assertThat(board.lines().get(0).toCut()).isZero();
        assertThat(board.lines().get(0).shortBy()).isEqualTo(60);
    }

    @Test
    @DisplayName("a draft added later uses harvest without taking it from an earlier confirmed promise")
    void draftDoesNotOverPromiseHarvest() {
        Planting zinnias = new Planting("Zinnia", MON, MON.plusDays(9), 100);
        Board board = StemDemand.plan(MON, END, true, List.of(
                        new Need(MON.plusDays(1), "Zinnia", 15, false, "Order #1 (draft)"),
                        new Need(MON.plusDays(2), "Zinnia", 30, true, "Order #2")),
                List.of(), List.of(zinnias), List.of(), List.of());

        // Confirmed Wednesday takes all 30 projected by then; the draft on Tuesday has none left.
        assertThat(board.lines().get(1).toCut()).isEqualTo(30);
        assertThat(board.lines().get(0).toCut()).isZero();
        assertThat(board.lines().get(0).shortBy()).isEqualTo(15);
    }

    @Test
    @DisplayName("only needs and not-counted lines inside the window are shown")
    void windowIsRespected() {
        Board board = plan(List.of(
                        new Need(MON.minusDays(1), "Zinnia", 10, true, "Order #1"),
                        new Need(END.plusDays(1), "Zinnia", 10, true, "Order #2"),
                        new Need(FRI, "Zinnia", 10, true, "Order #3")),
                List.of(), List.of());

        assertThat(board.lines()).singleElement().extracting(Line::neededFor).isEqualTo("Order #3");
        assertThatThrownBy(() -> StemDemand.plan(FRI, MON, false, List.of(), List.of(), List.of(),
                List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recipe stems include the recipe's waste and round up once per crop")
    void recipeStemsWithWaste() {
        assertThat(StemDemand.recipeStems(5, 12, 10)).isEqualTo(66);
        assertThat(StemDemand.recipeStems(2.5, 3, 0)).isEqualTo(8);
        assertThat(StemDemand.recipeStems(3, 10, 0)).isEqualTo(30);
        assertThat(StemDemand.recipeStems(0, 10, 10)).isZero();
    }

    @Test
    @DisplayName("order lines count directly only in stems; cooler stock counts per stem or per item")
    void units() {
        assertThat(StemDemand.isStemUnit("stems")).isTrue();
        assertThat(StemDemand.isStemUnit("Per Stem")).isTrue();
        assertThat(StemDemand.isStemUnit("bunches")).isFalse();
        assertThat(StemDemand.isStemUnit("each")).isFalse();
        assertThat(StemDemand.isStockCountedInStems("Per Stem")).isTrue();
        assertThat(StemDemand.isStockCountedInStems("Per Unit")).isTrue();
        assertThat(StemDemand.isStockCountedInStems("each")).isTrue();
        assertThat(StemDemand.isStockCountedInStems("bunch")).isFalse();
        assertThat(StemDemand.isStockCountedInStems("lb")).isFalse();
    }

    @Test
    @DisplayName("CSV opens in a spreadsheet: one row per crop and date, not-counted rows, no formulas")
    void csv() {
        Board board = plan(List.of(new Need(FRI, "=Nootka Rose", 40, true, "Order #1 @Bloom")),
                List.of(new Stock("=Nootka Rose", 10)), List.of(),
                List.of(new NotCounted(SAT, 4L, 7L, "Order #4 Kitsap Weddings", "Centerpiece", 12, "designs",
                        "Sold in designs, so its stems are unknown.")));

        String csv = StemDemand.csv(board);

        assertThat(csv).startsWith("Date,Crop,Stems needed,Draft stems,From cooler,To cut (forecast),"
                + "Short,Action,Needed for\n");
        assertThat(csv).contains("2026-10-16,'=Nootka Rose,40,0,10,0,30,Short: buy or substitute,Order #1 @Bloom\n");
        assertThat(csv).doesNotContain(",=Nootka");
        assertThat(csv).contains("2026-10-17,Centerpiece,,,,,,Not counted,"
                + "\"Order #4 Kitsap Weddings · 12 designs · Sold in designs, so its stems are unknown.\"\n");
        assertThat(StemDemand.suggestedCsvFileName(board)).isEqualTo("stem-demand-2026-10-12-to-2026-10-25.csv");
    }

    @Test
    @DisplayName("share text puts the buy list first, then the cut list, then the board")
    void shareText() {
        Planting zinnias = new Planting("Zinnia", MON, MON.plusDays(9), 100);
        Board board = plan(List.of(
                        new Need(FRI, "Zinnia", 30, true, "Order #1"),
                        new Need(SAT, "Nootka Rose", 25, true, "Order #2")),
                List.of(), List.of(zinnias));

        String text = StemDemand.shareText(board, "Evergreen & Bloom Farm");

        assertThat(text).startsWith("Evergreen & Bloom Farm — stem demand 2026-10-12 to 2026-10-25\n");
        assertThat(text).contains("55 stems needed, 25 short.");
        assertThat(text.indexOf("Buy or substitute:")).isLessThan(text.indexOf("Cut from the field"));
        assertThat(text).contains("2026-10-17  Nootka Rose", "2026-10-16  Zinnia");
        assertThat(text).contains(StemDemand.FORECAST_NOTE);
        assertThat(StemDemand.summary(board)).isEqualTo("55 stems needed across 2 crop(s) from 2026-10-12 "
                + "to 2026-10-25. 25 stems short on 1 crop(s).");
    }

    @Test
    @DisplayName("an empty window says so plainly")
    void emptyBoard() {
        Board board = plan(List.of(), List.of(new Stock("Zinnia", 10)), List.of());

        assertThat(board.isEmpty()).isTrue();
        assertThat(StemDemand.shareText(board, null)).contains("No stems are needed by orders in these dates.");
        assertThat(StemDemand.summary(board)).startsWith("No stems are needed");
        assertThat(board.notes()).anyMatch(note -> note.contains("Draft orders are not included"));
    }

    private static Board plan(List<Need> needs, List<Stock> stock, List<Planting> plantings) {
        return plan(needs, stock, plantings, List.of());
    }

    private static Board plan(List<Need> needs, List<Stock> stock, List<Planting> plantings,
                              List<NotCounted> notCounted) {
        return StemDemand.plan(MON, END, false, needs, stock, plantings, notCounted, List.of());
    }
}

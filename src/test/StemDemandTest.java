package com.flowerfarm.service;

import com.flowerfarm.service.StemDemand.Action;
import com.flowerfarm.service.StemDemand.Board;
import com.flowerfarm.service.StemDemand.Event;
import com.flowerfarm.service.StemDemand.EventLine;
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

    @Test
    @DisplayName("by event: two events on one date share the cooler once, lower order number first, drafts last")
    void eventsShareCoolerOnce() {
        Board board = StemDemand.plan(MON, END, true, List.of(
                        new Need(FRI, "Nootka Rose", 10, false, "Order #3 Market (draft)", 3L,
                                "Order #3 Market (draft)"),
                        new Need(FRI, "Nootka Rose", 30, true, "Order #2 Kitsap Weddings", 2L,
                                "Order #2 Kitsap Weddings"),
                        new Need(FRI, "Nootka Rose", 30, true, "Order #1 Bloom Studio", 1L, "Order #1 Bloom Studio")),
                List.of(new Stock("Nootka Rose", 40)), List.of(), List.of(), List.of());

        assertThat(board.events()).extracting(Event::orderId).containsExactly(1L, 2L, 3L);
        assertThat(board.events()).extracting(Event::confirmed).containsExactly(true, true, false);
        EventLine first = board.events().get(0).lines().get(0);
        EventLine second = board.events().get(1).lines().get(0);
        EventLine draft = board.events().get(2).lines().get(0);
        assertThat(first.fromCooler()).isEqualTo(30);
        assertThat(first.shortBy()).isZero();
        assertThat(first.action()).isEqualTo(Action.FROM_COOLER);
        assertThat(second.fromCooler()).isEqualTo(10);
        assertThat(second.shortBy()).isEqualTo(20);
        assertThat(second.action()).isEqualTo(Action.BUY);
        assertThat(draft.fromCooler()).isZero();
        assertThat(draft.shortBy()).isEqualTo(10);
        Line combined = board.lines().get(0);
        assertThat(combined.fromCooler()).isEqualTo(40);
        assertThat(combined.shortBy()).isEqualTo(30);
        assertThat(board.events().stream().mapToInt(Event::totalNeeded).sum()).isEqualTo(board.totalNeeded());
        assertThat(board.events().stream().mapToInt(Event::totalShort).sum()).isEqualTo(board.totalShort());
    }

    @Test
    @DisplayName("by event: a confirmed event on a later date keeps its cooler stems ahead of an earlier draft")
    void eventsConfirmedBeforeDraft() {
        Board board = StemDemand.plan(MON, END, true, List.of(
                        new Need(MON, "Zinnia", 30, false, "Order #1 (draft)", 1L, "Order #1 (draft)"),
                        new Need(FRI, "Zinnia", 30, true, "Order #2", 2L, "Order #2")),
                List.of(new Stock("Zinnia", 30)), List.of(), List.of(), List.of());

        assertThat(board.events()).extracting(Event::date).containsExactly(MON, FRI);
        assertThat(board.events().get(0).lines().get(0).shortBy()).isEqualTo(30);
        assertThat(board.events().get(1).lines().get(0).fromCooler()).isEqualTo(30);
    }

    @Test
    @DisplayName("by event: planned harvest is promised once, to the first event that can use it")
    void eventsShareHarvestOnce() {
        // 10 days of harvest from Monday, 100 stems: 20 projected by Tuesday.
        Planting zinnias = new Planting("Zinnia", MON, MON.plusDays(9), 100);
        Board board = plan(List.of(
                        new Need(MON.plusDays(1), "Zinnia", 15, true, "Order #4", 4L, "Order #4"),
                        new Need(MON.plusDays(1), "Zinnia", 15, true, "Order #5", 5L, "Order #5")),
                List.of(), List.of(zinnias));

        assertThat(board.events()).extracting(event -> event.lines().get(0).toCut()).containsExactly(15, 5);
        assertThat(board.events()).extracting(event -> event.lines().get(0).shortBy()).containsExactly(0, 10);
        assertThat(board.lines().get(0).toCut()).isEqualTo(20);
        assertThat(board.lines().get(0).shortBy()).isEqualTo(10);
    }

    @Test
    @DisplayName("by event: stems and recipe stems of one crop for one event become one line naming both")
    void eventsMergeOneCropPerEvent() {
        Board board = plan(List.of(
                        new Need(SAT, "Dahlia", 66, true, "Order #7 Kitsap Weddings · Centerpiece × 12", 7L,
                                "Order #7 Kitsap Weddings"),
                        new Need(SAT, "Dahlia", 10, true, "Order #7 Kitsap Weddings", 7L, "Order #7 Kitsap Weddings"),
                        new Need(SAT, "Eucalyptus", 27, true, "Order #7 Kitsap Weddings · Centerpiece × 12", 7L,
                                "Order #7 Kitsap Weddings")),
                List.of(new Stock("Dahlia", 50)), List.of());

        Event wedding = board.events().get(0);
        assertThat(board.events()).hasSize(1);
        assertThat(wedding.event()).isEqualTo("Order #7 Kitsap Weddings");
        assertThat(wedding.lines()).extracting(EventLine::crop).containsExactly("Dahlia", "Eucalyptus");
        EventLine dahlias = wedding.lines().get(0);
        assertThat(dahlias.needed()).isEqualTo(76);
        assertThat(dahlias.fromCooler()).isEqualTo(50);
        assertThat(dahlias.shortBy()).isEqualTo(26);
        assertThat(dahlias.madeFor()).isEqualTo("Centerpiece × 12; Stems ordered");
        assertThat(wedding.totalNeeded()).isEqualTo(103);
        assertThat(wedding.totalShort()).isEqualTo(53);
    }

    @Test
    @DisplayName("by event: not-counted lines stay with their event, even an event with nothing counted")
    void eventsKeepNotCountedLines() {
        Board board = StemDemand.plan(MON, END, true,
                List.of(new Need(FRI, "Zinnia", 10, true, "Order #1 Bloom Studio", 1L, "Order #1 Bloom Studio")),
                List.of(), List.of(),
                List.of(new NotCounted(FRI, 1L, 3L, "Order #1 Bloom Studio", "Dahlia mix", 8, "bunches",
                                "Sold in bunches, so its stems are unknown."),
                        new NotCounted(SAT, 2L, 4L, "Order #2 Market (draft)", "Bud vase", 4, "designs",
                                "Sold in designs, so its stems are unknown.", false)),
                List.of());

        assertThat(board.events()).hasSize(2);
        assertThat(board.events().get(0).lines()).hasSize(1);
        assertThat(board.events().get(0).notCounted()).extracting(NotCounted::product).containsExactly("Dahlia mix");
        Event draft = board.events().get(1);
        assertThat(draft.lines()).isEmpty();
        assertThat(draft.confirmed()).isFalse();
        assertThat(draft.notCounted()).extracting(NotCounted::product).containsExactly("Bud vase");
    }

    @Test
    @DisplayName("by event: across many mixed events, the event lines always add up to the crop-and-date board")
    void eventsAddUpToTheBoard() {
        java.util.Random random = new java.util.Random(52);
        String[] crops = {"Zinnia", "Dahlia", "Nootka Rose"};
        for (int round = 0; round < 40; round++) {
            List<Need> needs = new java.util.ArrayList<>();
            for (long order = 1; order <= 12; order++) {
                LocalDate date = MON.plusDays(random.nextInt(14));
                boolean confirmed = random.nextBoolean();
                for (String crop : crops) {
                    if (random.nextInt(3) > 0) {
                        needs.add(new Need(date, crop, random.nextInt(60), confirmed, "Order #" + order, order,
                                "Order #" + order));
                    }
                }
            }
            Board board = StemDemand.plan(MON, END, true, needs,
                    List.of(new Stock("Zinnia", random.nextInt(200)), new Stock("Dahlia", random.nextInt(100))),
                    List.of(new Planting("Zinnia", MON, MON.plusDays(9), random.nextInt(300)),
                            new Planting("Nootka Rose", MON.plusDays(3), MON.plusDays(20), random.nextInt(300))),
                    List.of(), List.of());

            for (Line line : board.lines()) {
                List<EventLine> parts = board.events().stream()
                        .filter(event -> event.date().equals(line.date()))
                        .flatMap(event -> event.lines().stream())
                        .filter(part -> part.crop().equals(line.crop()))
                        .toList();
                assertThat(parts.stream().mapToInt(EventLine::needed).sum()).isEqualTo(line.needed());
                assertThat(parts.stream().mapToInt(EventLine::fromCooler).sum()).isEqualTo(line.fromCooler());
                assertThat(parts.stream().mapToInt(EventLine::toCut).sum()).isEqualTo(line.toCut());
                assertThat(parts.stream().mapToInt(EventLine::shortBy).sum()).isEqualTo(line.shortBy());
            }
            for (StemDemand.CropTotal crop : board.crops()) {
                assertThat(crop.fromCooler()).isLessThanOrEqualTo(crop.coolerStock());
            }
        }
    }

    @Test
    @DisplayName("by event CSV: one row per event and crop, then its not-counted lines, no formulas")
    void eventsCsv() {
        Board board = plan(List.of(new Need(FRI, "=Nootka Rose", 40, true, "Order #1 @Bloom", 1L, "Order #1 @Bloom")),
                List.of(new Stock("=Nootka Rose", 10)), List.of(),
                List.of(new NotCounted(FRI, 1L, 7L, "Order #1 @Bloom", "Centerpiece", 12, "designs",
                        "Sold in designs, so its stems are unknown.")));

        String csv = StemDemand.eventsCsv(board);

        assertThat(csv).startsWith("Date,Event,Status,Crop,Stems needed,From cooler,To cut (forecast),Short,"
                + "Action,For\n");
        assertThat(csv).contains("2026-10-16,Order #1 @Bloom,Confirmed,'=Nootka Rose,40,10,0,30,"
                + "Short: buy or substitute,Stems ordered\n");
        assertThat(csv).contains("2026-10-16,Order #1 @Bloom,Confirmed,Centerpiece,,,,,Not counted,"
                + "\"12 designs · Sold in designs, so its stems are unknown.\"\n");
        assertThat(csv).doesNotContain(",=Nootka");
        assertThat(StemDemand.suggestedEventsCsvFileName(board))
                .isEqualTo("buying-list-by-event-2026-10-12-to-2026-10-25.csv");
    }

    @Test
    @DisplayName("by event share text lists each event with what to pull, cut, and buy, then the shared-stock note")
    void eventsShareText() {
        Board board = plan(List.of(
                        new Need(FRI, "Zinnia", 30, true, "Order #1 Bloom Studio", 1L, "Order #1 Bloom Studio"),
                        new Need(SAT, "Zinnia", 25, true, "Order #2 Kitsap Weddings", 2L, "Order #2 Kitsap Weddings")),
                List.of(new Stock("Zinnia", 40)), List.of());

        String text = StemDemand.eventsShareText(board, "Evergreen & Bloom Farm");

        assertThat(text).startsWith("Evergreen & Bloom Farm — buying list by event 2026-10-12 to 2026-10-25\n");
        assertThat(text).contains("2 event(s), 55 stems needed, 15 short across 1 event(s).");
        assertThat(text.indexOf("2026-10-16  Order #1 Bloom Studio"))
                .isLessThan(text.indexOf("2026-10-17  Order #2 Kitsap Weddings"));
        assertThat(text).contains("Short: buy or substitute", StemDemand.SHARED_STOCK_NOTE);
        Board empty = plan(List.of(), List.of(), List.of());
        assertThat(StemDemand.eventsShareText(empty, null)).contains("No events need stems in these dates.");
        assertThat(StemDemand.eventsSummary(empty)).startsWith("No events need stems");
        assertThat(empty.events()).isEmpty();
    }

    private static Board plan(List<Need> needs, List<Stock> stock, List<Planting> plantings) {
        return plan(needs, stock, plantings, List.of());
    }

    private static Board plan(List<Need> needs, List<Stock> stock, List<Planting> plantings,
                              List<NotCounted> notCounted) {
        return StemDemand.plan(MON, END, false, needs, stock, plantings, notCounted, List.of());
    }
}

package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Stem demand service — orders, recipes, cooler stock, and plantings from saved records")
class StemDemandServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate EVENT = LocalDate.of(2026, 10, 17);

    private final OrderService orders = mock(OrderService.class);
    private final CustomerOrderRepository orderRepository = mock(CustomerOrderRepository.class);
    private final ProductionStudioService studio = mock(ProductionStudioService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final SeasonPlanService seasonPlans = mock(SeasonPlanService.class);
    private final SyncHistoryService history = mock(SyncHistoryService.class);
    private StemDemandService service;
    private final List<CustomerOrder> saved = new ArrayList<>();
    private FloralRecipe centerpiece;

    @BeforeEach
    void setUp() {
        service = new StemDemandService(orders, orderRepository, studio, inventory, seasonPlans, history,
                new FarmProfile());
        centerpiece = new FloralRecipe("Centerpiece", "Centerpiece", 65, 20, 25, 10, 10, "");
        centerpiece.setId(5L);
        centerpiece.addIngredient(new FloralRecipeIngredient("Dahlia", 5, 10, 12, ""));
        centerpiece.addIngredient(new FloralRecipeIngredient("Eucalyptus", 2, 10, 8, ""));
        when(studio.listRecipes()).thenReturn(List.of(centerpiece));
        when(studio.getRecipe(5L)).thenReturn(centerpiece);
        when(studio.getRecipe(99L)).thenThrow(new IndexOutOfBoundsException("No floral recipe with id=99"));
        when(orders.findBetween(any(), any())).thenAnswer(call -> List.copyOf(saved));
        when(inventory.getAllItems()).thenReturn(List.of());
        when(seasonPlans.listForSeason(anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("an order line counted as a recipe adds each ingredient's stems with the recipe's waste")
    void recipeLineExpandsToStems() {
        OrderLine line = line(1L, "Centerpiece", 12, "designs");
        line.setRecipeId(5L);
        order(10L, EVENT, "CONFIRMED", "Kitsap Weddings", line);

        StemDemand.Board board = service.build(FROM, 14, false);

        assertThat(board.lines()).extracting(StemDemand.Line::crop, StemDemand.Line::needed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Dahlia", 66),
                        org.assertj.core.groups.Tuple.tuple("Eucalyptus", 27));
        assertThat(board.lines().get(0).neededFor()).isEqualTo("Order #10 Kitsap Weddings · Centerpiece × 12");
        assertThat(board.notCounted()).isEmpty();
    }

    @Test
    @DisplayName("stems count directly; other units are listed as not counted with the reason")
    void unitsDecideWhatCounts() {
        order(11L, EVENT, "CONFIRMED", "Bloom Studio",
                line(1L, "Nootka Rose", 40, "stems"), line(2L, "Dahlia mix", 8, "bunches"));

        StemDemand.Board board = service.build(FROM, 14, false);

        assertThat(board.lines()).singleElement().satisfies(row -> {
            assertThat(row.crop()).isEqualTo("Nootka Rose");
            assertThat(row.needed()).isEqualTo(40);
        });
        assertThat(board.notCounted()).singleElement().satisfies(row -> {
            assertThat(row.product()).isEqualTo("Dahlia mix");
            assertThat(row.orderId()).isEqualTo(11L);
            assertThat(row.lineId()).isEqualTo(2L);
            assertThat(row.reason()).contains("Sold in bunches", "Count it as a saved recipe");
        });
    }

    @Test
    @DisplayName("drafts only when asked; cancelled and fulfilled orders never")
    void statuses() {
        order(1L, EVENT, "CONFIRMED", "A", line(1L, "Zinnia", 10, "stems"));
        order(2L, EVENT, "DRAFT", "B", line(2L, "Zinnia", 20, "stems"));
        order(3L, EVENT, "CANCELLED", "C", line(3L, "Zinnia", 40, "stems"));
        order(4L, EVENT, "FULFILLED", "D", line(4L, "Zinnia", 80, "stems"));

        assertThat(service.build(FROM, 14, false).totalNeeded()).isEqualTo(10);
        StemDemand.Board withDrafts = service.build(FROM, 14, true);
        assertThat(withDrafts.totalNeeded()).isEqualTo(30);
        assertThat(withDrafts.lines().get(0).draftStems()).isEqualTo(20);
        assertThat(withDrafts.lines().get(0).neededFor()).contains("Order #2 B (draft)");
    }

    @Test
    @DisplayName("a deleted or empty recipe is reported instead of silently counting zero")
    void missingRecipe() {
        OrderLine gone = line(1L, "Bridal bouquet", 1, "each");
        gone.setRecipeId(77L);
        order(12L, EVENT, "CONFIRMED", "Kitsap Weddings", gone);

        StemDemand.Board board = service.build(FROM, 14, false);

        assertThat(board.lines()).isEmpty();
        assertThat(board.notCounted()).singleElement().extracting(StemDemand.NotCounted::reason)
                .asString().contains("no longer exists");
    }

    @Test
    @DisplayName("cooler stock in stems counts; stock in bunches is named in a note; complete plantings are ignored")
    void supplySources() {
        order(13L, EVENT, "CONFIRMED", "Bloom Studio",
                line(1L, "Nootka Rose", 40, "stems"), line(2L, "Zinnia", 50, "stems"));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Roses", 2.5, "Per Stem", 1, 25, ""),
                new Item("Zinnia", "Annuals", 10, "bunch", 3, 9, "")));
        when(seasonPlans.listForSeason(2026)).thenReturn(List.of(
                plan("Zinnia", SeasonPlanStatus.PLANNED, 300),
                plan("Zinnia", SeasonPlanStatus.COMPLETE, 5000)));

        StemDemand.Board board = service.build(FROM, 14, false);

        StemDemand.Line roses = board.lines().stream().filter(row -> row.crop().equals("Nootka Rose"))
                .findFirst().orElseThrow();
        assertThat(roses.fromCooler()).isEqualTo(25);
        assertThat(roses.shortBy()).isEqualTo(15);
        StemDemand.Line zinnias = board.lines().stream().filter(row -> row.crop().equals("Zinnia"))
                .findFirst().orElseThrow();
        assertThat(zinnias.fromCooler()).isZero();
        assertThat(zinnias.toCut()).isEqualTo(50);
        assertThat(board.notes()).anyMatch(note -> note.contains("Cooler stock of Zinnia is recorded in bunch"));
    }

    @Test
    @DisplayName("window limits are enforced")
    void windowLimits() {
        assertThatThrownBy(() -> service.build(FROM, 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.build(FROM, StemDemandService.MAX_DAYS + 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.build(EVENT, FROM, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("linking a line to a recipe saves the link and records it in sync history; null clears it")
    void linkAndUnlink() {
        OrderLine centerpieces = line(7L, "Centerpiece", 12, "designs");
        CustomerOrder order = order(20L, EVENT, "CONFIRMED", "Kitsap Weddings", centerpieces);
        when(orderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));

        StemDemandService.RecipeLink link = service.linkRecipe(20L, 7L, 5L);

        assertThat(link.recipeName()).isEqualTo("Centerpiece");
        assertThat(centerpieces.getRecipeId()).isEqualTo(5L);
        verify(orderRepository).save(order);
        verify(history).record(eq("crm"), eq("ORDER_LINE_RECIPE"), eq(true),
                eq("Counted Centerpiece on order #20 as recipe Centerpiece"), anyString(), eq(1));

        service.linkRecipe(20L, 7L, null);

        assertThat(centerpieces.getRecipeId()).isNull();
    }

    @Test
    @DisplayName("linking refuses fulfilled orders, unknown lines, and unknown recipes without saving")
    void linkRefusals() {
        CustomerOrder done = order(21L, EVENT, "FULFILLED", "A", line(8L, "Centerpiece", 1, "each"));
        CustomerOrder open = order(22L, EVENT, "DRAFT", "B", line(9L, "Centerpiece", 1, "each"));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(done));
        when(orderRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(open));
        when(orderRepository.findByIdForUpdate(23L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkRecipe(21L, 8L, 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fulfilled");
        assertThatThrownBy(() -> service.linkRecipe(22L, 404L, 5L)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> service.linkRecipe(22L, 9L, 99L)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> service.linkRecipe(23L, 9L, 5L)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(open.getLines().get(0).getRecipeId()).isNull();
        verify(orderRepository, never()).save(any());
    }

    private CustomerOrder order(long id, LocalDate date, String status, String customer, OrderLine... lines) {
        Customer buyer = new Customer(customer, "", "", "", "FLORIST", "");
        CustomerOrder order = new CustomerOrder(buyer, date, status, "", "DELIVERY");
        order.setId(id);
        for (OrderLine line : lines) {
            order.addLine(line);
        }
        saved.add(order);
        return order;
    }

    private static OrderLine line(long id, String product, double quantity, String unit) {
        OrderLine line = new OrderLine(product, quantity, unit, 1);
        line.setId(id);
        return line;
    }

    private static SeasonPlanEntry plan(String crop, SeasonPlanStatus status, int stems) {
        return new SeasonPlanEntry(2026, crop, "", "Bed A", FROM.minusDays(60), null,
                FROM, FROM.plusDays(9), 50, stems, status, "");
    }
}

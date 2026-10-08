package com.flowerfarm.service;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.CustomerOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the {@link StemDemand} board from saved records: confirmed (and optionally
 * draft) orders in a date window, saved floral recipes linked to order lines, live
 * inventory, and the season plan. Linking an order line to a recipe is the only write.
 */
@Service
public class StemDemandService {

    public static final int DEFAULT_DAYS = 14;
    public static final int MAX_DAYS = 120;

    private final OrderService orders;
    private final CustomerOrderRepository orderRepository;
    private final ProductionStudioService studio;
    private final InventoryService inventory;
    private final SeasonPlanService seasonPlans;
    private final SyncHistoryService history;
    private final FarmProfile farmProfile;

    public StemDemandService(OrderService orders, CustomerOrderRepository orderRepository,
                             ProductionStudioService studio, InventoryService inventory,
                             SeasonPlanService seasonPlans, SyncHistoryService history,
                             FarmProfile farmProfile) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.studio = Objects.requireNonNull(studio, "studio");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.seasonPlans = Objects.requireNonNull(seasonPlans, "seasonPlans");
        this.history = Objects.requireNonNull(history, "history");
        this.farmProfile = Objects.requireNonNull(farmProfile, "farmProfile");
    }

    public record RecipeLink(long orderId, long lineId, String product, Long recipeId, String recipeName) {
    }

    public LocalDate today() {
        return LocalDate.now(farmProfile.zoneId());
    }

    public String farmName() {
        return farmProfile.getName();
    }

    /** The board for {@code days} days starting {@code from} (today when null). */
    @Transactional(readOnly = true)
    public StemDemand.Board build(LocalDate from, int days, boolean includeDrafts) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Choose between 1 and " + MAX_DAYS + " days.");
        }
        LocalDate start = from == null ? today() : from;
        return build(start, start.plusDays(days - 1L), includeDrafts);
    }

    @Transactional(readOnly = true)
    public StemDemand.Board build(LocalDate from, LocalDate to, boolean includeDrafts) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Start and end dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        if (to.toEpochDay() - from.toEpochDay() >= MAX_DAYS) {
            throw new IllegalArgumentException("The stem demand board covers at most " + MAX_DAYS + " days.");
        }
        Map<Long, FloralRecipe> recipes = new LinkedHashMap<>();
        for (FloralRecipe recipe : studio.listRecipes()) {
            recipes.put(recipe.getId(), recipe);
        }
        List<StemDemand.Need> needs = new ArrayList<>();
        List<StemDemand.NotCounted> notCounted = new ArrayList<>();
        for (CustomerOrder order : orders.findBetween(from, to)) {
            String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
            boolean confirmed = "CONFIRMED".equals(status);
            if (!confirmed && !(includeDrafts && "DRAFT".equals(status))) {
                continue;
            }
            String source = source(order, confirmed);
            for (OrderLine line : order.getLines()) {
                addLine(order, line, confirmed, source, recipes, needs, notCounted);
            }
        }
        Set<String> demanded = new LinkedHashSet<>();
        needs.forEach(need -> demanded.add(StemDemand.normalize(need.crop())));
        List<StemDemand.Stock> stock = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Item item : inventory.getAllItems()) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            if (StemDemand.isStockCountedInStems(item.getUnit())) {
                stock.add(new StemDemand.Stock(item.getName(), item.getQuantity()));
            } else if (demanded.contains(StemDemand.normalize(item.getName()))) {
                notes.add("Cooler stock of " + item.getName() + " is recorded in " + item.getUnit()
                        + ", not stems, so it was not counted.");
            }
        }
        List<StemDemand.Planting> plantings = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            for (SeasonPlanEntry plan : seasonPlans.listForSeason(year)) {
                if (plan.getStatus() == SeasonPlanStatus.COMPLETE || plan.getHarvestStart() == null
                        || plan.getHarvestEnd() == null) {
                    continue;
                }
                plantings.add(new StemDemand.Planting(plan.getCropName(), plan.getHarvestStart(),
                        plan.getHarvestEnd(), plan.getExpectedStems()));
            }
        }
        return StemDemand.plan(from, to, includeDrafts, needs, stock, plantings, notCounted, notes);
    }

    /**
     * Counts an order line as a saved recipe (design count = line quantity), or stops
     * counting it with a {@code null} recipe. Only draft and confirmed orders change.
     */
    @Transactional
    public RecipeLink linkRecipe(Long orderId, Long lineId, Long recipeId) {
        if (orderId == null || lineId == null) {
            throw new IllegalArgumentException("Choose an order line first.");
        }
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(status) && !"CONFIRMED".equals(status)) {
            throw new IllegalArgumentException("Only draft or confirmed orders can change; order #"
                    + orderId + " is " + status.toLowerCase(Locale.ROOT) + ".");
        }
        OrderLine line = order.getLines().stream()
                .filter(candidate -> lineId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No product line with id=" + lineId + " on order #" + orderId));
        FloralRecipe recipe = recipeId == null ? null : studio.getRecipe(recipeId);
        line.setRecipeId(recipeId);
        orderRepository.save(order);
        String message = recipe == null
                ? "Stopped counting " + line.getProductName() + " on order #" + orderId + " as a recipe"
                : "Counted " + line.getProductName() + " on order #" + orderId + " as recipe "
                        + recipe.getName();
        history.record("crm", "ORDER_LINE_RECIPE", true, message,
                "lineId=" + lineId + ", recipeId=" + recipeId, 1);
        return new RecipeLink(orderId, lineId, line.getProductName(), recipeId,
                recipe == null ? null : recipe.getName());
    }

    private static void addLine(CustomerOrder order, OrderLine line, boolean confirmed, String source,
                                Map<Long, FloralRecipe> recipes, List<StemDemand.Need> needs,
                                List<StemDemand.NotCounted> notCounted) {
        LocalDate date = order.getOrderDate();
        if (line.getRecipeId() != null) {
            FloralRecipe recipe = recipes.get(line.getRecipeId());
            if (recipe == null) {
                notCounted.add(notCounted(order, line, source,
                        "Its linked recipe no longer exists; link another recipe to count its stems."));
                return;
            }
            int designs = (int) Math.min(Integer.MAX_VALUE, Math.ceil(line.getQuantity() - 1e-9));
            String detail = source + " · " + recipe.getName() + " × " + designs;
            boolean counted = false;
            for (FloralRecipeIngredient ingredient : recipe.getIngredients()) {
                int stems = StemDemand.recipeStems(ingredient.getStemsPerDesign(), designs,
                        recipe.getWastePercent());
                if (stems > 0) {
                    needs.add(new StemDemand.Need(date, ingredient.getItemName(), stems, confirmed, detail));
                    counted = true;
                }
            }
            if (!counted) {
                notCounted.add(notCounted(order, line, source,
                        "Recipe " + recipe.getName() + " has no ingredients yet."));
            }
            return;
        }
        if (StemDemand.isStemUnit(line.getUnit())) {
            int stems = (int) Math.min(Integer.MAX_VALUE, Math.ceil(line.getQuantity() - 1e-9));
            needs.add(new StemDemand.Need(date, line.getProductName(), stems, confirmed, source));
            return;
        }
        notCounted.add(notCounted(order, line, source, "Sold in " + line.getUnit()
                + ", so its stems are unknown. Count it as a saved recipe to include it."));
    }

    private static StemDemand.NotCounted notCounted(CustomerOrder order, OrderLine line, String source,
                                                    String reason) {
        return new StemDemand.NotCounted(order.getOrderDate(), order.getId(), line.getId(), source,
                line.getProductName(), line.getQuantity(), line.getUnit(), reason);
    }

    private static String source(CustomerOrder order, boolean confirmed) {
        String customer = order.getCustomer() == null || order.getCustomer().getName() == null
                ? "customer" : order.getCustomer().getName().trim();
        return "Order #" + order.getId() + " " + customer + (confirmed ? "" : " (draft)");
    }
}

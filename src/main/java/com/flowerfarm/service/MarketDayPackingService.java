package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.util.InventoryUnits;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Market-morning packing list for Kitsap / Port Orchard growers.
 *
 * <p>Pulls CONFIRMED (and optionally DRAFT) orders for a date — or a trailing
 * window when {@code date} is null — aggregates a product pick list against
 * inventory, and formats per-customer packing slips for the van load-out.
 */
@Service
public class MarketDayPackingService {

    private static final Logger log = LoggerFactory.getLogger(MarketDayPackingService.class);
    private static final int MAX_WINDOW_DAYS = 366;

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final FarmProfile farmProfile;

    public MarketDayPackingService(OrderService orderService, InventoryService inventoryService,
                                   FarmProfile farmProfile) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.farmProfile = farmProfile;
    }

    public record PackLine(
            String productName,
            String unit,
            double quantity,
            double unitPrice,
            double lineTotal,
            Long lineId
    ) {
        /** Compatibility constructor for plans that predate saved-line handoffs. */
        public PackLine(String productName, String unit, double quantity,
                        double unitPrice, double lineTotal) {
            this(productName, unit, quantity, unitPrice, lineTotal, null);
        }
    }

    public record CustomerPack(
            long orderId,
            String customerName,
            String customerType,
            String contactName,
            String phone,
            String email,
            String accountNotes,
            String status,
            LocalDate orderDate,
            String notes,
            List<PackLine> lines,
            double orderTotal,
            String handoffType
    ) {
        public CustomerPack {
            handoffType = CustomerOrder.normalizeHandoffType(handoffType);
            lines = lines == null ? List.of() : List.copyOf(lines);
            accountNotes = accountNotes == null ? "" : accountNotes;
        }

        /** Compatibility constructor for callers without customer account notes. */
        public CustomerPack(long orderId, String customerName, String customerType,
                            String contactName, String phone, String email, String status,
                            LocalDate orderDate, String notes, List<PackLine> lines,
                            double orderTotal, String handoffType) {
            this(orderId, customerName, customerType, contactName, phone, email, "", status,
                    orderDate, notes, lines, orderTotal, handoffType);
        }

        public CustomerPack(long orderId, String customerName, String customerType,
                            String contactName, String phone, String email, String status,
                            LocalDate orderDate, String notes, List<PackLine> lines,
                            double orderTotal) {
            this(orderId, customerName, customerType, contactName, phone, email, status,
                    orderDate, notes, lines, orderTotal, "UNSPECIFIED");
        }

        /** Compatibility constructor for plans created without customer contact context. */
        public CustomerPack(long orderId, String customerName, String customerType,
                            String status, LocalDate orderDate, String notes,
                            List<PackLine> lines, double orderTotal) {
            this(orderId, customerName, customerType, "", "", "", status,
                    orderDate, notes, lines, orderTotal, "UNSPECIFIED");
        }
    }

    public record ProductNeed(
            String productName,
            String unit,
            double neededQty,
            int stockOnHand,
            boolean shortfall,
            double shortfallQty,
            boolean unitMismatch,
            String inventoryUnit,
            boolean quantityInvalid
    ) {
        /** Compatibility constructor for stock-only packing needs. */
        public ProductNeed(String productName, String unit, double neededQty,
                           int stockOnHand, boolean shortfall, double shortfallQty) {
            this(productName, unit, neededQty, stockOnHand, shortfall, shortfallQty,
                    false, "", false);
        }

        public ProductNeed {
            inventoryUnit = inventoryUnit == null ? "" : inventoryUnit;
        }

        public boolean blockingIssue() {
            return shortfall || unitMismatch || quantityInvalid;
        }
    }

    public record MarketDayPlan(
            LocalDate marketDate,
            LocalDate from,
            LocalDate to,
            String scope,
            int orderCount,
            double pipelineValue,
            List<CustomerPack> customers,
            List<ProductNeed> pickList,
            int shortfallSkuCount,
            List<ProductNeed> confirmedPickList,
            int confirmedShortfallSkuCount,
            String plainText
    ) {
        /** Compatibility constructor for callers that provide a confirmed-only plan. */
        public MarketDayPlan(LocalDate marketDate, LocalDate from, LocalDate to, String scope,
                             int orderCount, double pipelineValue,
                             List<CustomerPack> customers, List<ProductNeed> pickList,
                             int shortfallSkuCount, String plainText) {
            this(marketDate, from, to, scope, orderCount, pipelineValue, customers,
                    pickList, shortfallSkuCount, pickList, shortfallSkuCount, plainText);
        }

        public int confirmedOrderCount() {
            return (int) customers.stream()
                    .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                    .count();
        }

        public double confirmedValue() {
            return round2(customers.stream()
                    .filter(customer -> "CONFIRMED".equalsIgnoreCase(customer.status()))
                    .mapToDouble(CustomerPack::orderTotal)
                    .sum());
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("marketDate", marketDate.toString());
            m.put("from", from.toString());
            m.put("to", to.toString());
            m.put("scope", scope);
            m.put("orderCount", orderCount);
            m.put("pipelineValue", pipelineValue);
            m.put("shortfallSkuCount", shortfallSkuCount);
            m.put("confirmedOrderCount", confirmedOrderCount());
            m.put("confirmedValue", confirmedValue());
            m.put("confirmedShortfallSkuCount", confirmedShortfallSkuCount);
            m.put("pickList", needMaps(pickList));
            m.put("confirmedPickList", needMaps(confirmedPickList));
            m.put("customers", customers.stream().map(c -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderId", c.orderId());
                row.put("customerName", c.customerName());
                row.put("customerType", c.customerType());
                row.put("contactName", c.contactName());
                row.put("phone", c.phone());
                row.put("email", c.email());
                row.put("accountNotes", c.accountNotes());
                row.put("status", c.status());
                row.put("orderDate", c.orderDate().toString());
                row.put("handoffType", c.handoffType());
                row.put("notes", c.notes());
                row.put("orderTotal", c.orderTotal());
                row.put("lines", c.lines().stream().map(l -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("productName", l.productName());
                    line.put("unit", l.unit());
                    line.put("quantity", l.quantity());
                    line.put("unitPrice", l.unitPrice());
                    line.put("lineTotal", l.lineTotal());
                    return line;
                }).toList());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }

        private static List<Map<String, Object>> needMaps(List<ProductNeed> needs) {
            return needs.stream().map(p -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productName", p.productName());
                row.put("unit", p.unit());
                row.put("neededQty", p.neededQty());
                row.put("stockOnHand", p.stockOnHand());
                row.put("shortfall", p.shortfall());
                row.put("shortfallQty", p.shortfallQty());
                row.put("unitMismatch", p.unitMismatch());
                row.put("inventoryUnit", p.inventoryUnit());
                row.put("quantityInvalid", p.quantityInvalid());
                return row;
            }).toList();
        }
    }

    /**
     * Build packing plan.
     *
     * @param marketDate focus date (default today); when {@code windowDays} &gt; 0, includes
     *                   orders from {@code marketDate - windowDays + 1} through {@code marketDate}
     * @param windowDays 1 = single day only; 7 = trailing week ending marketDate
     * @param includeDraft include DRAFT orders (default false)
     * @param includeFulfilled include already-fulfilled (default false — usually already packed)
     */
    @Transactional(readOnly = true)
    public MarketDayPlan buildPlan(LocalDate marketDate, int windowDays,
                                   boolean includeDraft, boolean includeFulfilled) {
        LocalDate day = marketDate != null ? marketDate : LocalDate.now();
        int window = windowDays <= 0 ? 1 : windowDays;
        if (window > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                    "windowDays cannot exceed " + MAX_WINDOW_DAYS + ".");
        }
        LocalDate from = day.minusDays(window - 1L);
        LocalDate to = day;

        List<CustomerOrder> orders = orderService.findBetween(from, to).stream()
                .filter(o -> includeStatus(o.getStatus(), includeDraft, includeFulfilled))
                .sorted(Comparator
                        .comparingInt((CustomerOrder o) -> handoffSequence(o.getHandoffType()))
                        .thenComparing(CustomerOrder::getOrderDate,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing((CustomerOrder o) -> o.getCustomer() != null
                                ? o.getCustomer().getName() : "", String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CustomerOrder::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<CustomerPack> packs = new ArrayList<>();
        for (CustomerOrder order : orders) {
            List<PackLine> lines = new ArrayList<>();
            for (OrderLine line : order.getLines()) {
                PackLine pl = new PackLine(
                        line.getProductName(),
                        line.getUnit(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.lineTotal(),
                        line.getId());
                lines.add(pl);
            }
            packs.add(customerPack(order, lines));
        }

        List<CustomerPack> activePacks = packs.stream()
                .filter(pack -> "CONFIRMED".equalsIgnoreCase(pack.status())
                        || "DRAFT".equalsIgnoreCase(pack.status()))
                .toList();
        List<CustomerPack> confirmedPacks = packs.stream()
                .filter(pack -> "CONFIRMED".equalsIgnoreCase(pack.status()))
                .toList();
        List<Item> inventory = inventoryService.getAllItems();
        List<ProductNeed> pickList = productNeeds(activePacks, inventory);
        int shortfalls = (int) pickList.stream().filter(ProductNeed::blockingIssue).count();
        List<ProductNeed> confirmedPickList = productNeeds(confirmedPacks, inventory);
        int confirmedShortfalls = (int) confirmedPickList.stream()
                .filter(ProductNeed::blockingIssue).count();

        double pipeline = activePacks.stream().mapToDouble(CustomerPack::orderTotal).sum();
        String scope = describeScope(includeDraft, includeFulfilled);
        String text = formatPlainText(day, from, to, scope, packs, pickList, pipeline, shortfalls);

        return new MarketDayPlan(
                day, from, to, scope,
                packs.size(),
                round2(pipeline),
                List.copyOf(packs),
                List.copyOf(pickList),
                shortfalls,
                List.copyOf(confirmedPickList),
                confirmedShortfalls,
                text
        );
    }

    private static int handoffSequence(String handoffType) {
        return switch (CustomerOrder.normalizeHandoffType(handoffType)) {
            case "UNSPECIFIED" -> 0;
            case "MARKET" -> 1;
            case "DELIVERY" -> 2;
            case "PICKUP" -> 3;
            default -> 4;
        };
    }

    /** Convenience: confirmed pipeline for a single calendar day. */
    @Transactional(readOnly = true)
    public MarketDayPlan planForDay(LocalDate marketDate) {
        return buildPlan(marketDate, 1, false, false);
    }

    public record FulfillBatchResult(
            int attempted,
            int fulfilled,
            int skipped,
            int failed,
            List<String> messages,
            Set<Long> fulfilledOrderIds,
            Set<Long> failedOrderIds
    ) {
        public FulfillBatchResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            fulfilledOrderIds = fulfilledOrderIds == null
                    ? Set.of() : Set.copyOf(fulfilledOrderIds);
            failedOrderIds = failedOrderIds == null ? Set.of() : Set.copyOf(failedOrderIds);
        }

        /** Compatibility constructor for callers that only have legacy receipt messages. */
        public FulfillBatchResult(int attempted, int fulfilled, int skipped, int failed,
                                  List<String> messages) {
            this(attempted, fulfilled, skipped, failed, messages,
                    outcomeOrderIds(messages, "→"), outcomeOrderIds(messages, "FAILED:"));
        }

        private static Set<Long> outcomeOrderIds(
                List<String> messages, String outcomeMarker) {
            Set<Long> ids = new LinkedHashSet<>();
            if (messages == null) {
                return Set.of();
            }
            for (String message : messages) {
                if (message == null || !message.contains(outcomeMarker)
                        || !message.startsWith("#")) {
                    continue;
                }
                int end = message.indexOf(' ');
                if (end <= 1) {
                    continue;
                }
                try {
                    long id = Long.parseLong(message.substring(1, end));
                    if (id > 0) {
                        ids.add(id);
                    }
                } catch (NumberFormatException ignored) {
                    // A malformed public message must never become completion evidence.
                }
            }
            return Set.copyOf(ids);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attempted", attempted);
            m.put("fulfilled", fulfilled);
            m.put("skipped", skipped);
            m.put("failed", failed);
            m.put("messages", messages);
            m.put("fulfilledOrderIds", fulfilledOrderIds());
            m.put("failedOrderIds", failedOrderIds());
            return m;
        }
    }

    /**
     * Fulfill every CONFIRMED order in the plan (post-market load-out).
     * Already-fulfilled rows are skipped; DRAFT/CANCELLED are not in a normal plan.
     */
    public FulfillBatchResult fulfillConfirmedOrders(MarketDayPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan is required.");
        }
        Map<Long, OrderService.OrderReviewSnapshot> reviewedOrders =
                verifyReviewedPlanIsCurrent(plan);
        List<String> messages = new ArrayList<>();
        int fulfilled = 0;
        int skipped = 0;
        int failed = 0;
        int attempted = 0;
        Set<Long> fulfilledOrderIds = new LinkedHashSet<>();
        Set<Long> failedOrderIds = new LinkedHashSet<>();

        for (CustomerPack pack : plan.customers()) {
            if (pack.orderId() <= 0) {
                continue;
            }
            if (!"CONFIRMED".equalsIgnoreCase(pack.status())) {
                skipped++;
                messages.add("#" + pack.orderId() + " " + pack.customerName()
                        + " skipped (status " + pack.status() + ")");
                continue;
            }
            attempted++;
            try {
                var order = orderService.fulfillReviewed(
                        pack.orderId(), reviewedOrders.get(pack.orderId()));
                fulfilled++;
                fulfilledOrderIds.add(pack.orderId());
                messages.add("#" + pack.orderId() + " " + pack.customerName()
                        + " → " + order.getStatus()
                        + " ($" + String.format(Locale.US, "%.2f", pack.orderTotal()) + ")");
            } catch (Exception ex) {
                failed++;
                failedOrderIds.add(pack.orderId());
                messages.add("#" + pack.orderId() + " " + pack.customerName()
                        + " FAILED: " + publicFulfillmentFailure(ex));
                if (ex instanceof IllegalArgumentException) {
                    log.warn("Batch fulfill rejected for order #{}: {}",
                            pack.orderId(), ex.getMessage());
                } else {
                    log.error("Batch fulfill failed unexpectedly for order #{}",
                            pack.orderId(), ex);
                }
            }
        }

        log.info("Market-day batch fulfill: attempted={} fulfilled={} skipped={} failed={}",
                attempted, fulfilled, skipped, failed);
        return new FulfillBatchResult(attempted, fulfilled, skipped, failed,
                List.copyOf(messages), fulfilledOrderIds, failedOrderIds);
    }

    /**
     * Rechecks the reviewed customer scope, exact loads, and aggregate stock before any batch write.
     * This prevents a stale checklist from omitting new work or completing changed packing details.
     */
    private Map<Long, OrderService.OrderReviewSnapshot> verifyReviewedPlanIsCurrent(
            MarketDayPlan plan) {
        List<CustomerPack> confirmed = plan.customers().stream()
                .filter(pack -> "CONFIRMED".equalsIgnoreCase(pack.status()))
                .toList();
        List<CustomerPack> emptyOrders = confirmed.stream()
                .filter(pack -> pack.lines().isEmpty())
                .toList();
        if (!emptyOrders.isEmpty()) {
            String orders = emptyOrders.stream()
                    .map(pack -> "#" + pack.orderId() + " " + pack.customerName())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("confirmed order");
            throw new IllegalArgumentException(
                    "Confirmed packing order has no saved products: " + orders
                            + ". Add at least one product in Customers & Orders, then rebuild "
                            + "the packing list before completing orders.");
        }

        List<CustomerPack> contextFreeDeliveries = confirmed.stream()
                .filter(MarketDayPackingService::missingDeliveryContext)
                .toList();
        if (!contextFreeDeliveries.isEmpty()) {
            String orders = contextFreeDeliveries.stream()
                    .map(pack -> "#" + pack.orderId() + " " + pack.customerName())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("delivery order");
            throw new IllegalArgumentException(
                    "Confirmed Delivery order has no contact or destination context: " + orders
                            + ". Add customer contact or account delivery notes, or save explicit "
                            + "order delivery instructions, then rebuild the packing list before "
                            + "completing orders.");
        }

        List<CustomerOrder> unreviewedConfirmed = orderService.findBetween(plan.from(), plan.to())
                .stream()
                .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .filter(order -> order.getId() != null && confirmed.stream()
                        .noneMatch(reviewed -> reviewed.orderId() == order.getId()))
                .toList();
        if (!unreviewedConfirmed.isEmpty()) {
            String orders = unreviewedConfirmed.stream()
                    .map(order -> "#" + order.getId() + " "
                            + (order.getCustomer() == null
                            ? "(unknown customer)" : order.getCustomer().getName()))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("new confirmed order");
            throw new IllegalArgumentException(
                    "New confirmed work entered this Market Day window after review: " + orders
                            + ". Rebuild the packing list and pack every current confirmed load "
                            + "before completing orders.");
        }

        List<String> changed = new ArrayList<>();
        Map<Long, OrderService.OrderReviewSnapshot> reviewedOrders = new LinkedHashMap<>();
        for (CustomerPack reviewed : confirmed) {
            Optional<CustomerOrder> current = orderService.findById(reviewed.orderId());
            if (current.isEmpty()
                    || !reviewed.equals(customerPack(current.get(), packLines(current.get())))) {
                changed.add("#" + reviewed.orderId() + " " + reviewed.customerName());
            } else {
                reviewedOrders.put(reviewed.orderId(),
                        OrderService.reviewSnapshot(current.get()));
            }
        }
        if (!changed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Packing details changed for " + String.join(", ", changed)
                            + ". Rebuild the packing list and check the updated load before completing orders.");
        }

        List<ProductNeed> liveNeeds = productNeeds(confirmed, inventoryService.getAllItems());
        List<ProductNeed> gaps = liveNeeds.stream().filter(ProductNeed::blockingIssue).toList();
        if (!gaps.isEmpty()) {
            String detail = gaps.stream()
                    .limit(3)
                    .map(MarketDayPackingService::blockingIssueText)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("stock unavailable");
            if (gaps.size() > 3) {
                detail += "; +" + (gaps.size() - 3) + " more";
            }
            boolean nonStockIssue = gaps.stream().anyMatch(need ->
                    need.unitMismatch() || need.quantityInvalid());
            throw new IllegalArgumentException(nonStockIssue
                    ? "Reviewed packing list has blocking product issues: " + detail
                            + ". Correct the order or Inventory, then rebuild the packing list "
                            + "before completing orders."
                    : "Current inventory no longer covers the reviewed packing list: " + detail
                            + ". Resolve stock, then rebuild the packing list before completing orders.");
        }
        return Map.copyOf(reviewedOrders);
    }

    private static CustomerPack customerPack(CustomerOrder order, List<PackLine> lines) {
        String custName = order.getCustomer() != null ? order.getCustomer().getName() : "(unknown)";
        String custType = order.getCustomer() != null ? order.getCustomer().getCustomerType() : "";
        String contactName = order.getCustomer() != null ? order.getCustomer().getContactName() : "";
        String phone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";
        String email = order.getCustomer() != null ? order.getCustomer().getEmail() : "";
        String accountNotes = order.getCustomer() != null ? order.getCustomer().getNotes() : "";
        return new CustomerPack(
                order.getId() != null ? order.getId() : 0L,
                custName,
                custType == null ? "" : custType,
                contactName == null ? "" : contactName,
                phone == null ? "" : phone,
                email == null ? "" : email,
                accountNotes == null ? "" : accountNotes,
                order.getStatus(),
                order.getOrderDate(),
                order.getNotes() == null ? "" : order.getNotes(),
                List.copyOf(lines),
                order.lineTotal(),
                order.getHandoffType());
    }

    public static boolean missingDeliveryContext(CustomerPack pack) {
        return pack != null
                && "DELIVERY".equals(CustomerOrder.normalizeHandoffType(pack.handoffType()))
                && (pack.contactName() == null || pack.contactName().isBlank())
                && (pack.phone() == null || pack.phone().isBlank())
                && (pack.email() == null || pack.email().isBlank())
                && (pack.accountNotes() == null || pack.accountNotes().isBlank())
                && (pack.notes() == null || pack.notes().isBlank());
    }

    private static List<PackLine> packLines(CustomerOrder order) {
        return order.getLines().stream()
                .map(line -> new PackLine(
                        line.getProductName(), line.getUnit(), line.getQuantity(),
                        line.getUnitPrice(), line.lineTotal(), line.getId()))
                .toList();
    }

    private static List<ProductNeed> productNeeds(List<CustomerPack> packs, List<Item> inventory) {
        Map<String, Agg> needs = new LinkedHashMap<>();
        for (CustomerPack pack : packs) {
            for (PackLine line : pack.lines()) {
                String key = normalizeKey(line.productName());
                Agg agg = needs.computeIfAbsent(
                        key, ignored -> new Agg(line.productName(), line.unit()));
                agg.add(line.unit(), roundedInventoryQuantity(line.quantity()));
            }
        }
        List<ProductNeed> result = new ArrayList<>();
        for (Agg agg : needs.values()) {
            int stock = stockForProduct(inventory, agg.productName);
            String inventoryUnit = inventoryUnitForProduct(inventory, agg.productName);
            boolean unitMismatch = agg.mixedUnits
                    || (!inventoryUnit.isBlank()
                    && !InventoryUnits.equivalent(inventoryUnit, agg.unit));
            double shortBy = Math.max(0, agg.qty - stock);
            result.add(new ProductNeed(
                    agg.productName, agg.unit, round1(agg.qty), stock,
                    shortBy > 0.0001, round1(shortBy), unitMismatch, inventoryUnit,
                    agg.invalidQuantity));
        }
        result.sort(Comparator.comparing(ProductNeed::productName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static int roundedInventoryQuantity(double quantity) {
        return Math.max(0, (int) Math.round(quantity));
    }

    private static String inventoryUnitForProduct(List<Item> inventory, String productName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        String target = normalizeKey(productName);
        return inventory.stream()
                .filter(item -> normalizeKey(item.getName()).equals(target))
                .map(Item::getUnit)
                .filter(unit -> unit != null && !unit.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private static String blockingIssueText(ProductNeed need) {
        if (need.quantityInvalid()) {
            return need.productName() + " has a line below one rounded inventory unit";
        }
        if (need.unitMismatch()) {
            String inventoryUnit = need.inventoryUnit().isBlank()
                    ? "unknown" : need.inventoryUnit();
            return need.productName() + " unit mismatch (order " + need.unit()
                    + ", inventory " + inventoryUnit + ")";
        }
        return need.productName() + " short "
                + String.format(Locale.US, "%.1f", need.shortfallQty())
                + " " + need.unit();
    }

    private static String packingStatus(ProductNeed need) {
        if (need.quantityInvalid()) {
            return "INVALID QTY";
        }
        if (need.unitMismatch()) {
            return "UNIT MISMATCH";
        }
        return need.shortfall()
                ? "SHORT " + String.format(Locale.US, "%.1f", need.shortfallQty())
                : "OK";
    }

    private static String publicFulfillmentFailure(Exception exception) {
        if (exception instanceof IllegalArgumentException && exception.getMessage() != null
                && !exception.getMessage().isBlank()) {
            String safe = exception.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
            return safe.length() <= 240 ? safe : safe.substring(0, 239) + "…";
        }
        return "The order could not be completed. Check server logs and retry.";
    }

    /**
     * Printable packing PDF: brand banner, pick list with shortfalls, per-customer slips.
     */
    public byte[] generatePackingPdf(MarketDayPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan is required.");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Color shortRed = new Color(255, 235, 230);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    farmProfile.documentBanner("Market Day Pack"),
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Market Day Packing List", titleFont));
            String period = plan.from().equals(plan.to())
                    ? plan.marketDate().toString()
                    : plan.from() + " → " + plan.to();
            doc.add(new Paragraph(
                    "Date: " + period + "     ·     Scope: " + plan.scope()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(new Paragraph(
                    "Load sequence: needs handoff review → Market → Delivery → Pickup",
                    small));
            long unresolvedHandoffs = confirmedUnspecifiedHandoffCount(plan.customers());
            if (unresolvedHandoffs > 0) {
                doc.add(new Paragraph(
                        "ACTION REQUIRED: " + unresolvedHandoffs
                                + " confirmed order(s) need Pickup, Delivery, or Market before load-out.",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.RED)));
            }
            long contextFreeDeliveries = confirmedMissingDeliveryContextCount(plan.customers());
            if (contextFreeDeliveries > 0) {
                doc.add(new Paragraph(
                        "ACTION REQUIRED: " + contextFreeDeliveries
                                + " confirmed Delivery order(s) have no contact or destination context.",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.RED)));
            }
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Orders", String.valueOf(plan.orderCount()), brandSoft));
            summary.addCell(summaryCell("Pipeline $",
                    String.format(Locale.US, "%,.2f", plan.pipelineValue()), brandSoft));
            summary.addCell(summaryCell("Product issues",
                    String.valueOf(plan.shortfallSkuCount()),
                    plan.shortfallSkuCount() > 0 ? shortRed : brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Pick list (aggregate vs stock)", h2));
            doc.add(Chunk.NEWLINE);
            if (plan.pickList().isEmpty()) {
                doc.add(new Paragraph(
                        "No CONFIRMED orders in range — create CRM orders first.", body));
            } else {
                PdfPTable pick = new PdfPTable(new float[]{3, 1.2f, 1.2f, 1.2f, 1.5f});
                pick.setWidthPercentage(100);
                headerCell(pick, "Product");
                headerCell(pick, "Need");
                headerCell(pick, "Unit");
                headerCell(pick, "Stock");
                headerCell(pick, "Status");
                for (ProductNeed p : plan.pickList()) {
                    Color rowBg = p.blockingIssue() ? shortRed : Color.WHITE;
                    pick.addCell(cell(p.productName(), body, rowBg));
                    pick.addCell(cell(String.format(Locale.US, "%.1f", p.neededQty()), body, rowBg));
                    pick.addCell(cell(p.unit(), body, rowBg));
                    pick.addCell(cell(String.valueOf(p.stockOnHand()), body, rowBg));
                    pick.addCell(cell(packingStatus(p), body, rowBg));
                }
                doc.add(pick);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("2. Pack slips by customer", h2));
            doc.add(Chunk.NEWLINE);
            if (plan.customers().isEmpty()) {
                doc.add(new Paragraph("(none)", body));
            } else {
                for (CustomerPack c : plan.customers()) {
                    String head = "#" + c.orderId() + "  " + c.customerName();
                    if (c.customerType() != null && !c.customerType().isBlank()) {
                        head += "  [" + c.customerType() + "]";
                    }
                    head += "  " + c.status() + "  $"
                            + String.format(Locale.US, "%.2f", c.orderTotal());
                    doc.add(new Paragraph(head,
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, brandGreen)));
                    doc.add(new Paragraph("Handoff: " + friendlyHandoff(c.handoffType())
                            + "  ·  Date: " + c.orderDate(), small));
                    String contact = contactDetails(c);
                    if (!contact.isBlank()) {
                        doc.add(new Paragraph("Contact: " + contact, small));
                    }
                    if (c.accountNotes() != null && !c.accountNotes().isBlank()) {
                        doc.add(new Paragraph("Account delivery note: " + c.accountNotes(), small));
                    }
                    if (c.notes() != null && !c.notes().isBlank()) {
                        doc.add(new Paragraph("Notes: " + c.notes(), small));
                    }
                    if (c.lines().isEmpty()) {
                        doc.add(new Paragraph("  (no lines)", small));
                    } else {
                        PdfPTable lines = new PdfPTable(new float[]{3, 1.2f, 1.2f, 1.2f, 1.4f});
                        lines.setWidthPercentage(100);
                        lines.setSpacingBefore(8f);
                        lines.setSpacingAfter(4f);
                        headerCell(lines, "Product");
                        headerCell(lines, "Qty");
                        headerCell(lines, "Unit");
                        headerCell(lines, "Price");
                        headerCell(lines, "Line $");
                        for (PackLine l : c.lines()) {
                            lines.addCell(cell(l.productName(), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.1f", l.quantity()), body, Color.WHITE));
                            lines.addCell(cell(l.unit(), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.2f", l.unitPrice()), body, Color.WHITE));
                            lines.addCell(cell(String.format(Locale.US, "%.2f", l.lineTotal()), body, Color.WHITE));
                        }
                        doc.add(lines);
                    }
                    doc.add(Chunk.NEWLINE);
                }
            }

            doc.add(new Paragraph(
                    "Tip: Fulfill in CRM after the market to deduct inventory & record revenue.",
                    small));
            doc.add(new Paragraph(
                    farmProfile.softwareFooter(), small));

            doc.close();
            log.info("Generated market-day packing PDF for {} ({} orders, {} packing issues)",
                    plan.marketDate(), plan.orderCount(), plan.shortfallSkuCount());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build packing PDF: " + e.getMessage(), e);
        }
    }

    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        cell.setBackgroundColor(bg != null ? bg : Color.WHITE);
        cell.setBorderColor(new Color(200, 210, 200));
        return cell;
    }

    private static PdfPCell summaryCell(String label, String value, Color bg) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    public String exportCsv(MarketDayPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("section,orderId,customer,status,handoffType,product,unit,qty,unitPrice,lineTotal,stock,shortfall,unitMismatch,inventoryUnit,quantityInvalid,contactName,phone,email,accountNotes,notes,orderDate\n");
        for (ProductNeed p : plan.pickList()) {
            sb.append(csvRow("PICK", "", "", "", "", p.productName(), p.unit(),
                    p.neededQty(), "", "", p.stockOnHand(),
                    p.shortfall() ? p.shortfallQty() : 0, p.unitMismatch(),
                    p.inventoryUnit(), p.quantityInvalid(), "", "", "", "", "", ""));
        }
        for (CustomerPack c : plan.customers()) {
            if (c.lines().isEmpty()) {
                sb.append(csvRow("ORDER", c.orderId(), c.customerName(), c.status(),
                        c.handoffType(), "", "", "", "", "", "", "", "", "", "",
                        c.contactName(), c.phone(), c.email(), c.accountNotes(), c.notes(),
                        c.orderDate()));
                continue;
            }
            for (PackLine l : c.lines()) {
                sb.append(csvRow("ORDER", c.orderId(), c.customerName(), c.status(),
                        c.handoffType(), l.productName(), l.unit(), l.quantity(),
                        l.unitPrice(), l.lineTotal(), "", "", "", "", "",
                        c.contactName(), c.phone(), c.email(), c.accountNotes(), c.notes(),
                        c.orderDate()));
            }
        }
        return sb.toString();
    }

    private static boolean includeStatus(String status, boolean includeDraft, boolean includeFulfilled) {
        if (status == null) {
            return false;
        }
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            return true;
        }
        if (includeDraft && "DRAFT".equalsIgnoreCase(status)) {
            return true;
        }
        if (includeFulfilled && "FULFILLED".equalsIgnoreCase(status)) {
            return true;
        }
        return false;
    }

    private static String describeScope(boolean draft, boolean fulfilled) {
        StringBuilder s = new StringBuilder("CONFIRMED");
        if (draft) {
            s.append("+DRAFT");
        }
        if (fulfilled) {
            s.append("+FULFILLED");
        }
        return s.toString();
    }

    private static int stockForProduct(List<Item> inventory, String productName) {
        if (productName == null || productName.isBlank()) {
            return 0;
        }
        String target = productName.trim().toLowerCase(Locale.ROOT);
        // Fulfillment requires the same exact case-insensitive SKU match. Never borrow
        // availability from a similarly named product such as "Rose" / "Spray Rose".
        return inventory.stream()
                .filter(item -> item.getName() != null
                        && item.getName().trim().toLowerCase(Locale.ROOT).equals(target))
                .mapToInt(Item::getQuantity)
                .sum();
    }

    private String formatPlainText(LocalDate marketDate, LocalDate from, LocalDate to,
                                   String scope, List<CustomerPack> packs,
                                   List<ProductNeed> pickList, double pipeline,
                                   int shortfalls) {
        StringBuilder sb = new StringBuilder();
        sb.append(farmProfile.documentBanner("Market Day Packing List")
                .toUpperCase(Locale.ROOT)).append('\n');
        sb.append("══════════════════════════════════════════════════════\n");
        sb.append("Market date : ").append(marketDate).append('\n');
        if (!from.equals(to)) {
            sb.append("Orders from : ").append(from).append(" → ").append(to).append('\n');
        }
        sb.append("Scope       : ").append(scope).append('\n');
        sb.append("Load order  : needs handoff review → Market → Delivery → Pickup\n");
        sb.append("Orders      : ").append(packs.size())
                .append("   Pipeline $").append(String.format(Locale.US, "%,.2f", pipeline)).append('\n');
        long unresolvedHandoffs = confirmedUnspecifiedHandoffCount(packs);
        if (unresolvedHandoffs > 0) {
            sb.append("⚠ Unspecified handoffs: ").append(unresolvedHandoffs)
                    .append(" confirmed order(s) need Pickup, Delivery, or Market\n");
        }
        long contextFreeDeliveries = confirmedMissingDeliveryContextCount(packs);
        if (contextFreeDeliveries > 0) {
            sb.append("⚠ Missing Delivery context: ").append(contextFreeDeliveries)
                    .append(" confirmed order(s) need contact or destination instructions\n");
        }
        if (shortfalls > 0) {
            sb.append("⚠ Packing issues: ").append(shortfalls)
                    .append(" product(s) need attention\n");
        }
        sb.append('\n');

        sb.append("PICK LIST (aggregate)\n");
        sb.append("─────────────────────\n");
        if (pickList.isEmpty()) {
            sb.append("  (no confirmed orders in range — create CONFIRMED CRM orders first)\n");
        } else {
            sb.append(String.format(Locale.US, "  %-28s %8s %-10s %8s %s%n",
                    "Product", "Need", "Unit", "Stock", "Status"));
            for (ProductNeed p : pickList) {
                String flag = packingStatus(p);
                sb.append(String.format(Locale.US, "  %-28s %8.1f %-10s %8d %s%n",
                        truncate(p.productName(), 28), p.neededQty(), p.unit(),
                        p.stockOnHand(), flag));
            }
        }

        sb.append('\n');
        sb.append("BY CUSTOMER (pack slips)\n");
        sb.append("───────────────────────\n");
        if (packs.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (CustomerPack c : packs) {
                sb.append('\n');
                sb.append("  #").append(c.orderId()).append("  ")
                        .append(c.customerName());
                if (c.customerType() != null && !c.customerType().isBlank()) {
                    sb.append("  [").append(c.customerType()).append(']');
                }
                sb.append("  ").append(c.status())
                        .append("  $").append(String.format(Locale.US, "%.2f", c.orderTotal()))
                        .append('\n');
                sb.append("    handoff: ").append(friendlyHandoff(c.handoffType()))
                        .append(" · date: ").append(c.orderDate()).append('\n');
                String contact = contactDetails(c);
                if (!contact.isBlank()) {
                    sb.append("    contact: ").append(contact).append('\n');
                }
                if (c.accountNotes() != null && !c.accountNotes().isBlank()) {
                    sb.append("    account delivery note: ").append(c.accountNotes()).append('\n');
                }
                if (c.notes() != null && !c.notes().isBlank()) {
                    sb.append("    notes: ").append(c.notes()).append('\n');
                }
                for (PackLine l : c.lines()) {
                    sb.append(String.format(Locale.US, "    • %-24s %6.1f %-8s @ $%.2f  = $%.2f%n",
                            truncate(l.productName(), 24), l.quantity(), l.unit(),
                            l.unitPrice(), l.lineTotal()));
                }
            }
        }
        sb.append('\n');
        sb.append("Tip: Fulfill in CRM after the market to deduct inventory & record revenue.\n");
        return sb.toString();
    }

    private static long confirmedUnspecifiedHandoffCount(List<CustomerPack> packs) {
        return packs.stream()
                .filter(pack -> "CONFIRMED".equalsIgnoreCase(pack.status()))
                .filter(pack -> "UNSPECIFIED".equals(
                        CustomerOrder.normalizeHandoffType(pack.handoffType())))
                .count();
    }

    private static long confirmedMissingDeliveryContextCount(List<CustomerPack> packs) {
        return packs.stream()
                .filter(pack -> "CONFIRMED".equalsIgnoreCase(pack.status()))
                .filter(MarketDayPackingService::missingDeliveryContext)
                .count();
    }

    private static String contactDetails(CustomerPack customer) {
        return java.util.stream.Stream.of(
                        customer.contactName(), customer.phone(), customer.email())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String csvRow(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> csv(value == null ? "" : String.valueOf(value)))
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "\n";
    }

    private static String friendlyHandoff(String value) {
        String normalized = CustomerOrder.normalizeHandoffType(value);
        if ("UNSPECIFIED".equals(normalized)) {
            return "Unspecified";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class Agg {
        final String productName;
        String unit;
        int qty;
        boolean mixedUnits;
        boolean invalidQuantity;

        Agg(String productName, String unit) {
            this.productName = productName;
            this.unit = unit == null ? "" : unit;
        }

        void add(String lineUnit, int lineQuantity) {
            String normalizedUnit = lineUnit == null ? "" : lineUnit.trim();
            if (!InventoryUnits.equivalent(unit, normalizedUnit)) {
                mixedUnits = true;
                unit = "mixed units";
            }
            if (lineQuantity <= 0) {
                invalidQuantity = true;
            }
            qty += lineQuantity;
        }
    }
}

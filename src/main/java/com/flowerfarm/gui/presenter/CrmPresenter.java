package com.flowerfarm.gui.presenter;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.util.InventoryUnits;

import java.time.LocalDate;
import java.util.List;

/**
 * UI-independent orchestration and projection for the CRM desktop view.
 */
public final class CrmPresenter {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final CustomerStatementService statementService;
    private final InventoryService inventoryService;

    public CrmPresenter(CustomerService customerService, OrderService orderService,
                        CustomerStatementService statementService) {
        this(customerService, orderService, statementService, null);
    }

    public CrmPresenter(CustomerService customerService, OrderService orderService,
                        CustomerStatementService statementService,
                        InventoryService inventoryService) {
        this.customerService = customerService;
        this.orderService = orderService;
        this.statementService = statementService;
        this.inventoryService = inventoryService;
    }

    public List<CustomerRow> searchCustomers(String query) {
        return customerService.search(query).stream().map(CustomerRow::from).toList();
    }

    public List<CustomerChoice> customerChoices() {
        return customerService.getAll().stream()
                .map(customer -> new CustomerChoice(
                        customer.getId(), customer.getName(), customer.getContactName(),
                        customer.getPhone(), customer.getEmail(), customer.getCustomerType(),
                        customer.getNotes()))
                .toList();
    }

    public List<InventoryChoice> inventoryChoices() {
        if (inventoryService == null) {
            return List.of();
        }
        return inventoryService.getAllItems().stream()
                .map(item -> new InventoryChoice(item.getName(), item.getUnit(),
                        item.getPrice(), item.getQuantity()))
                .toList();
    }

    /** Unfiltered confirmed commitments used only for stock-reservation guidance. */
    public List<CustomerOrder> confirmedCompletionQueue() {
        return orderService.confirmedCompletionQueue();
    }

    public OrderView filterOrders(String status, String customer, String fromText, String toText) {
        return filterOrders(status, customer, fromText, toText, "ALL");
    }

    public OrderView filterOrders(String status, String customer, String fromText, String toText,
                                  String handoffType) {
        LocalDate from = parseFilterDate(fromText, "filterFrom", "From");
        LocalDate to = parseFilterDate(toText, "filterTo", "To");
        if (from != null && to != null && to.isBefore(from)) {
            throw new InputValidationException("filterTo",
                    "The To date must be on or after the From date.");
        }
        List<CustomerOrder> orders = orderService.filter(status, customer, from, to);
        String handoff = handoffType == null || handoffType.isBlank()
                || "ALL".equalsIgnoreCase(handoffType)
                ? "" : CustomerOrder.normalizeHandoffType(handoffType);
        if (!handoff.isEmpty()) {
            orders = orders.stream()
                    .filter(order -> handoff.equals(
                            CustomerOrder.normalizeHandoffType(order.getHandoffType())))
                    .toList();
        }
        return orderView(orders);
    }

    /** Build the table projection for an already-selected order queue. */
    public static OrderView orderView(List<CustomerOrder> orders) {
        List<CustomerOrder> safeOrders = orders == null ? List.of() : List.copyOf(orders);
        List<OrderRow> rows = safeOrders.stream().map(OrderRow::from).toList();
        double total = rows.stream().mapToDouble(OrderRow::total).sum();
        long pipeline = rows.stream().filter(row -> "Confirmed".equalsIgnoreCase(row.status())).count();
        return new OrderView(safeOrders, rows, total, pipeline);
    }

    public Customer addCustomer(CustomerDraft draft) {
        return customerService.add(toCustomer(draft));
    }

    public Customer updateCustomer(long id, CustomerDraft draft) {
        return customerService.update(id, toCustomer(draft));
    }

    private static Customer toCustomer(CustomerDraft draft) {
        if (draft == null || draft.name() == null || draft.name().isBlank()) {
            throw new InputValidationException("customerName",
                    "Enter the customer or business name.");
        }
        String email = draft.email() == null ? "" : draft.email().trim();
        if (!email.isEmpty() && (!email.contains("@") || email.startsWith("@")
                || email.endsWith("@") || email.chars().anyMatch(Character::isWhitespace))) {
            throw new InputValidationException("customerEmail",
                    "Enter an email such as name@example.com, or leave it blank.");
        }
        try {
            return new Customer(draft.name(), draft.contact(), email, draft.phone(),
                    draft.type(), draft.notes());
        } catch (IllegalArgumentException exception) {
            throw new InputValidationException("customerForm", exception.getMessage());
        }
    }

    public void deleteCustomer(long id) {
        customerService.delete(id);
    }

    public CustomerOrder createOrder(OrderDraft draft) {
        if (draft == null) {
            throw new InputValidationException("orderForm", "Enter the order details.");
        }
        LocalDate orderDate = parseOrderDate(draft.orderDate());
        OrderLine line = toOrderLine(new LineDraft(
                draft.product(), draft.quantity(), draft.unit(), draft.unitPrice()));
        return orderService.create(draft.customerId(), orderDate, "DRAFT",
                draft.notes().trim(), CustomerOrder.normalizeHandoffType(draft.handoffType()),
                List.of(line));
    }

    public CustomerOrder createOrder(
            OrderDraft draft,
            java.util.Map<Long, OrderService.OrderReviewSnapshot> reviewedActiveOrders,
            boolean separateOrderApproved) {
        if (draft == null) {
            throw new InputValidationException("orderForm", "Enter the order details.");
        }
        LocalDate orderDate = parseOrderDate(draft.orderDate());
        OrderLine line = toOrderLine(new LineDraft(
                draft.product(), draft.quantity(), draft.unit(), draft.unitPrice()));
        // Desktop workers review the saved multi-product order before confirmation. Keeping this
        // boundary Draft-only prevents a stale or manipulated control from bypassing that review.
        return orderService.createReviewed(draft.customerId(), orderDate, "DRAFT",
                draft.notes().trim(), CustomerOrder.normalizeHandoffType(draft.handoffType()),
                List.of(line), reviewedActiveOrders, separateOrderApproved);
    }

    /** Active orders that could unintentionally split one customer's same-day handoff. */
    public List<ActiveOrderMatch> activeOrdersForCustomerDate(long customerId, String orderDate) {
        LocalDate date = parseOrderDate(orderDate);
        return orderService.findByCustomer(customerId).stream()
                .filter(order -> date.equals(order.getOrderDate()))
                .filter(order -> "DRAFT".equalsIgnoreCase(order.getStatus())
                        || "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .sorted(java.util.Comparator.comparing(
                        CustomerOrder::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(order -> new ActiveOrderMatch(
                        OrderRow.from(order), OrderService.reviewSnapshot(order)))
                .toList();
    }

    private static LocalDate parseOrderDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InputValidationException("orderDate",
                    "Enter the pickup, delivery, or market date in YYYY-MM-DD format.");
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim());
            LocalDate today = LocalDate.now();
            if (date.isBefore(today.minusYears(10)) || date.isAfter(today.plusYears(10))) {
                throw new InputValidationException("orderDate",
                        "Order date must be within 10 years of today; check the year.");
            }
            return date;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new InputValidationException("orderDate",
                    "Order date must use YYYY-MM-DD, for example "
                            + LocalDate.now().plusDays(1) + ".");
        }
    }

    public LocalDate validatedOrderDate(String raw) {
        return parseOrderDate(raw);
    }

    public CustomerOrder addOrderLine(long orderId, LineDraft draft) {
        return orderService.addLine(orderId, toOrderLine(draft));
    }

    public CustomerOrder addOrderLine(
            long orderId, OrderService.OrderReviewSnapshot reviewedSnapshot, LineDraft draft) {
        return orderService.addLineReviewed(orderId, reviewedSnapshot, toOrderLine(draft));
    }

    public CustomerOrder updateOrderLine(long orderId, long lineId, LineDraft draft) {
        return orderService.updateLine(orderId, lineId, toOrderLine(draft));
    }

    public CustomerOrder updateOrderLine(
            long orderId, long lineId, OrderService.OrderReviewLine reviewedLine,
            LineDraft draft) {
        return orderService.updateLineReviewed(
                orderId, lineId, reviewedLine, toOrderLine(draft));
    }

    public CustomerOrder updateOrderLine(
            long orderId, long lineId, OrderService.OrderReviewSnapshot reviewedOrder,
            OrderService.OrderReviewLine reviewedLine, LineDraft draft) {
        return orderService.updateLineReviewed(
                orderId, lineId, reviewedOrder, reviewedLine, toOrderLine(draft));
    }

    /** Validates product entry in the same order workers encounter its fields. */
    public void validateOrderLine(LineDraft draft) {
        toOrderLine(draft);
    }

    public CustomerOrder removeOrderLine(long orderId, long lineId) {
        return orderService.removeLine(orderId, lineId);
    }

    public CustomerOrder removeOrderLine(
            long orderId, long lineId, OrderService.OrderReviewLine reviewedLine) {
        return orderService.removeLineReviewed(orderId, lineId, reviewedLine);
    }

    public CustomerOrder removeOrderLine(
            long orderId, long lineId, OrderService.OrderReviewSnapshot reviewedOrder,
            OrderService.OrderReviewLine reviewedLine) {
        return orderService.removeLineReviewed(orderId, lineId, reviewedOrder, reviewedLine);
    }

    private static OrderLine toOrderLine(LineDraft draft) {
        if (draft == null || draft.product() == null || draft.product().isBlank()) {
            throw new InputValidationException("product", "Enter the inventory product name.");
        }
        double quantity = positiveNumber(draft.quantity(), "quantity", "Quantity");
        if (draft.unit() == null || draft.unit().isBlank()) {
            throw new InputValidationException("unit", "Enter how this product is sold.");
        }
        double unitPrice = nonNegativeNumber(draft.unitPrice(), "price", "Unit price");
        try {
            return new OrderLine(draft.product().trim(), quantity, draft.unit().trim(), unitPrice);
        } catch (IllegalArgumentException exception) {
            throw new InputValidationException(lineField(exception), exception.getMessage());
        }
    }

    /** Validates one proposed line and previews its value and fulfillment stock. */
    public LinePreview previewLine(LineDraft draft) {
        List<InventoryChoice> choices = inventoryService == null ? null : inventoryChoices();
        return previewLine(draft, choices);
    }

    /**
     * Validates one proposed line against an already-loaded inventory snapshot.
     * A {@code null} snapshot means inventory guidance is unavailable; an empty
     * snapshot means it is available but contains no exact product match.
     */
    public LinePreview previewLine(LineDraft draft, List<InventoryChoice> inventorySnapshot) {
        return previewLine(draft, inventorySnapshot, List.of(), null);
    }

    /**
     * Previews the post-save stock requirement for a selected order. The edited
     * line is excluded because its entered replacement is counted below.
     */
    public LinePreview previewLine(
            LineDraft draft, List<InventoryChoice> inventorySnapshot,
            List<OrderLine> savedLines, Long excludedLineId) {
        try {
            OrderLine line = toOrderLine(draft);
            double total = line.getQuantity() * line.getUnitPrice();
            int duplicateSavedLines = savedLines == null ? 0 : (int) savedLines.stream()
                    .filter(saved -> excludedLineId == null
                            || !excludedLineId.equals(saved.getId()))
                    .filter(saved -> saved.getProductName() != null
                            && saved.getProductName().equalsIgnoreCase(line.getProductName()))
                    .count();
            String duplicateWarning = duplicateSavedLines == 0 ? ""
                    : "Already saved on this order in " + duplicateSavedLines + " line"
                            + (duplicateSavedLines == 1 ? "" : "s") + " · ";
            if (inventorySnapshot == null) {
                return new LinePreview(true, false, true, duplicateSavedLines, total, null,
                        duplicateWarning
                                + String.format(java.util.Locale.ROOT, "$%,.2f line total", total));
            }
            return inventorySnapshot.stream()
                    .filter(choice -> choice.name().equalsIgnoreCase(line.getProductName()))
                    .findFirst()
                    .map(choice -> {
                        int required = (int) Math.round(line.getQuantity());
                        if (required <= 0) {
                            return new LinePreview(false, true, false, duplicateSavedLines, total,
                                    "quantity",
                                    "Quantity must round to at least 1 inventory unit");
                        }
                        if (!InventoryUnits.equivalent(choice.unit(), line.getUnit())) {
                            return new LinePreview(false, true, false, duplicateSavedLines, total,
                                    "unit",
                                    "Unit must match Inventory: use " + choice.unit()
                                            + " for " + choice.name());
                        }
                        int alreadyRequired = savedLines == null ? 0 : savedLines.stream()
                                .filter(saved -> excludedLineId == null
                                        || !excludedLineId.equals(saved.getId()))
                                .filter(saved -> saved.getProductName() != null
                                        && saved.getProductName().equalsIgnoreCase(
                                                line.getProductName()))
                                .filter(saved -> InventoryUnits.equivalent(
                                        saved.getUnit(), line.getUnit()))
                                .mapToInt(saved -> Math.max(
                                        0, (int) Math.round(saved.getQuantity())))
                                .sum();
                        int postSaveRequired = alreadyRequired + required;
                        boolean enough = required > 0
                                && choice.available() >= postSaveRequired;
                        String stock;
                        if (enough) {
                            stock = choice.available() + " on hand"
                                    + (alreadyRequired == 0 ? ""
                                    : " · " + postSaveRequired + " required after save ("
                                            + alreadyRequired + " already saved)");
                        } else {
                            stock = "Short by " + (postSaveRequired - choice.available())
                                    + " at completion"
                                    + (alreadyRequired == 0 ? ""
                                    : " · " + alreadyRequired + " already saved + " + required
                                            + " entered = " + postSaveRequired + " required");
                        }
                        return new LinePreview(true, true, enough, duplicateSavedLines, total, null,
                                duplicateWarning + String.format(java.util.Locale.ROOT,
                                        "$%,.2f line total · %s", total, stock));
                    })
                    .orElseGet(() -> new LinePreview(true, false, false,
                            duplicateSavedLines, total, null, duplicateWarning
                                    + String.format(java.util.Locale.ROOT,
                                            "$%,.2f line total · No exact inventory match", total)));
        } catch (InputValidationException exception) {
            return new LinePreview(false, false, false, 0, 0,
                    exception.field(), exception.getMessage());
        }
    }

    public boolean inventoryGuidanceAvailable() {
        return inventoryService != null;
    }

    private static double positiveNumber(String raw, String field, String label) {
        try {
            double value = Double.parseDouble(raw == null ? "" : raw.trim());
            if (!Double.isFinite(value) || value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new InputValidationException(field,
                    label + " must be a number greater than zero.");
        }
    }

    private static double nonNegativeNumber(String raw, String field, String label) {
        try {
            double value = Double.parseDouble(raw == null ? "" : raw.trim());
            if (!Double.isFinite(value) || value < 0) {
                throw new NumberFormatException();
            }
            return value == 0 ? 0 : value;
        } catch (NumberFormatException exception) {
            throw new InputValidationException(field,
                    label + " must be a number of zero or more.");
        }
    }

    private static String lineField(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("Quantity")) {
            return "quantity";
        }
        if (message.startsWith("Unit price")) {
            return "price";
        }
        if (message.startsWith("Product")) {
            return "product";
        }
        if (message.startsWith("Unit")) {
            return "unit";
        }
        return "line";
    }

    public void confirmOrder(long id) {
        orderService.confirm(id);
    }

    public OrderService.OrderReviewSnapshot reviewSnapshot(CustomerOrder order) {
        return OrderService.reviewSnapshot(order);
    }

    public void confirmOrder(long id, OrderService.OrderReviewSnapshot reviewedSnapshot) {
        orderService.confirmReviewed(id, reviewedSnapshot);
    }

    public void fulfillOrder(long id) {
        orderService.fulfill(id);
    }

    public void fulfillOrder(long id, OrderService.OrderReviewSnapshot reviewedSnapshot) {
        orderService.fulfillReviewed(id, reviewedSnapshot);
    }

    public void cancelOrder(long id) {
        orderService.cancel(id);
    }

    public void cancelOrder(long id, OrderService.OrderReviewSnapshot reviewedSnapshot) {
        orderService.cancelReviewed(id, reviewedSnapshot);
    }

    public void updateOrderNotes(long id, String notes) {
        orderService.updateNotes(id, notes);
    }

    public void updateOrderNotes(long id, String reviewedNotes, String notes) {
        orderService.updateNotesReviewed(id, reviewedNotes, notes);
    }

    public void updateOrderDate(long id, String orderDate) {
        orderService.updateOrderDate(id, parseOrderDate(orderDate));
    }

    public void updateOrderDate(long id, String reviewedOrderDate, String orderDate) {
        orderService.updateOrderDateReviewed(
                id, parseOrderDate(reviewedOrderDate), parseOrderDate(orderDate));
    }

    public void updateOrderHandoffType(long id, String handoffType) {
        orderService.updateHandoffType(id, CustomerOrder.normalizeHandoffType(handoffType));
    }

    public void updateOrderHandoffType(
            long id, String reviewedHandoffType, String handoffType) {
        orderService.updateHandoffTypeReviewed(
                id, CustomerOrder.normalizeHandoffType(reviewedHandoffType),
                CustomerOrder.normalizeHandoffType(handoffType));
    }

    public void deleteOrder(long id) {
        orderService.delete(id);
    }

    public void deleteOrder(long id, OrderService.OrderReviewSnapshot reviewedSnapshot) {
        orderService.deleteReviewed(id, reviewedSnapshot);
    }

    public byte[] invoicePdf(long id) {
        return orderService.generateInvoicePdf(id);
    }

    public void exportOrders(String path, List<CustomerOrder> orders) {
        orderService.exportToCsv(path, orders);
    }

    public void exportAllOrders(String path) {
        orderService.exportToCsv(path);
    }

    public boolean statementsAvailable() {
        return statementService != null;
    }

    public CustomerStatementService.CustomerStatement statement(long id,
                                                                 LocalDate from,
                                                                 LocalDate to) {
        if (statementService == null) {
            throw new IllegalStateException("Statement service not available.");
        }
        return statementService.build(id, from, to);
    }

    public StatementRange statementRange(String fromText, String toText, LocalDate today) {
        LocalDate end = today == null ? LocalDate.now() : today;
        LocalDate from = parseStatementDate(fromText, "statementFrom");
        LocalDate to = parseStatementDate(toText, "statementTo");
        from = from == null ? end.minusDays(90) : from;
        to = to == null ? end : to;
        if (to.isBefore(from)) {
            throw new InputValidationException("statementTo",
                    "Statement end date must be on or after the start date.");
        }
        return new StatementRange(from, to);
    }

    private static LocalDate parseStatementDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException exception) {
            throw new InputValidationException(field,
                    "Use YYYY-MM-DD for statement dates, or leave the field blank.");
        }
    }

    public byte[] statementPdf(CustomerStatementService.CustomerStatement statement) {
        if (statementService == null) {
            throw new IllegalStateException("Statement service not available.");
        }
        return statementService.generatePdf(statement);
    }

    public static LocalDate parseOptionalDate(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
    }

    private static LocalDate parseFilterDate(String raw, String field, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new InputValidationException(field,
                    label + " date must use YYYY-MM-DD, or be left blank.");
        }
    }

    public record CustomerDraft(String name, String contact, String email,
                                String phone, String type, String notes) {
    }

    public record OrderDraft(long customerId, String orderDate, String product, String quantity,
                             String unit, String unitPrice, String status, String notes,
                             String handoffType) {
        public OrderDraft(long customerId, String orderDate, String product, String quantity,
                          String unit, String unitPrice, String status, String notes) {
            this(customerId, orderDate, product, quantity, unit, unitPrice, status, notes,
                    "PICKUP");
        }
        /**
         * {@code status} remains for source compatibility; desktop creation always persists Draft.
         */
        public OrderDraft(long customerId, String product, String quantity,
                          String unit, String unitPrice, String status, String notes) {
            this(customerId, LocalDate.now().toString(), product, quantity,
                    unit, unitPrice, status, notes, "PICKUP");
        }
    }

    public record LineDraft(String product, String quantity, String unit, String unitPrice) {
    }

    public record LinePreview(boolean valid, boolean inventoryMatched,
                              boolean enoughStock, int duplicateSavedLines,
                              double lineTotal, String invalidField, String message) {
    }

    public record StatementRange(LocalDate from, LocalDate to) {
    }

    /** Input failure tied to one stable CRM form field key. */
    public static final class InputValidationException extends IllegalArgumentException {
        private final String field;

        public InputValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public record CustomerChoice(Long id, String name, String contact, String phone,
                                 String email, String type, String notes) {
        public CustomerChoice {
            name = clean(name);
            contact = clean(contact);
            phone = clean(phone);
            email = clean(email);
            type = clean(type);
            notes = clean(notes);
        }

        /** Compatibility constructor for callers that only need selection identity. */
        public CustomerChoice(Long id, String name) {
            this(id, name, "", "", "", "", "");
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }

        @Override public String toString() {
            return name;
        }
    }

    public record InventoryChoice(String name, String unit, double unitPrice, int available) {
        @Override public String toString() {
            return name + " — " + available + " on hand";
        }
    }

    public record CustomerRow(Long id, String name, String contact, String phone,
                              String email, String type, String notes) {
        private static CustomerRow from(Customer customer) {
            return new CustomerRow(customer.getId(), customer.getName(), customer.getContactName(),
                    customer.getPhone(), customer.getEmail(), friendlyType(customer.getCustomerType()),
                    customer.getNotes());
        }

        private static String friendlyType(String type) {
            if (type == null || type.isBlank()) {
                return "Other";
            }
            String lower = type.toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        public Object[] cells() {
            return new Object[]{id, name, contact, phone, email, type, notes};
        }
    }

    public record OrderRow(Long id, LocalDate date, String customer, String status,
                           String handoff, int lineCount, double total, String notes) {
        private static OrderRow from(CustomerOrder order) {
            String customerName = order.getCustomer() == null ? "?" : order.getCustomer().getName();
            return new OrderRow(order.getId(), order.getOrderDate(), customerName,
                    friendlyStatus(order.getStatus()), friendlyHandoff(order.getHandoffType()),
                    order.getLines().size(), order.lineTotal(), order.getNotes());
        }

        private static String friendlyHandoff(String handoff) {
            String normalized = CustomerOrder.normalizeHandoffType(handoff);
            if ("UNSPECIFIED".equals(normalized)) {
                return "Needs review";
            }
            String lower = normalized.toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        private static String friendlyStatus(String status) {
            if (status == null || status.isBlank()) {
                return "Draft";
            }
            return switch (status.toUpperCase(java.util.Locale.ROOT)) {
                case "CONFIRMED" -> "Confirmed";
                case "FULFILLED" -> "Completed";
                case "CANCELLED" -> "Cancelled";
                default -> "Draft";
            };
        }

        public Object[] cells() {
            return new Object[]{id, date, customer, status, handoff, lineCount,
                    String.format("%.2f", total), notes};
        }
    }

    public record ActiveOrderMatch(OrderRow row, OrderService.OrderReviewSnapshot reviewedOrder) {
        public String label() {
            return "#" + row.id() + " " + row.status() + " · " + row.handoff()
                    + " · " + row.lineCount() + " product"
                    + (row.lineCount() == 1 ? "" : "s") + " · $"
                    + String.format(java.util.Locale.ROOT, "%,.2f", row.total());
        }
    }

    public record OrderView(List<CustomerOrder> sourceOrders, List<OrderRow> rows,
                            double total, long pipelineCount) {
        public OrderView {
            sourceOrders = List.copyOf(sourceOrders);
            rows = List.copyOf(rows);
        }
    }
}

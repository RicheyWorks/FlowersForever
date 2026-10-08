package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.repository.CustomerOrderRepository;
import com.flowerfarm.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_ORDER_LINES = 500;
    private static final long MAX_QUERY_DAYS = 3_660;

    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;
    private final OrderReportGenerator reportGenerator;

    public OrderService(CustomerOrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        InventoryService inventoryService,
                        SyncHistoryService syncHistoryService,
                        OrderReportGenerator reportGenerator) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
        this.reportGenerator = reportGenerator;
    }

    public record OrderReviewLine(
            Long lineId, String productName, double quantity, String unit, double unitPrice) {
    }

    public static final class StaleOrderLineException extends IllegalArgumentException {
        private final OrderReviewLine currentLine;

        public StaleOrderLineException(long orderId, String action,
                                       OrderReviewLine currentLine) {
            super("Product line " + currentLine.lineId() + " on order #" + orderId
                    + " changed after you started " + action + ". The saved line is now “"
                    + currentLine.productName() + " · " + currentLine.quantity() + " "
                    + currentLine.unit() + " · $"
                    + String.format(Locale.ROOT, "%.2f", currentLine.unitPrice())
                    + "”. No order or inventory value was changed. Review the current saved "
                    + "product, then try again only if your change is still intended.");
            this.currentLine = currentLine;
        }

        public OrderReviewLine currentLine() {
            return currentLine;
        }
    }

    public static final class StaleOrderReviewException extends IllegalArgumentException {
        private final long orderId;
        private final boolean orderMissing;

        public StaleOrderReviewException(long orderId, String reviewName,
                                         String unchangedConsequence, String retryAction) {
            this(orderId, reviewName, unchangedConsequence, retryAction, false);
        }

        public StaleOrderReviewException(long orderId, String reviewName,
                                         String unchangedConsequence, String retryAction,
                                         boolean orderMissing) {
            super("Order #" + orderId + " changed after " + reviewName + " review. "
                    + unchangedConsequence + ". Refresh Customers and Orders, review the "
                    + "current customer load, then " + retryAction + ".");
            this.orderId = orderId;
            this.orderMissing = orderMissing;
        }

        public long orderId() {
            return orderId;
        }

        public boolean orderMissing() {
            return orderMissing;
        }
    }

    public record OrderReviewSnapshot(
            long orderId, Long customerId, String customerName, String contactName,
            String phone, String email, String status, LocalDate orderDate, String notes,
            List<OrderReviewLine> lines, String handoffType, String customerNotes) {
        public OrderReviewSnapshot {
            lines = List.copyOf(lines);
            handoffType = CustomerOrder.normalizeHandoffType(handoffType);
            customerNotes = confirmationText(customerNotes);
        }

        public OrderReviewSnapshot(
                long orderId, Long customerId, String customerName, String contactName,
                String phone, String email, String status, LocalDate orderDate, String notes,
                List<OrderReviewLine> lines, String handoffType) {
            this(orderId, customerId, customerName, contactName, phone, email, status,
                    orderDate, notes, lines, handoffType, "");
        }

        public OrderReviewSnapshot(
                long orderId, Long customerId, String customerName, String contactName,
                String phone, String email, String status, LocalDate orderDate, String notes,
                List<OrderReviewLine> lines) {
            this(orderId, customerId, customerName, contactName, phone, email, status,
                    orderDate, notes, lines, "UNSPECIFIED", "");
        }
    }

    /** Result of a phone/tablet handoff attempt against one reviewed operational load. */
    public record WorkerFulfillmentOutcome(
            CustomerOrder order,
            boolean alreadyFulfilled) {
    }

    /** Captures exactly what a worker reviewed before a lifecycle change. */
    public static OrderReviewSnapshot reviewSnapshot(CustomerOrder order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("A saved order is required for lifecycle review.");
        }
        Customer customer = order.getCustomer();
        List<OrderReviewLine> lines = order.getLines().stream()
                .map(OrderService::reviewLine)
                .sorted(Comparator
                        .comparing(OrderReviewLine::lineId,
                                Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(OrderReviewLine::productName)
                        .thenComparingDouble(OrderReviewLine::quantity)
                        .thenComparing(OrderReviewLine::unit)
                        .thenComparingDouble(OrderReviewLine::unitPrice))
                .toList();
        return new OrderReviewSnapshot(
                order.getId(), customer == null ? null : customer.getId(),
                customer == null ? "" : confirmationText(customer.getName()),
                customer == null ? "" : confirmationText(customer.getContactName()),
                customer == null ? "" : confirmationText(customer.getPhone()),
                customer == null ? "" : confirmationText(customer.getEmail()),
                confirmationText(order.getStatus()), order.getOrderDate(),
                confirmationText(order.getNotes()), lines, order.getHandoffType(),
                customer == null ? "" : confirmationText(customer.getNotes()));
    }

    /**
     * Opaque concurrency token for the operational fields shown on a worker handoff card.
     * Price is intentionally excluded because it is not displayed or used for stock deduction.
     */
    public static String handoffReviewToken(CustomerOrder order) {
        OrderReviewSnapshot review = reviewSnapshot(order);
        MessageDigest digest = sha256();
        appendFingerprintField(digest, review.orderId());
        appendFingerprintField(digest, review.customerId());
        appendFingerprintField(digest, review.customerName());
        appendFingerprintField(digest, review.contactName());
        appendFingerprintField(digest, review.phone());
        appendFingerprintField(digest, review.email());
        appendFingerprintField(digest, review.orderDate());
        appendFingerprintField(digest, review.notes());
        appendFingerprintField(digest, review.handoffType());
        appendFingerprintField(digest, review.customerNotes());
        appendFingerprintField(digest, review.lines().size());
        for (OrderReviewLine line : review.lines()) {
            appendFingerprintField(digest, line.lineId());
            appendFingerprintField(digest, line.productName());
            appendFingerprintField(digest, Double.toHexString(line.quantity()));
            appendFingerprintField(digest, line.unit());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Captures exactly one saved product as displayed for edit or removal. */
    public static OrderReviewLine reviewLine(OrderLine line) {
        if (line == null) {
            throw new IllegalArgumentException("A saved order product is required for review.");
        }
        return new OrderReviewLine(
                line.getId(), confirmationText(line.getProductName()), line.getQuantity(),
                confirmationText(line.getUnit()), line.getUnitPrice());
    }

    private static String confirmationText(String value) {
        return value == null ? "" : value;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void appendFingerprintField(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
            return;
        }
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> getAll() {
        return new ArrayList<>(orderRepository.findAllNewestFirst());
    }

    /** Complete confirmed queue used for deterministic stock reservation guidance. */
    @Transactional(readOnly = true)
    public List<CustomerOrder> confirmedCompletionQueue() {
        return getAll().stream()
                .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .sorted(Comparator
                        .comparing(CustomerOrder::getOrderDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CustomerOrder::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Bounded database-backed queue for phone/tablet order handoffs. */
    @Transactional(readOnly = true)
    public List<CustomerOrder> confirmedDueThrough(LocalDate throughDate, int limit) {
        return new ArrayList<>(orderRepository.findConfirmedDueThrough(throughDate, limit));
    }

    @Transactional(readOnly = true)
    public long countConfirmedDueThrough(LocalDate throughDate) {
        return orderRepository.countConfirmedDueThrough(throughDate);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> findBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed 10 years.");
        }
        return new ArrayList<>(orderRepository.findBetween(from, to));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> findByCustomer(Long customerId) {
        return new ArrayList<>(orderRepository.findByCustomer(customerId));
    }

    /**
     * Create an order for an existing customer. Drafts may begin empty; confirmed orders must
     * contain at least one product.
     */
    @Transactional
    public CustomerOrder create(Long customerId, LocalDate orderDate, String status, String notes,
                                List<OrderLine> lines) {
        return create(customerId, orderDate, status, notes, "UNSPECIFIED", lines);
    }

    @Transactional
    public CustomerOrder create(Long customerId, LocalDate orderDate, String status, String notes,
                                 String handoffType, List<OrderLine> lines) {
        if (lines != null && lines.size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException(
                    "Order cannot contain more than " + MAX_ORDER_LINES + " lines.");
        }
        if (lines != null && lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Order lines must not be null.");
        }
        String initialStatus = CustomerOrder.normalizeStatus(status);
        String normalizedHandoff = CustomerOrder.normalizeHandoffType(handoffType);
        if (!"DRAFT".equals(initialStatus) && !"CONFIRMED".equals(initialStatus)) {
            throw new IllegalArgumentException(
                    "New orders must start as DRAFT or CONFIRMED; use the order actions for "
                            + initialStatus + ".");
        }
        if ("CONFIRMED".equals(initialStatus) && (lines == null || lines.isEmpty())) {
            throw new IllegalArgumentException(
                    "A confirmed order must contain at least one product.");
        }
        if ("CONFIRMED".equals(initialStatus) && "UNSPECIFIED".equals(normalizedHandoff)) {
            throw new IllegalArgumentException(
                    "Choose PICKUP, DELIVERY, or MARKET before creating a confirmed order.");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + customerId));
        CustomerOrder order = new CustomerOrder(
                customer, orderDate, initialStatus, notes, normalizedHandoff);
        if (lines != null) {
            for (OrderLine line : lines) {
                order.addLine(new OrderLine(
                        line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
            }
        }
        if ("CONFIRMED".equals(initialStatus)) {
            requireDeliveryContext(order, "creating it as CONFIRMED");
        }
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm",
                "ORDER_CREATE",
                true,
                "Order #" + saved.getId() + " created for " + customer.getName(),
                "status=" + saved.getStatus() + ", lines=" + saved.getLines().size()
                        + ", total=$" + String.format("%.2f", saved.lineTotal()),
                saved.getLines().size()
        );
        return saved;
    }

    /**
     * Imports an existing order in any supported final state without reserving or deducting
     * current inventory. New operational orders must continue to use {@link #create}.
     */
    @Transactional
    public CustomerOrder importHistorical(
            Long customerId, LocalDate orderDate, String status, String notes,
            String handoffType, List<OrderLine> lines) {
        if (lines != null && lines.size() > MAX_ORDER_LINES) {
            throw new IllegalArgumentException(
                    "Order cannot contain more than " + MAX_ORDER_LINES + " lines.");
        }
        if (lines != null && lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Order lines must not be null.");
        }
        String historicalStatus = CustomerOrder.normalizeStatus(status);
        String normalizedHandoff = CustomerOrder.normalizeHandoffType(handoffType);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No customer with id=" + customerId));
        CustomerOrder order = new CustomerOrder(
                customer, orderDate, historicalStatus, notes, normalizedHandoff);
        if (lines != null) {
            for (OrderLine line : lines) {
                order.addLine(new OrderLine(
                        line.getProductName(), line.getQuantity(),
                        line.getUnit(), line.getUnitPrice()));
            }
        }
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm",
                "ORDER_HISTORY_IMPORT",
                true,
                "Historical order #" + saved.getId() + " imported for " + customer.getName(),
                "status=" + saved.getStatus() + ", lines=" + saved.getLines().size()
                        + ", inventoryAdjusted=false",
                saved.getLines().size());
        return saved;
    }

    /**
     * Creates a desktop-reviewed order while serializing the same-customer/date decision.
     * A concurrent worker changing that active-order set invalidates the review before any save.
     */
    @Transactional
    public CustomerOrder createReviewed(
            Long customerId, LocalDate orderDate, String status, String notes,
            String handoffType, List<OrderLine> lines, Set<Long> reviewedActiveOrderIds,
            boolean separateOrderApproved) {
        customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No customer with id=" + customerId));
        Set<Long> reviewed = reviewedActiveOrderIds == null
                ? Set.of() : Set.copyOf(reviewedActiveOrderIds);
        Set<Long> current = orderRepository.findByCustomer(customerId).stream()
                .filter(order -> orderDate != null && orderDate.equals(order.getOrderDate()))
                .filter(order -> "DRAFT".equalsIgnoreCase(order.getStatus())
                        || "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .map(CustomerOrder::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!current.equals(reviewed) || (!current.isEmpty() && !separateOrderApproved)) {
            throw new DuplicateOrderReviewChangedException(customerId, orderDate, reviewed, current);
        }
        return create(customerId, orderDate, status, notes, handoffType, lines);
    }

    /** Final split-order write bound to every exact active order the worker reviewed. */
    @Transactional
    public CustomerOrder createReviewed(
            Long customerId, LocalDate orderDate, String status, String notes,
            String handoffType, List<OrderLine> lines,
            Map<Long, OrderReviewSnapshot> reviewedActiveOrders,
            boolean separateOrderApproved) {
        customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No customer with id=" + customerId));
        Map<Long, OrderReviewSnapshot> reviewed = reviewedActiveOrders == null
                ? Map.of() : Map.copyOf(reviewedActiveOrders);
        List<CustomerOrder> currentOrders = orderRepository.findByCustomer(customerId).stream()
                .filter(order -> orderDate != null && orderDate.equals(order.getOrderDate()))
                .filter(order -> "DRAFT".equalsIgnoreCase(order.getStatus())
                        || "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .filter(order -> order.getId() != null)
                .toList();
        Map<Long, OrderReviewSnapshot> current = currentOrders.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        CustomerOrder::getId, OrderService::reviewSnapshot));
        if (!current.equals(reviewed) || (!current.isEmpty() && !separateOrderApproved)) {
            throw new DuplicateOrderReviewChangedException(
                    customerId, orderDate, reviewed.keySet(), current.keySet());
        }
        return create(customerId, orderDate, status, notes, handoffType, lines);
    }

    public static final class DuplicateOrderReviewChangedException
            extends IllegalStateException {
        private final Set<Long> currentActiveOrderIds;

        public DuplicateOrderReviewChangedException(
                Long customerId, LocalDate orderDate, Set<Long> reviewed, Set<Long> current) {
            super("Active order details or selection changed for customer #" + customerId
                    + " on " + orderDate
                    + " after duplicate review. No order was created. Refresh Customers and "
                    + "Orders, review " + current.size() + " active order(s), then choose the "
                    + "intended existing order or explicitly create a separate order.");
            this.currentActiveOrderIds = Set.copyOf(current);
        }

        public Set<Long> currentActiveOrderIds() {
            return currentActiveOrderIds;
        }
    }

    @Transactional
    public CustomerOrder updateStatus(Long orderId, String status) {
        return updateStatus(orderId, status, null, null, null, null);
    }

    private CustomerOrder updateStatus(Long orderId, String status,
                                       OrderReviewSnapshot reviewedSnapshot,
                                       String reviewName, String unchangedConsequence,
                                       String retryAction) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required.");
        }
        String next = CustomerOrder.normalizeStatus(status);
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (reviewedSnapshot != null) {
            verifyReviewedOrderCurrent(orderId, order, reviewedSnapshot,
                    reviewName, unchangedConsequence, retryAction);
        } else if (("CONFIRMED".equals(next) || "FULFILLED".equals(next))
                && "DELIVERY".equals(CustomerOrder.normalizeHandoffType(
                        order.getHandoffType()))) {
            refreshCustomerForLifecycle(order);
        }
        String previous = order.getStatus();
        if ("CONFIRMED".equals(next) && order.getLines().isEmpty()) {
            throw new IllegalArgumentException(
                    "A confirmed order must contain at least one product.");
        }
        if ("CONFIRMED".equals(next)) {
            requireSpecifiedHandoff(order, "confirming");
            requireDeliveryContext(order, "confirming");
        }
        if ("FULFILLED".equals(next) && !"FULFILLED".equalsIgnoreCase(previous)) {
            return fulfillLocked(order);
        }
        if (next.equalsIgnoreCase(previous)) {
            return order;
        }
        // Prevent reverse inventory thrash: cannot un-fulfill via status alone
        if ("FULFILLED".equalsIgnoreCase(previous) && !"FULFILLED".equals(next)) {
            throw new IllegalArgumentException(
                    "Cannot change status of a FULFILLED order (inventory already deducted).");
        }
        if ("CANCELLED".equalsIgnoreCase(previous)) {
            throw new IllegalArgumentException("Cannot change status of a CANCELLED order.");
        }
        order.setStatus(next);
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm",
                "ORDER_STATUS",
                true,
                "Order #" + orderId + " status " + previous + " → " + saved.getStatus(),
                "notes=" + saved.getNotes(),
                1
        );
        log.info("[crm] Order #{} status {} → {}", orderId, previous, saved.getStatus());
        return saved;
    }

    /** DRAFT → CONFIRMED (pipeline). No inventory change. */
    @Transactional
    public CustomerOrder confirm(Long orderId) {
        return updateStatus(orderId, "CONFIRMED");
    }

    /** Confirms only when the locked live order exactly matches what the worker reviewed. */
    @Transactional
    public CustomerOrder confirmReviewed(Long orderId, OrderReviewSnapshot reviewedSnapshot) {
        requireMatchingOrderReview(orderId, reviewedSnapshot, "confirmation");
        return updateStatus(orderId, "CONFIRMED", reviewedSnapshot,
                "confirmation", "No status was changed", "confirm it again");
    }

    private void verifyReviewedOrderCurrent(
            Long orderId, CustomerOrder order, OrderReviewSnapshot reviewedSnapshot,
            String reviewName, String unchangedConsequence, String retryAction) {
        Customer liveCustomer = order.getCustomer() == null
                || order.getCustomer().getId() == null
                ? null : customerRepository.findByIdForUpdate(order.getCustomer().getId())
                .orElse(null);
        if (liveCustomer != null) {
            order.setCustomer(liveCustomer);
        }
        if (liveCustomer == null || !reviewedSnapshot.equals(reviewSnapshot(order))) {
            throw new StaleOrderReviewException(
                    orderId, reviewName, unchangedConsequence, retryAction);
        }
    }

    /** Mark CANCELLED if not already fulfilled. */
    @Transactional
    public CustomerOrder cancel(Long orderId) {
        return updateStatus(orderId, "CANCELLED");
    }

    /** Cancels only when the locked order exactly matches the worker's review. */
    @Transactional
    public CustomerOrder cancelReviewed(Long orderId, OrderReviewSnapshot reviewedSnapshot) {
        requireMatchingOrderReview(orderId, reviewedSnapshot, "cancellation");
        return updateStatus(orderId, "CANCELLED", reviewedSnapshot,
                "cancellation", "The order was not cancelled and inventory was unchanged",
                "review the cancellation again");
    }

    @Transactional
    public CustomerOrder updateNotes(Long orderId, String notes) {
        return updateNotes(orderId, notes, null, false);
    }

    /** Saves instructions only if the locked order still has the value the worker edited. */
    @Transactional
    public CustomerOrder updateNotesReviewed(
            Long orderId, String reviewedNotes, String notes) {
        return updateNotes(orderId, notes,
                reviewedNotes == null ? "" : reviewedNotes.trim(), true);
    }

    private CustomerOrder updateNotes(
            Long orderId, String notes, String reviewedNotes, boolean requireReviewMatch) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (requireReviewMatch && !Objects.equals(order.getNotes(), reviewedNotes)) {
            throw new IllegalArgumentException(
                    "Order #" + orderId + " instructions changed after you started editing. "
                            + "The saved instructions now read: “"
                            + conciseReviewValue(order.getNotes()) + "”. "
                            + "No order or inventory value was changed. Refresh Customers and "
                            + "Orders, review the current instructions, then save again.");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot change the instructions of a " + order.getStatus() + " order.");
        }
        String previous = order.getNotes();
        order.setNotes(notes);
        if (order.getNotes().equals(previous)) {
            return order;
        }
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm", "ORDER_NOTES_UPDATE", true,
                "Updated instructions for order #" + orderId,
                "previousCharacters=" + previous.length()
                        + ", currentCharacters=" + saved.getNotes().length()
                        + ", inventoryChanged=false",
                1);
        return saved;
    }

    /** Reschedules a draft or confirmed order; inventory is unchanged. */
    @Transactional
    public CustomerOrder updateOrderDate(Long orderId, LocalDate orderDate) {
        return updateOrderDate(orderId, orderDate, null, false);
    }

    /** Reschedules only if the locked order still has the date the worker edited. */
    @Transactional
    public CustomerOrder updateOrderDateReviewed(
            Long orderId, LocalDate reviewedOrderDate, LocalDate orderDate) {
        if (reviewedOrderDate == null) {
            throw new IllegalArgumentException("The reviewed order date is required.");
        }
        return updateOrderDate(orderId, orderDate, reviewedOrderDate, true);
    }

    private CustomerOrder updateOrderDate(
            Long orderId, LocalDate orderDate, LocalDate reviewedOrderDate,
            boolean requireReviewMatch) {
        if (orderDate == null) {
            throw new IllegalArgumentException("Order date is required.");
        }
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (requireReviewMatch && !Objects.equals(order.getOrderDate(), reviewedOrderDate)) {
            throw new IllegalArgumentException(
                    "Order #" + orderId + " pickup or delivery date changed after you started "
                            + "editing. The saved date is now " + order.getOrderDate() + ". "
                            + "No order or inventory value was changed. Refresh Customers "
                            + "and Orders, review the current date, then save again.");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot change the date of a " + order.getStatus() + " order.");
        }
        LocalDate previous = order.getOrderDate();
        if (orderDate.equals(previous)) {
            return order;
        }
        order.setOrderDate(orderDate);
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm", "ORDER_DATE_UPDATE", true,
                "Rescheduled order #" + orderId + " to " + orderDate,
                "previous=" + previous + ", current=" + orderDate
                        + ", inventoryChanged=false",
                1);
        return saved;
    }

    /** Changes the operational handoff for a draft or confirmed order. */
    @Transactional
    public CustomerOrder updateHandoffType(Long orderId, String handoffType) {
        return updateHandoffType(orderId, handoffType, null, false);
    }

    /** Changes the handoff only if the locked order still has the value the worker edited. */
    @Transactional
    public CustomerOrder updateHandoffTypeReviewed(
            Long orderId, String reviewedHandoffType, String handoffType) {
        if (reviewedHandoffType == null || reviewedHandoffType.isBlank()) {
            throw new IllegalArgumentException("The reviewed handoff type is required.");
        }
        return updateHandoffType(orderId, handoffType, reviewedHandoffType, true);
    }

    private CustomerOrder updateHandoffType(
            Long orderId, String handoffType, String reviewedHandoffType,
            boolean requireReviewMatch) {
        String requested = CustomerOrder.normalizeHandoffType(handoffType);
        if ("UNSPECIFIED".equals(requested)) {
            throw new IllegalArgumentException(
                    "Choose PICKUP, DELIVERY, or MARKET. An open order cannot be saved with an "
                            + "unspecified handoff.");
        }
        String reviewed = requireReviewMatch
                ? CustomerOrder.normalizeHandoffType(reviewedHandoffType) : null;
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (requireReviewMatch && !order.getHandoffType().equals(reviewed)) {
            throw new IllegalArgumentException(
                    "Order #" + orderId + " handoff type changed after you started editing. "
                            + "The saved handoff is now " + order.getHandoffType() + ". "
                            + "No order or inventory value was changed. Refresh Customers and "
                            + "Orders, review the current handoff, then save again.");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot change the handoff type of a " + order.getStatus() + " order.");
        }
        String previous = order.getHandoffType();
        if (requested.equals(previous)) {
            return order;
        }
        order.setHandoffType(requested);
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm", "ORDER_HANDOFF_UPDATE", true,
                "Updated handoff for order #" + orderId + " to " + requested,
                "previous=" + previous + ", current=" + requested
                        + ", inventoryChanged=false",
                1);
        return saved;
    }

    private static String conciseReviewValue(String value) {
        String clean = value == null || value.isBlank()
                ? "(blank)" : value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 160 ? clean : clean.substring(0, 159) + "…";
    }

    /**
     * Filter helper for CRM UI / API. Blank status or customer → ignore that constraint.
     * Null from/to → unbounded on that side.
     */
    @Transactional(readOnly = true)
    public List<CustomerOrder> filter(String status, String customerQuery,
                                      LocalDate from, LocalDate to) {
        return filter(status, customerQuery, from, to, null);
    }

    /** Filter by lifecycle, customer, date window, and an optional structured handoff. */
    @Transactional(readOnly = true)
    public List<CustomerOrder> filter(String status, String customerQuery,
                                      LocalDate from, LocalDate to, String handoffType) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(st) || "*".equals(st)) {
            st = "";
        }
        String cq = customerQuery == null ? "" : customerQuery.trim().toLowerCase(Locale.ROOT);
        String handoff = handoffType == null || handoffType.isBlank()
                || "ALL".equalsIgnoreCase(handoffType) || "*".equals(handoffType.trim())
                ? "" : CustomerOrder.normalizeHandoffType(handoffType);
        Stream<CustomerOrder> stream = getAll().stream();
        if ("OPEN".equals(st)) {
            stream = stream.filter(order -> "DRAFT".equalsIgnoreCase(order.getStatus())
                    || "CONFIRMED".equalsIgnoreCase(order.getStatus()));
        } else if (!st.isEmpty()) {
            String statusKey = st;
            stream = stream.filter(o -> o.getStatus() != null
                    && o.getStatus().equalsIgnoreCase(statusKey));
        }
        if (!cq.isEmpty()) {
            stream = stream.filter(o -> {
                if (o.getCustomer() == null || o.getCustomer().getName() == null) {
                    return false;
                }
                return o.getCustomer().getName().toLowerCase(Locale.ROOT).contains(cq);
            });
        }
        if (!handoff.isEmpty()) {
            stream = stream.filter(order -> handoff.equals(
                    CustomerOrder.normalizeHandoffType(order.getHandoffType())));
        }
        if (from != null) {
            stream = stream.filter(o -> o.getOrderDate() != null && !o.getOrderDate().isBefore(from));
        }
        if (to != null) {
            stream = stream.filter(o -> o.getOrderDate() != null && !o.getOrderDate().isAfter(to));
        }
        return stream.toList();
    }

    /** Export a filtered subset (e.g. current CRM filter). */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename, List<CustomerOrder> orders) {
        reportGenerator.exportCsv(filename, orders);
    }

    /**
     * Marks the order FULFILLED, decrements matching inventory SKUs by product name,
     * and records a CRM audit event in sync history.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CustomerOrder fulfill(Long orderId) {
        return fulfill(orderId, null);
    }

    /** Completes only when the locked live order exactly matches the worker's review. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CustomerOrder fulfillReviewed(Long orderId, OrderReviewSnapshot reviewedSnapshot) {
        if (orderId == null || reviewedSnapshot == null
                || reviewedSnapshot.orderId() != orderId.longValue()) {
            throw new IllegalArgumentException(
                    "The completion review does not match the selected order. Refresh Customers "
                            + "and Orders and review it again.");
        }
        return fulfill(orderId, reviewedSnapshot);
    }

    /**
     * Completes a mobile handoff in the caller's transaction so fulfillment and the
     * worker retry receipt commit or roll back together.
     */
    @Transactional
    public WorkerFulfillmentOutcome fulfillWorkerReviewed(
            Long orderId, String reviewedToken) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order id must be positive.");
        }
        String token = reviewedToken == null ? "" : reviewedToken.trim();
        if (!token.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Order handoff review token is invalid.");
        }
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new StaleOrderReviewException(
                        orderId, "mobile handoff", "No status or inventory was changed",
                        "remove the unavailable handoff", true));
        Customer customer = order.getCustomer();
        Customer liveCustomer = customer == null || customer.getId() == null
                ? null : customerRepository.findByIdForUpdate(customer.getId()).orElse(null);
        if (liveCustomer == null) {
            throw new StaleOrderReviewException(
                    orderId, "mobile handoff", "No status or inventory was changed",
                    "review the current customer load");
        }
        order.setCustomer(liveCustomer);
        String currentToken = handoffReviewToken(order);
        if (!MessageDigest.isEqual(
                token.getBytes(StandardCharsets.US_ASCII),
                currentToken.getBytes(StandardCharsets.US_ASCII))) {
            throw new StaleOrderReviewException(
                    orderId, "mobile handoff", "No status or inventory was changed",
                    "review the refreshed handoff and complete it again");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
            return new WorkerFulfillmentOutcome(order, true);
        }
        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new StaleOrderReviewException(
                    orderId, "mobile handoff", "No status or inventory was changed",
                    "review its current lifecycle state");
        }
        return new WorkerFulfillmentOutcome(fulfillLocked(order), false);
    }

    private CustomerOrder fulfill(Long orderId, OrderReviewSnapshot reviewedSnapshot) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (reviewedSnapshot != null) {
            verifyReviewedOrderCurrent(orderId, order, reviewedSnapshot,
                    "completion", "No status or inventory was changed", "complete it again");
        } else if ("DELIVERY".equals(CustomerOrder.normalizeHandoffType(
                order.getHandoffType()))) {
            refreshCustomerForLifecycle(order);
        }
        return fulfillLocked(order);
    }

    private CustomerOrder fulfillLocked(CustomerOrder order) {
        Long orderId = order.getId();

        if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
            log.info("Order #{} already fulfilled — no inventory change.", orderId);
            return order;
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Cannot fulfill a CANCELLED order.");
        }
        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Only CONFIRMED orders can be fulfilled.");
        }
        requireSpecifiedHandoff(order, "completing");
        requireDeliveryContext(order, "completing");
        if (order.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cannot fulfill an order with no lines.");
        }

        int matched = 0;
        StringBuilder detail = new StringBuilder();
        for (OrderLine line : order.getLines()) {
            int qty = (int) Math.round(line.getQuantity());
            if (qty <= 0) {
                throw new IllegalArgumentException(
                        "Order line quantity must round to at least one inventory unit.");
            }
            inventoryService.decrementQuantityByNameStrict(
                    line.getProductName(), qty, line.getUnit());
            matched++;
            detail.append(line.getProductName()).append(" -").append(qty).append("; ");
        }

        order.setStatus("FULFILLED");
        CustomerOrder saved = orderRepository.save(order);

        String msg = "Order #" + orderId + " fulfilled — inventory matched " + matched
                + " line(s)";
        syncHistoryService.record(
                "crm",
                "ORDER_FULFILL",
                true,
                msg,
                detail.toString(),
                matched
        );
        log.info("[crm] {}", msg);
        return saved;
    }

    private static void requireSpecifiedHandoff(CustomerOrder order, String action) {
        if ("UNSPECIFIED".equals(CustomerOrder.normalizeHandoffType(order.getHandoffType()))) {
            throw new IllegalArgumentException(
                    "Choose PICKUP, DELIVERY, or MARKET for order #" + order.getId()
                            + " before " + action
                            + ". No status or inventory was changed.");
        }
    }

    private void refreshCustomerForLifecycle(CustomerOrder order) {
        Customer customer = order.getCustomer();
        if (customer == null || customer.getId() == null) {
            return;
        }
        Customer liveCustomer = customerRepository.findByIdForUpdate(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "The customer account for Delivery order #" + order.getId()
                                + " is unavailable. No status or inventory was changed."));
        order.setCustomer(liveCustomer);
    }

    private static void requireDeliveryContext(CustomerOrder order, String action) {
        if (!"DELIVERY".equals(CustomerOrder.normalizeHandoffType(order.getHandoffType()))) {
            return;
        }
        Customer customer = order.getCustomer();
        boolean hasContext = customer != null
                && ((!customer.getContactName().isBlank())
                || !customer.getPhone().isBlank()
                || !customer.getEmail().isBlank()
                || !customer.getNotes().isBlank());
        hasContext = hasContext || (order.getNotes() != null && !order.getNotes().isBlank());
        if (!hasContext) {
            String target = order.getId() == null
                    ? "New Delivery order" : "Delivery order #" + order.getId();
            throw new IllegalArgumentException(
                    target + " needs customer contact, account delivery notes, or explicit order "
                            + "delivery instructions before " + action
                            + ". No status or inventory was changed.");
        }
    }

    @Transactional
    public CustomerOrder addLine(Long orderId, OrderLine line) {
        return addLine(orderId, null, line);
    }

    /** Adds a product only if the locked order still matches the worker's displayed order. */
    @Transactional
    public CustomerOrder addLineReviewed(
            Long orderId, OrderReviewSnapshot reviewedSnapshot, OrderLine line) {
        requireMatchingOrderReview(orderId, reviewedSnapshot, "product entry");
        return addLine(orderId, reviewedSnapshot, line);
    }

    private CustomerOrder addLine(
            Long orderId, OrderReviewSnapshot reviewedSnapshot, OrderLine line) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> reviewedSnapshot == null
                        ? new IndexOutOfBoundsException("No order with id=" + orderId)
                        : new StaleOrderReviewException(orderId, "product entry",
                                "The product was not added and inventory was unchanged",
                                "review the order and add the product again", true));
        if (reviewedSnapshot != null) {
            verifyReviewedOrderCurrent(orderId, order, reviewedSnapshot,
                    "product entry", "The product was not added and inventory was unchanged",
                    "review the order and add the product again");
        }
        if (line == null) {
            throw new IllegalArgumentException("Line must not be null.");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot add lines to a " + order.getStatus() + " order.");
        }
        if (order.getLines().size() >= MAX_ORDER_LINES) {
            throw new IllegalArgumentException(
                    "Order cannot contain more than " + MAX_ORDER_LINES + " lines.");
        }
        order.addLine(new OrderLine(
                line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
        return orderRepository.save(order);
    }

    /** Updates one captured product line before fulfillment; inventory is unchanged. */
    @Transactional
    public CustomerOrder updateLine(Long orderId, Long lineId, OrderLine replacement) {
        return updateLine(orderId, lineId, null, null, replacement);
    }

    /** Updates only if the locked product still matches the worker's captured line. */
    @Transactional
    public CustomerOrder updateLineReviewed(
            Long orderId, Long lineId, OrderReviewLine reviewedLine,
            OrderLine replacement) {
        requireMatchingReviewedLineTarget(lineId, reviewedLine);
        return updateLine(orderId, lineId, null, reviewedLine, replacement);
    }

    /** Updates only if the saved product and its order still match the worker's review. */
    @Transactional
    public CustomerOrder updateLineReviewed(
            Long orderId, Long lineId, OrderReviewSnapshot reviewedOrder,
            OrderReviewLine reviewedLine, OrderLine replacement) {
        requireMatchingOrderReview(orderId, reviewedOrder, "product correction");
        requireMatchingReviewedLineTarget(lineId, reviewedLine);
        return updateLine(orderId, lineId, reviewedOrder, reviewedLine, replacement);
    }

    private CustomerOrder updateLine(
            Long orderId, Long lineId, OrderReviewSnapshot reviewedOrder,
            OrderReviewLine reviewedLine,
            OrderLine replacement) {
        if (lineId == null) {
            throw new IllegalArgumentException("Select a saved order product to update.");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("Replacement product details are required.");
        }
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> reviewedOrder == null
                        ? new IndexOutOfBoundsException("No order with id=" + orderId)
                        : new StaleOrderReviewException(orderId, "product correction",
                                "The product correction was not saved and inventory was unchanged",
                                "review the order and correct the product again", true));
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot update products on a " + order.getStatus() + " order.");
        }
        OrderLine line = order.getLines().stream()
                .filter(candidate -> lineId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No product line with id=" + lineId + " on order #" + orderId));
        if (reviewedLine != null && !reviewedLine.equals(reviewLine(line))) {
            throw new StaleOrderLineException(orderId, "editing", reviewLine(line));
        }
        if (reviewedOrder != null) {
            verifyReviewedOrderCurrent(orderId, order, reviewedOrder,
                    "product correction",
                    "The product correction was not saved and inventory was unchanged",
                    "review the order and correct the product again");
        }
        String previous = line.getProductName() + " × " + line.getQuantity() + " " + line.getUnit();
        if (!line.getProductName().equalsIgnoreCase(replacement.getProductName().trim())) {
            // A different product is no longer made from the linked recipe.
            line.setRecipeId(null);
        }
        line.setProductName(replacement.getProductName());
        line.setQuantity(replacement.getQuantity());
        line.setUnit(replacement.getUnit());
        line.setUnitPrice(replacement.getUnitPrice());
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm", "ORDER_LINE_UPDATE", true,
                "Updated " + line.getProductName() + " on order #" + orderId,
                "lineId=" + lineId + ", previous=" + previous
                        + ", current=" + line.getProductName() + " × " + line.getQuantity()
                        + " " + line.getUnit() + ", unitPrice=" + line.getUnitPrice(),
                1);
        return saved;
    }

    /** Removes one captured product line before fulfillment; inventory is unchanged. */
    @Transactional
    public CustomerOrder removeLine(Long orderId, Long lineId) {
        return removeLine(orderId, lineId, null, null);
    }

    /** Removes only if the locked product still matches the worker's captured line. */
    @Transactional
    public CustomerOrder removeLineReviewed(
            Long orderId, Long lineId, OrderReviewLine reviewedLine) {
        requireMatchingReviewedLineTarget(lineId, reviewedLine);
        return removeLine(orderId, lineId, null, reviewedLine);
    }

    /** Removes only if the saved product and its order still match the worker's review. */
    @Transactional
    public CustomerOrder removeLineReviewed(
            Long orderId, Long lineId, OrderReviewSnapshot reviewedOrder,
            OrderReviewLine reviewedLine) {
        requireMatchingOrderReview(orderId, reviewedOrder, "product removal");
        requireMatchingReviewedLineTarget(lineId, reviewedLine);
        return removeLine(orderId, lineId, reviewedOrder, reviewedLine);
    }

    private CustomerOrder removeLine(
            Long orderId, Long lineId, OrderReviewSnapshot reviewedOrder,
            OrderReviewLine reviewedLine) {
        if (lineId == null) {
            throw new IllegalArgumentException("Select a saved order product to remove.");
        }
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> reviewedOrder == null
                        ? new IndexOutOfBoundsException("No order with id=" + orderId)
                        : new StaleOrderReviewException(orderId, "product removal",
                                "Nothing was removed and inventory was unchanged",
                                "review the order and remove the product again", true));
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())
                || "CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot remove products from a " + order.getStatus() + " order.");
        }
        OrderLine line = order.getLines().stream()
                .filter(candidate -> lineId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No product line with id=" + lineId + " on order #" + orderId));
        if (reviewedLine != null && !reviewedLine.equals(reviewLine(line))) {
            throw new StaleOrderLineException(orderId, "removing it", reviewLine(line));
        }
        if (reviewedOrder != null) {
            verifyReviewedOrderCurrent(orderId, order, reviewedOrder,
                    "product removal", "Nothing was removed and inventory was unchanged",
                    "review the order and remove the product again");
        }
        if ("CONFIRMED".equalsIgnoreCase(order.getStatus()) && order.getLines().size() == 1) {
            throw new IllegalArgumentException(
                    "A confirmed order must keep at least one product; add the replacement first.");
        }
        order.getLines().remove(line);
        CustomerOrder saved = orderRepository.save(order);
        syncHistoryService.record(
                "crm", "ORDER_LINE_REMOVE", true,
                "Removed " + line.getProductName() + " from order #" + orderId,
                "lineId=" + lineId + ", quantity=" + line.getQuantity()
                        + ", unit=" + line.getUnit(),
                1);
        return saved;
    }

    private static void requireMatchingReviewedLineTarget(
            Long lineId, OrderReviewLine reviewedLine) {
        if (lineId == null || reviewedLine == null
                || !Objects.equals(lineId, reviewedLine.lineId())) {
            throw new IllegalArgumentException(
                    "The product review does not match the selected saved line. Refresh Customers "
                            + "and Orders, select the product again, then retry.");
        }
    }

    @Transactional
    public void delete(Long id) {
        delete(id, null);
    }

    /** Permanently deletes only when the locked order exactly matches the worker's review. */
    @Transactional
    public void deleteReviewed(Long id, OrderReviewSnapshot reviewedSnapshot) {
        requireMatchingOrderReview(id, reviewedSnapshot, "deletion");
        delete(id, reviewedSnapshot);
    }

    private void delete(Long id, OrderReviewSnapshot reviewedSnapshot) {
        CustomerOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + id));
        if (reviewedSnapshot != null) {
            verifyReviewedOrderCurrent(id, order, reviewedSnapshot,
                    "deletion", "The order was not deleted and inventory was unchanged",
                    "review the deletion again");
        }
        if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot delete a FULFILLED order because inventory was already deducted.");
        }
        orderRepository.deleteById(id);
    }

    private static void requireMatchingOrderReview(
            Long orderId, OrderReviewSnapshot reviewedSnapshot, String action) {
        if (orderId == null || reviewedSnapshot == null
                || reviewedSnapshot.orderId() != orderId.longValue()) {
            throw new IllegalArgumentException(
                    "The " + action + " review does not match the selected order. Refresh "
                            + "Customers and Orders and review it again.");
        }
    }

    /**
     * Booked revenue (CONFIRMED + FULFILLED) between dates.
     * Prefer {@link #weekRevenueSummary()} on the dashboard for realized vs pipeline split.
     */
    @Transactional(readOnly = true)
    public double fulfilledRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, true, true, false);
    }

    /** Realized cash: FULFILLED orders only. */
    @Transactional(readOnly = true)
    public double realizedRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, true, false, false);
    }

    /** Pipeline: CONFIRMED orders not yet fulfilled. */
    @Transactional(readOnly = true)
    public double pipelineRevenueBetween(LocalDate from, LocalDate to) {
        return revenueBetween(from, to, false, true, false);
    }

    private double revenueBetween(LocalDate from, LocalDate to,
                                  boolean includeFulfilled, boolean includeConfirmed, boolean includeDraft) {
        return findBetween(from, to).stream()
                .filter(o -> matchesStatus(o.getStatus(), includeFulfilled, includeConfirmed, includeDraft))
                .mapToDouble(CustomerOrder::lineTotal)
                .sum();
    }

    private static boolean matchesStatus(String status, boolean fulfilled, boolean confirmed, boolean draft) {
        if (status == null) {
            return false;
        }
        if (fulfilled && "FULFILLED".equalsIgnoreCase(status)) {
            return true;
        }
        if (confirmed && "CONFIRMED".equalsIgnoreCase(status)) {
            return true;
        }
        return draft && "DRAFT".equalsIgnoreCase(status);
    }

    /**
     * Trailing 7-day revenue breakdown for dashboard KPIs.
     * {@code realized} = FULFILLED only; {@code pipeline} = CONFIRMED; {@code booked} = sum of both.
     */
    @Transactional(readOnly = true)
    public WeekRevenueSummary weekRevenueSummary() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        double realized = 0;
        double pipeline = 0;
        double draft = 0;
        int fulfilledCount = 0;
        int confirmedCount = 0;
        int draftCount = 0;
        for (CustomerOrder o : findBetween(from, to)) {
            double total = o.lineTotal();
            String s = o.getStatus();
            if ("FULFILLED".equalsIgnoreCase(s)) {
                realized += total;
                fulfilledCount++;
            } else if ("CONFIRMED".equalsIgnoreCase(s)) {
                pipeline += total;
                confirmedCount++;
            } else if ("DRAFT".equalsIgnoreCase(s)) {
                draft += total;
                draftCount++;
            }
        }
        return new WeekRevenueSummary(from, to, realized, pipeline, draft,
                fulfilledCount, confirmedCount, draftCount);
    }

    /**
     * Daily realized (FULFILLED) revenue for the last 7 days (index 0 = 6 days ago).
     * Use for accurate cash-trend sparklines.
     */
    @Transactional(readOnly = true)
    public double[] dailyRevenueLast7Days() {
        return dailyRevenueLast7Days(true, false);
    }

    /**
     * Daily revenue series. {@code includeFulfilled}/{@code includeConfirmed} control status filter.
     */
    @Transactional(readOnly = true)
    public double[] dailyRevenueLast7Days(boolean includeFulfilled, boolean includeConfirmed) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        java.util.Map<LocalDate, Double> byDay = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            byDay.put(from.plusDays(i), 0.0);
        }
        for (CustomerOrder o : findBetween(from, today)) {
            if (!matchesStatus(o.getStatus(), includeFulfilled, includeConfirmed, false)) {
                continue;
            }
            byDay.merge(o.getOrderDate(), o.lineTotal(), Double::sum);
        }
        double[] out = new double[7];
        int i = 0;
        for (double v : byDay.values()) {
            out[i++] = v;
        }
        return out;
    }

    /** Compact week revenue snapshot for API / dashboard. */
    public record WeekRevenueSummary(
            LocalDate from,
            LocalDate to,
            double realized,
            double pipeline,
            double draft,
            int fulfilledOrderCount,
            int confirmedOrderCount,
            int draftOrderCount
    ) {
        public double booked() {
            return realized + pipeline;
        }
    }

    /** Realized (FULFILLED) revenue for the prior 7-day window (days -13 … -7). */
    @Transactional(readOnly = true)
    public double realizedRevenuePrior7Days() {
        LocalDate to = LocalDate.now().minusDays(7);
        LocalDate from = to.minusDays(6);
        return realizedRevenueBetween(from, to);
    }

    /** Percent change in realized revenue vs prior week; null if prior was zero. */
    @Transactional(readOnly = true)
    public Double realizedWeekOverWeekPercentChange() {
        double current = weekRevenueSummary().realized();
        double prior = realizedRevenuePrior7Days();
        if (prior <= 0) {
            return null;
        }
        return ((current - prior) / prior) * 100.0;
    }

    /** Export all orders + line totals for accounting. */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename) {
        exportToCsv(filename, getAll());
    }

    /**
     * Wholesale / florist invoice (or packing slip) PDF for a single order.
     * Suitable for CONFIRMED, FULFILLED, or DRAFT — status is printed on the sheet.
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required.");
        }
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        return generateInvoicePdf(order);
    }

    public byte[] generateInvoicePdf(CustomerOrder order) {
        return reportGenerator.generateInvoicePdf(order);
    }

    /** Plain-text invoice for CLI / quick view. */
    @Transactional(readOnly = true)
    public String formatInvoiceText(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required.");
        }
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        return formatInvoiceText(order);
    }

    public String formatInvoiceText(CustomerOrder order) {
        return reportGenerator.formatInvoiceText(order);
    }
}

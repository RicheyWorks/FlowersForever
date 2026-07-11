package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.repository.CustomerJpaRepository;
import com.flowerfarm.repository.CustomerOrderJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerOrderJpaRepository orderRepository;
    private final CustomerJpaRepository customerRepository;
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;

    public OrderService(CustomerOrderJpaRepository orderRepository,
                        CustomerJpaRepository customerRepository,
                        InventoryService inventoryService,
                        SyncHistoryService syncHistoryService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> getAll() {
        return new ArrayList<>(orderRepository.findAllByOrderByOrderDateDescIdDesc());
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
        return new ArrayList<>(orderRepository.findByOrderDateBetweenOrderByOrderDateDescIdDesc(from, to));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> findByCustomer(Long customerId) {
        return new ArrayList<>(orderRepository.findByCustomerIdOrderByOrderDateDescIdDesc(customerId));
    }

    /**
     * Create an order for an existing customer with zero or more lines.
     */
    @Transactional
    public CustomerOrder create(Long customerId, LocalDate orderDate, String status, String notes,
                                List<OrderLine> lines) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + customerId));
        CustomerOrder order = new CustomerOrder(customer, orderDate, status, notes);
        if (lines != null) {
            for (OrderLine line : lines) {
                order.addLine(new OrderLine(
                        line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
            }
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

    @Transactional
    public CustomerOrder updateStatus(Long orderId, String status) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        String next = status == null ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
        String previous = order.getStatus();
        if ("FULFILLED".equals(next) && !"FULFILLED".equalsIgnoreCase(previous)) {
            return fulfill(orderId);
        }
        if (next.equalsIgnoreCase(previous)) {
            return order;
        }
        // Prevent reverse inventory thrash: cannot un-fulfill via status alone
        if ("FULFILLED".equalsIgnoreCase(previous) && !"FULFILLED".equals(next)) {
            throw new IllegalArgumentException(
                    "Cannot change status of a FULFILLED order (inventory already deducted).");
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

    /** Mark CANCELLED if not already fulfilled. */
    @Transactional
    public CustomerOrder cancel(Long orderId) {
        return updateStatus(orderId, "CANCELLED");
    }

    @Transactional
    public CustomerOrder updateNotes(Long orderId, String notes) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        order.setNotes(notes);
        return orderRepository.save(order);
    }

    /**
     * Filter helper for CRM UI / API. Blank status or customer → ignore that constraint.
     * Null from/to → unbounded on that side.
     */
    @Transactional(readOnly = true)
    public List<CustomerOrder> filter(String status, String customerQuery,
                                      LocalDate from, LocalDate to) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(st) || "*".equals(st)) {
            st = "";
        }
        String cq = customerQuery == null ? "" : customerQuery.trim().toLowerCase(Locale.ROOT);
        Stream<CustomerOrder> stream = getAll().stream();
        if (!st.isEmpty()) {
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
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        if (orders == null) {
            orders = List.of();
        }
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(filename.trim()))) {
            bw.write("OrderId,Date,Customer,Status,LineCount,Total,Notes");
            bw.newLine();
            for (CustomerOrder o : orders) {
                String cust = o.getCustomer() == null ? "" : o.getCustomer().getName();
                String notes = o.getNotes() == null ? "" : o.getNotes().replace("\"", "\"\"");
                bw.write(String.format("%s,%s,%s,%s,%d,%.2f,\"%s\"",
                        o.getId(),
                        o.getOrderDate(),
                        csvEscape(cust),
                        o.getStatus(),
                        o.getLines() == null ? 0 : o.getLines().size(),
                        o.lineTotal(),
                        notes));
                bw.newLine();
            }
            log.info("Orders exported to '{}' ({} row(s)).", filename, orders.size());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Order export failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Marks the order FULFILLED, decrements matching inventory SKUs by product name,
     * and records a CRM audit event in sync history.
     */
    @Transactional
    public CustomerOrder fulfill(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));

        if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
            log.info("Order #{} already fulfilled — no inventory change.", orderId);
            return order;
        }

        int matched = 0;
        int missing = 0;
        StringBuilder detail = new StringBuilder();
        for (OrderLine line : order.getLines()) {
            int qty = (int) Math.round(line.getQuantity());
            var updated = inventoryService.decrementQuantityByName(line.getProductName(), qty);
            if (updated.isPresent()) {
                matched++;
                detail.append(line.getProductName()).append(" -").append(qty).append("; ");
            } else {
                missing++;
                detail.append(line.getProductName()).append(" (no inventory match); ");
                log.warn("Fulfill order #{}: no inventory SKU named '{}'", orderId, line.getProductName());
            }
        }

        order.setStatus("FULFILLED");
        CustomerOrder saved = orderRepository.save(order);

        String msg = "Order #" + orderId + " fulfilled — inventory matched " + matched
                + " line(s)" + (missing > 0 ? ", " + missing + " unmatched" : "");
        syncHistoryService.record(
                "crm",
                "ORDER_FULFILL",
                missing == 0,
                msg,
                detail.toString(),
                matched
        );
        log.info("[crm] {}", msg);
        return saved;
    }

    @Transactional
    public CustomerOrder addLine(Long orderId, OrderLine line) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + orderId));
        if (line == null) {
            throw new IllegalArgumentException("Line must not be null.");
        }
        order.addLine(new OrderLine(
                line.getProductName(), line.getQuantity(), line.getUnit(), line.getUnitPrice()));
        return orderRepository.save(order);
    }

    @Transactional
    public void delete(Long id) {
        if (orderRepository.findById(id).isEmpty()) {
            throw new IndexOutOfBoundsException("No order with id=" + id);
        }
        orderRepository.deleteById(id);
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

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}

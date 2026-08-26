package com.flowerfarm.controller;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<CustomerOrder> list() {
        return orderService.getAll();
    }

    @GetMapping("/range")
    public ResponseEntity<?> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(orderService.findBetween(from, to));
    }

    @GetMapping("/by-customer/{customerId}")
    public List<CustomerOrder> byCustomer(@PathVariable Long customerId) {
        return orderService.findByCustomer(customerId);
    }

    /**
     * Flexible order filter for CRM tools.
     * {@code status=ALL} or omit for any status.
     */
    @GetMapping("/filter")
    public ResponseEntity<?> filter(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customer", required = false) String customer,
            @RequestParam(value = "handoff", required = false) String handoff,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
        return ResponseEntity.ok(orderService.filter(status, customer, from, to, handoff));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<?> notes(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String notes = body == null ? "" : body.getOrDefault("notes", "");
        return ResponseEntity.ok(orderService.updateNotes(id, notes));
    }

    /**
     * Trailing 7-day revenue: realized (FULFILLED), pipeline (CONFIRMED), draft,
     * plus daily realized sparkline series (oldest → today).
     */
    @GetMapping("/week")
    public Map<String, Object> weekSummary() {
        OrderService.WeekRevenueSummary s = orderService.weekRevenueSummary();
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("from", s.from().toString());
        body.put("to", s.to().toString());
        body.put("realized", s.realized());
        body.put("pipeline", s.pipeline());
        body.put("draft", s.draft());
        body.put("booked", s.booked());
        body.put("fulfilledOrderCount", s.fulfilledOrderCount());
        body.put("confirmedOrderCount", s.confirmedOrderCount());
        body.put("draftOrderCount", s.draftOrderCount());
        body.put("dailyRealized", orderService.dailyRevenueLast7Days());
        body.put("dailyBooked", orderService.dailyRevenueLast7Days(true, true));
        return body;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        CustomerOrder order = orderService.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No order with id=" + id));
        return ResponseEntity.ok(order);
    }

    /** Wholesale invoice / packing slip PDF for one order. */
    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> invoicePdf(@PathVariable Long id) {
        byte[] pdf = orderService.generateInvoicePdf(id);
        String filename = "invoice-order-" + id + ".pdf";
        return DownloadResponse.pdf(pdf, filename);
    }

    /** Plain-text invoice for quick CLI / curl. */
    @GetMapping(value = "/{id}/invoice.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> invoiceText(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.formatInvoiceText(id));
    }

    /**
     * Create order body example:
     * <pre>
     * { "customerId": 1, "orderDate": "2026-07-10", "status": "CONFIRMED",
     *   "handoffType": "MARKET",
     *   "notes": "Saturday market", "lines": [
     *     { "productName": "Nootka Rose", "quantity": 20, "unit": "bunch", "unitPrice": 12.0 }
     *   ] }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrderRequest body) {
        if (body == null || body.customerId() == null) {
            throw new IllegalArgumentException("customerId is required.");
        }
        LocalDate date = body.orderDate() != null ? body.orderDate() : LocalDate.now();
        CustomerOrder saved = orderService.create(
                body.customerId(), date, body.status(), body.notes(),
                body.handoffType(), body.lines());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }

    @PatchMapping("/{id}/handoff")
    public ResponseEntity<?> handoff(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String handoffType = body == null ? null : body.get("handoffType");
        return ResponseEntity.ok(orderService.updateHandoffType(id, handoffType));
    }

    /**
     * Fulfill an order: set status FULFILLED, decrement inventory SKUs that match
     * line product names, and write a CRM audit event to sync history.
     */
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<?> fulfill(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.fulfill(id));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<?> addLine(@PathVariable Long id, @RequestBody OrderLine line) {
        return ResponseEntity.ok(orderService.addLine(id, line));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateOrderRequest(
            Long customerId,
            LocalDate orderDate,
            String status,
            String notes,
            String handoffType,
            List<OrderLine> lines
    ) {}
}

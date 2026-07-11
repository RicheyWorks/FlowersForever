package com.flowerfarm.controller;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
        try {
            return ResponseEntity.ok(orderService.findBetween(from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            if (from != null && to != null && to.isBefore(from)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "to must be on or after from."));
            }
            return ResponseEntity.ok(orderService.filter(status, customer, from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.confirm(id));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.cancel(id));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<?> notes(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String notes = body == null ? "" : body.getOrDefault("notes", "");
            return ResponseEntity.ok(orderService.updateNotes(id, notes));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
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
        return orderService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No order with id=" + id)));
    }

    /** Wholesale invoice / packing slip PDF for one order. */
    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> invoicePdf(@PathVariable Long id) {
        try {
            byte[] pdf = orderService.generateInvoicePdf(id);
            String filename = "invoice-order-" + id + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /** Plain-text invoice for quick CLI / curl. */
    @GetMapping(value = "/{id}/invoice.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> invoiceText(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.formatInvoiceText(id));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Create order body example:
     * <pre>
     * { "customerId": 1, "orderDate": "2026-07-10", "status": "CONFIRMED",
     *   "notes": "Saturday market", "lines": [
     *     { "productName": "Nootka Rose", "quantity": 20, "unit": "bunch", "unitPrice": 12.0 }
     *   ] }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrderRequest body) {
        try {
            if (body == null || body.customerId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "customerId is required."));
            }
            LocalDate date = body.orderDate() != null ? body.orderDate() : LocalDate.now();
            CustomerOrder saved = orderService.create(
                    body.customerId(), date, body.status(), body.notes(), body.lines());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String status = body == null ? null : body.get("status");
            return ResponseEntity.ok(orderService.updateStatus(id, status));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fulfill an order: set status FULFILLED, decrement inventory SKUs that match
     * line product names, and write a CRM audit event to sync history.
     */
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<?> fulfill(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.fulfill(id));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<?> addLine(@PathVariable Long id, @RequestBody OrderLine line) {
        try {
            return ResponseEntity.ok(orderService.addLine(id, line));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            orderService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    public record CreateOrderRequest(
            Long customerId,
            LocalDate orderDate,
            String status,
            String notes,
            List<OrderLine> lines
    ) {}
}

package com.flowerfarm.controller;

import com.flowerfarm.controller.dto.FarmApiDtos.CustomerRequest;
import com.flowerfarm.controller.dto.FarmApiDtos.CustomerResponse;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.CustomerStatementService.CustomerStatement;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerStatementService customerStatementService;

    public CustomerController(CustomerService customerService,
                              CustomerStatementService customerStatementService) {
        this.customerService = customerService;
        this.customerStatementService = customerStatementService;
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.getAll().stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return customerService.findById(id)
                .<ResponseEntity<?>>map(customer -> ResponseEntity.ok(CustomerResponse.from(customer)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No customer with id=" + id)));
    }

    /**
     * Account statement JSON for one customer.
     * Defaults: trailing 90 days through today when {@code from}/{@code to} omitted.
     */
    @GetMapping("/{id}/statement")
    public ResponseEntity<?> statement(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(customerStatementService.build(id, from, to).toMap());
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/statement.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> statementText(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(customerStatementService.build(id, from, to).plainText());
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping(value = "/{id}/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> statementPdf(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            CustomerStatement statement = customerStatementService.build(id, from, to);
            byte[] pdf = customerStatementService.generatePdf(statement);
            String filename = "statement-customer-" + id + ".pdf";
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

    @GetMapping("/search")
    public List<CustomerResponse> search(@RequestParam("q") String q) {
        return customerService.search(q).stream().map(CustomerResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody CustomerRequest body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customerService.add(body.toCustomer())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CustomerRequest body) {
        try {
            return ResponseEntity.ok(CustomerResponse.from(customerService.update(id, body.toCustomer())));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            customerService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}

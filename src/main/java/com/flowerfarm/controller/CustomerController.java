package com.flowerfarm.controller;

import com.flowerfarm.model.Customer;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.CustomerStatementService.CustomerStatement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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
    public List<Customer> list() {
        return customerService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Customer customer = customerService.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + id));
        return ResponseEntity.ok(customer);
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
        return ResponseEntity.ok(customerStatementService.build(id, from, to).toMap());
    }

    @GetMapping(value = "/{id}/statement.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> statementText(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(customerStatementService.build(id, from, to).plainText());
    }

    @GetMapping(value = "/{id}/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> statementPdf(
            @PathVariable Long id,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CustomerStatement statement = customerStatementService.build(id, from, to);
        byte[] pdf = customerStatementService.generatePdf(statement);
        String filename = "statement-customer-" + id + ".pdf";
        return DownloadResponse.pdf(pdf, filename);
    }

    @GetMapping("/search")
    public List<Customer> search(@RequestParam("q") String q) {
        return customerService.search(q);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Customer customer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.add(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Customer customer) {
        return ResponseEntity.ok(customerService.update(id, customer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

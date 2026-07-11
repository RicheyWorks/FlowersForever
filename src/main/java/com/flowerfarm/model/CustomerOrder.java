package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Wholesale / market order placed by a {@link Customer}.
 * Named {@code CustomerOrder} to avoid clashing with SQL {@code ORDER}.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    /** DRAFT, CONFIRMED, FULFILLED, CANCELLED */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 2000)
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, LocalDate orderDate, String status, String notes) {
        setCustomer(customer);
        setOrderDate(orderDate);
        setStatus(status);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public LocalDate getOrderDate() { return orderDate; }
    public String getStatus() { return status; }
    public String getNotes() { return notes; }
    public List<OrderLine> getLines() { return lines; }

    public void setId(Long id) { this.id = id; }

    public void setCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer is required.");
        }
        this.customer = customer;
    }

    public void setOrderDate(LocalDate orderDate) {
        if (orderDate == null) {
            throw new IllegalArgumentException("Order date is required.");
        }
        this.orderDate = orderDate;
    }

    public void setStatus(String status) {
        this.status = (status == null || status.isBlank()) ? "DRAFT" : status.trim().toUpperCase();
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes.trim();
    }

    public void setLines(List<OrderLine> lines) {
        this.lines.clear();
        if (lines != null) {
            for (OrderLine line : lines) {
                addLine(line);
            }
        }
    }

    public void addLine(OrderLine line) {
        if (line == null) {
            return;
        }
        line.setOrder(this);
        this.lines.add(line);
    }

    public double lineTotal() {
        return lines.stream().mapToDouble(OrderLine::lineTotal).sum();
    }
}

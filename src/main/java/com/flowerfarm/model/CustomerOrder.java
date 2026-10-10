package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wholesale / market order placed by a {@link Customer}.
 * Named {@code CustomerOrder} to avoid clashing with SQL {@code ORDER}.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    private static final Set<String> SUPPORTED_STATUSES =
            Set.of("DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED");
    private static final Set<String> SUPPORTED_HANDOFF_TYPES =
            Set.of("PICKUP", "DELIVERY", "MARKET", "UNSPECIFIED");

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

    @Column(name = "handoff_type", length = 16)
    private String handoffType = "UNSPECIFIED";

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, LocalDate orderDate, String status, String notes) {
        this(customer, orderDate, status, notes, "UNSPECIFIED");
    }

    public CustomerOrder(Customer customer, LocalDate orderDate, String status, String notes,
                         String handoffType) {
        setCustomer(customer);
        setOrderDate(orderDate);
        setStatus(status);
        setNotes(notes);
        setHandoffType(handoffType);
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public LocalDate getOrderDate() { return orderDate; }
    public String getStatus() { return status; }
    public String getNotes() { return notes; }
    public String getHandoffType() {
        return handoffType == null || handoffType.isBlank() ? "UNSPECIFIED" : handoffType;
    }
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
        this.status = normalizeStatus(status);
    }

    public static String normalizeStatus(String status) {
        String normalized = (status == null || status.isBlank())
                ? "DRAFT"
                : status.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported order status '" + normalized
                            + "'. Expected DRAFT, CONFIRMED, FULFILLED, or CANCELLED.");
        }
        return normalized;
    }

    public static String normalizeHandoffType(String handoffType) {
        String normalized = handoffType == null || handoffType.isBlank()
                ? "UNSPECIFIED" : handoffType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_HANDOFF_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported handoff type '" + normalized
                    + "'. Expected PICKUP, DELIVERY, MARKET, or UNSPECIFIED.");
        }
        return normalized;
    }

    public void setNotes(String notes) {
        String normalized = notes == null ? "" : notes.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Notes cannot exceed 2000 characters.");
        }
        this.notes = normalized;
    }

    public void setHandoffType(String handoffType) {
        this.handoffType = normalizeHandoffType(handoffType);
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

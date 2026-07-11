package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * One product line on a {@link CustomerOrder}.
 */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private CustomerOrder order;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false, length = 64)
    private String unit;

    @Column(nullable = false)
    private double unitPrice;

    protected OrderLine() {
    }

    public OrderLine(String productName, double quantity, String unit, double unitPrice) {
        setProductName(productName);
        setQuantity(quantity);
        setUnit(unit);
        setUnitPrice(unitPrice);
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public String getProductName() { return productName; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public double getUnitPrice() { return unitPrice; }

    public void setId(Long id) { this.id = id; }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public void setProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        this.productName = productName.trim();
    }

    public void setQuantity(double quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        this.quantity = quantity;
    }

    public void setUnit(String unit) {
        this.unit = (unit == null || unit.isBlank()) ? "bunch" : unit.trim();
    }

    public void setUnitPrice(double unitPrice) {
        if (unitPrice < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative.");
        }
        this.unitPrice = unitPrice;
    }

    public double lineTotal() {
        return quantity * unitPrice;
    }
}

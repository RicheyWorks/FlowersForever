package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * One product line on a {@link CustomerOrder}.
 */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    private static final double MAX_UNIT_PRICE = 1_000_000_000.0;

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
        this.productName = requireMaxLength(productName.trim(), 255, "Product name");
    }

    public void setQuantity(double quantity) {
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Quantity must be a finite number greater than 0 and no more than "
                            + Integer.MAX_VALUE + ".");
        }
        this.quantity = quantity;
    }

    public void setUnit(String unit) {
        String normalized = (unit == null || unit.isBlank()) ? "bunch" : unit.trim();
        this.unit = requireMaxLength(normalized, 64, "Unit");
    }

    public void setUnitPrice(double unitPrice) {
        if (!Double.isFinite(unitPrice) || unitPrice < 0 || unitPrice > MAX_UNIT_PRICE) {
            throw new IllegalArgumentException("Unit price must be a finite number between 0 and "
                    + (long) MAX_UNIT_PRICE + ".");
        }
        this.unitPrice = unitPrice;
    }

    private static String requireMaxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return value;
    }

    public double lineTotal() {
        return quantity * unitPrice;
    }
}

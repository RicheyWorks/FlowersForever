package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/** Safe outbound reference for a contract, payment, or marketplace integration. */
@Entity
@Table(name = "integration_handoffs")
public class IntegrationHandoff {

    private static final Set<String> TYPES = Set.of("CONTRACT", "PAYMENT", "MARKETPLACE");
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "READY", "SENT", "ACCEPTED", "PAID", "FAILED", "CANCELLED");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handoff_type", nullable = false, length = 24)
    private String handoffType;

    @Column(nullable = false, length = 80)
    private String provider;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "related_order_id")
    private Long relatedOrderId;

    @Column(name = "related_recipe_id")
    private Long relatedRecipeId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payload_summary", nullable = false, length = 2000)
    private String payloadSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationHandoff() {
    }

    public IntegrationHandoff(String handoffType, String provider, String status,
                              String externalReference, Long relatedOrderId,
                              Long relatedRecipeId, double amount, String currency,
                              String payloadSummary) {
        setHandoffType(handoffType);
        setProvider(provider);
        setStatus(status);
        setExternalReference(externalReference);
        setRelatedOrderId(relatedOrderId);
        setRelatedRecipeId(relatedRecipeId);
        setAmount(amount);
        setCurrency(currency);
        setPayloadSummary(payloadSummary);
    }

    public Long getId() { return id; }
    public String getHandoffType() { return handoffType; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public Long getRelatedOrderId() { return relatedOrderId; }
    public Long getRelatedRecipeId() { return relatedRecipeId; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPayloadSummary() { return payloadSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setHandoffType(String type) { this.handoffType = supported(type, TYPES, "handoff type"); }
    public void setProvider(String provider) { this.provider = text(provider, 80, "Provider", true); }
    public void setStatus(String status) { this.status = supported(status, STATUSES, "status"); }
    public void setExternalReference(String reference) {
        String normalized = text(reference, 255, "External reference", false);
        this.externalReference = normalized.isBlank() ? null : normalized;
    }
    public void setRelatedOrderId(Long id) { this.relatedOrderId = positiveOrNull(id, "Order id"); }
    public void setRelatedRecipeId(Long id) { this.relatedRecipeId = positiveOrNull(id, "Recipe id"); }

    public void setAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0 || amount > 1_000_000_000) {
            throw new IllegalArgumentException("Amount must be a non-negative finite amount.");
        }
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        String normalized = text(currency, 3, "Currency", true).toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Currency must be a three-letter code.");
        }
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must contain three letters.");
        }
        this.currency = normalized;
    }

    public void setPayloadSummary(String summary) {
        this.payloadSummary = text(summary, 2000, "Payload summary", true);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    private static String supported(String value, Set<String> supported, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported " + field + " '" + normalized + "'.");
        }
        return normalized;
    }

    private static Long positiveOrNull(Long id, String field) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return id;
    }

    private static String text(String value, int max, String field, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}

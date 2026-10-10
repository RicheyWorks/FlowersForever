package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A farm-owned observation and its practical lesson.
 *
 * <p>Links use nullable record ids instead of object graphs so entries remain
 * readable when an operational record is later removed. Database foreign keys
 * clear those links without deleting the lesson.
 */
@Entity
@Table(name = "knowledge_entries", indexes = {
        @Index(name = "idx_knowledge_observed", columnList = "observed_on"),
        @Index(name = "idx_knowledge_follow_up", columnList = "follow_up_date"),
        @Index(name = "idx_knowledge_crop", columnList = "crop_name"),
        @Index(name = "idx_knowledge_field_science_export",
                columnList = "field_science_export_id")
})
public class KnowledgeEntry {

    public static final int MAX_TAGS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Column(nullable = false, length = 180)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private KnowledgeKind kind;

    @Column(name = "crop_name", length = 255)
    private String cropName;

    @Column(length = 255)
    private String variety;

    @Column(name = "bed_or_field", length = 128)
    private String bedOrField;

    @Column(name = "production_stage", length = 128)
    private String productionStage;

    @Column(nullable = false, length = 2000)
    private String observation;

    @Column(name = "action_taken", length = 2000)
    private String actionTaken;

    @Column(name = "performed_by", length = 255)
    private String performedBy;

    @Column(length = 2000)
    private String result;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private KnowledgeConfidence confidence;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_entry_tags",
            joinColumns = @JoinColumn(name = "knowledge_entry_id"))
    @Column(name = "tag", nullable = false, length = 64)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "harvest_id")
    private Long harvestId;

    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "field_science_export_id")
    private Long fieldScienceExportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeEntry() {
    }

    public KnowledgeEntry(LocalDate observedOn, String summary, KnowledgeKind kind,
                          String cropName, String variety, String bedOrField,
                          String productionStage, String observation, String actionTaken,
                          String performedBy, String result, LocalDate followUpDate,
                          KnowledgeConfidence confidence, Collection<String> tags) {
        this(null, observedOn, summary, kind, cropName, variety, bedOrField,
                productionStage, observation, actionTaken, performedBy, result,
                followUpDate, confidence, tags, null, null, null, null, null, null);
    }

    @JsonCreator
    public KnowledgeEntry(
            @JsonProperty("id") Long id,
            @JsonProperty("observedOn") LocalDate observedOn,
            @JsonProperty("summary") String summary,
            @JsonProperty("kind") KnowledgeKind kind,
            @JsonProperty("cropName") String cropName,
            @JsonProperty("variety") String variety,
            @JsonProperty("bedOrField") String bedOrField,
            @JsonProperty("productionStage") String productionStage,
            @JsonProperty("observation") String observation,
            @JsonProperty("actionTaken") String actionTaken,
            @JsonProperty("performedBy") String performedBy,
            @JsonProperty("result") String result,
            @JsonProperty("followUpDate") LocalDate followUpDate,
            @JsonProperty("confidence") KnowledgeConfidence confidence,
            @JsonProperty("tags") Collection<String> tags,
            @JsonProperty("harvestId") Long harvestId,
            @JsonProperty("inventoryItemId") Long inventoryItemId,
            @JsonProperty("orderId") Long orderId,
            @JsonProperty("customerId") Long customerId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt) {
        this.id = id;
        setObservedOn(observedOn);
        setSummary(summary);
        setKind(kind);
        setCropName(cropName);
        setVariety(variety);
        setBedOrField(bedOrField);
        setProductionStage(productionStage);
        setObservation(observation);
        setActionTaken(actionTaken);
        setPerformedBy(performedBy);
        setResult(result);
        setFollowUpDate(followUpDate);
        setConfidence(confidence);
        setTags(tags);
        setHarvestId(harvestId);
        setInventoryItemId(inventoryItemId);
        setOrderId(orderId);
        setCustomerId(customerId);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public LocalDate getObservedOn() { return observedOn; }
    public String getSummary() { return summary; }
    public KnowledgeKind getKind() { return kind; }
    public String getCropName() { return cropName; }
    public String getVariety() { return variety; }
    public String getBedOrField() { return bedOrField; }
    public String getProductionStage() { return productionStage; }
    public String getObservation() { return observation; }
    public String getActionTaken() { return actionTaken; }
    public String getPerformedBy() { return performedBy; }
    public String getResult() { return result; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public KnowledgeConfidence getConfidence() { return confidence; }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public Long getHarvestId() { return harvestId; }
    public Long getInventoryItemId() { return inventoryItemId; }
    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public Long getFieldScienceExportId() { return fieldScienceExportId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }

    public void setObservedOn(LocalDate observedOn) {
        if (observedOn == null) {
            throw new IllegalArgumentException("Observation date is required.");
        }
        this.observedOn = observedOn;
    }

    public void setSummary(String summary) {
        this.summary = requireText(summary, 180, "What happened");
    }

    public void setKind(KnowledgeKind kind) {
        this.kind = kind == null ? KnowledgeKind.PRACTICE : kind;
    }

    public void setCropName(String cropName) {
        this.cropName = optionalText(cropName, 255, "Crop");
    }

    public void setVariety(String variety) {
        this.variety = optionalText(variety, 255, "Variety");
    }

    public void setBedOrField(String bedOrField) {
        this.bedOrField = optionalText(bedOrField, 128, "Bed or field");
    }

    public void setProductionStage(String productionStage) {
        this.productionStage = optionalText(productionStage, 128, "Production stage");
    }

    public void setObservation(String observation) {
        this.observation = requireText(observation, 2000, "Observation");
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = optionalText(actionTaken, 2000, "Action taken");
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = optionalText(performedBy, 255, "Action owner");
    }

    public void setResult(String result) {
        this.result = optionalText(result, 2000, "Result");
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public void setConfidence(KnowledgeConfidence confidence) {
        this.confidence = confidence == null
                ? KnowledgeConfidence.NEEDS_REVIEW : confidence;
    }

    public void setTags(Collection<String> tags) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                String value = normalizeTag(tag);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        if (normalized.size() > MAX_TAGS) {
            throw new IllegalArgumentException(
                    "A knowledge entry cannot contain more than " + MAX_TAGS + " tags.");
        }
        this.tags.clear();
        this.tags.addAll(normalized);
    }

    public void setHarvestId(Long harvestId) {
        this.harvestId = optionalId(harvestId, "Harvest id");
    }

    public void setInventoryItemId(Long inventoryItemId) {
        this.inventoryItemId = optionalId(inventoryItemId, "Inventory item id");
    }

    public void setOrderId(Long orderId) {
        this.orderId = optionalId(orderId, "Order id");
    }

    public void setCustomerId(Long customerId) {
        this.customerId = optionalId(customerId, "Customer id");
    }

    @JsonProperty("fieldScienceExportId")
    public void setFieldScienceExportId(Long fieldScienceExportId) {
        this.fieldScienceExportId = optionalId(fieldScienceExportId,
                "Field science export id");
    }

    public void copyBusinessFieldsFrom(KnowledgeEntry source) {
        if (source == null) {
            throw new IllegalArgumentException("Knowledge entry is required.");
        }
        setObservedOn(source.getObservedOn());
        setSummary(source.getSummary());
        setKind(source.getKind());
        setCropName(source.getCropName());
        setVariety(source.getVariety());
        setBedOrField(source.getBedOrField());
        setProductionStage(source.getProductionStage());
        setObservation(source.getObservation());
        setActionTaken(source.getActionTaken());
        setPerformedBy(source.getPerformedBy());
        setResult(source.getResult());
        setFollowUpDate(source.getFollowUpDate());
        setConfidence(source.getConfidence());
        setTags(source.getTags());
        setHarvestId(source.getHarvestId());
        setInventoryItemId(source.getInventoryItemId());
        setOrderId(source.getOrderId());
        setCustomerId(source.getCustomerId());
        setFieldScienceExportId(source.getFieldScienceExportId());
    }

    public void initializeTimestamps(Instant now) {
        Instant timestamp = now == null ? Instant.now() : now;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void touch(Instant now) {
        this.updatedAt = now == null ? Instant.now() : now;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String value = Normalizer.normalize(tag, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{L}\\p{N}_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return optionalText(value, 64, "Tag");
    }

    private static Long optionalId(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be a positive number.");
        }
        return value;
    }

    private static String requireText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return optionalText(value, max, field);
    }

    private static String optionalText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(
                    field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}

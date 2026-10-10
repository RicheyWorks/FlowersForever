package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** One plan-linked production task; generated tasks can be safely regenerated while open. */
@Entity
@Table(name = "production_tasks")
public class ProductionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_plan_id")
    private Long seasonPlanId;

    @Column(name = "growing_template_id")
    private Long growingTemplateId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private ProductionTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductionTaskStatus status;

    @Column(name = "crop_name", nullable = false, length = 120)
    private String cropName;

    @Column(name = "bed_or_field", nullable = false, length = 128)
    private String bedOrField;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(nullable = false)
    private boolean generated;

    @Column(length = 1000)
    private String notes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "input_cost", nullable = false)
    private double inputCost;

    @Column(name = "field_observation", length = 1000)
    private String fieldObservation;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductionTask() {
    }

    public ProductionTask(Long seasonPlanId, Long growingTemplateId, LocalDate dueDate,
                          ProductionTaskType taskType, ProductionTaskStatus status,
                          String cropName, String bedOrField, int sequenceNumber,
                          boolean generated, String notes) {
        this.seasonPlanId = positiveOrNull(seasonPlanId, "Season plan id");
        this.growingTemplateId = positiveOrNull(growingTemplateId, "Growing template id");
        setDueDate(dueDate);
        setTaskType(taskType);
        setStatus(status);
        setCropName(cropName);
        setBedOrField(bedOrField);
        setSequenceNumber(sequenceNumber);
        setGenerated(generated);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public Long getSeasonPlanId() { return seasonPlanId; }
    public Long getGrowingTemplateId() { return growingTemplateId; }
    public LocalDate getDueDate() { return dueDate; }
    public ProductionTaskType getTaskType() { return taskType; }
    public ProductionTaskStatus getStatus() { return status; }
    public String getCropName() { return cropName; }
    public String getBedOrField() { return bedOrField; }
    public int getSequenceNumber() { return sequenceNumber; }
    public boolean isGenerated() { return generated; }
    public String getNotes() { return notes; }
    public Integer getActualMinutes() { return actualMinutes; }
    public double getInputCost() { return inputCost; }
    public String getFieldObservation() { return fieldObservation; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setSeasonPlanId(Long id) { this.seasonPlanId = positiveOrNull(id, "Season plan id"); }
    public void setGrowingTemplateId(Long id) {
        this.growingTemplateId = positiveOrNull(id, "Growing template id");
    }

    public void setDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Task due date is required.");
        }
        this.dueDate = dueDate;
    }

    public void setTaskType(ProductionTaskType taskType) {
        this.taskType = taskType == null ? ProductionTaskType.CUSTOM : taskType;
    }

    public void setStatus(ProductionTaskStatus status) {
        this.status = status == null ? ProductionTaskStatus.TODO : status;
    }

    public void setCropName(String cropName) { this.cropName = text(cropName, 120, "Crop", true); }
    public void setBedOrField(String bed) { this.bedOrField = text(bed, 128, "Bed or field", true); }

    public void setSequenceNumber(int sequenceNumber) {
        if (sequenceNumber < 0 || sequenceNumber > 1_000_000) {
            throw new IllegalArgumentException("Task sequence must be between 0 and 1,000,000.");
        }
        this.sequenceNumber = sequenceNumber;
    }

    public void setGenerated(boolean generated) { this.generated = generated; }
    public void setNotes(String notes) { this.notes = text(notes, 1000, "Notes", false); }

    public void recordActuals(int minutes, double cost, String observation,
                              Instant completedAt) {
        if (minutes < 0 || minutes > 10_080) {
            throw new IllegalArgumentException(
                    "Actual labor minutes must be between 0 and 10,080.");
        }
        if (!Double.isFinite(cost) || cost < 0 || cost > 1_000_000) {
            throw new IllegalArgumentException(
                    "Input cost must be between 0 and 1,000,000.");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("Completion time is required.");
        }
        actualMinutes = minutes;
        inputCost = cost;
        fieldObservation = text(
                observation, 1000, "Field observation", false);
        this.completedAt = completedAt;
        status = ProductionTaskStatus.DONE;
    }

    public boolean hasActuals() {
        return actualMinutes != null || Double.compare(inputCost, 0) != 0
                || fieldObservation != null && !fieldObservation.isBlank();
    }

    public void markDone(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("Completion time is required.");
        }
        this.completedAt = completedAt;
        status = ProductionTaskStatus.DONE;
    }

    public void reopenOrSkip(ProductionTaskStatus nextStatus) {
        if (nextStatus != ProductionTaskStatus.TODO
                && nextStatus != ProductionTaskStatus.SKIPPED) {
            throw new IllegalArgumentException(
                    "Task must be reopened or skipped.");
        }
        status = nextStatus;
        completedAt = null;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

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

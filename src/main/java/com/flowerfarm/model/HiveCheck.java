package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A local apiary observation, separate from treatment, honey stock, and sales. */
@Entity
@Table(name = "hive_checks", indexes = {
        @Index(name = "idx_hive_checks_apiary_date", columnList = "apiary_name,check_date")
})
public class HiveCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apiary_name", nullable = false, length = 120)
    private String apiaryName;

    @Column(name = "hive_label", nullable = false, length = 120)
    private String hiveLabel;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false, length = 120)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HiveCheckStatus status;

    @Column(name = "next_check_date")
    private LocalDate nextCheckDate;

    @Column(nullable = false, length = 2000)
    private String observations;

    @Column(nullable = false, length = 2000)
    private String actions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HiveCheck() {
    }

    public HiveCheck(String apiaryName, String hiveLabel, LocalDate checkDate,
                     String purpose, HiveCheckStatus status, LocalDate nextCheckDate,
                     String observations, String actions) {
        setApiaryName(apiaryName);
        setHiveLabel(hiveLabel);
        setCheckDate(checkDate);
        setPurpose(purpose);
        setStatus(status);
        setNextCheckDate(nextCheckDate);
        setObservations(observations);
        setActions(actions);
        validate();
    }

    public Long getId() { return id; }
    public String getApiaryName() { return apiaryName; }
    public String getHiveLabel() { return hiveLabel; }
    public LocalDate getCheckDate() { return checkDate; }
    public String getPurpose() { return purpose; }
    public HiveCheckStatus getStatus() { return status; }
    public LocalDate getNextCheckDate() { return nextCheckDate; }
    public String getObservations() { return observations; }
    public String getActions() { return actions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setApiaryName(String apiaryName) {
        this.apiaryName = text(apiaryName, 120, "Apiary name", true);
    }

    public void setHiveLabel(String hiveLabel) {
        this.hiveLabel = text(hiveLabel, 120, "Hive label", true);
    }

    public void setCheckDate(LocalDate checkDate) {
        this.checkDate = checkDate;
    }

    public void setPurpose(String purpose) {
        this.purpose = text(purpose, 120, "Purpose", false);
    }

    public void setStatus(HiveCheckStatus status) {
        this.status = status == null ? HiveCheckStatus.NOT_ASSESSED : status;
    }

    public void setNextCheckDate(LocalDate nextCheckDate) {
        this.nextCheckDate = nextCheckDate;
    }

    public void setObservations(String observations) {
        this.observations = text(observations, 2000, "Observations", false);
    }

    public void setActions(String actions) {
        this.actions = text(actions, 2000, "Actions", false);
    }

    public void validate() {
        if (apiaryName == null || apiaryName.isBlank() || hiveLabel == null
                || hiveLabel.isBlank() || checkDate == null || purpose == null
                || status == null || observations == null || actions == null) {
            throw new IllegalArgumentException("Complete the required hive check details.");
        }
        text(apiaryName, 120, "Apiary name", true);
        text(hiveLabel, 120, "Hive label", true);
        text(purpose, 120, "Purpose", false);
        text(observations, 2000, "Observations", false);
        text(actions, 2000, "Actions", false);
        if (nextCheckDate != null && nextCheckDate.isBefore(checkDate)) {
            throw new IllegalArgumentException(
                    "Next check date cannot be before the check date.");
        }
    }

    public void copyBusinessFieldsFrom(HiveCheck source) {
        if (source == null) {
            throw new IllegalArgumentException("Hive check is required.");
        }
        source.validate();
        setApiaryName(source.apiaryName);
        setHiveLabel(source.hiveLabel);
        setCheckDate(source.checkDate);
        setPurpose(source.purpose);
        setStatus(source.status);
        setNextCheckDate(source.nextCheckDate);
        setObservations(source.observations);
        setActions(source.actions);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public HiveCheck snapshot() {
        HiveCheck copy = new HiveCheck(apiaryName, hiveLabel, checkDate,
                purpose, status, nextCheckDate, observations, actions);
        copy.id = id;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    @PrePersist
    void beforeInsert() {
        validate();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        validate();
        updatedAt = Instant.now();
    }

    private static String text(String value, int max, String field, boolean required) {
        String clean = value == null ? "" : value.trim();
        if (required && clean.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (clean.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return clean;
    }
}

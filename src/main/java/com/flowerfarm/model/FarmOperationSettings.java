package com.flowerfarm.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Persisted, installation-wide farm specialization choices. */
@Entity
@Table(name = "farm_operation_settings")
public class FarmOperationSettings {

    public static final Long SINGLETON_ID = 1L;
    private static final int MAX_SPECIALTY_CROPS = 40;
    private static final int MAX_CROP_LENGTH = 120;

    @Id
    private Long id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_code", nullable = false, length = 2)
    private FarmState state = FarmState.WA;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_enterprise", nullable = false, length = 32)
    private FarmEnterprise primaryEnterprise = FarmEnterprise.CUT_FLOWERS;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_code", nullable = false, length = 32)
    private FarmWorkspace workspace = FarmWorkspace.ROSE_AND_EVENTS;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_operation_workspaces",
            joinColumns = @JoinColumn(name = "settings_id"))
    @OrderColumn(name = "display_order", nullable = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_code", nullable = false, length = 32)
    private List<FarmWorkspace> enabledWorkspaces =
            new ArrayList<>(List.of(FarmWorkspace.ROSE_AND_EVENTS));

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_operation_enterprises",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "enterprise_code", nullable = false, length = 32)
    private Set<FarmEnterprise> enterprises =
            new LinkedHashSet<>(Set.of(FarmEnterprise.CUT_FLOWERS));

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_operation_specialty_crops",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "crop_name", nullable = false, length = MAX_CROP_LENGTH)
    private Set<String> specialtyCrops = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_operation_flower_offerings",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "offering_code", nullable = false, length = 32)
    private Set<FlowerOffering> flowerOfferings = new LinkedHashSet<>(Arrays.asList(
            FlowerOffering.STEMS, FlowerOffering.BOUQUETS, FlowerOffering.WEDDINGS));

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FarmOperationSettings() {
    }

    public Long getId() {
        return id;
    }

    public FarmState getState() {
        return state;
    }

    public void setState(FarmState state) {
        this.state = Objects.requireNonNull(state, "Farm state is required.");
    }

    public FarmEnterprise getPrimaryEnterprise() {
        return primaryEnterprise;
    }

    public void setPrimaryEnterprise(FarmEnterprise primaryEnterprise) {
        this.primaryEnterprise = Objects.requireNonNull(
                primaryEnterprise, "Primary enterprise is required.");
    }

    public FarmWorkspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(FarmWorkspace workspace) {
        FarmWorkspace next = Objects.requireNonNull(workspace, "Farm workspace is required.");
        if (enabledWorkspaces.size() == 1
                && enabledWorkspaces.get(0) == this.workspace) {
            enabledWorkspaces.clear();
        } else {
            enabledWorkspaces.remove(next);
        }
        enabledWorkspaces.add(0, next);
        this.workspace = next;
    }

    /** Selected workspaces in display order, with the primary workspace first. */
    public Set<FarmWorkspace> getEnabledWorkspaces() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(enabledWorkspaces));
    }

    public void setEnabledWorkspaces(Set<FarmWorkspace> workspaces) {
        if (workspaces == null || workspaces.isEmpty()
                || workspaces.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Choose at least one farm type.");
        }
        LinkedHashSet<FarmWorkspace> ordered = new LinkedHashSet<>();
        ordered.add(workspace);
        ordered.addAll(workspaces);
        enabledWorkspaces.clear();
        enabledWorkspaces.addAll(ordered);
    }

    public Set<FarmEnterprise> getEnterprises() {
        LinkedHashSet<FarmEnterprise> ordered = new LinkedHashSet<>();
        for (FarmEnterprise enterprise : FarmEnterprise.values()) {
            if (enterprises.contains(enterprise)) {
                ordered.add(enterprise);
            }
        }
        return Collections.unmodifiableSet(ordered);
    }

    public void setEnterprises(Set<FarmEnterprise> enterprises) {
        if (enterprises == null || enterprises.isEmpty()
                || enterprises.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Choose at least one farm enterprise.");
        }
        this.enterprises.clear();
        this.enterprises.addAll(enterprises);
    }

    public Set<String> getSpecialtyCrops() {
        ArrayList<String> ordered = new ArrayList<>(specialtyCrops);
        ordered.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    public void setSpecialtyCrops(Set<String> specialtyCrops) {
        if (specialtyCrops == null) {
            throw new IllegalArgumentException("Specialty crops are required.");
        }
        if (specialtyCrops.size() > MAX_SPECIALTY_CROPS) {
            throw new IllegalArgumentException("Use no more than 40 specialty crops.");
        }
        ArrayList<String> cleanedCrops = new ArrayList<>();
        for (String crop : specialtyCrops) {
            if (crop == null || crop.isBlank()) {
                throw new IllegalArgumentException("Specialty crop names cannot be blank.");
            }
            String cleaned = crop.trim();
            if (cleaned.length() > MAX_CROP_LENGTH) {
                throw new IllegalArgumentException(
                        "Specialty crop names cannot exceed 120 characters.");
            }
            cleanedCrops.add(cleaned);
        }
        cleanedCrops.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(
                Comparator.naturalOrder()));
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String cleaned : cleanedCrops) {
            if (keys.add(cleaned.toLowerCase(Locale.ROOT))) {
                normalized.add(cleaned);
            }
        }
        this.specialtyCrops.clear();
        this.specialtyCrops.addAll(normalized);
    }

    public Set<FlowerOffering> getFlowerOfferings() {
        LinkedHashSet<FlowerOffering> ordered = new LinkedHashSet<>();
        for (FlowerOffering offering : FlowerOffering.values()) {
            if (flowerOfferings.contains(offering)) {
                ordered.add(offering);
            }
        }
        return Collections.unmodifiableSet(ordered);
    }

    public void setFlowerOfferings(Set<FlowerOffering> flowerOfferings) {
        if (flowerOfferings == null
                || flowerOfferings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Flower offerings are required.");
        }
        this.flowerOfferings.clear();
        this.flowerOfferings.addAll(flowerOfferings);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void copyBusinessFieldsFrom(FarmOperationSettings source) {
        Objects.requireNonNull(source, "Farm operation settings are required.");
        source.validate();
        setState(source.getState());
        setPrimaryEnterprise(source.getPrimaryEnterprise());
        setWorkspace(source.getWorkspace());
        setEnabledWorkspaces(source.getEnabledWorkspaces());
        setEnterprises(source.getEnterprises());
        setSpecialtyCrops(source.getSpecialtyCrops());
        setFlowerOfferings(source.getFlowerOfferings());
    }

    public void validate() {
        if (workspace == null) {
            throw new IllegalArgumentException("Farm workspace is required.");
        }
        if (enabledWorkspaces == null || enabledWorkspaces.isEmpty()
                || enabledWorkspaces.get(0) != workspace
                || enabledWorkspaces.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(enabledWorkspaces).size() != enabledWorkspaces.size()) {
            throw new IllegalArgumentException(
                    "Selected farm types must include the primary workspace first.");
        }
        if (state == null || primaryEnterprise == null || enterprises == null
                || enterprises.isEmpty() || !enterprises.contains(primaryEnterprise)) {
            throw new IllegalArgumentException(
                    "The primary enterprise must be one of the selected farm enterprises.");
        }
        for (FarmWorkspace selected : enabledWorkspaces) {
            selected.requiredEnterprise().ifPresent(required -> {
                if (!enterprises.contains(required)) {
                    throw new IllegalArgumentException(
                            "The " + selected + " workspace requires " + required
                                    + " as a selected enterprise.");
                }
            });
        }
        if (specialtyCrops == null || flowerOfferings == null) {
            throw new IllegalArgumentException("Farm operation selections are required.");
        }
        if (!enterprises.contains(FarmEnterprise.CUT_FLOWERS)
                && !enterprises.contains(FarmEnterprise.FLORAL_RETAIL)
                && !flowerOfferings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Flower offerings require Cut flowers or Floral retail "
                            + "as a selected enterprise.");
        }
    }

    /** Detached business snapshot so callers cannot mutate a managed settings row. */
    public FarmOperationSettings snapshot() {
        FarmOperationSettings copy = new FarmOperationSettings();
        copy.copyBusinessFieldsFrom(this);
        copy.updatedAt = updatedAt;
        return copy;
    }

    @PrePersist
    @PreUpdate
    void beforeSave() {
        validate();
        updatedAt = Instant.now();
    }
}

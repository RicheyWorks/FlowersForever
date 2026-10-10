package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/** Farm-owned frost anchors and costing assumptions for one production year. */
@Entity
@Table(name = "farm_season_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_farm_season_year", columnNames = "season_year"))
public class FarmSeasonSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "last_spring_frost", nullable = false)
    private LocalDate lastSpringFrost;

    @Column(name = "first_fall_frost", nullable = false)
    private LocalDate firstFallFrost;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "loaded_labor_rate_per_hour", nullable = false)
    private double loadedLaborRatePerHour;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FarmSeasonSettings() {
    }

    public FarmSeasonSettings(int seasonYear, LocalDate lastSpringFrost,
                              LocalDate firstFallFrost, String source) {
        this(seasonYear, lastSpringFrost, firstFallFrost, source, 0);
    }

    public FarmSeasonSettings(int seasonYear, LocalDate lastSpringFrost,
                              LocalDate firstFallFrost, String source,
                              double loadedLaborRatePerHour) {
        setSeasonYear(seasonYear);
        setLastSpringFrost(lastSpringFrost);
        setFirstFallFrost(firstFallFrost);
        setSource(source);
        setLoadedLaborRatePerHour(loadedLaborRatePerHour);
        validateWindow();
    }

    public Long getId() { return id; }
    public int getSeasonYear() { return seasonYear; }
    public LocalDate getLastSpringFrost() { return lastSpringFrost; }
    public LocalDate getFirstFallFrost() { return firstFallFrost; }
    public String getSource() { return source; }
    public double getLoadedLaborRatePerHour() { return loadedLaborRatePerHour; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }

    public void setSeasonYear(int seasonYear) {
        if (seasonYear < 2000 || seasonYear > 2200) {
            throw new IllegalArgumentException("Season year must be between 2000 and 2200.");
        }
        this.seasonYear = seasonYear;
    }

    public void setLastSpringFrost(LocalDate lastSpringFrost) {
        if (lastSpringFrost == null) {
            throw new IllegalArgumentException("Last spring frost is required.");
        }
        this.lastSpringFrost = lastSpringFrost;
    }

    public void setFirstFallFrost(LocalDate firstFallFrost) {
        if (firstFallFrost == null) {
            throw new IllegalArgumentException("First fall frost is required.");
        }
        this.firstFallFrost = firstFallFrost;
    }

    public void setSource(String source) {
        String normalized = source == null ? "Farm estimate" : source.trim();
        this.source = normalized.isBlank() ? "Farm estimate" : requireLength(normalized, 255);
    }

    public void setLoadedLaborRatePerHour(double loadedLaborRatePerHour) {
        if (!Double.isFinite(loadedLaborRatePerHour)
                || loadedLaborRatePerHour < 0 || loadedLaborRatePerHour > 10_000) {
            throw new IllegalArgumentException(
                    "Loaded labor rate must be between 0 and 10,000 per hour.");
        }
        this.loadedLaborRatePerHour = loadedLaborRatePerHour;
    }

    public void copyBusinessFieldsFrom(FarmSeasonSettings sourceSettings) {
        if (sourceSettings == null) {
            throw new IllegalArgumentException("Season settings are required.");
        }
        setSeasonYear(sourceSettings.getSeasonYear());
        setLastSpringFrost(sourceSettings.getLastSpringFrost());
        setFirstFallFrost(sourceSettings.getFirstFallFrost());
        setSource(sourceSettings.getSource());
        setLoadedLaborRatePerHour(sourceSettings.getLoadedLaborRatePerHour());
        validateWindow();
    }

    public void validateWindow() {
        if (lastSpringFrost == null || firstFallFrost == null) {
            return;
        }
        if (lastSpringFrost.getYear() != seasonYear || firstFallFrost.getYear() != seasonYear) {
            throw new IllegalArgumentException("Frost dates must fall in the season year.");
        }
        if (!firstFallFrost.isAfter(lastSpringFrost)) {
            throw new IllegalArgumentException("First fall frost must be after last spring frost.");
        }
    }

    @PrePersist
    @PreUpdate
    void beforeSave() {
        validateWindow();
        updatedAt = Instant.now();
    }

    private static String requireLength(String value, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException("Source cannot exceed " + max + " characters.");
        }
        return value;
    }
}

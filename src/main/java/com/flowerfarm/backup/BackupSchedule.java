package com.flowerfarm.backup;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** How often automatic backups run. */
public enum BackupSchedule {
    DAILY("Every day", Duration.ofDays(1)),
    WEEKLY("Every week", Duration.ofDays(7));

    private final String label;
    private final Duration interval;

    BackupSchedule(String label, Duration interval) {
        this.label = label;
        this.interval = interval;
    }

    public Duration interval() {
        return interval;
    }

    public String label() {
        return label;
    }

    public static Optional<BackupSchedule> fromName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (BackupSchedule schedule : values()) {
            if (schedule.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(schedule);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return label;
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

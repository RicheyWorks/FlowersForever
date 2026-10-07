package com.flowerfarm.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Loads and saves cooler review intervals / last-reviewed lot dates. */
public final class CoolerShelfLifePreferences {

    public static final String PREF_DEFAULT_DAYS = "cooler.defaultDays";
    public static final String PREF_CROP_PREFIX = "cooler.crop.";
    public static final String PREF_METHOD_PREFIX = "cooler.method.";
    public static final String PREF_REVIEWED_PREFIX = "cooler.reviewed.";

    private final Preferences prefs;

    public CoolerShelfLifePreferences() {
        this(Preferences.userNodeForPackage(CoolerShelfLifePreferences.class));
    }

    public CoolerShelfLifePreferences(Preferences prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
    }

    public CoolerShelfLife.Settings load() {
        int defaults = prefs.getInt(PREF_DEFAULT_DAYS, CoolerShelfLife.DEFAULT_INTERVAL_DAYS);
        Map<String, Integer> crops = new LinkedHashMap<>();
        Map<String, CoolerShelfLife.Conditioning> methods = new LinkedHashMap<>();
        Map<String, LocalDate> reviewed = new LinkedHashMap<>();
        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(PREF_CROP_PREFIX)) {
                    String crop = key.substring(PREF_CROP_PREFIX.length());
                    try {
                        crops.put(crop, CoolerShelfLife.clampInterval(prefs.getInt(key, defaults)));
                    } catch (IllegalArgumentException ignored) {
                        // skip corrupt interval
                    }
                } else if (key.startsWith(PREF_METHOD_PREFIX)) {
                    String crop = key.substring(PREF_METHOD_PREFIX.length());
                    try {
                        methods.put(crop, CoolerShelfLife.Conditioning.fromName(prefs.get(key, "")));
                    } catch (IllegalArgumentException ignored) {
                        // skip corrupt method
                    }
                } else if (key.startsWith(PREF_REVIEWED_PREFIX)) {
                    String lot = key.substring(PREF_REVIEWED_PREFIX.length());
                    try {
                        reviewed.put(lot, LocalDate.parse(prefs.get(key, "")));
                    } catch (DateTimeParseException ignored) {
                        // skip corrupt date
                    }
                }
            }
        } catch (BackingStoreException ignored) {
            return CoolerShelfLife.Settings.defaults().withDefaultInterval(
                    safeDefault(defaults));
        }
        try {
            return new CoolerShelfLife.Settings(safeDefault(defaults), crops, methods, reviewed);
        } catch (IllegalArgumentException invalid) {
            return CoolerShelfLife.Settings.defaults();
        }
    }

    public void save(CoolerShelfLife.Settings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            clearPrefixed(PREF_CROP_PREFIX);
            clearPrefixed(PREF_METHOD_PREFIX);
            clearPrefixed(PREF_REVIEWED_PREFIX);
            prefs.putInt(PREF_DEFAULT_DAYS, settings.defaultIntervalDays());
            for (Map.Entry<String, Integer> entry : settings.cropIntervals().entrySet()) {
                prefs.putInt(PREF_CROP_PREFIX + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, CoolerShelfLife.Conditioning> entry
                    : settings.cropConditioning().entrySet()) {
                prefs.put(PREF_METHOD_PREFIX + entry.getKey(), entry.getValue().name());
            }
            for (Map.Entry<String, LocalDate> entry : settings.lastReviewedByLot().entrySet()) {
                prefs.put(PREF_REVIEWED_PREFIX + entry.getKey(), entry.getValue().toString());
            }
            prefs.flush();
        } catch (BackingStoreException failed) {
            throw new IllegalStateException("Could not save cooler review preferences.", failed);
        }
    }

    private void clearPrefixed(String prefix) throws BackingStoreException {
        for (String key : prefs.keys()) {
            if (key.startsWith(prefix)) {
                prefs.remove(key);
            }
        }
    }

    private static int safeDefault(int days) {
        try {
            return CoolerShelfLife.clampInterval(days);
        } catch (IllegalArgumentException invalid) {
            return CoolerShelfLife.DEFAULT_INTERVAL_DAYS;
        }
    }
}

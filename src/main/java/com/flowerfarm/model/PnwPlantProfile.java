package com.flowerfarm.model;

import java.util.Locale;
import java.util.Objects;

/** A concise, sourced growing profile for a Pacific Northwest farm or garden. */
public record PnwPlantProfile(
        String commonName,
        String scientificName,
        String type,
        boolean nativeToPacificNorthwest,
        String light,
        String moisture,
        String season,
        String uses,
        String fieldNotes,
        Source source) {

    public PnwPlantProfile {
        commonName = requireText(commonName, "commonName");
        scientificName = requireText(scientificName, "scientificName");
        type = requireText(type, "type");
        light = requireText(light, "light");
        moisture = requireText(moisture, "moisture");
        season = requireText(season, "season");
        uses = requireText(uses, "uses");
        fieldNotes = requireText(fieldNotes, "fieldNotes");
        source = Objects.requireNonNull(source, "source");
    }

    public String originLabel() {
        return nativeToPacificNorthwest ? "PNW native" : "Garden crop / adapted";
    }

    /** Lowercase aggregate used by both desktop and REST search surfaces. */
    public String searchableText() {
        return String.join(" ", commonName, scientificName, type, originLabel(), light,
                moisture, season, uses, fieldNotes, source.organization(), source.title())
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** Public provenance displayed beside every plant profile. */
    public record Source(String organization, String title, String url) {
        public Source {
            organization = requireText(organization, "organization");
            title = requireText(title, "title");
            url = requireText(url, "url");
            if (!url.startsWith("https://")) {
                throw new IllegalArgumentException("source URL must use HTTPS");
            }
        }

        public String label() {
            return organization + " — " + title;
        }

        @Override
        public String toString() {
            return label();
        }
    }
}

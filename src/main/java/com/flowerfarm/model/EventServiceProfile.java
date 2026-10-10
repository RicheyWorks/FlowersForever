package com.flowerfarm.model;

import java.util.Locale;
import java.util.Objects;

/** A sourced service starter for wedding, memorial, and funeral event work. */
public record EventServiceProfile(
        String serviceName,
        String occasion,
        String category,
        String pricingBasis,
        String leadTime,
        String deliverables,
        String intakeQuestions,
        String operationsChecklist,
        String privacyAndSafety,
        Source source) {

    public EventServiceProfile {
        serviceName = requireText(serviceName, "serviceName");
        occasion = requireText(occasion, "occasion");
        category = requireText(category, "category");
        pricingBasis = requireText(pricingBasis, "pricingBasis");
        leadTime = requireText(leadTime, "leadTime");
        deliverables = requireText(deliverables, "deliverables");
        intakeQuestions = requireText(intakeQuestions, "intakeQuestions");
        operationsChecklist = requireText(operationsChecklist, "operationsChecklist");
        privacyAndSafety = requireText(privacyAndSafety, "privacyAndSafety");
        source = Objects.requireNonNull(source, "source");
    }

    /** Lowercase aggregate shared by desktop and REST search surfaces. */
    public String searchableText() {
        return String.join(" ", serviceName, occasion, category, pricingBasis, leadTime,
                deliverables, intakeQuestions, operationsChecklist, privacyAndSafety,
                source.organization(), source.title()).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** Public product or professional source behind the workflow pattern. */
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

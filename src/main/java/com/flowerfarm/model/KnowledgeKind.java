package com.flowerfarm.model;

/** The farm context that prompted a knowledge entry. */
public enum KnowledgeKind {
    PROBLEM("Problem"),
    OPPORTUNITY("Opportunity"),
    PRACTICE("Practice worth repeating");

    private final String label;

    KnowledgeKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

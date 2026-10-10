package com.flowerfarm.model;

/** How strongly a recorded farm result should guide future work. */
public enum KnowledgeConfidence {
    NEEDS_REVIEW("Needs review"),
    EARLY_SIGNAL("Early signal"),
    PROMISING("Promising"),
    PROVEN("Proven on this farm");

    private final String label;

    KnowledgeConfidence(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

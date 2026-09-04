package com.flowerfarm.automation;

/** Risk tier assigned by a harness plugin to each automation action. */
public enum HarnessRisk {
    READ,
    SENSITIVE_READ,
    NAVIGATE,
    DRAFT,
    MUTATE,
    DESTRUCTIVE,
    PHYSICAL
}

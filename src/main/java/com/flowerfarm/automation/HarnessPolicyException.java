package com.flowerfarm.automation;

/** Raised when a command exceeds the operator-enabled harness risk tier. */
public final class HarnessPolicyException extends RuntimeException {
    public HarnessPolicyException(String message) {
        super(message);
    }
}

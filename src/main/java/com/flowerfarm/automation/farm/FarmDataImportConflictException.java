package com.flowerfarm.automation.farm;

/** Raised when one durable upstream identity is reused for different record content. */
public final class FarmDataImportConflictException extends RuntimeException {
    public FarmDataImportConflictException(String message) {
        super(message);
    }
}

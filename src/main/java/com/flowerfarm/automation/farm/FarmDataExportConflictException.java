package com.flowerfarm.automation.farm;

/** Raised when one durable export key is reused for a different snapshot request. */
public final class FarmDataExportConflictException extends RuntimeException {
    public FarmDataExportConflictException(String message) {
        super(message);
    }
}

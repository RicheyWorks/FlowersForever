package com.flowerfarm.service;

/** A safe, actionable conflict detected before a production mutation is applied. */
public class ProductionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductionConflictException(String message) {
        super(message);
    }
}

package com.flowerfarm.service;

/** Signals that a mobile order handoff no longer matches the reviewed load. */
public class WorkerOrderConflictException extends RuntimeException {

    private final long orderId;

    public WorkerOrderConflictException(long orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public long orderId() {
        return orderId;
    }
}

package com.flowerfarm.service;

/** A worker operation key was already committed for another payload or action. */
public class WorkerActionConflictException extends RuntimeException {

    public WorkerActionConflictException(String message) {
        super(message);
    }
}

package com.flowerfarm.service;

/** Signals that field evidence exceeds the application-level attachment limit. */
public class AttachmentTooLargeException extends RuntimeException {

    public AttachmentTooLargeException(String message) {
        super(message);
    }
}

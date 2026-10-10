package com.flowerfarm.update;

/** A manifest, package, or backup failed a safety check. The message is plain language. */
public class UpdateVerificationException extends Exception {

    private static final long serialVersionUID = 1L;

    public UpdateVerificationException(String message) {
        super(message);
    }

    public UpdateVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

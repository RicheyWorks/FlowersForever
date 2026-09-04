package com.flowerfarm.automation;

/** Raised when a harness transport does not provide the configured local token. */
public final class HarnessAuthenticationException extends RuntimeException {
    public HarnessAuthenticationException(String message) {
        super(message);
    }
}

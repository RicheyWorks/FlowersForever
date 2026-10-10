package com.flowerfarm.update;

/** Thrown during startup when the first launch after an update fails its health check. */
public class UpdateHealthCheckFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UpdateHealthCheckFailedException(String reason, Throwable cause) {
        super(reason, cause);
    }
}

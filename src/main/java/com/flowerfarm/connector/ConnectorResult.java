package com.flowerfarm.connector;

import java.time.Instant;

/**
 * Generic result envelope returned by every connector operation.
 *
 * <p>On success  → {@link #isSuccess()} is true, {@link #getPayload()} holds the data.
 * <p>On failure  → {@link #isSuccess()} is false, {@link #getMessage()} and
 *                  {@link #getErrorDetail()} describe what went wrong.
 *
 * <p>Every result carries the {@link #getTimestamp() timestamp} at which it was created.
 *
 * @param <T> payload type (e.g. {@code List<Item>}, {@code Integer}, {@link SyncSummary})
 */
public class ConnectorResult<T> {

    private final boolean success;
    private final T       payload;
    private final String  message;
    private final String  errorDetail;
    private final String  connectorName;
    private final Instant timestamp;

    private ConnectorResult(boolean success, T payload,
                            String message, String errorDetail,
                            String connectorName) {
        this.success       = success;
        this.payload       = payload;
        this.message       = message;
        this.errorDetail   = errorDetail;
        this.connectorName = connectorName;
        this.timestamp     = Instant.now();
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public static <T> ConnectorResult<T> ok(T payload, String message, String connectorName) {
        return new ConnectorResult<>(true, payload, message, null, connectorName);
    }

    /** Convenience success factory when the connector name is not relevant. */
    public static <T> ConnectorResult<T> ok(T payload, String message) {
        return new ConnectorResult<>(true, payload, message, null, null);
    }

    public static <T> ConnectorResult<T> fail(String message, String errorDetail, String connectorName) {
        return new ConnectorResult<>(false, null, message, errorDetail, connectorName);
    }

    /** Convenience failure factory when the connector name is not relevant. */
    public static <T> ConnectorResult<T> fail(String message, String errorDetail) {
        return new ConnectorResult<>(false, null, message, errorDetail, null);
    }

    public static <T> ConnectorResult<T> fail(String message, Exception cause, String connectorName) {
        return new ConnectorResult<>(false, null, message, describe(cause), connectorName);
    }

    /** Convenience failure factory from a throwable — its class name and message become the detail. */
    public static <T> ConnectorResult<T> fail(String message, Throwable cause) {
        return new ConnectorResult<>(false, null, message, describe(cause), null);
    }

    public static <T> ConnectorResult<T> unavailable(String connectorName) {
        return new ConnectorResult<>(false, null,
                "Connector '" + connectorName + "' is not available.",
                "Missing credentials or configuration — set the required properties in "
                        + "application.properties or application-local.properties.",
                connectorName);
    }

    /** Formats a throwable as "SimpleClassName: message" for the error detail. */
    private static String describe(Throwable cause) {
        if (cause == null) {
            return "unknown error";
        }
        return cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isSuccess()        { return success; }
    public T       getPayload()       { return payload; }
    public String  getMessage()       { return message; }
    public String  getErrorDetail()   { return errorDetail; }
    public String  getConnectorName() { return connectorName; }
    public Instant getTimestamp()     { return timestamp; }

    @Override
    public String toString() {
        return String.format("ConnectorResult{connector='%s', success=%b, message='%s'}",
                connectorName, success, message);
    }
}

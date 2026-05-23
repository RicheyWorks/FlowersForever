package com.flowerfarm.connector;

/**
 * Generic result envelope returned by every connector operation.
 *
 * <p>On success  → {@link #isSuccess()} is true, {@link #getPayload()} holds the data.
 * <p>On failure  → {@link #isSuccess()} is false, {@link #getMessage()} and
 *                  {@link #getErrorDetail()} describe what went wrong.
 *
 * @param <T> payload type (e.g. {@code List<Item>}, {@code Integer}, {@link SyncSummary})
 */
public class ConnectorResult<T> {

    private final boolean success;
    private final T       payload;
    private final String  message;
    private final String  errorDetail;
    private final String  connectorName;

    private ConnectorResult(boolean success, T payload,
                            String message, String errorDetail,
                            String connectorName) {
        this.success       = success;
        this.payload       = payload;
        this.message       = message;
        this.errorDetail   = errorDetail;
        this.connectorName = connectorName;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public static <T> ConnectorResult<T> ok(T payload, String message, String connectorName) {
        return new ConnectorResult<>(true, payload, message, null, connectorName);
    }

    public static <T> ConnectorResult<T> fail(String message, String errorDetail, String connectorName) {
        return new ConnectorResult<>(false, null, message, errorDetail, connectorName);
    }

    public static <T> ConnectorResult<T> fail(String message, Exception cause, String connectorName) {
        return new ConnectorResult<>(false, null, message,
                cause != null ? cause.getMessage() : "unknown error", connectorName);
    }

    public static <T> ConnectorResult<T> unavailable(String connectorName) {
        return new ConnectorResult<>(false, null,
                "Connector '" + connectorName + "' is unavailable — missing credentials or config.",
                "Configure the required properties in application.properties or application-local.properties.",
                connectorName);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isSuccess()      { return success; }
    public T       getPayload()     { return payload; }
    public String  getMessage()     { return message; }
    public String  getErrorDetail() { return errorDetail; }
    public String  getConnectorName() { return connectorName; }

    @Override
    public String toString() {
        return String.format("ConnectorResult{connector='%s', success=%b, message='%s'}",
                connectorName, success, message);
    }
}

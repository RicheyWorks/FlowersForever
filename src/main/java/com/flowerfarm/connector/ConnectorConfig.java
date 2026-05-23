package com.flowerfarm.connector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight key-value configuration bag used by every connector.
 *
 * <p>Each connector creates its own {@code ConnectorConfig} in its constructor
 * and calls {@link #set} for each property it needs.  The config is then
 * queried via {@link #get} and {@link #hasAll} to validate availability.
 *
 * <p>This is intentionally not a Spring bean — connectors that need Spring
 * property injection use {@code @Value} on their own constructor parameters
 * and populate a {@code ConnectorConfig} from those values.
 */
public final class ConnectorConfig {

    private final String              connectorName;
    private final Map<String, String> props = new LinkedHashMap<>();

    public ConnectorConfig(String connectorName) {
        this.connectorName = connectorName;
    }

    // ── Fluent builder ────────────────────────────────────────────────────────

    /**
     * Store a value.  {@code null} or blank values are silently ignored so
     * that unconfigured Spring {@code @Value} placeholders don't pollute the map.
     */
    public ConnectorConfig set(String key, String value) {
        if (value != null && !value.isBlank()) {
            props.put(key, value.trim());
        }
        return this;
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /** Returns the stored value, or {@code ""} if not present. */
    public String get(String key) {
        return props.getOrDefault(key, "");
    }

    /** Returns the stored value, or {@code defaultValue} if absent / blank. */
    public String get(String key, String defaultValue) {
        String v = props.get(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    /** Returns {@code true} if the key is present and non-blank. */
    public boolean has(String key) {
        String v = props.get(key);
        return v != null && !v.isBlank();
    }

    /** Returns {@code true} only when every supplied key is present and non-blank. */
    public boolean hasAll(String... keys) {
        for (String k : keys) {
            if (!has(k)) return false;
        }
        return true;
    }

    public String getConnectorName() { return connectorName; }

    @Override
    public String toString() {
        return "ConnectorConfig{connector='" + connectorName + "', keys=" + props.keySet() + "}";
    }
}

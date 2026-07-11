package com.flowerfarm.connector;

/**
 * Optional capability for connectors that support both a local JSON mirror
 * (offline demos / CI) and remote REST credentials.
 */
public interface DualModeCapable {

    /** {@code true} when operating against a configured local mirror file. */
    boolean isLocalMode();

    /** Short mode label for UI / API discovery: {@code local} or {@code rest}. */
    default String operatingMode() {
        return isLocalMode() ? "local" : "rest";
    }
}

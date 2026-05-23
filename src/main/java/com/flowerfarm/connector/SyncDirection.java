package com.flowerfarm.connector;

/**
 * Declares which data-flow directions a connector supports.
 */
public enum SyncDirection {

    /** Connector can only pull data in (e.g. a read-only data source). */
    IMPORT_ONLY,

    /** Connector can only push data out (e.g. a write-only sink). */
    EXPORT_ONLY,

    /** Connector supports both import and full bidirectional sync. */
    BIDIRECTIONAL;

    public boolean canImport() { return this == IMPORT_ONLY  || this == BIDIRECTIONAL; }
    public boolean canExport() { return this == EXPORT_ONLY  || this == BIDIRECTIONAL; }
    public boolean canSync()   { return this == BIDIRECTIONAL; }
}

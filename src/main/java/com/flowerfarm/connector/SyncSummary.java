package com.flowerfarm.connector;

/**
 * Describes the outcome of a bidirectional sync operation.
 * Returned as the payload of {@link ConnectorResult} from
 * {@link ExternalConnector#syncUpdates(java.util.List)}.
 */
public record SyncSummary(
        int created,   // items added to external system (or local inventory)
        int updated,   // items updated in place
        int deleted,   // items removed from external system
        int skipped,   // items identical on both sides — no action taken
        int errors     // items that failed due to validation / API error
) {
    /** Total records examined during the sync pass. */
    public int total() { return created + updated + deleted + skipped + errors; }

    /** True when at least one record changed and no errors occurred. */
    public boolean isClean() { return errors == 0 && (created + updated + deleted) > 0; }

    @Override
    public String toString() {
        return String.format(
                "SyncSummary{total=%d → created=%d, updated=%d, deleted=%d, skipped=%d, errors=%d}",
                total(), created, updated, deleted, skipped, errors);
    }
}

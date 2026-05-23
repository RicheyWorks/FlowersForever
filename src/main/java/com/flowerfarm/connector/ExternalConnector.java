package com.flowerfarm.connector;

import com.flowerfarm.model.Item;

import java.util.List;

/**
 * Contract that every external-system connector must implement.
 *
 * <p>The generic type {@code R} is the raw record type for the external system
 * (e.g. {@code Row} for Excel, {@code Map<String,Object>} for REST APIs,
 * {@code String[]} for CSV). The non-generic lifecycle methods
 * ({@code importItems}, {@code exportItems}, {@code syncUpdates}) always work
 * in terms of {@link Item}, making {@link ConnectorRegistry} type-safe without casts.
 *
 * @param <R> raw record type used by the external system
 */
public interface ExternalConnector<R> {

    /** Unique lower-case identifier used to look up the connector by name. */
    String getName();

    /** Human-readable one-line description shown in the UI and REST discovery. */
    String getDescription();

    /** Which directions this connector supports. */
    SyncDirection getSupportedDirection();

    /**
     * Live availability check.  Should be fast — typically just validates that
     * required credentials / files are present, <em>not</em> that the remote
     * service is reachable.
     */
    boolean isAvailable();

    // ── Data operations ───────────────────────────────────────────────────────

    /**
     * Pull records from the external system and return them as {@link Item}s.
     * Callers are responsible for merging the result into the local inventory.
     */
    ConnectorResult<List<Item>> importItems();

    /**
     * Push the supplied {@link Item} list to the external system.
     *
     * @param items items to export
     * @return result whose payload is the number of records successfully sent
     */
    ConnectorResult<Integer> exportItems(List<Item> items);

    /**
     * Bidirectional reconciliation.  Implementations should compare local vs.
     * remote state and apply the minimum set of changes in both directions.
     *
     * @param localItems current local inventory snapshot
     * @return result whose payload is a {@link SyncSummary} with change counts
     */
    ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems);

    // ── Low-level mapping ─────────────────────────────────────────────────────

    /** Convert a raw external record to an {@link Item}. */
    Item mapToItem(R raw);

    /** Convert an {@link Item} to the raw external record format. */
    R mapFromItem(Item item);
}

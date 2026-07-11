package com.flowerfarm.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Audit record for a connector import / export / sync attempt.
 * Stored in H2 so growers can see what ran last market morning.
 */
@Entity
@Table(name = "sync_history", indexes = {
        @Index(name = "idx_sync_history_occurred", columnList = "occurredAt")
})
public class SyncHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String connectorName;

    /** IMPORT, EXPORT, or SYNC */
    @Column(nullable = false, length = 16)
    private String operation;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false, length = 512)
    private String message;

    @Column(length = 2000)
    private String detail;

    /** Optional record count (imported items, exported rows, sync total). */
    private Integer recordCount;

    @Column(nullable = false)
    private Instant occurredAt;

    protected SyncHistoryEntry() {
    }

    public SyncHistoryEntry(String connectorName, String operation, boolean success,
                            String message, String detail, Integer recordCount) {
        this.connectorName = connectorName == null ? "unknown" : connectorName.trim().toLowerCase();
        this.operation = operation == null ? "UNKNOWN" : operation.trim().toUpperCase();
        this.success = success;
        this.message = message == null ? "" : truncate(message, 512);
        this.detail = detail == null ? "" : truncate(detail, 2000);
        this.recordCount = recordCount;
        this.occurredAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getId() { return id; }
    public String getConnectorName() { return connectorName; }
    public String getOperation() { return operation; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
    public Integer getRecordCount() { return recordCount; }
    public Instant getOccurredAt() { return occurredAt; }
}

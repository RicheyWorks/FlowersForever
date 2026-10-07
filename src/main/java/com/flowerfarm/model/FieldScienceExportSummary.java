package com.flowerfarm.model;

import java.time.Instant;

/** Listing metadata, deliberately excluding the potentially large CLOB body. */
public record FieldScienceExportSummary(
        Long id,
        Instant createdAt,
        String instrumentId,
        String instrumentTitle,
        String sourceUrl,
        String sourceCommit,
        String title,
        String filename,
        String note,
        int byteLength,
        String sha256) {
}

package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** Immutable copy of one plain-text field-science export with pinned provenance. */
@Entity
@Table(name = "field_science_exports")
public class FieldScienceExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "instrument_id", nullable = false, length = 64)
    private String instrumentId;

    @Column(name = "instrument_title", nullable = false, length = 180)
    private String instrumentTitle;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @Column(name = "source_commit", nullable = false, length = 40)
    private String sourceCommit;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 255)
    private String filename;

    @Column(length = 500)
    private String note;

    @Column(name = "byte_length", nullable = false)
    private int byteLength;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Lob
    @Column(nullable = false)
    private String content;

    protected FieldScienceExport() {
    }

    public FieldScienceExport(Instant createdAt, String instrumentId,
                              String instrumentTitle, String sourceUrl,
                              String sourceCommit, String title, String filename,
                              String note, int byteLength, String sha256,
                              String content) {
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.instrumentTitle = Objects.requireNonNull(instrumentTitle, "instrumentTitle");
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
        this.sourceCommit = Objects.requireNonNull(sourceCommit, "sourceCommit");
        this.title = Objects.requireNonNull(title, "title");
        this.filename = filename;
        this.note = note;
        this.byteLength = byteLength;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.content = Objects.requireNonNull(content, "content");
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getInstrumentId() { return instrumentId; }
    public String getInstrumentTitle() { return instrumentTitle; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceCommit() { return sourceCommit; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public String getNote() { return note; }
    public int getByteLength() { return byteLength; }
    public String getSha256() { return sha256; }
    public String getContent() { return content; }
}

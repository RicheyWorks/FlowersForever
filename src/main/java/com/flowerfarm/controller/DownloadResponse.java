package com.flowerfarm.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Shared HTTP attachment policy for REST adapter endpoints. */
final class DownloadResponse {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private DownloadResponse() {
    }

    static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return attachment(body, filename, MediaType.APPLICATION_PDF);
    }

    static ResponseEntity<byte[]> csv(byte[] body, String filename) {
        return attachment(body, filename, CSV);
    }

    private static ResponseEntity<byte[]> attachment(
            byte[] body, String filename, MediaType mediaType) {
        if (body == null) {
            throw new IllegalArgumentException("Download body must not be null.");
        }
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.indexOf('\r') >= 0 || filename.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Download filename is invalid.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}

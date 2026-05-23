package com.flowerfarm.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ConnectorResult")
class ConnectorResultTest {

    // ── ok() ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ok(payload, message) marks success with payload")
    void okWithPayload() {
        ConnectorResult<Integer> result = ConnectorResult.ok(42, "Exported 42 items");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload()).isEqualTo(42);
        assertThat(result.getMessage()).isEqualTo("Exported 42 items");
        assertThat(result.getErrorDetail()).isNull();
        assertThat(result.getConnectorName()).isNull();
    }

    @Test
    @DisplayName("ok(payload, message, name) stores connector name")
    void okWithConnectorName() {
        ConnectorResult<Integer> result = ConnectorResult.ok(5, "Done", "airtable");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConnectorName()).isEqualTo("airtable");
    }

    @Test
    @DisplayName("ok() sets a non-null timestamp")
    void okSetsTimestamp() {
        Instant before = Instant.now();
        ConnectorResult<String> result = ConnectorResult.ok("data", "msg");
        Instant after = Instant.now();

        assertThat(result.getTimestamp()).isBetween(before, after);
    }

    // ── fail() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fail(message, detail) marks failure with no payload")
    void failWithDetail() {
        ConnectorResult<List<?>> result = ConnectorResult.fail("Connection refused", "SocketException: timeout");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getPayload()).isNull();
        assertThat(result.getMessage()).isEqualTo("Connection refused");
        assertThat(result.getErrorDetail()).isEqualTo("SocketException: timeout");
    }

    @Test
    @DisplayName("fail(message, detail, name) stores connector name")
    void failWithConnectorName() {
        ConnectorResult<Integer> result = ConnectorResult.fail("Not found", "", "shopify");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getConnectorName()).isEqualTo("shopify");
    }

    @Test
    @DisplayName("fail(message, Throwable) extracts exception class and message as detail")
    void failFromThrowable() {
        RuntimeException cause = new RuntimeException("disk full");
        ConnectorResult<Integer> result = ConnectorResult.fail("Export failed", cause);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorDetail())
                .contains("RuntimeException")
                .contains("disk full");
    }

    @Test
    @DisplayName("fail(message, Throwable, name) stores connector name")
    void failFromThrowableWithName() {
        ConnectorResult<Integer> result = ConnectorResult.fail("oops", new IllegalStateException("bad"), "csv");
        assertThat(result.getConnectorName()).isEqualTo("csv");
    }

    // ── unavailable() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("unavailable() produces a standardised failure message")
    void unavailableStandardMessage() {
        ConnectorResult<Object> result = ConnectorResult.unavailable("quickbooks");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("quickbooks").contains("not available");
        assertThat(result.getErrorDetail()).contains("credentials");
        assertThat(result.getConnectorName()).isEqualTo("quickbooks");
    }

    // ── toString() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString() includes connector name, success flag, and message")
    void toStringContainsKeyFields() {
        ConnectorResult<Integer> result = ConnectorResult.ok(10, "all good", "excel");
        String s = result.toString();

        assertThat(s).contains("excel").contains("true").contains("all good");
    }
}

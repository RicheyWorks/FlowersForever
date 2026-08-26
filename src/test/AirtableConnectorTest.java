package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AirtableConnectorTest {

    private RestTemplate restTemplate;
    private AirtableConnector connector;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        connector = new AirtableConnector("token", "app-base", "Inventory", "", restTemplate);
    }

    @Test
    void remoteSyncCreatesMissingAndPatchesChangedRecords() {
        stubRemoteRecords(List.of(remoteRecord("rec-1", item("Shared", 4, 3.0))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("records", List.of(Map.of("id", "rec-2"))), HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("records", List.of(Map.of("id", "rec-1"))), HttpStatus.OK));

        ConnectorResult<SyncSummary> result = connector.syncUpdates(List.of(
                item("Shared", 9, 3.0),
                item("New", 2, 5.0)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload().created()).isEqualTo(1);
        assertThat(result.getPayload().updated()).isEqualTo(1);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void repeatedSyncSkipsIdenticalRecordsWithoutWritingDuplicates() {
        Item shared = item("Shared", 4, 3.0);
        stubRemoteRecords(List.of(remoteRecord("rec-1", shared)));

        ConnectorResult<SyncSummary> result = connector.syncUpdates(List.of(shared));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload().skipped()).isEqualTo(1);
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(Map.class));
    }

    private void stubRemoteRecords(List<Map<String, Object>> records) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("records", records), HttpStatus.OK));
    }

    private Map<String, Object> remoteRecord(String id, Item item) {
        return Map.of("id", id, "fields", connector.mapFromItem(item));
    }

    private Item item(String name, int quantity, double price) {
        return new Item(name, "Flowers/Plants", price, "stem", 1.0, quantity, "PNW");
    }
}

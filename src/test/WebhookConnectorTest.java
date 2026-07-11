package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncDirection;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WebhookConnector")
class WebhookConnectorTest {

    @Test
    @DisplayName("export-only metadata")
    void metadata() {
        WebhookConnector c = new WebhookConnector("https://example.com/hook", "secret");
        assertThat(c.getName()).isEqualTo("webhook");
        assertThat(c.getSupportedDirection()).isEqualTo(SyncDirection.EXPORT_ONLY);
        assertThat(c.isAvailable()).isTrue();
        assertThat(c.isLocalMode()).isFalse();
    }

    @Test
    @DisplayName("local dry-run writes payload file")
    void localDryRun() throws Exception {
        Path tmp = Files.createTempFile("webhook-payload", ".json");
        try {
            WebhookConnector local = new WebhookConnector("", "sec", tmp.toString(), mock(RestTemplate.class));
            assertThat(local.isLocalMode()).isTrue();
            assertThat(local.isAvailable()).isTrue();

            List<Item> items = List.of(
                    new Item("Nootka Rose", "Flowers/Plants", 3.0, "stems", 1.0, 12, "hook")
            );
            ConnectorResult<Integer> r = local.exportItems(items);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getPayload()).isEqualTo(1);
            assertThat(r.getMessage()).containsIgnoringCase("local");

            String body = Files.readString(tmp);
            assertThat(body).contains("flowerfarm-manager");
            assertThat(body).contains("Nootka Rose");
            assertThat(body).contains("itemCount");
            assertThat(body).contains("timestamp");

            ConnectorResult<SyncSummary> sync = local.syncUpdates(items);
            assertThat(sync.isSuccess()).isTrue();
            assertThat(sync.getMessage()).containsIgnoringCase("local");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("remote POST sends JSON body")
    @SuppressWarnings("unchecked")
    void remotePost() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        WebhookConnector remote = new WebhookConnector("https://hooks.example/farm", "s3cret", "", rest);
        ConnectorResult<Integer> r = remote.exportItems(List.of(
                new Item("Damask", "Flowers/Plants", 4.0, "stems", 2.0, 5, "")
        ));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getPayload()).isEqualTo(1);
        verify(rest).exchange(contains("hooks.example"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("HMAC helper produces hex digest")
    void hmac() throws Exception {
        String sig = WebhookConnector.hmac("secret", "{\"a\":1}");
        assertThat(sig).hasSize(64);
        assertThat(sig).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("unavailable without url or local-file")
    void unavailable() {
        WebhookConnector empty = new WebhookConnector("", "", "", mock(RestTemplate.class));
        assertThat(empty.isAvailable()).isFalse();
        assertThat(empty.exportItems(List.of()).isSuccess()).isFalse();
    }
}

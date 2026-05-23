package com.flowerfarm.connector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Webhook connector — pushes the full inventory as a JSON POST to a configurable URL.
 *
 * <p>When a {@code connector.webhook.secret} is configured, each request includes
 * an {@code X-FlowerFarm-Signature} header containing an HMAC-SHA256 signature
 * of the request body, allowing the receiving server to verify authenticity.
 *
 * <pre>
 * connector.webhook.url=https://your-server.example.com/inventory-hook
 * connector.webhook.secret=your-shared-secret        (optional)
 * </pre>
 */
@Component
public class WebhookConnector implements ExternalConnector<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(WebhookConnector.class);
    private static final String HMAC_ALG = "HmacSHA256";

    private final ConnectorConfig config;
    private final RestTemplate    restTemplate = new RestTemplate();
    private final ObjectMapper    objectMapper = new ObjectMapper();

    public WebhookConnector(
            @Value("${connector.webhook.url:}")    String url,
            @Value("${connector.webhook.secret:}") String secret
    ) {
        this.config = new ConnectorConfig("webhook")
                .set("url",    url)
                .set("secret", secret);
    }

    @Override public String getName()        { return "webhook"; }
    @Override public String getDescription() { return "HTTP POST webhook — pushes inventory JSON to a configurable URL"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return config.has("url");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());

        String url = config.get("url");

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "source",    "flowerfarm-manager",
                    "itemCount", items.size(),
                    "items",     items
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (config.has("secret")) {
                headers.set("X-FlowerFarm-Signature", "sha256=" + hmac(config.get("secret"), body));
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[webhook] Delivered {} items to {}", items.size(), url);
                return ConnectorResult.ok(items.size(),
                        "Webhook delivered " + items.size() + " items to " + url + ".", getName());
            }

            return ConnectorResult.fail(
                    "Webhook delivery failed.",
                    "HTTP " + response.getStatusCode() + ": " + response.getBody(),
                    getName());

        } catch (Exception e) {
            return ConnectorResult.fail("Webhook delivery error.", e, getName());
        }
    }

    // ── Unsupported import/sync ───────────────────────────────────────────────

    @Override
    public ConnectorResult<List<Item>> importItems() {
        return ConnectorResult.fail("Webhook connector is export-only.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) return ConnectorResult.fail("Webhook sync failed.", r.getErrorDetail(), getName());
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "Webhook sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw)  { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }

    // ── HMAC helper ───────────────────────────────────────────────────────────

    private String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

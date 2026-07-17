package com.flowerfarm.connector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook connector — dual mode:
 * <ul>
 *   <li><b>Local dry-run</b> ({@code connector.webhook.local-file}) — writes the
 *       outbound JSON payload to disk (no network) for demos and CI.</li>
 *   <li><b>Remote POST</b> — pushes inventory JSON to {@code connector.webhook.url}.</li>
 * </ul>
 *
 * <p>Optional HMAC: {@code X-FlowerFarm-Signature: sha256=&lt;hex&gt;} when secret is set.
 *
 * <pre>
 * connector.webhook.local-file=data/webhook-last-payload.json
 * # or live:
 * connector.webhook.url=https://your-server.example.com/inventory-hook
 * connector.webhook.secret=your-shared-secret
 * </pre>
 */
@Component
public class WebhookConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(WebhookConnector.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    public WebhookConnector(
            @Value("${connector.webhook.url:}") String url,
            @Value("${connector.webhook.secret:}") String secret,
            @Value("${connector.webhook.local-file:}") String localFile) {
        this(url, secret, localFile, new RestTemplate());
    }

    /** Package-private for tests. */
    WebhookConnector(String url, String secret, String localFile, RestTemplate restTemplate) {
        this.config = new ConnectorConfig("webhook")
                .set("url", nullToEmpty(url))
                .set("secret", nullToEmpty(secret))
                .set("local-file", nullToEmpty(localFile));
        this.restTemplate = restTemplate;
    }

    /** Backward-compatible test constructor (remote-only). */
    WebhookConnector(String url, String secret) {
        this(url, secret, "", new RestTemplate());
    }

    @Override public String getName() { return "webhook"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Webhook (local dry-run) — writes inventory JSON payload to disk"
                : "HTTP POST webhook — pushes inventory JSON to a configurable URL";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.has("url");
    }

    @Override
    public boolean isLocalMode() {
        return config.has("local-file");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }
        if (items == null) {
            items = List.of();
        }

        try {
            Map<String, Object> payload = buildPayload(items);
            String body = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);

            if (isLocalMode()) {
                return writeLocalPayload(body, items.size());
            }
            return postRemote(body, items.size());

        } catch (Exception e) {
            return ConnectorResult.fail("Webhook delivery error.", e, getName());
        }
    }

    private Map<String, Object> buildPayload(List<Item> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "flowerfarm-manager");
        payload.put("timestamp", Instant.now().toString());
        payload.put("itemCount", items.size());
        payload.put("items", items.stream().map(this::mapFromItem).toList());
        return payload;
    }

    private ConnectorResult<Integer> writeLocalPayload(String body, int count) throws Exception {
        Path path = Path.of(config.get("local-file"));
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, body, StandardCharsets.UTF_8);
        String msg = "Webhook local dry-run — wrote " + count + " item(s) to " + path.getFileName() + ".";
        log.info("[webhook] {}", msg);
        return ConnectorResult.ok(count, msg, getName());
    }

    private ConnectorResult<Integer> postRemote(String body, int count) {
        String url = config.get("url");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-FlowerFarm-Source", "flowerfarm-manager");
            headers.set("X-FlowerFarm-Item-Count", String.valueOf(count));

            if (config.has("secret")) {
                headers.set("X-FlowerFarm-Signature", "sha256=" + hmac(config.get("secret"), body));
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String msg = "Webhook delivered " + count + " item(s) to " + url
                        + " (HTTP " + response.getStatusCode().value() + ").";
                log.info("[webhook] {}", msg);
                return ConnectorResult.ok(count, msg, getName());
            }

            return ConnectorResult.fail(
                    "Webhook delivery failed.",
                    "HTTP " + response.getStatusCode() + ": " + response.getBody(),
                    getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Webhook delivery failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Webhook delivery error.", e, getName());
        }
    }

    // ── Unsupported import / sync-as-export ───────────────────────────────────

    @Override
    public ConnectorResult<List<Item>> importItems() {
        return ConnectorResult.fail("Webhook connector is export-only.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("Webhook sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        String mode = isLocalMode() ? " (local dry-run)" : " (POST)";
        return ConnectorResult.ok(
                new SyncSummary(count, 0, 0, 0, 0),
                "Webhook sync complete — delivered " + count + " item(s)." + mode,
                getName());
    }

    @Override
    public Item mapToItem(Map<String, Object> raw) {
        return null;
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        if (item == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", item.getName());
        m.put("category", item.getCategory());
        m.put("price", item.getPrice());
        m.put("unit", item.getUnit());
        m.put("cost", item.getCost());
        m.put("quantity", item.getQuantity());
        m.put("notes", item.getNotes() == null ? "" : item.getNotes());
        if (item.getId() != null) {
            m.put("id", item.getId());
        }
        return m;
    }

    // ── HMAC ──────────────────────────────────────────────────────────────────

    static String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}

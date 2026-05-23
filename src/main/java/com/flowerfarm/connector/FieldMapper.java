package com.flowerfarm.connector;

import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Configurable, chainable field-mapping engine used by every connector.
 *
 * <p>Each connector instantiates its own {@code FieldMapper} in its constructor
 * and registers named transformers for each field direction:
 *
 * <ul>
 *   <li><b>Outbound</b> — {@link Item} → external system field (used in export)
 *   <li><b>Inbound</b>  — external map → Item field value (used in import)
 * </ul>
 *
 * Example (Shopify product ↔ Item):
 * <pre>{@code
 * FieldMapper mapper = new FieldMapper()
 *   .registerOutbound("title",              item -> item.getName())
 *   .registerOutbound("vendor",             item -> item.getCategory())
 *   .registerOutbound("variants[0].price",  item -> String.format("%.2f", item.getPrice()))
 *   .registerInbound ("name",    raw -> raw.get("title"))
 *   .registerInbound ("category",raw -> raw.getOrDefault("product_type", "Other"))
 *   .registerInbound ("price",   raw -> raw.getOrDefault("price", "0.00"));
 * }</pre>
 *
 * <p>All mapping failures are logged as warnings and produce a safe default
 * rather than propagating an exception.
 */
public final class FieldMapper {

    private static final Logger log = LoggerFactory.getLogger(FieldMapper.class);

    // Item  →  (external field name → value)
    private final Map<String, Function<Item, Object>> outbound = new LinkedHashMap<>();

    // (Item field name → extraction function that reads from external map)
    private final Map<String, Function<Map<String, Object>, Object>> inbound = new LinkedHashMap<>();

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Register a transform that converts an {@link Item} to a named external field.
     *
     * @param externalField the field name used by the external system
     * @param extractor     function that extracts the value from an {@link Item}
     */
    public FieldMapper registerOutbound(String externalField, Function<Item, Object> extractor) {
        outbound.put(externalField, extractor);
        return this;
    }

    /**
     * Register a transform that extracts a named {@link Item} field from
     * a raw external record (represented as a flat {@code Map<String,Object>}).
     *
     * @param itemField  the Item property name ("name", "price", "quantity", …)
     * @param extractor  function that reads the value from the raw map
     */
    public FieldMapper registerInbound(String itemField,
                                       Function<Map<String, Object>, Object> extractor) {
        inbound.put(itemField, extractor);
        return this;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    /**
     * Convert an {@link Item} to a flat external field map (used on export).
     * Missing or erroring fields produce a {@code null} entry — callers
     * are responsible for omitting nulls if the external API requires it.
     */
    public Map<String, Object> toExternalMap(Item item) {
        Map<String, Object> result = new LinkedHashMap<>();
        outbound.forEach((field, fn) -> {
            try {
                result.put(field, fn.apply(item));
            } catch (Exception e) {
                log.warn("[FieldMapper] Outbound mapping failed for field '{}': {}", field, e.getMessage());
                result.put(field, null);
            }
        });
        return result;
    }

    /**
     * Extract Item-field values from a raw external map (used on import).
     * Returns a map of Item-field-name → extracted value; missing entries
     * fall back to safe defaults in {@link #buildItem(Map)}.
     */
    public Map<String, Object> fromExternalMap(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        inbound.forEach((field, fn) -> {
            try {
                result.put(field, fn.apply(raw));
            } catch (Exception e) {
                log.warn("[FieldMapper] Inbound mapping failed for field '{}': {}", field, e.getMessage());
                result.put(field, null);
            }
        });
        return result;
    }

    /**
     * Convenience: build an {@link Item} directly from a raw external map using
     * the registered inbound mappings. Applies safe defaults for any missing field.
     */
    public Item buildItem(Map<String, Object> raw) {
        Map<String, Object> fields = fromExternalMap(raw);
        return new Item(
                safeString(fields, "name",     "Unknown Item"),
                safeString(fields, "category", "Other"),
                safeDouble(fields, "price",    0.0),
                safeString(fields, "unit",     "Per Unit"),
                safeDouble(fields, "cost",     0.0),
                safeInt   (fields, "quantity", 0),
                safeString(fields, "notes",    "")
        );
    }

    /** Returns registered outbound field names (useful for CSV header generation). */
    public Set<String> outboundFields() {
        return Collections.unmodifiableSet(outbound.keySet());
    }

    // ── Safe type coercion helpers ────────────────────────────────────────────

    private String safeString(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString().trim() : def;
    }

    private double safeDouble(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.toString().replaceAll("[^\\d.]", "")); }
        catch (NumberFormatException e) { return def; }
    }

    private int safeInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return (int) Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return def; }
    }
}

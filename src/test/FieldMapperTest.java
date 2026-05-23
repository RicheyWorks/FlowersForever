package com.flowerfarm.connector;

import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FieldMapper")
class FieldMapperTest {

    private FieldMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FieldMapper()
                // outbound: Item → external
                .registerOutbound("title",    item -> item.getName())
                .registerOutbound("vendor",   item -> item.getCategory())
                .registerOutbound("price",    item -> String.format("%.2f", item.getPrice()))
                .registerOutbound("qty",      item -> item.getQuantity())
                // inbound: external map → Item field
                .registerInbound("name",      raw -> raw.get("title"))
                .registerInbound("category",  raw -> raw.getOrDefault("vendor", "Other"))
                .registerInbound("price",     raw -> raw.get("price"))
                .registerInbound("unit",      raw -> raw.getOrDefault("unit", "Per Unit"))
                .registerInbound("cost",      raw -> raw.get("cost"))
                .registerInbound("quantity",  raw -> raw.get("qty"))
                .registerInbound("notes",     raw -> raw.getOrDefault("notes", ""));
    }

    // ── toExternalMap ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toExternalMap()")
    class OutboundTests {

        @Test
        @DisplayName("maps all registered outbound fields")
        void mapsAllFields() {
            Item item = new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "PNW");
            Map<String, Object> ext = mapper.toExternalMap(item);

            assertThat(ext).containsEntry("title",  "Nootka Rose")
                           .containsEntry("vendor", "Flowers/Plants")
                           .containsEntry("price",  "3.50")
                           .containsEntry("qty",     50);
        }

        @Test
        @DisplayName("only registers fields explicitly added — unmapped fields absent")
        void onlyRegisteredFieldsPresent() {
            Item item = new Item("Rose", "Other", 1.00, "Per Unit", 0.50, 5, "");
            Map<String, Object> ext = mapper.toExternalMap(item);

            assertThat(ext).doesNotContainKey("notes")
                           .doesNotContainKey("cost");
        }

        @Test
        @DisplayName("extractor throwing stores null for that field, does not propagate")
        void gracefullyHandlesExtractorFailure() {
            FieldMapper badMapper = new FieldMapper()
                    .registerOutbound("boom", item -> { throw new RuntimeException("extractor crash"); });

            Item item = new Item("Rose", "Other", 1.00, "Per Unit", 0.50, 5, "");
            Map<String, Object> ext = badMapper.toExternalMap(item);

            assertThat(ext).containsEntry("boom", null);
        }
    }

    // ── fromExternalMap ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromExternalMap()")
    class InboundTests {

        @Test
        @DisplayName("maps all registered inbound fields")
        void mapsAllFields() {
            Map<String, Object> raw = Map.of(
                    "title",  "Rosa rugosa",
                    "vendor", "Shrubs",
                    "price",  "2.50",
                    "qty",    "30"
            );
            Map<String, Object> fields = mapper.fromExternalMap(raw);

            assertThat(fields).containsEntry("name",     "Rosa rugosa")
                              .containsEntry("category", "Shrubs")
                              .containsEntry("price",    "2.50")
                              .containsEntry("quantity", "30");
        }

        @Test
        @DisplayName("extractor throwing stores null for that field, does not propagate")
        void gracefullyHandlesExtractorFailure() {
            FieldMapper badMapper = new FieldMapper()
                    .registerInbound("name", raw -> { throw new RuntimeException("crash"); });

            Map<String, Object> fields = badMapper.fromExternalMap(Map.of());
            assertThat(fields).containsEntry("name", null);
        }
    }

    // ── buildItem ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildItem()")
    class BuildItemTests {

        @Test
        @DisplayName("builds a valid Item from a well-formed external map")
        void buildsItemFromExternalMap() {
            Map<String, Object> raw = Map.of(
                    "title",  "Bourbon Rose",
                    "vendor", "Flowers/Plants",
                    "price",  "4.00",
                    "unit",   "Per Stem",
                    "cost",   "2.00",
                    "qty",    "25",
                    "notes",  "Fragrant"
            );
            Item item = mapper.buildItem(raw);

            assertThat(item.getName()).isEqualTo("Bourbon Rose");
            assertThat(item.getCategory()).isEqualTo("Flowers/Plants");
            assertThat(item.getPrice()).isEqualTo(4.00);
            assertThat(item.getUnit()).isEqualTo("Per Stem");
            assertThat(item.getCost()).isEqualTo(2.00);
            assertThat(item.getQuantity()).isEqualTo(25);
            assertThat(item.getNotes()).isEqualTo("Fragrant");
        }

        @Test
        @DisplayName("falls back to safe defaults for missing fields")
        void fallbackDefaults() {
            // Only title is present — all other fields fall through to defaults
            Map<String, Object> raw = Map.of("title", "Mystery Rose");
            Item item = mapper.buildItem(raw);

            assertThat(item.getName()).isEqualTo("Mystery Rose");
            assertThat(item.getCategory()).isEqualTo("Other");
            assertThat(item.getPrice()).isZero();
            assertThat(item.getUnit()).isEqualTo("Per Unit");
            assertThat(item.getCost()).isZero();
            assertThat(item.getQuantity()).isZero();
            assertThat(item.getNotes()).isEmpty();
        }

        @Test
        @DisplayName("falls back to 'Unknown Item' when name mapping returns null")
        void fallbackNameWhenMissing() {
            FieldMapper nameMapper = new FieldMapper()
                    .registerInbound("name", raw -> null);

            Item item = nameMapper.buildItem(Map.of());
            assertThat(item.getName()).isEqualTo("Unknown Item");
        }

        @Test
        @DisplayName("strips non-numeric characters from price string (e.g. '$4.50')")
        void stripsNonNumericFromPrice() {
            Map<String, Object> raw = Map.of("title", "Rose", "price", "$4.50", "qty", "10");
            Item item = mapper.buildItem(raw);
            assertThat(item.getPrice()).isEqualTo(4.50);
        }
    }

    // ── outboundFields() ────────────────────────────────────────────────────

    @Test
    @DisplayName("outboundFields() returns registered field names as an unmodifiable set")
    void outboundFieldsReturnsRegisteredNames() {
        assertThat(mapper.outboundFields())
                .containsExactlyInAnyOrder("title", "vendor", "price", "qty");
    }
}

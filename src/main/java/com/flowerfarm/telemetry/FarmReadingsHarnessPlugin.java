package com.flowerfarm.telemetry;

import com.flowerfarm.automation.HarnessActionEffect;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Transport-independent readings store exposed through the existing protected HTTP/MCP harness. */
@Component
@ConditionalOnProperty(name = "flowerfarm.readings.enabled", havingValue = "true")
public class FarmReadingsHarnessPlugin implements HarnessPlugin {

    public static final String ID = "farm-readings";
    private static final Map<String, Object> READING_SCHEMA = Map.of(
            "type", "object", "additionalProperties", false,
            "required", List.of("readingKey", "deviceId", "metric", "unit", "observedAt"),
            "properties", Map.ofEntries(
                    Map.entry("readingKey", field("string", "Stable per-source sample/channel key; reuse for retries.")),
                    Map.entry("deviceId", field("string", "Adapter-reported device identity.")),
                    Map.entry("metric", field("string", "Explicit metric, such as air.temperature or soil.moisture.")),
                    Map.entry("unit", field("string", "Reported unit; never silently converted.")),
                    Map.entry("value", field("number", "Finite numeric reading, omitted for MISSING.")),
                    Map.entry("quality", Map.of("type", "string", "enum",
                            List.of("GOOD", "SUSPECT", "MISSING", "ERROR"))),
                    Map.entry("observedAt", field("string", "ISO-8601 measurement instant with offset.")),
                    Map.entry("location", field("string", "Optional farm zone or growing area.")),
                    Map.entry("rawFrame", field("string", "Optional original text frame, up to 8192 UTF-8 bytes."))));
    private static final HarnessPluginDescriptor DESCRIPTOR = new HarnessPluginDescriptor(
            ID, "Farm readings", "Receive adapter-reported measurements and read persistent history. "
                    + "No equipment commands or built-in Bluetooth pairing.", "1.0",
            HarnessPluginKind.WORKFLOW,
            Set.of(HarnessPluginCapability.OBSERVATION, HarnessPluginCapability.COMMANDS),
            List.of(
                    new HarnessActionSpec("get-reading-contract", "Read the versioned adapter contract.",
                            HarnessRisk.READ, List.of()),
                    new HarnessActionSpec("record-readings", "Append 1-500 readings; exact source/key retries do not duplicate data.",
                            HarnessRisk.MUTATE, HarnessActionEffect.PERSISTENT_DATA, true, false,
                            List.of(sourceArgument(), new HarnessArgumentSpec("readings",
                                    HarnessValueType.ARRAY, "Reported measurements.", true,
                                    HarnessValueType.OBJECT, READING_SCHEMA))),
                    new HarnessActionSpec("list-readings", "Read up to 200 persisted readings in receipt order.",
                            HarnessRisk.SENSITIVE_READ, List.of(sourceArgument(),
                                    new HarnessArgumentSpec("after-id", HarnessValueType.INTEGER,
                                            "Exclusive receipt cursor; starts at zero.", false),
                                    new HarnessArgumentSpec("limit", HarnessValueType.INTEGER,
                                            "Page size from 1 to 200; defaults to 100.", false)))));

    private final FarmReadingService service;

    public FarmReadingsHarnessPlugin(FarmReadingService service) {
        this.service = service;
    }

    @Override public HarnessPluginDescriptor descriptor() { return DESCRIPTOR; }

    @Override public HarnessSnapshot observe() {
        return new HarnessSnapshot(ID, Instant.now(), List.of("Farm readings"), List.of(),
                Map.of("reading-count", service.count(), "equipment-control", false));
    }

    @Override public HarnessExecution execute(HarnessCommand command) {
        return switch (command.action()) {
            case "get-reading-contract" -> HarnessExecution.success("Read farm readings contract",
                    Map.of("version", "1.0", "readingSchema", READING_SCHEMA,
                            "maxBatch", FarmReadingService.MAX_BATCH,
                            "maxPage", FarmReadingService.MAX_PAGE,
                            "equipmentControl", false));
            case "record-readings" -> {
                Object rows = command.arguments().get("readings");
                if (!(rows instanceof Collection<?> readings)) {
                    throw new IllegalArgumentException("Readings must be an array.");
                }
                yield HarnessExecution.success("Stored adapter-reported readings",
                        Map.of("receipt", service.ingest(command.text("source-id"), readings)));
            }
            case "list-readings" -> HarnessExecution.success("Read persisted farm readings",
                    Map.of("page", service.history(command.text("source-id"),
                            number(command, "after-id", 0), pageLimit(command))));
            default -> throw new IllegalArgumentException("Unsupported farm readings action.");
        };
    }

    private static HarnessArgumentSpec sourceArgument() {
        return new HarnessArgumentSpec("source-id", HarnessValueType.STRING,
                "Stable adapter source identifier; an attribution label, not a device credential.", true);
    }

    private static Map<String, Object> field(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static long number(HarnessCommand command, String field, long fallback) {
        Object value = command.arguments().get(field);
        if (value == null) return fallback;
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Reading cursor and limit must be exact integers.");
        }
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Reading cursor and limit must be exact integers.");
        }
    }

    private static int pageLimit(HarnessCommand command) {
        long limit = number(command, "limit", 100);
        if (limit < 1 || limit > FarmReadingService.MAX_PAGE) {
            throw new IllegalArgumentException("Reading page limit must be from 1 to 200.");
        }
        return (int) limit;
    }
}

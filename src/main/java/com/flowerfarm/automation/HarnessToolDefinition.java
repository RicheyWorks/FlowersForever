package com.flowerfarm.automation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral function/tool definition derived from a plugin action. */
public record HarnessToolDefinition(String name, String pluginId, String action,
                                    String description, HarnessRisk risk, boolean allowed,
                                    HarnessToolAnnotations annotations,
                                    Map<String, Object> inputSchema) {

    /** Backward-compatible constructor for adapters that predate tool annotations. */
    public HarnessToolDefinition(String name, String pluginId, String action,
                                 String description, HarnessRisk risk, boolean allowed,
                                 Map<String, Object> inputSchema) {
        this(name, pluginId, action, description, risk, allowed,
                conservativeAnnotations(risk), inputSchema);
    }

    public HarnessToolDefinition {
        name = required(name, "tool name");
        pluginId = required(pluginId, "plugin id");
        action = required(action, "action");
        description = required(description, "tool description");
        risk = Objects.requireNonNull(risk, "risk");
        annotations = Objects.requireNonNull(annotations, "annotations");
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }

    public static HarnessToolDefinition from(
            HarnessPluginDescriptor plugin, HarnessActionSpec action, boolean allowed) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (HarnessArgumentSpec argument : action.arguments()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", argument.type().wireName());
            property.put("description", argument.description());
            if (argument.itemType() != null) {
                Map<String, Object> items = new LinkedHashMap<>();
                items.put("type", argument.itemType().wireName());
                items.putAll(argument.itemSchema());
                property.put("items", Map.copyOf(items));
            }
            properties.put(argument.name(), Map.copyOf(property));
        }
        List<String> required = action.arguments().stream()
                .filter(HarnessArgumentSpec::required)
                .map(HarnessArgumentSpec::name)
                .toList();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return new HarnessToolDefinition(
                plugin.id() + "__" + action.name(), plugin.id(), action.name(),
                action.description(), action.risk(), allowed,
                HarnessToolAnnotations.from(action), schema);
    }

    private static HarnessToolAnnotations conservativeAnnotations(HarnessRisk risk) {
        HarnessRisk declared = Objects.requireNonNull(risk, "risk");
        boolean physical = declared == HarnessRisk.PHYSICAL;
        return new HarnessToolAnnotations(
                declared == HarnessRisk.READ || declared == HarnessRisk.SENSITIVE_READ,
                declared == HarnessRisk.DESTRUCTIVE || physical,
                false, physical, physical, physical);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}

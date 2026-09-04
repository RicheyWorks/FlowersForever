package com.flowerfarm.automation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Discoverable JSON argument accepted by one harness action. */
public record HarnessArgumentSpec(String name, HarnessValueType type,
                                  String description, boolean required,
                                  @JsonInclude(JsonInclude.Include.NON_NULL)
                                  HarnessValueType itemType,
                                  @JsonInclude(JsonInclude.Include.NON_EMPTY)
                                  Map<String, Object> itemSchema) {

    /** Backward-compatible constructor for scalar or unconstrained collection arguments. */
    public HarnessArgumentSpec(String name, HarnessValueType type,
                               String description, boolean required) {
        this(name, type, description, required, null, Map.of());
    }

    /** Backward-compatible constructor for typed arrays without a detailed item schema. */
    public HarnessArgumentSpec(String name, HarnessValueType type,
                               String description, boolean required,
                               HarnessValueType itemType) {
        this(name, type, description, required, itemType, Map.of());
    }

    public HarnessArgumentSpec {
        name = requireText(name, "argument name").toLowerCase(Locale.ROOT);
        type = Objects.requireNonNull(type, "type");
        description = requireText(description, "argument description");
        if (itemType != null && type != HarnessValueType.ARRAY) {
            throw new IllegalArgumentException(
                    "itemType is supported only for array arguments");
        }
        itemSchema = itemSchema == null ? Map.of() : Map.copyOf(itemSchema);
        if (!itemSchema.isEmpty() && type != HarnessValueType.ARRAY) {
            throw new IllegalArgumentException(
                    "itemSchema is supported only for array arguments");
        }
        if (!itemSchema.isEmpty() && itemType == null) {
            throw new IllegalArgumentException(
                    "itemSchema requires an array itemType");
        }
    }

    public void validate(Object value, String actionName) {
        if (!type.accepts(value)) {
            throw new IllegalArgumentException("Action '" + actionName + "' argument '"
                    + name + "' must be a JSON " + type.wireName());
        }
        if (type == HarnessValueType.ARRAY && itemType != null) {
            Object[] items = arrayItems(value);
            for (int index = 0; index < items.length; index++) {
                Object item = items[index];
                if (!itemType.accepts(item)) {
                    throw new IllegalArgumentException("Action '" + actionName
                            + "' argument '" + name + "' items must be JSON "
                            + itemType.wireName() + " values");
                }
                validateSchema(item, itemSchema,
                        "Action '" + actionName + "' argument '" + name + "[" + index + "]'");
            }
        }
    }

    /** Enforces the same nested canonical shape advertised to tool clients. */
    private static void validateSchema(Object value, Map<?, ?> schema, String path) {
        if (schema.get("type") instanceof String expected) {
            HarnessValueType expectedType = java.util.Arrays.stream(HarnessValueType.values())
                    .filter(candidate -> candidate.wireName().equals(expected))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Unsupported harness schema type '" + expected + "'"));
            if (!expectedType.accepts(value)) {
                throw new IllegalArgumentException(path + " must be a JSON " + expected);
            }
        }
        if (schema.get("enum") instanceof Collection<?> choices && !choices.contains(value)) {
            throw new IllegalArgumentException(path + " must be one of " + choices);
        }
        if (value instanceof Map<?, ?> object) {
            Map<?, ?> fields = schema.get("properties") instanceof Map<?, ?> properties
                    ? properties : Map.of();
            if (schema.get("required") instanceof Collection<?> required) {
                for (Object field : required) {
                    if (!object.containsKey(field) || object.get(field) == null) {
                        throw new IllegalArgumentException(path + " requires field '" + field + "'");
                    }
                }
            }
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                Object fieldSchema = fields.get(entry.getKey());
                if (fieldSchema instanceof Map<?, ?> nested) {
                    validateSchema(entry.getValue(), nested, path + "." + entry.getKey());
                } else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                    throw new IllegalArgumentException(path
                            + " does not accept field '" + entry.getKey() + "'");
                }
            }
        }
        if (HarnessValueType.ARRAY.accepts(value)
                && schema.get("items") instanceof Map<?, ?> itemDefinition) {
            Object[] items = arrayItems(value);
            for (int index = 0; index < items.length; index++) {
                validateSchema(items[index], itemDefinition, path + "[" + index + "]");
            }
        }
    }

    private static Object[] arrayItems(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.toArray();
        }
        Object[] items = new Object[Array.getLength(value)];
        for (int index = 0; index < items.length; index++) {
            items[index] = Array.get(value, index);
        }
        return items;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}

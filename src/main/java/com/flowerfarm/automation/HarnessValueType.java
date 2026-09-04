package com.flowerfarm.automation;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collection;
import java.util.Map;

/** JSON-compatible value types published through harness discovery. */
public enum HarnessValueType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object");

    private final String wireName;

    HarnessValueType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    public boolean accepts(Object value) {
        return switch (this) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long
                    || value instanceof java.math.BigInteger;
            case NUMBER -> value instanceof Number
                    && (!(value instanceof Double number) || Double.isFinite(number))
                    && (!(value instanceof Float number) || Float.isFinite(number));
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof Collection<?> || value != null
                    && value.getClass().isArray();
            case OBJECT -> value instanceof Map<?, ?>;
        };
    }
}

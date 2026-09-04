package com.flowerfarm.automation;

import java.time.Instant;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Idempotent command submitted by an AI tool or another automation transport. */
public record HarnessCommand(String requestId, String action,
                             Map<String, Object> arguments, Instant expiresAt) {

    /** Backward-compatible constructor for non-physical commands. */
    public HarnessCommand(String requestId, String action, Map<String, Object> arguments) {
        this(requestId, action, arguments, null);
    }

    public HarnessCommand {
        requestId = required(requestId, "requestId");
        if (requestId.length() > 160) {
            throw new IllegalArgumentException("requestId must contain at most 160 characters");
        }
        action = required(action, "action").toLowerCase(Locale.ROOT);
        arguments = arguments == null ? Map.of()
                : immutableArguments(arguments, new IdentityHashMap<>());
    }

    public String text(String name) {
        Object value = arguments.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing command argument '" + name + "'");
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Command argument '" + name + "' is blank");
        }
        return text;
    }

    public int integer(String name) {
        Object value = arguments.get(name);
        return exactInteger(value, name);
    }

    public List<Integer> integers(String name) {
        Object value = arguments.get(name);
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException(
                    "Command argument '" + name + "' must be an array of integers");
        }
        List<Integer> integers = new ArrayList<>();
        for (Object item : collection) {
            integers.add(exactInteger(item, name));
        }
        return List.copyOf(integers);
    }

    public List<String> texts(String name) {
        Object value = arguments.get(name);
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException(
                    "Command argument '" + name + "' must be an array of strings");
        }
        List<String> texts = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Command argument '" + name + "' must be an array of non-blank strings");
            }
            texts.add(text.trim());
        }
        return List.copyOf(texts);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static int exactInteger(Object value, String name) {
        try {
            if (value instanceof BigInteger integer) {
                return integer.intValueExact();
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long) {
                return Math.toIntExact(((Number) value).longValue());
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Command argument '" + name
                    + "' must be an integer in the 32-bit signed range", exception);
        }
        throw new IllegalArgumentException(
                "Command argument '" + name + "' must be a JSON integer");
    }

    private static Map<String, Object> immutableArguments(
            Map<?, ?> source, IdentityHashMap<Object, Boolean> visiting) {
        enterContainer(source, visiting);
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Command object keys must be strings");
                }
                copy.put(key, immutableValue(entry.getValue(), visiting));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Object immutableValue(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value instanceof Map<?, ?> map) {
            return immutableArguments(map, visiting);
        }
        if (value instanceof Collection<?> || value != null && value.getClass().isArray()) {
            enterContainer(value, visiting);
            try {
                List<Object> copy = new ArrayList<>();
                if (value instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        copy.add(immutableValue(item, visiting));
                    }
                } else {
                    for (int index = 0; index < Array.getLength(value); index++) {
                        copy.add(immutableValue(Array.get(value, index), visiting));
                    }
                }
                return Collections.unmodifiableList(copy);
            } finally {
                visiting.remove(value);
            }
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || HarnessValueType.INTEGER.accepts(value) || value instanceof java.math.BigDecimal
                || value instanceof Double && Double.isFinite((Double) value)
                || value instanceof Float && Float.isFinite((Float) value)) {
            return value;
        }
        throw new IllegalArgumentException("Command arguments must contain JSON values");
    }

    private static void enterContainer(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.size() >= 100 || visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Command arguments are cyclic or nested too deeply");
        }
    }
}

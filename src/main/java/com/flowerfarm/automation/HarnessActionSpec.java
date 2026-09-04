package com.flowerfarm.automation;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Discoverable action contract published by one harness plugin. */
public record HarnessActionSpec(String name, String description, HarnessRisk risk,
                                HarnessActionEffect effect, boolean idempotent,
                                boolean requiresFreshness,
                                List<HarnessArgumentSpec> arguments) {

    /** Additive constructor used by plugins that do not need custom effect hints. */
    public HarnessActionSpec(String name, String description, HarnessRisk risk,
                             List<HarnessArgumentSpec> arguments) {
        this(name, description, risk, HarnessActionEffect.defaultFor(risk),
                defaultIdempotent(risk), risk == HarnessRisk.PHYSICAL, arguments);
    }

    public HarnessActionSpec {
        name = slug(name, "action name");
        description = requireText(description, "action description");
        risk = Objects.requireNonNull(risk, "risk");
        effect = Objects.requireNonNull(effect, "effect");
        if ((risk == HarnessRisk.PHYSICAL)
                != (effect == HarnessActionEffect.PHYSICAL_WORLD)) {
            throw new IllegalArgumentException("Physical-world actions must use "
                    + "the PHYSICAL risk tier, and PHYSICAL actions must declare "
                    + "the PHYSICAL_WORLD effect");
        }
        if (risk == HarnessRisk.PHYSICAL && !requiresFreshness) {
            throw new IllegalArgumentException(
                    "Physical-world actions must require a fresh command deadline");
        }
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        Set<String> names = new HashSet<>();
        for (HarnessArgumentSpec argument : arguments) {
            if (!names.add(argument.name())) {
                throw new IllegalArgumentException("Duplicate harness argument '"
                        + argument.name() + "' in action '" + name + "'");
            }
        }
    }

    /** Convenience constructor for argument-free and older test plugins. */
    public HarnessActionSpec(String name, String description, HarnessRisk risk,
                             Set<String> requiredArguments) {
        this(name, description, risk, requiredArguments == null ? List.of()
                : requiredArguments.stream().sorted().map(argument ->
                        new HarnessArgumentSpec(argument, HarnessValueType.STRING,
                                "Required action argument.", true)).toList());
    }

    private static boolean defaultIdempotent(HarnessRisk risk) {
        Objects.requireNonNull(risk, "risk");
        return switch (risk) {
            case READ, SENSITIVE_READ, NAVIGATE, DRAFT -> true;
            case MUTATE, DESTRUCTIVE, PHYSICAL -> false;
        };
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String slug(String value, String label) {
        String normalized = requireText(value, label).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,29}")) {
            throw new IllegalArgumentException(label
                    + " must be a 1-30 character lowercase letter, number, or hyphen slug");
        }
        return normalized;
    }
}

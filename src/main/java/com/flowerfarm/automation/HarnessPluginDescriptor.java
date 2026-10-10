package com.flowerfarm.automation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Provider-neutral discovery document for an automation plugin. */
public record HarnessPluginDescriptor(String id, String name, String description,
                                      String version, HarnessPluginKind kind,
                                      Set<HarnessPluginCapability> capabilities,
                                      List<HarnessObservationSpec> observations,
                                      List<HarnessActionSpec> actions) {

    /** Backward-compatible application-plugin constructor. */
    public HarnessPluginDescriptor(String id, String name, String description,
                                   String version, List<HarnessActionSpec> actions) {
        this(id, name, description, version, HarnessPluginKind.APPLICATION,
                Set.of(HarnessPluginCapability.OBSERVATION,
                        HarnessPluginCapability.COMMANDS),
                List.of(), actions);
    }

    /** Convenience constructor for a typed plugin without observation signals. */
    public HarnessPluginDescriptor(String id, String name, String description,
                                   String version, HarnessPluginKind kind,
                                   Set<HarnessPluginCapability> capabilities,
                                   List<HarnessActionSpec> actions) {
        this(id, name, description, version, kind, capabilities, List.of(), actions);
    }

    public HarnessPluginDescriptor {
        id = slug(id, "plugin id");
        name = required(name, "plugin name");
        description = required(description, "plugin description");
        version = required(version, "plugin version");
        kind = kind == null ? HarnessPluginKind.APPLICATION : kind;
        capabilities = capabilities == null || capabilities.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        observations = observations == null ? List.of() : List.copyOf(observations);
        actions = actions == null ? List.of() : List.copyOf(actions);
        Set<String> names = new HashSet<>();
        for (HarnessActionSpec action : actions) {
            if (!names.add(action.name())) {
                throw new IllegalArgumentException(
                        "Duplicate harness action '" + action.name() + "' in " + id);
            }
        }
        names.clear();
        for (HarnessObservationSpec observation : observations) {
            if (!names.add(observation.name())) {
                throw new IllegalArgumentException("Duplicate harness observation '"
                        + observation.name() + "' in " + id);
            }
        }
        boolean physicalAction = actions.stream()
                .anyMatch(action -> action.effect() == HarnessActionEffect.PHYSICAL_WORLD);
        boolean physicalCapability = capabilities.stream().anyMatch(capability ->
                capability == HarnessPluginCapability.PHYSICAL_SENSING
                        || capability == HarnessPluginCapability.PHYSICAL_ACTUATION
                        || capability == HarnessPluginCapability.EMERGENCY_STOP);
        if (physicalCapability
                && kind != HarnessPluginKind.DEVICE && kind != HarnessPluginKind.ROBOT) {
            throw new IllegalArgumentException("Physical capabilities require a "
                    + "DEVICE or ROBOT plugin kind");
        }
        if (!actions.isEmpty()
                && !capabilities.contains(HarnessPluginCapability.COMMANDS)) {
            throw new IllegalArgumentException(
                    "Discoverable actions require the COMMANDS plugin capability");
        }
        if (physicalAction
                && kind != HarnessPluginKind.DEVICE && kind != HarnessPluginKind.ROBOT) {
            throw new IllegalArgumentException(
                    "Physical-world actions require a DEVICE or ROBOT plugin kind");
        }
        if (physicalAction
                && !capabilities.contains(HarnessPluginCapability.PHYSICAL_ACTUATION)) {
            throw new IllegalArgumentException("Physical-world actions require the "
                    + "PHYSICAL_ACTUATION plugin capability");
        }
        if (!observations.isEmpty()
                && !capabilities.contains(HarnessPluginCapability.OBSERVATION)) {
            throw new IllegalArgumentException(
                    "Typed observations require the OBSERVATION plugin capability");
        }
    }

    public HarnessActionSpec action(String actionName) {
        String expected = actionName == null ? "" : actionName.trim();
        return actions.stream().filter(action -> action.name().equals(expected))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Plugin '" + id + "' has no action '" + expected + "'"));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String slug(String value, String label) {
        String normalized = required(value, label).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,29}")) {
            throw new IllegalArgumentException(label
                    + " must be a 1-30 character lowercase letter, number, or hyphen slug");
        }
        return normalized;
    }
}

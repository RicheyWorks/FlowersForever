package com.flowerfarm.lsystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Classic L-System (Lindenmayer system) generator for plant-like structures.
 *
 * <p>Symbols used by the turtle renderer:
 * <ul>
 *   <li>{@code F} — draw forward (stem)</li>
 *   <li>{@code G} — draw forward (thicker / greener stem)</li>
 *   <li>{@code f} — move forward without drawing</li>
 *   <li>{@code +} / {@code -} — turn left / right by current angle</li>
 *   <li>{@code [} / {@code ]} — push / pop turtle state</li>
 *   <li>{@code B} — bloom (draw flower head at current tip)</li>
 *   <li>{@code X}, {@code Y}, … — variables expanded by production rules</li>
 * </ul>
 */
public final class LSystem {

    public static final int MAX_DEPTH = 7;

    private final String name;
    private final String description;
    private final String axiom;
    private final Map<Character, String> rules;
    private final double defaultAngleDegrees;
    private final double defaultStep;
    private final int defaultDepth;

    public LSystem(String name, String description, String axiom,
                   Map<Character, String> rules,
                   double defaultAngleDegrees, double defaultStep, int defaultDepth) {
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.axiom = Objects.requireNonNull(axiom);
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.defaultAngleDegrees = defaultAngleDegrees;
        this.defaultStep = defaultStep;
        this.defaultDepth = Math.min(MAX_DEPTH, Math.max(1, defaultDepth));
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAxiom() { return axiom; }
    public Map<Character, String> getRules() { return rules; }
    public double getDefaultAngleDegrees() { return defaultAngleDegrees; }
    public double getDefaultStep() { return defaultStep; }
    public int getDefaultDepth() { return defaultDepth; }

    /**
     * Expand the axiom {@code depth} times using the production rules.
     * Depth is clamped to {@link #MAX_DEPTH}.
     */
    public String expand(int depth) {
        int d = Math.min(MAX_DEPTH, Math.max(0, depth));
        String current = axiom;
        for (int i = 0; i < d; i++) {
            StringBuilder next = new StringBuilder(current.length() * 2);
            for (int j = 0; j < current.length(); j++) {
                char c = current.charAt(j);
                String replacement = rules.get(c);
                if (replacement != null) {
                    next.append(replacement);
                } else {
                    next.append(c);
                }
            }
            current = next.toString();
            // Soft cap to keep rendering responsive
            if (current.length() > 50_000) {
                break;
            }
        }
        return current;
    }

    /**
     * Produce a mutated copy: slight angle tweak and optional random rule tweak.
     */
    public LSystem mutate(Random rng) {
        Random r = rng == null ? new Random() : rng;
        double angleDelta = (r.nextDouble() - 0.5) * 12.0; // ±6°
        double stepDelta = (r.nextDouble() - 0.5) * 2.0;
        Map<Character, String> newRules = new LinkedHashMap<>(rules);

        // Occasionally mutate one production string (insert/remove a branch)
        if (!newRules.isEmpty() && r.nextDouble() < 0.65) {
            Character[] keys = newRules.keySet().toArray(new Character[0]);
            char key = keys[r.nextInt(keys.length)];
            String prod = newRules.get(key);
            if (prod != null && prod.length() > 2) {
                int mode = r.nextInt(3);
                if (mode == 0 && prod.length() < 40) {
                    // Insert a small branch somewhere in the middle
                    int at = 1 + r.nextInt(Math.max(1, prod.length() - 1));
                    prod = prod.substring(0, at) + "[+F]" + prod.substring(at);
                } else if (mode == 1 && prod.length() < 40) {
                    int at = 1 + r.nextInt(Math.max(1, prod.length() - 1));
                    prod = prod.substring(0, at) + "[-F]" + prod.substring(at);
                } else if (prod.contains("[+F]")) {
                    prod = prod.replaceFirst("\\[\\+F\\]", "");
                } else if (prod.contains("[-F]")) {
                    prod = prod.replaceFirst("\\[-F\\]", "");
                }
                newRules.put(key, prod);
            }
        }

        return new LSystem(
                name + " (mutant)",
                description + " — evolved variant",
                axiom,
                newRules,
                clamp(defaultAngleDegrees + angleDelta, 8, 55),
                clamp(defaultStep + stepDelta, 4, 28),
                defaultDepth
        );
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String toString() {
        return name;
    }
}

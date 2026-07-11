package com.flowerfarm.lsystem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-serializable L-System definition for save/load.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LSystemDefinition {

    private String name;
    private String description;
    private String axiom;
    private Map<String, String> rules = new LinkedHashMap<>();
    private double angleDegrees = 25;
    private double step = 8;
    private int depth = 4;
    private String linkedSku; // optional inventory name association

    public LSystemDefinition() {
    }

    public static LSystemDefinition from(LSystem system) {
        LSystemDefinition d = new LSystemDefinition();
        d.name = system.getName();
        d.description = system.getDescription();
        d.axiom = system.getAxiom();
        d.angleDegrees = system.getDefaultAngleDegrees();
        d.step = system.getDefaultStep();
        d.depth = system.getDefaultDepth();
        system.getRules().forEach((k, v) -> d.rules.put(String.valueOf(k), v));
        return d;
    }

    public LSystem toLSystem() {
        Map<Character, String> map = new LinkedHashMap<>();
        if (rules != null) {
            rules.forEach((k, v) -> {
                if (k != null && !k.isEmpty()) {
                    map.put(k.charAt(0), v == null ? "" : v);
                }
            });
        }
        return new LSystem(
                name == null ? "Custom" : name,
                description,
                axiom == null || axiom.isBlank() ? "X" : axiom,
                map,
                angleDegrees,
                step,
                depth
        );
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAxiom() { return axiom; }
    public void setAxiom(String axiom) { this.axiom = axiom; }
    public Map<String, String> getRules() { return rules; }
    public void setRules(Map<String, String> rules) { this.rules = rules; }
    public double getAngleDegrees() { return angleDegrees; }
    public void setAngleDegrees(double angleDegrees) { this.angleDegrees = angleDegrees; }
    public double getStep() { return step; }
    public void setStep(double step) { this.step = step; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getLinkedSku() { return linkedSku; }
    public void setLinkedSku(String linkedSku) { this.linkedSku = linkedSku; }
}

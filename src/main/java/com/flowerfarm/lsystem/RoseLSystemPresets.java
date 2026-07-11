package com.flowerfarm.lsystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PNW-themed L-System presets inspired by rose growth habits
 * (native Nootka, shrub rugosa, Bourbon, climber).
 */
public final class RoseLSystemPresets {

    private RoseLSystemPresets() {}

    public static List<LSystem> all() {
        return List.of(nootka(), rugosa(), bourbon(), climber(), damask());
    }

    /** Wild native PNW shrub — open branching, airy blooms. */
    public static LSystem nootka() {
        Map<Character, String> rules = new LinkedHashMap<>();
        rules.put('X', "F[+X]F[-X]+X");
        rules.put('F', "FF");
        return new LSystem(
                "Nootka Rose",
                "Native PNW shrub rose — open, airy branching (west of Cascades).",
                "X",
                rules,
                25.0, 8.0, 5
        );
    }

    /** Compact disease-resistant shrub — denser canopy. */
    public static LSystem rugosa() {
        Map<Character, String> rules = new LinkedHashMap<>();
        rules.put('X', "F-[[X]+X]+F[+FX]-X");
        rules.put('F', "FF");
        return new LSystem(
                "Rosa rugosa",
                "Dense rugosa shrub — hardy coastal form with heavy branching.",
                "X",
                rules,
                22.5, 7.0, 4
        );
    }

    /** Full, rounded old-garden silhouette. */
    public static LSystem bourbon() {
        Map<Character, String> rules = new LinkedHashMap<>();
        rules.put('X', "F[+X][-X]FX");
        rules.put('F', "F[+F]F[-F]F");
        return new LSystem(
                "Bourbon Rose",
                "Old garden rose — rounded, full canopy with layered blooms.",
                "X",
                rules,
                20.0, 6.5, 4
        );
    }

    /** Tall arching canes. */
    public static LSystem climber() {
        Map<Character, String> rules = new LinkedHashMap<>();
        rules.put('X', "F[+X]F[-X]FX");
        rules.put('F', "F[+F]F");
        return new LSystem(
                "Climbing Rose",
                "Tall arching canes — train along a Port Orchard trellis.",
                "X",
                rules,
                28.0, 9.0, 5
        );
    }

    /** Fragrant damask-style with bloom tips. */
    public static LSystem damask() {
        Map<Character, String> rules = new LinkedHashMap<>();
        rules.put('X', "F[+X]F[-X]FB");
        rules.put('F', "FF");
        rules.put('B', "B");
        return new LSystem(
                "Damask Rose",
                "Fragrant damask habit — blooms emphasized at branch tips.",
                "X",
                rules,
                24.0, 7.5, 4
        );
    }
}

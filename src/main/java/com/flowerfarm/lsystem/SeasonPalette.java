package com.flowerfarm.lsystem;

import java.awt.Color;

/**
 * Seasonal color palettes for the rose visualizer.
 */
public enum SeasonPalette {

    SPRING_BLOOM("Spring bloom",
            new Color(46, 125, 50),
            new Color(27, 94, 32),
            new Color(244, 143, 177),
            new Color(255, 235, 238),
            new Color(245, 250, 245)),

    SUMMER("Summer",
            new Color(56, 142, 60),
            new Color(27, 94, 32),
            new Color(211, 47, 47),
            new Color(255, 205, 210),
            new Color(250, 252, 248)),

    LATE_SUMMER("Late summer",
            new Color(85, 139, 47),
            new Color(51, 105, 30),
            new Color(194, 24, 91),
            new Color(248, 187, 208),
            new Color(252, 250, 245)),

    FALL("Fall",
            new Color(104, 115, 40),
            new Color(62, 70, 20),
            new Color(183, 28, 28),
            new Color(255, 171, 145),
            new Color(252, 248, 240));

    private final String label;
    private final Color stem;
    private final Color stemDark;
    private final Color bloom;
    private final Color bloomCenter;
    private final Color background;

    SeasonPalette(String label, Color stem, Color stemDark, Color bloom, Color bloomCenter, Color background) {
        this.label = label;
        this.stem = stem;
        this.stemDark = stemDark;
        this.bloom = bloom;
        this.bloomCenter = bloomCenter;
        this.background = background;
    }

    public String getLabel() { return label; }
    public Color getStem() { return stem; }
    public Color getStemDark() { return stemDark; }
    public Color getBloom() { return bloom; }
    public Color getBloomCenter() { return bloomCenter; }
    public Color getBackground() { return background; }

    @Override
    public String toString() {
        return label;
    }
}

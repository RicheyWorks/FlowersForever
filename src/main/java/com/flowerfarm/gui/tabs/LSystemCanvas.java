package com.flowerfarm.gui.tabs;

import com.flowerfarm.lsystem.LSystem;
import com.flowerfarm.lsystem.SeasonPalette;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Turtle-graphics canvas that renders an expanded L-System string.
 */
public class LSystemCanvas extends JPanel {

    private String instructions = "";
    private double angleDegrees = 25;
    private double step = 8;
    private SeasonPalette palette = SeasonPalette.SPRING_BLOOM;
    private String statusLine = "";

    public LSystemCanvas() {
        setPreferredSize(new Dimension(640, 520));
        setBackground(SeasonPalette.SPRING_BLOOM.getBackground());
    }

    public void setScene(String instructions, double angleDegrees, double step, SeasonPalette palette) {
        this.instructions = instructions == null ? "" : instructions;
        this.angleDegrees = angleDegrees;
        this.step = step;
        this.palette = palette == null ? SeasonPalette.SPRING_BLOOM : palette;
        setBackground(this.palette.getBackground());
        repaint();
    }

    public void setStatusLine(String statusLine) {
        this.statusLine = statusLine == null ? "" : statusLine;
        repaint();
    }

    public BufferedImage toImage() {
        int w = Math.max(getWidth(), 640);
        int h = Math.max(getHeight(), 520);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paintScene(g, w, h);
        g.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintScene((Graphics2D) g, getWidth(), getHeight());
    }

    private void paintScene(Graphics2D g, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(palette.getBackground());
        g.fillRect(0, 0, width, height);

        if (instructions.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("Select a rose preset and click Grow.", 24, 32);
            return;
        }

        // First pass: measure bounding box in turtle space (origin at 0,0 heading up)
        Bounds bounds = simulate(false, null);

        double pad = 40;
        double bw = Math.max(1, bounds.maxX - bounds.minX);
        double bh = Math.max(1, bounds.maxY - bounds.minY);
        double scale = Math.min((width - 2 * pad) / bw, (height - 2 * pad) / bh);
        scale = Math.max(0.05, Math.min(scale, 4.0));

        double offsetX = width / 2.0 - (bounds.minX + bounds.maxX) / 2.0 * scale;
        double offsetY = height / 2.0 + (bounds.minY + bounds.maxY) / 2.0 * scale; // flip Y

        AffineTransform old = g.getTransform();
        g.translate(offsetX, offsetY);
        g.scale(scale, -scale); // screen Y down → plant Y up

        simulate(true, g);

        g.setTransform(old);

        if (!statusLine.isEmpty()) {
            g.setColor(new Color(40, 60, 40));
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString(statusLine, 12, height - 12);
        }
    }

    private Bounds simulate(boolean draw, Graphics2D g) {
        double x = 0;
        double y = 0;
        double heading = 90; // degrees, 90 = up
        double radStep = step;
        Deque<double[]> stack = new ArrayDeque<>();

        Bounds b = new Bounds();
        b.include(x, y);

        for (int i = 0; i < instructions.length(); i++) {
            char c = instructions.charAt(i);
            switch (c) {
                case 'F', 'G' -> {
                    double rad = Math.toRadians(heading);
                    double nx = x + Math.cos(rad) * radStep;
                    double ny = y + Math.sin(rad) * radStep;
                    if (draw && g != null) {
                        g.setStroke(new BasicStroke(c == 'G' ? 2.2f : 1.4f,
                                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(c == 'G' ? palette.getStemDark() : palette.getStem());
                        g.draw(new Line2D.Double(x, y, nx, ny));
                    }
                    x = nx;
                    y = ny;
                    b.include(x, y);
                }
                case 'f' -> {
                    double rad = Math.toRadians(heading);
                    x += Math.cos(rad) * radStep;
                    y += Math.sin(rad) * radStep;
                    b.include(x, y);
                }
                case '+' -> heading += angleDegrees;
                case '-' -> heading -= angleDegrees;
                case '[' -> stack.push(new double[]{x, y, heading});
                case ']' -> {
                    if (!stack.isEmpty()) {
                        double[] s = stack.pop();
                        x = s[0];
                        y = s[1];
                        heading = s[2];
                    }
                }
                case 'B' -> {
                    if (draw && g != null) {
                        double r = radStep * 0.85;
                        g.setColor(palette.getBloom());
                        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                        g.setColor(palette.getBloomCenter());
                        g.fill(new Ellipse2D.Double(x - r * 0.35, y - r * 0.35, r * 0.7, r * 0.7));
                    }
                    b.include(x + radStep, y + radStep);
                    b.include(x - radStep, y - radStep);
                }
                default -> {
                    // variables that remain after expansion: ignore for drawing
                }
            }
        }

        // Tip blooms: if no explicit B in string, add soft blooms at ends of deep branches
        // (already handled when B is in the language)

        return b;
    }

    private static final class Bounds {
        double minX = 0, minY = 0, maxX = 0, maxY = 0;
        boolean init = false;

        void include(double x, double y) {
            if (!init) {
                minX = maxX = x;
                minY = maxY = y;
                init = true;
                return;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
    }
}

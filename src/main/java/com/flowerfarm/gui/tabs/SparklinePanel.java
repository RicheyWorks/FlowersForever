package com.flowerfarm.gui.tabs;

import javax.swing.*;
import java.awt.*;

/**
 * Tiny sparkline for 7-day KPI trends on the dashboard.
 */
public class SparklinePanel extends JPanel {

    private double[] values = new double[7];
    private Color lineColor = new Color(34, 100, 54);

    public SparklinePanel() {
        setPreferredSize(new Dimension(88, 28));
        setOpaque(false);
        setToolTipText("Last 7 days (oldest → today)");
    }

    public void setValues(double[] values) {
        this.values = values == null ? new double[7] : values.clone();
        // Keep a plain fallback tip if caller did not set a rich HTML tip
        if (getToolTipText() == null || !getToolTipText().startsWith("<html>")) {
            double sum = 0;
            double peak = 0;
            for (double v : this.values) {
                sum += v;
                peak = Math.max(peak, v);
            }
            setToolTipText(String.format("Last %d days · sum %.0f · peak %.0f (oldest → today)",
                    this.values.length, sum, peak));
        }
        repaint();
    }

    public double[] getValues() {
        return values.clone();
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor == null ? Color.DARK_GRAY : lineColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int pad = 3;

        g2.setColor(new Color(0, 0, 0, 20));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

        if (values.length == 0) {
            g2.dispose();
            return;
        }

        double max = 0;
        for (double v : values) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            g2.setColor(lineColor);
            g2.drawLine(pad, h / 2, w - pad, h / 2);
            g2.dispose();
            return;
        }

        int n = values.length;
        double dx = (w - 2.0 * pad) / Math.max(1, n - 1);
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = pad + (int) Math.round(i * dx);
            double norm = values[i] / max;
            ys[i] = h - pad - (int) Math.round(norm * (h - 2.0 * pad));
        }

        // Soft fill under the curve
        int[] fillX = new int[n + 2];
        int[] fillY = new int[n + 2];
        System.arraycopy(xs, 0, fillX, 0, n);
        System.arraycopy(ys, 0, fillY, 0, n);
        fillX[n] = xs[n - 1];
        fillY[n] = h - pad;
        fillX[n + 1] = xs[0];
        fillY[n + 1] = h - pad;
        g2.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 40));
        g2.fillPolygon(fillX, fillY, n + 2);

        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(lineColor);
        for (int i = 1; i < n; i++) {
            g2.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
        }
        g2.fillOval(xs[n - 1] - 2, ys[n - 1] - 2, 5, 5);
        g2.dispose();
    }
}

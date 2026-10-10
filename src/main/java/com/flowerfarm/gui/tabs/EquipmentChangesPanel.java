package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.telemetry.EquipmentSpreadsheet;
import com.flowerfarm.telemetry.FarmReading;
import com.flowerfarm.telemetry.FarmReadingService;
import com.flowerfarm.telemetry.ManualFarmReading;
import com.flowerfarm.telemetry.ReadingTrend;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Read-only, accessible history with a bounded chart and the exact saved observations. */
final class EquipmentChangesPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    EquipmentChangesPanel(FarmReadingService.MeasurementHistory history, long selectedId, Instant shownAt) {
        super(new BorderLayout(0, 10));
        setName("equipmentReadings.changesPanel");
        setOpaque(false);
        ReadingTrend trend = new ReadingTrend(history.readings());
        FarmReading channel = trend.readings().get(0);
        boolean includesSelection = trend.readings().stream().anyMatch(reading -> reading.id() == selectedId);
        String range = trend.minimum() == null ? "No measured values to plot."
                : "Plotted value range: " + exact(trend.minimum()) + " to " + exact(trend.maximum()) + " " + channel.unit();
        JTextArea description = plainText(ManualFarmReading.equipmentName(channel) + " · " + channel.location()
                + "\n" + EquipmentSpreadsheet.measurementLabel(channel.metric()) + " · Unit: " + channel.unit()
                + " · Source: " + channel.sourceId() + "\n"
                + trend.readings().size() + " saved readings · Oldest measured first · Snapshot at " + shownAt
                + (history.hasOlderReadings() ? "\nLatest 200 observations only. More older readings are saved; use Save readings spreadsheet for the full history." : "\nAll saved observations for this measurement.")
                + (includesSelection ? "" : "\nYour selected older reading is outside these latest 200 observations.")
                + "\n" + range);
        description.setName("equipmentReadings.changesDescription");
        add(description, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        JPanel visual = new JPanel(new BorderLayout(0, 5));
        visual.setOpaque(false);
        visual.add(new Chart(trend), BorderLayout.CENTER);
        visual.add(plainText("● Measured: green dots and lines between adjacent measured observations.  ■ Needs checking: amber squares."
                + "\n× No reading: gray.  × Equipment error: red. These mark gaps, never zero. Lines show saved points, not continuous monitoring."
                + "\nTime runs left to right with actual intervals. The value range fits the saved points; it is not a safe operating range."), BorderLayout.SOUTH);
        content.add(visual, BorderLayout.NORTH);
        DefaultTableModel rows = new DefaultTableModel(new String[] {
                "Measured at (UTC)", "Value", "Unit", "Condition", "Received at (UTC)", "Reading reference", "Saved reading number"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (FarmReading reading : trend.readings()) {
            rows.addRow(new Object[] {reading.observedAt().toString(), exact(reading.value()), reading.unit(),
                    EquipmentSpreadsheet.conditionLabel(reading.quality()), reading.receivedAt().toString(),
                    reading.readingKey(), reading.id()});
        }
        JTable table = new JTable(rows);
        table.setName("equipmentReadings.changesHistory");
        FarmUi.configureTable(table, "Exact saved observations for this measurement, oldest measured first");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
        plain.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plain);
        int[] widths = {270, 250, 80, 145, 270, 220, 145};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = FarmUi.tableScroll(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(960, 210));
        content.add(scroll, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        add(plainText("Exact numbers and complete UTC times are in the table. Names, source, unit, and growing area stay together."
                + "\nThis view stays as loaded. Close it and choose See changes again for new readings. Your unfinished entries are kept."), BorderLayout.SOUTH);
    }

    private static String exact(BigDecimal value) { return value == null ? "No value" : value.stripTrailingZeros().toPlainString(); }
    private static JTextArea plainText(String value) {
        JTextArea text = new JTextArea(value);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setColumns(70);
        return text;
    }

    private static final class Chart extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final Color GREEN = new Color(34, 110, 55);
        private static final Color AMBER = new Color(171, 104, 0);
        private static final Color RED = new Color(175, 40, 40);
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss XXX")
                .withZone(ZoneId.systemDefault());
        private final ReadingTrend trend;

        private Chart(ReadingTrend trend) {
            this.trend = trend;
            setName("equipmentReadings.changesChart");
            setPreferredSize(new Dimension(960, 250));
            setBackground(Color.WHITE);
            getAccessibleContext().setAccessibleName("Measurement changes over measured time");
            getAccessibleContext().setAccessibleDescription("Exact values, times, and conditions are available in the table below. Missing and faulted values are gaps.");
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D pen = (Graphics2D) graphics.create();
            try {
                pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 58;
                int right = Math.max(left + 1, getWidth() - 24);
                int top = 20;
                int bottom = Math.max(top + 1, getHeight() - 60);
                pen.setColor(new Color(220, 226, 220));
                for (int i = 0; i <= 4; i++) {
                    int y = top + (bottom - top) * i / 4;
                    pen.drawLine(left, y, right, y);
                }
                pen.setColor(Color.DARK_GRAY);
                pen.drawString("Higher", 5, top + 10);
                pen.drawString("Lower", 5, bottom);
                String start = TIME.format(trend.readings().get(0).observedAt());
                String end = TIME.format(trend.readings().get(trend.readings().size() - 1).observedAt());
                pen.drawString(start, left, getHeight() - 10);
                if (right - left > pen.getFontMetrics().stringWidth(start) + pen.getFontMetrics().stringWidth(end) + 12) {
                    pen.drawString(end, right - pen.getFontMetrics().stringWidth(end), getHeight() - 10);
                }
                pen.setStroke(new BasicStroke(2f));
                ReadingTrend.Point previous = null;
                for (ReadingTrend.Point point : trend.points()) {
                    int x = left + (int) Math.round(point.timeFraction() * (right - left));
                    int y = point.valueFraction() == null ? bottom + 18
                            : bottom - (int) Math.round(point.valueFraction() * (bottom - top));
                    pen.setColor("SUSPECT".equals(point.condition()) ? AMBER : "ERROR".equals(point.condition())
                            ? RED : point.valueFraction() == null ? Color.GRAY : GREEN);
                    if (point.connectsPrevious() && previous != null && previous.valueFraction() != null) {
                        int oldX = left + (int) Math.round(previous.timeFraction() * (right - left));
                        int oldY = bottom - (int) Math.round(previous.valueFraction() * (bottom - top));
                        pen.drawLine(oldX, oldY, x, y);
                    }
                    if (point.valueFraction() == null) {
                        pen.drawLine(x - 4, y - 4, x + 4, y + 4);
                        pen.drawLine(x - 4, y + 4, x + 4, y - 4);
                    } else if ("SUSPECT".equals(point.condition())) pen.fillRect(x - 4, y - 4, 8, 8);
                    else pen.fillOval(x - 4, y - 4, 8, 8);
                    previous = point;
                }
            } finally { pen.dispose(); }
        }
    }
}

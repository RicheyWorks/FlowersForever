package com.flowerfarm.gui.tabs;

import com.flowerfarm.telemetry.FarmReading;
import com.flowerfarm.telemetry.FarmReadingService;
import org.junit.jupiter.api.Test;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EquipmentChangesPanelTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00.123456789Z");
    private static FarmReading row(long id, String value, String quality) {
        return new FarmReading(id, "logger", "<html>reading-" + id, "probe", "soil.moisture", "%",
                value == null ? null : new BigDecimal(value), quality, NOW.minusSeconds(300 - id), NOW,
                "<html>North house", "secret-raw-frame");
    }

    @Test void exactAccessibleTableAndLiteralNamesAccompanyPaintedQualityGaps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new EquipmentChangesPanel(new FarmReadingService.MeasurementHistory(List.of(
                    row(1, "-4", "GOOD"), row(2, "-3", "GOOD"), row(3, "1.123456789012", "SUSPECT"),
                    row(4, null, "MISSING"), row(5, "999", "ERROR"), row(6, "2", "GOOD")), false), 1, NOW);
            JTable table = (JTable) find(panel, "equipmentReadings.changesHistory");
            assertThat(table.getValueAt(2, 1)).isEqualTo("1.123456789012");
            assertThat(table.getValueAt(3, 1)).isEqualTo("No value");
            assertThat(table.getValueAt(4, 1)).isEqualTo("999");
            assertThat(table.getValueAt(0, 0)).isEqualTo("2026-10-10T11:55:01.123456789Z");
            assertThat(table.isCellEditable(0, 1)).isFalse();
            assertThat(((JComponent) table.getCellRenderer(0, 5)).getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            var description = (JTextArea) find(panel, "equipmentReadings.changesDescription");
            assertThat(description.getText()).contains("<html>North house", "All saved", "-4 to 2 %").doesNotContain("secret-raw-frame");
            paint(panel, 1000, 760);
            JComponent chart = (JComponent) find(panel, "equipmentReadings.changesChart");
            assertThat(chart.getAccessibleContext().getAccessibleDescription()).contains("table below", "gaps");
            paint(chart, 400, 250);
        });
    }

    @Test void missingOnlyHistoryStatesNoValuesAndSmallLayoutStillPaints() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new EquipmentChangesPanel(new FarmReadingService.MeasurementHistory(
                    List.of(row(1, null, "MISSING"), row(2, null, "ERROR")), false), 1, NOW);
            assertThat(((JTextArea) find(panel, "equipmentReadings.changesDescription")).getText()).contains("No measured values to plot");
            paint(panel, 520, 740);
            paint((JComponent) find(panel, "equipmentReadings.changesChart"), 160, 100);
        });
    }

    @Test void cappedSnapshotExplainsExcludedSelectionAndConstantRange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var readings = java.util.stream.LongStream.rangeClosed(2, 201).mapToObj(i -> row(i, "0", "GOOD")).toList();
            var panel = new EquipmentChangesPanel(new FarmReadingService.MeasurementHistory(readings, true), 1, NOW);
            assertThat(((JTextArea) find(panel, "equipmentReadings.changesDescription")).getText())
                    .contains("Latest 200", "More older", "selected older reading is outside", "0 to 0 %");
            assertThat(((JTable) find(panel, "equipmentReadings.changesHistory")).getRowCount()).isEqualTo(200);
            paint(panel, 860, 700);
        });
    }

    private static void paint(JComponent component, int width, int height) {
        component.setSize(width, height); layout(component);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { component.printAll(graphics); } finally { graphics.dispose(); }
    }
    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout(); for (Component child : container.getComponents()) layout(child);
        }
    }
    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            Component found = find(child, name); if (found != null) return found;
        }
        return null;
    }
}

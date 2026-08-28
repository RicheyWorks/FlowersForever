package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Season Planner desktop workflow")
class SeasonPlannerTabTest {

    @Test
    @DisplayName("maps persisted blocks into visual metrics and preserves read-only planning")
    void rendersPlanAndPermissions() throws Exception {
        SeasonPlanService service = mock(SeasonPlanService.class);
        SeasonPlanEntry rose = entry();
        SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                1, 1, 1, 1, 80, 640,
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10));
        SeasonPlanService.PlanPerformance performance = new SeasonPlanService.PlanPerformance(
                rose, 320, 300, 20, 50, SeasonPlanService.PlanPace.ON_TRACK);
        List<SeasonPlanService.WeeklyProduction> weekly = List.of(
                new SeasonPlanService.WeeklyProduction(
                        LocalDate.of(2026, 6, 15), 300, 140, -160),
                new SeasonPlanService.WeeklyProduction(
                        LocalDate.of(2026, 6, 22), 340, 180, -160));
        when(service.analyze(2026)).thenReturn(new SeasonPlanService.SeasonAnalysis(
                List.of(rose), summary, List.of(performance), List.of(), List.of(), weekly,
                320, 0, 0));
        when(service.availableSeasons()).thenReturn(List.of(2026));
        SeasonPlannerTab tab = new SeasonPlannerTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            ((javax.swing.JComboBox<?>) named(root[0], "seasonPlanner.season"))
                    .setSelectedItem(2026);
            tab.initialize();
        });
        awaitStatus(root[0], "320 actual / 640 planned stems");

        JTable table = (JTable) named(root[0], "seasonPlanner.table");
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("Bed A");
        assertThat(table.getValueAt(0, 1)).isEqualTo("Nootka Rose · Schoener's Nutkana");
        assertThat(table.getValueAt(0, 7)).isEqualTo(320);
        assertThat(table.getValueAt(0, 9)).isEqualTo("On track");
        assertThat(descendants(root[0], JLabel.class))
                .extracting(JLabel::getText)
                .contains("640", "Jun 20", "80 bed ft · 0 overlaps",
                        "320 actual · 50% of plan");
        assertThat(named(root[0], "seasonPlanner.timeline").getAccessibleContext()
                .getAccessibleDescription()).contains("sowing", "harvest");
        assertThat(named(root[0], "seasonPlanner.weeklyChart").getAccessibleContext()
                .getAccessibleDescription()).contains("2 weeks", "640 planned stems",
                        "320 actual stems");
        assertThat(((JLabel) named(root[0], "seasonPlanner.weeklyStatus")).getText())
                .isEqualTo("320 actual stems · 300 expected by today · 6.7% ahead of pace");
        assertThat(((JButton) named(root[0], "seasonPlanner.succession")).isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(false));
        assertThat(((JButton) named(root[0], "seasonPlanner.add")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.edit")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.succession")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.delete")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.refresh")).isEnabled()).isTrue();
    }

    private static SeasonPlanEntry entry() {
        SeasonPlanEntry entry = new SeasonPlanEntry(
                2026, "Nootka Rose", "Schoener's Nutkana", "Bed A",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 12),
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10),
                80, 640, SeasonPlanStatus.PLANTED, "First flush for market.");
        entry.setId(17L);
        return entry;
    }

    private static void awaitStatus(Component root, String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] text = new String[1];
            SwingUtilities.invokeAndWait(() ->
                    text[0] = ((JLabel) named(root, "seasonPlanner.status")).getText());
            if (text[0].contains(expected)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for season status: " + expected);
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = namedOrNull(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("Component not found: " + name);
    }

    private static Component namedOrNull(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = namedOrNull(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        java.util.ArrayList<T> matches = new java.util.ArrayList<>();
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(descendants(child, type));
            }
        }
        return matches;
    }

    private static final class TestHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void setStatus(String message) { }
        @Override public void selectTab(String title) { }
        @Override public void runTrendAnalysis() { }
    }
}

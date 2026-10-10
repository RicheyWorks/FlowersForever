package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.reference.PnwPlantCatalog;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
        AtomicReference<String> copiedAvailability = new AtomicReference<>();
        SeasonPlannerTab tab = new SeasonPlannerTab(service, new TestHost(),
                FarmDialogs.swing(), copiedAvailability::set);
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
        assertThat(((JLabel) named(root[0], "seasonPlanner.insightsSummary")).getText())
                .startsWith("Plan looks aligned");
        assertThat(descendants(root[0], JLabel.class))
                .extracting(JLabel::getText)
                .contains("The season plan is aligned");
        assertThat(((JButton) named(root[0], "seasonPlanner.succession")).isEnabled()).isTrue();
        JTable availability = (JTable) named(root[0], "seasonPlanner.availabilityTable");
        assertThat(availability.getRowCount()).isGreaterThan(8);
        int projected = 0;
        for (int row = 0; row < availability.getRowCount(); row++) {
            projected += (Integer) availability.getValueAt(row, 3);
        }
        assertThat(projected).isBetween(635, 645);
        JButton copyAvailability = (JButton) named(
                root[0], "seasonPlanner.copyAvailability");
        assertThat(copyAvailability.isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(copyAvailability::doClick);
        assertThat(copiedAvailability.get())
                .startsWith("Week of\tCrop\tVariety\tPlanned stems")
                .contains("Nootka Rose", "Schoener's Nutkana");
        assertThat(FarmUi.focusFirstInteractive(root[0]))
                .isSameAs(named(root[0], "seasonPlanner.season"));

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(false));
        assertThat(((JButton) named(root[0], "seasonPlanner.add")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.edit")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.succession")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.delete")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "seasonPlanner.refresh")).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("turns plan signals into direct record-review actions")
    void guidesPlanReview() throws Exception {
        SeasonPlanService service = mock(SeasonPlanService.class);
        SeasonPlanEntry rose = entry();
        SeasonPlanEntry sunflower = new SeasonPlanEntry(
                2026, "Sunflower", "ProCut Plum", "Bed A",
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 9, 5),
                100, 400, SeasonPlanStatus.SOWN, "Late summer succession.");
        sunflower.setId(18L);
        SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                2, 2, 1, 2, 180, 1_040,
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10));
        List<SeasonPlanService.PlanPerformance> performance = List.of(
                new SeasonPlanService.PlanPerformance(
                        rose, 10, 10, 0, 1.6, SeasonPlanService.PlanPace.ON_TRACK),
                new SeasonPlanService.PlanPerformance(
                        sunflower, 0, 100, -100, 0, SeasonPlanService.PlanPace.BEHIND));
        SeasonPlanService.BedConflict conflict = new SeasonPlanService.BedConflict(
                rose, sunflower, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 9, 5));
        SeasonPlanService.HarvestGap gap = new SeasonPlanService.HarvestGap(
                LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 27), 12);
        when(service.analyze(2026)).thenReturn(new SeasonPlanService.SeasonAnalysis(
                List.of(rose, sunflower), summary, performance, List.of(conflict),
                List.of(gap), List.of(), 10, 24, 1));
        when(service.availableSeasons()).thenReturn(List.of(2026));
        SeasonPlannerTab tab = new SeasonPlannerTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            ((javax.swing.JComboBox<?>) named(root[0], "seasonPlanner.season"))
                    .setSelectedItem(2026);
            tab.initialize();
        });
        awaitStatus(root[0], "10 actual / 1,040 planned stems");

        assertThat(((JLabel) named(root[0], "seasonPlanner.insightsSummary")).getText())
                .startsWith("4 planning signals to review");
        assertThat(named(root[0], "seasonPlanner.insight.reviewConflict")).isNotNull();
        assertThat(named(root[0], "seasonPlanner.insight.reviewGap")).isNotNull();
        assertThat(named(root[0], "seasonPlanner.insight.openHarvests")).isNotNull();

        JTabbedPane workspace = (JTabbedPane) named(root[0], "seasonPlanner.workspace");
        JTable table = (JTable) named(root[0], "seasonPlanner.table");
        SwingUtilities.invokeAndWait(() -> {
            workspace.setSelectedIndex(3);
            ((JButton) named(root[0], "seasonPlanner.insight.reviewPace")).doClick();
        });

        assertThat(workspace.getSelectedIndex()).isEqualTo(1);
        int selectedModelRow = table.convertRowIndexToModel(table.getSelectedRow());
        assertThat(table.getValueAt(selectedModelRow, 1)).isEqualTo("Sunflower · ProCut Plum");
    }

    @Test
    @DisplayName("turns a sourced plant profile into a validated farm-owned draft")
    void preparesSourcedPlantingDraft() throws Exception {
        SeasonPlanService service = mock(SeasonPlanService.class);
        when(service.create(any(SeasonPlanEntry.class))).thenAnswer(invocation -> {
            SeasonPlanEntry saved = invocation.getArgument(0);
            saved.setId(91L);
            return saved;
        });
        CapturingDialogs dialogs = new CapturingDialogs();
        SeasonPlannerTab tab = new SeasonPlannerTab(service, new TestHost(), dialogs);
        PnwPlantProfile dahlia = PnwPlantCatalog.all().stream()
                .filter(plant -> plant.commonName().equals("Dahlia"))
                .findFirst().orElseThrow();
        boolean[] accepted = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
                accepted[0] = tab.preparePlantingFromReference(dahlia));

        assertThat(accepted[0]).isTrue();
        verify(service, timeout(2000)).create(any(SeasonPlanEntry.class));
        assertThat(dialogs.crop).isEqualTo("Dahlia");
        assertThat(dialogs.variety).isBlank();
        assertThat(dialogs.sow).isBlank();
        assertThat(dialogs.harvestStart).isBlank();
        assertThat(dialogs.expectedStems).isBlank();
        assertThat(dialogs.notes)
                .contains(dahlia.scientificName(), dahlia.source().label(),
                        dahlia.source().url(), "Farm override / cultivar notes");
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

    private static final class CapturingDialogs implements FarmDialogs {
        private String crop;
        private String variety;
        private String notes;
        private String sow;
        private String harvestStart;
        private String expectedStems;

        @Override
        public int showOption(Component parent, Object message, String title,
                              int optionType, int messageType, Icon icon,
                              Object[] options, Object initialValue) {
            return JOptionPane.CANCEL_OPTION;
        }

        @Override
        public int showConfirm(Component parent, Object message, String title,
                               int optionType, int messageType) {
            Component form = (Component) message;
            JTextField cropField = field(form, "Crop");
            JTextField varietyField = field(form, "Variety");
            JTextField bedField = field(form, "Bed or field");
            JTextArea notesArea = descendants(form, JTextArea.class).get(0);
            crop = cropField.getText();
            variety = varietyField.getText();
            notes = notesArea.getText();
            sow = field(form, "Sow date").getText();
            harvestStart = field(form, "Harvest start").getText();
            expectedStems = field(form, "Expected stems").getText();
            bedField.setText("Reference Bed");
            field(form, "Sow date").setText("2026-03-01");
            field(form, "Harvest start").setText("2026-07-01");
            field(form, "Harvest end").setText("2026-09-01");
            field(form, "Bed length in feet").setText("40");
            field(form, "Expected stems").setText("320");
            return JOptionPane.OK_OPTION;
        }

        @Override
        public void showMessage(Component parent, Object message,
                                String title, int messageType) {
            throw new AssertionError("Unexpected validation message: " + message);
        }

        private static JTextField field(Component root, String accessibleName) {
            return descendants(root, JTextField.class).stream()
                    .filter(candidate -> accessibleName.equals(candidate
                            .getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
        }
    }
}

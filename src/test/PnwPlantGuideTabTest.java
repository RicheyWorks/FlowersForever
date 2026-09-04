package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.PnwPlantProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pacific Northwest plant guide desktop")
class PnwPlantGuideTabTest {

    @Test
    @DisplayName("keeps every filter and source control visible at compact desktop size")
    void keepsCompactControlsVisible() {
        SwingUserHarness user = SwingUserHarness.on(
                new PnwPlantGuideTab().getUIComponent()).layoutAt(1050, 700);

        assertThat(user.visibleExtent("plant.search").width()).isGreaterThan(100);
        assertThat(user.visibleExtent("plant.typeFilter").width()).isGreaterThan(100);
        assertThat(user.visibleExtent("plant.originFilter").width()).isGreaterThan(100);
        assertThat(user.visibleExtent("plant.lightFilter").width()).isGreaterThan(100);
        assertThat(user.visibleExtent("plant.clearFilters").width()).isGreaterThan(80);
        assertThat(user.visibleExtent("plant.sourceSelector").width()).isGreaterThan(200);
        assertThat(user.visibleExtent("plant.guide.table").height()).isGreaterThan(120);
    }

    @Test
    @DisplayName("filters profiles and keeps detailed source provenance visible")
    void filtersAndShowsSource() throws Exception {
        PnwPlantGuideTab tab = new PnwPlantGuideTab();
        JComponent root = tab.getUIComponent();
        JTable table = (JTable) named(root, "plant.guide.table");
        JTextField search = (JTextField) named(root, "plant.search");
        JTextArea details = (JTextArea) named(root, "plant.profile.details");
        JComboBox<?> sourceSelector = (JComboBox<?>) named(root, "plant.sourceSelector");

        assertThat(table.getRowCount()).isGreaterThanOrEqualTo(90);
        assertThat(details.getText()).contains("Source:", "https://");
        assertThat(sourceSelector.getItemCount()).isGreaterThanOrEqualTo(15);

        SwingUtilities.invokeAndWait(() -> search.setText("dahlia"));
        flushEvents();

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("Dahlia");
        assertThat(details.getText()).contains("Dahlia cultivars", "tuber", "Oregon State");
    }

    @Test
    @DisplayName("origin and type filters compose and clear in one action")
    void combinesAndClearsFilters() throws Exception {
        JComponent root = new PnwPlantGuideTab().getUIComponent();
        JTable table = (JTable) named(root, "plant.guide.table");
        JComboBox<?> type = (JComboBox<?>) named(root, "plant.typeFilter");
        JComboBox<?> origin = (JComboBox<?>) named(root, "plant.originFilter");
        JButton clear = (JButton) named(root, "plant.clearFilters");

        SwingUtilities.invokeAndWait(() -> {
            type.setSelectedItem("Native shrub");
            origin.setSelectedItem("PNW native");
        });
        flushEvents();

        assertThat(table.getRowCount()).isGreaterThanOrEqualTo(10).isLessThan(90);
        for (int row = 0; row < table.getRowCount(); row++) {
            assertThat(table.getValueAt(row, 2)).isEqualTo("Native shrub");
            assertThat(table.getValueAt(row, 3)).isEqualTo("PNW native");
        }

        SwingUtilities.invokeAndWait(clear::doClick);
        flushEvents();
        assertThat(table.getRowCount()).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("hands one sourced profile to Season Planner without retyping it")
    void startsSourcedSeasonPlanDraft() throws Exception {
        AtomicReference<PnwPlantProfile> prepared = new AtomicReference<>();
        TabHost host = new TestHost() {
            @Override public boolean showSeasonPlanDraft(PnwPlantProfile plant) {
                prepared.set(plant);
                return true;
            }
        };
        PnwPlantGuideTab tab = new PnwPlantGuideTab(host);
        JComponent root = tab.getUIComponent();
        JTextField search = (JTextField) named(root, "plant.search");
        JButton plan = (JButton) named(root, "plant.planSelected");

        SwingUtilities.invokeAndWait(() -> search.setText("dahlia"));
        flushEvents();
        SwingUtilities.invokeAndWait(plan::doClick);

        assertThat(prepared.get()).isNotNull();
        assertThat(prepared.get().commonName()).isEqualTo("Dahlia");
        assertThat(prepared.get().source().url()).startsWith("https://");
        assertThat(((javax.swing.JLabel) named(root, "plant.planStatus")).getText())
                .isEqualTo("Planting block accepted in Season Planner");

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(false));
        assertThat(plan.isEnabled()).isFalse();
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = named(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void flushEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static class TestHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}

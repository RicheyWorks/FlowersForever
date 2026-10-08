package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReportsWorkspacePresentationTest {

    @ParameterizedTest
    @EnumSource(value = FarmWorkspace.class,
            names = {"FLOWER_SHOP", "CANNABIS_FARM", "POTATO_FARM",
                    "APPLE_ORCHARD", "BERRY_FARM", "NURSERY_FARM", "CUSTOM_FARM"})
    void generalWorkspacesDescribeSharedRecordsWithoutPromisingBedProduction(
            FarmWorkspace workspace) throws Exception {
        ReportsTab tab = new ReportsTab(mock(ReportService.class), null);
        tab.setWorkspace(workspace);
        JComponent ui = build(tab);

        assertThat(tab.getDescription()).isEqualTo("Printable weekly activity and sales reports");
        assertThat(labelText(ui)).contains("recorded activity, orders, and sales");
        assertThat(named(ui, "reports.summary", JTextArea.class).getText())
                .contains("any harvests in the shared log", "Crop and field totals reflect")
                .doesNotContain("bed / field production");
        assertThat(named(ui, "reports.rangePreview", JLabel.class).getText())
                .contains("recorded harvests, orders, and revenue")
                .doesNotContain("bed production");
        assertThatThrownBy(() -> tab.setWorkspace(FarmWorkspace.ROSE_AND_EVENTS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultRoseWorkspaceKeepsExistingReportDescriptions() throws Exception {
        ReportsTab tab = new ReportsTab(mock(ReportService.class), null);
        JComponent ui = build(tab);

        assertThat(tab.getDescription()).isEqualTo("Printable weekly harvest and sales reports");
        assertThat(named(ui, "reports.summary", JTextArea.class).getText())
                .contains("bed / field production");
        assertThat(named(ui, "reports.rangePreview", JLabel.class).getText())
                .contains("harvest, bed production, orders, and revenue");
    }

    private static JComponent build(ReportsTab tab) throws Exception {
        AtomicReference<JComponent> ui = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ui.set(tab.getUIComponent()));
        return ui.get();
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return named(child, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Search the next child.
                }
            }
        }
        throw new IllegalArgumentException("No component named " + name);
    }

    private static String labelText(Component root) {
        StringBuilder text = new StringBuilder();
        appendLabelText(root, text);
        return text.toString();
    }

    private static void appendLabelText(Component root, StringBuilder text) {
        if (root instanceof JLabel label) {
            text.append(label.getText()).append('\n');
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                appendLabelText(child, text);
            }
        }
    }
}

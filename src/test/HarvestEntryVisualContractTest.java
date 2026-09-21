package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Harvest entry rendered human-interface contracts")
class HarvestEntryVisualContractTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "harvest-entry");

    @ParameterizedTest(name = "{0} theme at {1} by {2}")
    @CsvSource({"light, 850, 500", "dark, 850, 500", "light, 1180, 760", "dark, 1180, 760"})
    @DisplayName("quick and batch entry keep controls, target, and recovery copy reachable")
    void rendersReachableEntryWorkspaces(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        AtomicReference<JComponent> initialized = new AtomicReference<>();
        AtomicReference<HarvestLogTab> initializedTab = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            if ("dark".equals(theme)) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            FarmUi.installThemeDefaults();
            HarvestLogTab tab = new HarvestLogTab(fixture(), new Host(),
                    new SwingDialogHarness().safelyCancelUnscripted());
            initializedTab.set(tab);
            initialized.set(tab.getUIComponent());
            tab.initialize();
        });
        JComponent root = initialized.get();
        SwingUserHarness.on(root).awaitButtonEnabled("Refresh", true)
                .awaitTableRows("harvest.history.table", 2);
        SwingUtilities.invokeAndWait(() -> {
            try {
                root.setSize(width, height);
                String prefix = theme + "-" + width + "x" + height;
                JTabbedPane workspace = named(root, "harvest.workspace", JTabbedPane.class);
                JTable history = named(root, "harvest.history.table", JTable.class);
                history.setRowSelectionInterval(0, 0);
                workspace.setSelectedIndex(0);
                settleLayout(root);
                render(prefix + "-quick-entry", root);
                assertReachable(history, new Rectangle(0, 0, 160, 48), root,
                        prefix + "-history-selection");
                assertThat(history.getColumnModel().getColumn(1).getWidth())
                        .as("readable harvest date").isGreaterThanOrEqualTo(100);
                assertThat(history.getColumnModel().getColumn(2).getWidth())
                        .as("readable crop identity").isGreaterThanOrEqualTo(140);
                assertHistoryControlsReachable(root, prefix + "-history-controls");
                assertWorkspaceUsable(root, workspace, prefix + "-quick-entry");

                named(root, "harvest.loadSelected", JButton.class).doClick();
                history.setRowSelectionInterval(1, 1);
                named(root, "harvest.notes", JTextField.class).setText(
                        "Hold this morning's cut in Cooler 2 for the Thursday florist pickup.");
                settleLayout(root);
                JLabel status = accessible(root, "Harvest entry mode and target", JLabel.class);
                assertThat(status.getText()).as("editor target survives a different history selection")
                        .contains("Editing #31");
                assertThat(status.getAccessibleContext().getAccessibleDescription())
                        .contains("31", "Nootka Rose");
                assertLabelFits(status, root, prefix + "-editing-target");
                render(prefix + "-editing-target", root);
                assertWorkspaceUsable(root, workspace, prefix + "-editing-target");

                status.setText("Harvest draft kept. Your changes to harvest #31 are still in the form. "
                        + "Refresh harvest history successfully before saving this edit. "
                        + "Review the saved history before retrying an action that may have completed.");
                settleLayout(root);
                assertLabelFits(status, root, prefix + "-recovery");
                render(prefix + "-recovery", root);
                assertWorkspaceUsable(root, workspace, prefix + "-recovery");

                workspace.setSelectedIndex(1);
                accessible(root, "Batch default bed or field", JTextField.class)
                        .setText("North field · Bed A");
                accessible(root, "Batch harvest notes", JTextField.class)
                        .setText("Cut before the heat; keep market and florist stock separate.");
                JTextArea lines = accessible(root, "Batch harvest lines", JTextArea.class);
                lines.setText("Nootka Rose,40.4,stems,North field\n"
                        + "Dahlia Mix,12.2,bunches,Tunnel 2\n"
                        + "Sweet Pea,3.7,buckets,South field\n");
                settleLayout(root);
                JLabel batchStatus = named(root, "harvest.batchStatus", JLabel.class);
                assertThat(batchStatus.getText()).contains("3 rows ready", "rounded by row");
                assertLabelFits(batchStatus, root, prefix + "-batch-ready");
                assertWorkspaceUsable(root, workspace, prefix + "-batch-ready");
                render(prefix + "-batch-ready", root);

                // Exercise the real post-save reveal without writing a harvest in a render test.
                ReflectionTestUtils.invokeMethod(initializedTab.get(), "showNewInventoryProducts",
                        List.of("Anemone · Mistral", "Ranunculus · Cloni"));
                JLabel refreshStatus = named(root, "harvest.refreshStatus", JLabel.class);
                refreshStatus.setText("Refresh queued · current harvest load finishing");
                JLabel reportStatus = named(root, "harvest.reportStatus", JLabel.class);
                reportStatus.setText("Report or export failed · retry");
                settleLayout(root);
                String setupView = prefix + "-new-inventory-setup";
                render(setupView, root);
                JLabel setupStatus = named(root, "harvest.newSkuStatus", JLabel.class);
                JButton review = named(root, "harvest.reviewNewSku", JButton.class);
                JButton refresh = accessible(root, "Refresh", JButton.class);
                assertThat(setupStatus.isVisible()).as("new inventory setup status").isTrue();
                assertThat(review.isVisible()).as("new inventory setup action").isTrue();
                assertThat(setupStatus.getText()).contains("2 new Inventory SKUs need setup");
                assertThat(review.getText()).isEqualTo("Review new Inventory SKUs");
                for (JLabel headerLabel : List.of(setupStatus, refreshStatus, reportStatus,
                        named(root, "farm.pageTitle", JLabel.class))) {
                    assertLabelFits(headerLabel, root, setupView);
                }
                for (JButton headerButton : List.of(refresh, review)) {
                    assertThat(headerButton.getWidth()).as("complete header action in %s", setupView)
                            .isGreaterThanOrEqualTo(headerButton.getPreferredSize().width);
                    assertReachable(headerButton,
                            new Rectangle(0, 0, headerButton.getWidth(), headerButton.getHeight()),
                            root, setupView);
                }
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
    }

    private static void assertWorkspaceUsable(JComponent root, JTabbedPane workspace, String view) {
        List<JComponent> controls = new ArrayList<>();
        collectControls(workspace.getSelectedComponent(), controls);
        assertThat(controls).as("populated harvest workspace %s", view).isNotEmpty();
        for (JComponent control : controls) {
            String label = control.getAccessibleContext().getAccessibleName();
            assertThat(label).as("human accessible name in %s", view).isNotBlank()
                    .doesNotStartWith("harvest.");
            if (control instanceof JTextField || control instanceof JComboBox<?>) {
                int minimumWidth = switch (control.getName() == null ? "" : control.getName()) {
                    case "harvest.crop" -> 140;
                    case "harvest.unit" -> 100;
                    default -> 65;
                };
                assertThat(control.getWidth()).as("readable field width for %s in %s", label, view)
                        .isGreaterThanOrEqualTo(minimumWidth);
                assertThat(control.getHeight()).as("comfortable field height for %s in %s", label, view)
                        .isGreaterThanOrEqualTo(28);
            }
            if (control instanceof JTextField) {
                assertThat(hasLabelFor(workspace, control))
                        .as("visible label associated with %s in %s", label, view).isTrue();
            }
            if (control instanceof JButton button) {
                int textWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText());
                assertThat(button.getWidth()).as("unclipped action label %s in %s", label, view)
                        .isGreaterThanOrEqualTo(textWidth + button.getInsets().left + button.getInsets().right);
                assertThat(button.getHeight()).as("comfortable action height %s in %s", label, view)
                        .isGreaterThanOrEqualTo(32);
            }
            Rectangle target = new Rectangle(0, 0, control.getWidth(), control.getHeight());
            if (control instanceof JTextArea) {
                target.width = Math.min(300, target.width);
                target.height = Math.min(60, target.height);
                assertThat(target.height).as("readable batch line area in %s", view)
                        .isGreaterThanOrEqualTo(40);
            }
            assertReachable(control, target, root, view);
        }
        assertFormsAvoidHorizontalScrolling(workspace.getSelectedComponent(), view);
    }

    private static void assertHistoryControlsReachable(JComponent root, String view) {
        List<JComponent> controls = List.of(
                accessible(root, "Filter by crop", JTextField.class),
                accessible(root, "Filter by bed", JTextField.class),
                accessible(root, "Filter by notes", JTextField.class),
                named(root, "harvest.filterFrom", JTextField.class),
                named(root, "harvest.filterTo", JTextField.class),
                accessible(root, "Apply filters", JButton.class),
                accessible(root, "Show all", JButton.class),
                accessible(root, "This week", JButton.class),
                named(root, "harvest.reportTools", JComboBox.class),
                named(root, "harvest.createReport", JButton.class),
                accessible(root, "Totals = filtered view", JCheckBox.class));
        for (JComponent control : controls) {
            String label = control.getAccessibleContext().getAccessibleName();
            assertThat(control.getWidth()).as("visible history control %s in %s", label, view)
                    .isGreaterThan(0);
            assertThat(control.getHeight()).as("visible history control %s in %s", label, view)
                    .isGreaterThan(0);
            if (control instanceof AbstractButton) {
                assertThat(control.getWidth()).as("unclipped history action %s in %s", label, view)
                        .isGreaterThanOrEqualTo(control.getPreferredSize().width);
            }
            if (control instanceof JTextField field && control.getName() != null
                    && control.getName().startsWith("harvest.filter")) {
                int dateWidth = field.getFontMetrics(field.getFont()).stringWidth(field.getText());
                assertThat(field.getWidth()).as("complete filter date %s in %s", label, view)
                        .isGreaterThanOrEqualTo(dateWidth + field.getInsets().left + field.getInsets().right);
            }
            assertReachable(control, new Rectangle(0, 0, control.getWidth(), control.getHeight()),
                    root, view);
        }
        render(view, root);
    }

    private static void assertLabelFits(JLabel label, JComponent root, String view) {
        assertThat(label).as("status label in %s", view).isNotNull();
        Object html = label.getClientProperty(BasicHTML.propertyKey);
        int textWidth;
        int textHeight;
        if (html instanceof View renderer) {
            textWidth = (int) Math.ceil(renderer.getPreferredSpan(View.X_AXIS));
            textHeight = (int) Math.ceil(renderer.getPreferredSpan(View.Y_AXIS));
        } else {
            textWidth = label.getFontMetrics(label.getFont()).stringWidth(label.getText());
            textHeight = label.getFontMetrics(label.getFont()).getHeight();
        }
        assertThat(label.getWidth() - label.getInsets().left - label.getInsets().right)
                .as("complete status message width in %s", view).isGreaterThanOrEqualTo(textWidth);
        assertThat(label.getHeight() - label.getInsets().top - label.getInsets().bottom)
                .as("complete status message height in %s", view).isGreaterThanOrEqualTo(textHeight);
        assertReachable(label, new Rectangle(0, 0, label.getWidth(), label.getHeight()), root, view);
    }

    private static void assertReachable(JComponent control, Rectangle target,
                                        JComponent root, String view) {
        revealThroughScrollPanes(control, target);
        Rectangle visible = SwingUtilities.convertRectangle(control,
                new Rectangle(0, 0, control.getWidth(), control.getHeight()), root);
        for (Container ancestor = control.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            visible = visible.intersection(SwingUtilities.convertRectangle(ancestor,
                    new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root));
            if (ancestor == root) {
                break;
            }
        }
        if (visible.width < target.width || visible.height < target.height) {
            render(view + "-unreachable", root);
        }
        assertThat(visible.width).as("reachable width for %s in %s", control.getName(), view)
                .isGreaterThanOrEqualTo(target.width);
        assertThat(visible.height).as("reachable height for %s in %s", control.getName(), view)
                .isGreaterThanOrEqualTo(target.height);
    }

    private static void revealThroughScrollPanes(JComponent control, Rectangle requested) {
        for (Container parent = control.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof JViewport viewport) {
                Component view = viewport.getView();
                Rectangle target = SwingUtilities.convertRectangle(control, requested, view);
                Point position = viewport.getViewPosition();
                int width = viewport.getExtentSize().width;
                int height = viewport.getExtentSize().height;
                int x = Math.min(position.x, target.x);
                int y = Math.min(position.y, target.y);
                if (target.x + target.width > x + width) {
                    x = target.x + target.width - width;
                }
                if (target.y + target.height > y + height) {
                    y = target.y + target.height - height;
                }
                x = Math.max(0, Math.min(x, Math.max(0, view.getWidth() - width)));
                y = Math.max(0, Math.min(y, Math.max(0, view.getHeight() - height)));
                viewport.setViewPosition(new Point(x, y));
            }
        }
    }

    private static void assertFormsAvoidHorizontalScrolling(Component component, String view) {
        if (!component.isVisible()) {
            return;
        }
        if (component instanceof JScrollPane scroll
                && !(scroll.getViewport().getView() instanceof JTextArea)) {
            assertThat(scroll.getViewport().getViewSize().width)
                    .as("form fits viewport width in %s", view)
                    .isLessThanOrEqualTo(scroll.getViewport().getExtentSize().width);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                assertFormsAvoidHorizontalScrolling(child, view);
            }
        }
    }

    private static void collectControls(Component component, List<JComponent> controls) {
        if (!component.isVisible()) {
            return;
        }
        boolean semanticButton = component instanceof AbstractButton button
                && button.getText() != null && !button.getText().isBlank();
        if (component instanceof JTextField || component instanceof JComboBox<?>
                || component instanceof JTextArea || semanticButton) {
            controls.add((JComponent) component);
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectControls(child, controls);
            }
        }
    }

    private static boolean hasLabelFor(Component component, Component input) {
        if (component instanceof JLabel label && label.getLabelFor() == input
                && label.getText() != null && !label.getText().isBlank()) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (hasLabelFor(child, input)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void settleLayout(Component root) {
        for (int pass = 0; pass < 4; pass++) {
            layoutRecursively(root);
        }
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static void render(String name, JComponent root) {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write harvest visual evidence", exception);
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return find(root, name, type, false);
    }

    private static <T extends Component> T accessible(Component root, String name, Class<T> type) {
        return find(root, name, type, true);
    }

    private static <T extends Component> T find(Component root, String name, Class<T> type,
                                                boolean byAccessibleName) {
        String candidate = byAccessibleName && root.getAccessibleContext() != null
                ? root.getAccessibleContext().getAccessibleName() : root.getName();
        if (type.isInstance(root) && name.equals(candidate)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, name, type, byAccessibleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static HarvestService fixture() {
        HarvestService service = mock(HarvestService.class);
        List<HarvestEntry> entries = List.of(
                new HarvestEntry(31L, LocalDate.now(), "Nootka Rose", 72, "stems",
                        "North field · Bed A", "Cut before 8 AM"),
                new HarvestEntry(32L, LocalDate.now(), "Dahlia Mix", 14, "bunches",
                        "Tunnel 2", "Reserved for market bouquets"));
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(entries);
        Map<String, Double> totals = Map.of("Nootka Rose", 72.0, "Dahlia Mix", 14.0);
        when(service.totalsByCrop()).thenReturn(totals);
        when(service.totalsByCrop(anyList())).thenReturn(totals);
        return service;
    }

    private static final class Host implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}

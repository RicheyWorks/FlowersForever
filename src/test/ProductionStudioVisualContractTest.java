package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.ProductionStudioService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
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
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Production Studio rendered human-interface contracts")
class ProductionStudioVisualContractTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "production-studio");

    @ParameterizedTest(name = "{0} theme at {1} by {2}")
    @CsvSource({"light, 850, 500", "dark, 850, 500", "light, 1180, 760", "dark, 1180, 760",
            "light, 1180, 1000"})
    @DisplayName("all production workflows fit the real laptop content area and remain reachable")
    void rendersEveryWorkspaceWithReachableControls(String theme, int width, int height)
            throws Exception {
        Files.createDirectories(OUTPUT);
        AtomicReference<JComponent> initialized = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            if ("dark".equals(theme)) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            FarmUi.installThemeDefaults();
            ProductionStudioTab tab = new ProductionStudioTab(fixture(),
                    new SwingDialogHarness().safelyCancelUnscripted());
            JComponent root = tab.getUIComponent();
            initialized.set(root);
            tab.initialize();
        });
        JComponent root = initialized.get();
        SwingUserHarness.on(root).awaitText("Production Studio refreshed");
        SwingUtilities.invokeAndWait(() -> {
            loadLibraryExamples(root);
            JTabbedPane workspace = named(root, "productionStudio.workspace", JTabbedPane.class);
            JTabbedPane plantings = named(root, "production.plantingWorkspace", JTabbedPane.class);
            workspace.setSelectedIndex(1);
            plantings.setSelectedIndex(1);
            named(root, "production.refreshTasks", JButton.class).doClick();
        });
        SwingUserHarness.on(root).awaitText("Production Studio refreshed");
        SwingUtilities.invokeAndWait(() -> {
            root.addNotify();
            try {
                root.setSize(width, height);
                JTabbedPane workspace = named(root, "productionStudio.workspace", JTabbedPane.class);
                JTabbedPane economics = named(root, "production.economicsWorkspace", JTabbedPane.class);
                JTabbedPane plantings = named(root, "production.plantingWorkspace", JTabbedPane.class);
                assertThat(plantings).as("separate planning and field-work views").isNotNull();
                for (int section = 0; section < workspace.getTabCount(); section++) {
                    workspace.setSelectedIndex(section);
                    JTabbedPane inner = section == 3 ? economics : section == 1 ? plantings : null;
                    int details = inner == null ? 1 : inner.getTabCount();
                    for (int detail = 0; detail < details; detail++) {
                        if (inner != null) {
                            inner.setSelectedIndex(detail);
                        }
                        String view = theme + "-" + width + "x" + height
                                + "-" + section + "-" + detail;
                        settleLayout(root);
                        scrollToTop(root);
                        render(view, root);
                        if (width == 1180 && height == 1000 && section == 1 && detail == 1) {
                            JTable tasks = named(root, "production.taskTable", JTable.class);
                            JLabel selected = named(root, "production.selectedTaskSummary", JLabel.class);
                            JLabel status = named(root, "production.status", JLabel.class);
                            JButton save = named(root, "production.markTaskDone", JButton.class);
                            assertThat(status.getText()).as("field-work gallery follows a work refresh")
                                    .contains("Production Studio refreshed")
                                    .doesNotContain("Previewed");
                            assertThat(tasks.getValueAt(0, 3)).as("open task status matches the filter")
                                    .isEqualTo("Open");
                            assertThat(tasks.getValueAt(0, 4)).as("task type is readable in the gallery")
                                    .isEqualTo("Soil prep");
                            assertThat(tasks.getValueAt(1, 4)).as("short task type stays readable")
                                    .isEqualTo("Sow");
                            assertThat(selected.getText()).contains("Task #31", "Soil prep");
                            render(view + "-gallery", root);
                            assertThat(visibleBounds(tasks.getTableHeader(), root).height)
                                    .as("task headers visible in the field-work gallery view")
                                    .isGreaterThanOrEqualTo(24);
                            assertThat(visibleBounds(selected, root).height)
                                    .as("selected task identity visible in the field-work gallery view")
                                    .isGreaterThanOrEqualTo(20);
                            assertThat(visibleBounds(save, root).height)
                                    .as("field-work save action visible in the gallery view")
                                    .isGreaterThanOrEqualTo(32);
                        }
                        if (width == 850 && section == 3 && detail == 3) {
                            JTextField order = named(root, "production.handoffOrder", JTextField.class);
                            assertThat(visibleBounds(order, root).height)
                                    .as("related order field is visible before scrolling at laptop size")
                                    .isGreaterThanOrEqualTo(28);
                        }
                        assertViewUsable(root, view);
                    }
                }
                workspace.setSelectedIndex(1);
                plantings.setSelectedIndex(1);
                JLabel context = named(root, "production.selectedTaskSummary", JLabel.class);
                assertThat(context.getText()).contains("Task #31", "Sunflower", "North field", "due");
                JLabel status = named(root, "production.status", JLabel.class);
                status.setText("Task labor, inputs, and field result saved. Some views still show previous data. "
                        + "Choose Refresh costs to recover the cost report; do not repeat the saved action. "
                        + "Your unsaved frost settings and recipe draft have been kept.");
                settleLayout(root);
                String recovery = theme + "-" + width + "x" + height + "-recovery-message";
                assertWrappedLabelFits(status, recovery);
                assertWrappedLabelFits(context, recovery);
                revealThroughScrollPanes(context,
                        new Rectangle(0, 0, context.getWidth(), context.getHeight()));
                render(recovery, root);
                assertViewUsable(root, recovery);
            } finally {
                root.removeNotify();
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
    }

    private static void assertViewUsable(JComponent root, String view) {
        List<JComponent> controls = new ArrayList<>();
        collectVisibleControls(root, controls);
        assertThat(controls).as("populated workflow %s", view).isNotEmpty();
        for (JComponent control : controls) {
            String name = control.getName();
            String accessibleName = control.getAccessibleContext().getAccessibleName();
            assertThat(accessibleName).as("human screen-reader name for %s in %s", name, view)
                    .isNotBlank().doesNotStartWith("production.");
            if (control instanceof JTextField || control instanceof JComboBox<?>) {
                assertThat(hasLabelFor(root, control))
                        .as("visible label associated with %s in %s", name, view).isTrue();
                assertThat(control.getWidth()).as("usable field width for %s in %s", name, view)
                        .isGreaterThanOrEqualTo(65);
                assertThat(control.getHeight()).as("usable field height for %s in %s", name, view)
                        .isGreaterThanOrEqualTo(28);
            }
            if (control instanceof AbstractButton button) {
                int textWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText());
                int horizontalInsets = button.getInsets().left + button.getInsets().right;
                assertThat(button.getWidth()).as("unclipped button label for %s in %s", name, view)
                        .isGreaterThanOrEqualTo(textWidth + horizontalInsets);
                assertThat(button.getHeight()).as("comfortable button height for %s in %s", name, view)
                        .isGreaterThanOrEqualTo(32);
            }
            Rectangle requested = new Rectangle(0, 0, control.getWidth(), control.getHeight());
            if (control instanceof JTable table) {
                assertIdentityColumnsReadable(table, view);
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, table);
                assertThat(viewport).as("scrollable table %s", name).isNotNull();
                assertThat(viewport.getHeight()).as("readable table viewport for %s in %s", name, view)
                        .isGreaterThanOrEqualTo(80);
                requested = new Rectangle(0, 0, Math.min(160, table.getWidth()),
                        Math.min(80, table.getHeight()));
            } else if (control instanceof JTextArea) {
                requested.height = Math.min(40, requested.height);
            }
            revealThroughScrollPanes(control, requested);
            Rectangle visible = visibleBounds(control, root);
            if (visible.width < requested.width || visible.height < requested.height) {
                render(view + "-unreachable-" + name.substring("production.".length()), root);
            }
            assertThat(visible.width).as("reachable width for %s in %s", name, view)
                    .isGreaterThanOrEqualTo(requested.width);
            assertThat(visible.height).as("reachable height for %s in %s", name, view)
                    .isGreaterThanOrEqualTo(requested.height);
            if (List.of("production.saveTemplate", "production.markTaskDone",
                    "production.saveRecipe", "production.createHandoff").contains(name)) {
                render(view + "-" + name.substring("production.".length()), root);
            }
        }
        assertFormsAvoidHorizontalScrolling(root, view);
    }

    private static void assertFormsAvoidHorizontalScrolling(Component component, String view) {
        if (!component.isVisible()) {
            return;
        }
        if (component instanceof JScrollPane scroll
                && !(scroll.getViewport().getView() instanceof JTable)
                && !(scroll.getViewport().getView() instanceof JTextArea)) {
            assertThat(scroll.getViewport().getViewSize().width)
                    .as("form fits the viewport width in %s", view)
                    .isLessThanOrEqualTo(scroll.getViewport().getExtentSize().width);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                assertFormsAvoidHorizontalScrolling(child, view);
            }
        }
    }

    private static void assertIdentityColumnsReadable(JTable table, String view) {
        if (table.getRowCount() == 0) {
            return;
        }
        for (int column = 0; column < table.getColumnCount(); column++) {
            String header = table.getColumnName(column);
            if (List.of("Template", "Recipe", "Crop / variety", "Crop / SKU", "Provider",
                    "External reference").contains(header)) {
                String value = String.valueOf(table.getValueAt(0, column));
                int readable = Math.min(240, table.getFontMetrics(table.getFont())
                        .stringWidth(value) + 16);
                assertThat(table.getColumnModel().getColumn(column).getWidth())
                        .as("readable %s identity in %s for %s", header, table.getName(), view)
                        .isGreaterThanOrEqualTo(readable);
            }
        }
    }

    private static void assertWrappedLabelFits(JLabel label, String view) {
        Object content = label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
        assertThat(content).as("wrapping renderer for %s in %s", label.getName(), view)
                .isInstanceOf(javax.swing.text.View.class);
        javax.swing.text.View renderer = (javax.swing.text.View) content;
        int width = label.getWidth() - label.getInsets().left - label.getInsets().right;
        int height = label.getHeight() - label.getInsets().top - label.getInsets().bottom;
        assertThat((int) Math.ceil(renderer.getPreferredSpan(javax.swing.text.View.X_AXIS)))
                .as("full message width for %s in %s", label.getName(), view).isLessThanOrEqualTo(width);
        assertThat((int) Math.ceil(renderer.getPreferredSpan(javax.swing.text.View.Y_AXIS)))
                .as("full message height for %s in %s", label.getName(), view).isLessThanOrEqualTo(height);
    }

    private static Rectangle visibleBounds(JComponent component, JComponent root) {
        Rectangle visible = SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), root);
        for (Container ancestor = component.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            Rectangle bounds = SwingUtilities.convertRectangle(ancestor,
                    new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root);
            visible = visible.intersection(bounds);
            if (ancestor == root) {
                return visible;
            }
        }
        throw new AssertionError("Control is not inside the rendered workspace: " + component.getName());
    }

    private static void revealThroughScrollPanes(JComponent control, Rectangle requested) {
        // A table's viewport consumes scrollRectToVisible. Follow each actual scroll
        // range from the inner table to the page, as a person using its scrollbars can.
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

    private static void collectVisibleControls(Component component, List<JComponent> controls) {
        if (!component.isVisible()) {
            return;
        }
        String name = component.getName();
        boolean input = component instanceof JTextField || component instanceof JComboBox<?>
                || component instanceof JTextArea || component instanceof JTable
                || component instanceof AbstractButton;
        if (input && name != null && name.startsWith("production.")) {
            controls.add((JComponent) component);
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectVisibleControls(child, controls);
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
        // A later pass sizes nested viewport views after their enclosing cards settle.
        for (int pass = 0; pass < 3; pass++) {
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

    private static void scrollToTop(Component component) {
        if (component instanceof JScrollPane scroll) {
            scroll.getVerticalScrollBar().setValue(0);
            scroll.getHorizontalScrollBar().setValue(0);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                scrollToTop(child);
            }
        }
    }

    private static void render(String name, JComponent root) {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write production visual evidence", exception);
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = named(child, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ProductionStudioService fixture() {
        ProductionStudioService service = mock(ProductionStudioService.class);
        int year = Year.now().getValue();
        LocalDate today = LocalDate.now();
        when(service.findSeasonSettings(year)).thenReturn(Optional.of(new FarmSeasonSettings(
                year, LocalDate.of(year, 4, 20), LocalDate.of(year, 10, 10),
                "Farm weather station · reviewed April 1", 27.50)));
        GrowingTemplate sunflower = new GrowingTemplate("Sunflower · succession crop", "Sunflower",
                "ProCut Plum", 9, 4, -14, null, 65, 14, 14, 1, 90, 10, 7, true,
                "Direct sow into prepared beds; check drip irrigation before each succession.");
        sunflower.setId(3L);
        GrowingTemplate zinnia = new GrowingTemplate("Zinnia · summer cutting", "Zinnia",
                "Benary's Giant", 9, 4, -42, 7, 90, 21, 14, 3, 85, 14, 10, true,
                "Pinch established plants and record stem quality at harvest.");
        zinnia.setId(4L);
        when(service.listTemplates()).thenReturn(List.of(sunflower, zinnia));
        FloralRecipe recipe = new FloralRecipe("Saturday market bouquet", "Hand-tied bouquet",
                35, 12, 27.50, 15, 10, "Warm colors with long stems; wrap in kraft paper.");
        recipe.setId(11L);
        recipe.addIngredient(new FloralRecipeIngredient("Sunflower", 3, 10, 12, ""));
        recipe.addIngredient(new FloralRecipeIngredient("Zinnia", 5, 10, 9, ""));
        when(service.listRecipes()).thenReturn(List.of(recipe));
        ProductionTask task = new ProductionTask(21L, 3L, today,
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO,
                "Sunflower", "North field · Bed A", 1, true, "Check drip lines before sowing.");
        task.setId(31L);
        ProductionTask second = new ProductionTask(22L, 4L, today.plusDays(1),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO,
                "Zinnia", "Tunnel 2", 1, true, "Label the next succession.");
        second.setId(32L);
        when(service.listTasks(any(), any())).thenReturn(List.of(task, second));
        when(service.draftPlantings(anyLong(), anyInt(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(new ProductionStudioService.PlantingDraft(year, 3L,
                        sunflower.getName(), "Sunflower", "ProCut Plum", "Bed A", 50, 1,
                        today, null, today.plusDays(65), today.plusDays(79), 266, 239, 239,
                        false, "Inside the reviewed frost window")));
        when(service.availability(any())).thenReturn(List.of(
                new ProductionStudioService.AvailabilityLine("Sunflower", today, today.plusDays(6),
                        239, 80, 319, "North field · September succession"),
                new ProductionStudioService.AvailabilityLine("Zinnia", today, today.plusDays(6),
                        420, 120, 540, "Tunnel 2 · summer cutting")));
        ProductionStudioService.PlantingCostLine costs = new ProductionStudioService.PlantingCostLine(
                21L, "Sunflower", "ProCut Plum", "North field · Bed A", SeasonPlanStatus.HARVESTING,
                239, 180, 6, 4, 150, 68.75, 24, 92.75, 92.75 / 239, 92.75 / 180, 66.7);
        when(service.productionCosts(year, null)).thenReturn(new ProductionStudioService.ProductionCostReport(
                year, 27.50, "SEASON_SETTINGS", List.of(costs), 239, 180, 6, 4, 150,
                68.75, 24, 92.75, 92.75, 92.75 / 239, 92.75 / 180, 66.7,
                0, 0, 0, 0, 0, 0, 12, 1, List.of("1 harvest could not be matched to a planting.")));
        when(service.listHandoffs()).thenReturn(List.of(new IntegrationHandoff("MARKETPLACE",
                "Saturday market", "READY", "autumn-bouquet-26", null, 11L, 35, "USD",
                "Reviewed bouquet availability for Saturday pickup.")));
        return service;
    }

    private static void loadLibraryExamples(JComponent root) {
        // These are read-only UI actions on detached mocks, never on the local farm database.
        named(root, "production.templateTable", JTable.class).setRowSelectionInterval(0, 0);
        named(root, "production.loadTemplate", JButton.class).doClick();
        named(root, "production.recipeTable", JTable.class).setRowSelectionInterval(0, 0);
        named(root, "production.loadRecipe", JButton.class).doClick();
        named(root, "production.taskTable", JTable.class).setRowSelectionInterval(0, 0);
        named(root, "production.draftPlantings", JButton.class).doClick();
    }
}

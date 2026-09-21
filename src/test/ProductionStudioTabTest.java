package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.ProductionStudioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Production Studio desktop")
class ProductionStudioTabTest {

    @Test
    @DisplayName("keeps task filters, table, and every action reachable at compact desktop size")
    void keepsTaskControlsVisible() throws Exception {
        ProductionStudioTab tab = new ProductionStudioTab(mock(ProductionStudioService.class),
                new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        JTabbedPane workspace = (JTabbedPane) named(root, "productionStudio.workspace");
        SwingUtilities.invokeAndWait(() -> {
            workspace.setSelectedIndex(1);
            ((JTabbedPane) named(root, "production.plantingWorkspace")).setSelectedIndex(1);
        });
        SwingUserHarness user = SwingUserHarness.on(root).layoutAt(1050, 700);

        for (String name : List.of("production.taskStatusFilter", "production.taskTable",
                "production.markTaskDone", "production.skipTask", "production.reopenTask",
                "production.regenerateTasks")) {
            JComponent control = (JComponent) named(root, name);
            SwingUtilities.invokeAndWait(() -> {
                java.awt.Rectangle requested = new java.awt.Rectangle(0, 0,
                        Math.min(control.getWidth(), 140),
                        Math.min(control.getHeight(), control instanceof JTable ? 80 : 30));
                for (Container parent = control.getParent(); parent != null;
                     parent = parent.getParent()) {
                    if (parent instanceof JComponent component) {
                        component.scrollRectToVisible(SwingUtilities.convertRectangle(
                                control, requested, component));
                    }
                }
            });
            assertThat(user.visibleExtent(name).width()).as(name).isGreaterThan(55);
            assertThat(user.visibleExtent(name).height()).as(name)
                    .isGreaterThan(control instanceof JTable ? 70 : 27);
        }
    }

    @Test
    @DisplayName("reloads the selected season anchors and preserves working selections")
    void reloadsFrostAndPreservesSelections() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        GrowingTemplate firstTemplate = template(1L, "Sunflower production", "Sunflower");
        GrowingTemplate secondTemplate = template(2L, "Zinnia production", "Zinnia");
        FloralRecipe firstRecipe = recipe(10L, "Market bouquet");
        FloralRecipe secondRecipe = recipe(11L, "Event centerpiece");
        int year = Year.now().getValue();
        when(service.findSeasonSettings(year)).thenReturn(Optional.of(
                new FarmSeasonSettings(year, LocalDate.of(year, 4, 20),
                        LocalDate.of(year, 10, 10), "Farm station")));
        when(service.listTemplates()).thenReturn(List.of(firstTemplate, secondTemplate));
        when(service.listRecipes()).thenReturn(List.of(firstRecipe, secondRecipe));

        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JComboBox<?> planting = (JComboBox<?>) named(root, "production.plantingTemplate");
        JComboBox<?> estimates = (JComboBox<?>) named(root, "production.estimateRecipe");

        assertThat(((JTextField) named(root, "production.springFrost")).getText())
                .isEqualTo(year + "-04-20");
        assertThat(((JTextField) named(root, "production.fallFrost")).getText())
                .isEqualTo(year + "-10-10");
        assertThat(((JTextField) named(root, "production.loadedLaborRate")).getText())
                .isEqualTo("0.00");
        SwingUtilities.invokeAndWait(() -> {
            planting.setSelectedIndex(1);
            estimates.setSelectedIndex(1);
            tab.refreshData();
        });

        awaitRefresh(root);
        assertThat(((GrowingTemplate) planting.getSelectedItem()).getId()).isEqualTo(2L);
        assertThat(((FloralRecipe) estimates.getSelectedItem()).getId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("renders planting costs and preserves visible allocation warnings")
    void rendersProductionCosts() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        int year = Year.now().getValue();
        ProductionStudioService.PlantingCostLine line =
                new ProductionStudioService.PlantingCostLine(
                        7L, "Dahlia", "Cafe au Lait", "Bed A",
                        SeasonPlanStatus.HARVESTING, 100, 80,
                        2, 1, 120, 50, 30, 80, 0.8, 1.0, 50);
        when(service.productionCosts(year, null)).thenReturn(
                new ProductionStudioService.ProductionCostReport(
                        year, 25, "SEASON_SETTINGS", List.of(line), 100, 80,
                        2, 1, 120, 50, 30, 80, 80, 0.8, 1.0, 50,
                        0, 0, 0, 0, 0, 0, 5, 1,
                        List.of("1 harvest could not be matched.")));
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();

        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);

        JTable costs = (JTable) named(root, "production.costTable");
        assertThat(costs.getRowCount()).isEqualTo(1);
        assertThat(costs.getValueAt(0, 1)).isEqualTo("Dahlia · Cafe au Lait");
        assertThat(costs.getValueAt(0, 7)).isEqualTo("$80.00");
        assertThat(((javax.swing.JTextArea) named(root, "production.costSummary")).getText())
                .contains("Actuals 1/2 tasks", "allocated cost/harvested stem $1.00",
                        "could not be matched");
    }

    @Test
    @DisplayName("an unsaved season replaces stale frost anchors with reviewable defaults")
    void resetsUnsavedSeasonAnchors() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        int currentYear = Year.now().getValue();
        int nextYear = currentYear + 1;
        when(service.findSeasonSettings(currentYear)).thenReturn(Optional.of(
                new FarmSeasonSettings(currentYear, LocalDate.of(currentYear, 4, 20),
                        LocalDate.of(currentYear, 10, 10), "Farm station")));
        when(service.findSeasonSettings(nextYear)).thenReturn(Optional.empty());
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JTextField year = (JTextField) named(root, "production.seasonYear");

        SwingUtilities.invokeAndWait(() -> {
            year.setText(Integer.toString(nextYear));
            year.postActionEvent();
        });

        SwingUserHarness.on(root).awaitText("No saved frost anchors");
        assertThat(((JTextField) named(root, "production.springFrost")).getText())
                .isEqualTo(nextYear + "-04-15");
        assertThat(((JTextField) named(root, "production.fallFrost")).getText())
                .isEqualTo(nextYear + "-10-15");
        assertThat(((JTextField) named(root, "production.frostSource")).getText())
                .isEqualTo("Farm estimate");
        assertThat(((JLabel) named(root, "production.status")).getText())
                .contains("No saved frost anchors", Integer.toString(nextYear));
    }

    @Test
    @DisplayName("triages open work through skipped and reopened states")
    void triagesTaskStates() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        ProductionTask task = new ProductionTask(4L, 2L, LocalDate.now().minusDays(1),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Zinnia", "Bed A",
                1, true, "Direct sow");
        task.setId(19L);
        when(service.listTasks(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(task));
        when(service.updateTaskStatus(anyLong(), any())).thenAnswer(invocation -> {
            task.setStatus(invocation.getArgument(1));
            return task;
        });
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JTable table = (JTable) named(root, "production.taskTable");
        JButton skip = (JButton) named(root, "production.skipTask");
        JButton reopen = (JButton) named(root, "production.reopenTask");
        JComboBox<?> filter = (JComboBox<?>) named(root, "production.taskStatusFilter");

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 2)).isEqualTo("Overdue");
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            assertThat(skip.isEnabled()).isTrue();
            assertThat(reopen.isEnabled()).isFalse();
            skip.doClick();
            filter.setSelectedItem("Skipped");
        });
        assertThat(table.getRowCount()).isEqualTo(1);
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            assertThat(skip.isEnabled()).isFalse();
            assertThat(reopen.isEnabled()).isTrue();
            reopen.doClick();
            filter.setSelectedItem("Open");
        });

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(ProductionTaskStatus.TODO);
        assertThat(((JLabel) named(root, "production.status")).getText())
                .contains("now todo");
        verify(service).updateTaskStatus(19L, ProductionTaskStatus.SKIPPED);
        verify(service).updateTaskStatus(19L, ProductionTaskStatus.TODO);
    }

    @Test
    @DisplayName("completes selected field work with its labor inputs and observation")
    void completesTaskWithActuals() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        ProductionTask task = new ProductionTask(4L, 2L, LocalDate.now(),
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO,
                "Zinnia", "Bed A", 1, true, "Prepare bed");
        task.setId(19L);
        when(service.listTasks(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(task));
        when(service.recordTaskActuals(anyLong(), any())).thenAnswer(invocation -> {
            ProductionStudioService.TaskActuals actuals = invocation.getArgument(1);
            task.recordActuals(actuals.actualMinutes(), actuals.inputCost(),
                    actuals.fieldObservation(), java.time.Instant.now());
            return task;
        });
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JTable table = (JTable) named(root, "production.taskTable");
        JTextField minutes = (JTextField) named(root, "production.taskActualMinutes");
        JTextField cost = (JTextField) named(root, "production.taskInputCost");
        JTextField observation = (JTextField) named(
                root, "production.taskFieldObservation");
        JButton complete = (JButton) named(root, "production.markTaskDone");

        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            minutes.setText("65");
            cost.setText("17.25");
            observation.setText("Added compost; irrigation pressure looked even");
            complete.doClick();
        });

        SwingUserHarness.on(root).awaitText("field result saved");
        var captor = org.mockito.ArgumentCaptor.forClass(
                ProductionStudioService.TaskActuals.class);
        verify(service).recordTaskActuals(
                org.mockito.ArgumentMatchers.eq(19L), captor.capture());
        assertThat(captor.getValue().actualMinutes()).isEqualTo(65);
        assertThat(captor.getValue().inputCost()).isEqualTo(17.25);
        assertThat(captor.getValue().fieldObservation())
                .isEqualTo("Added compost; irrigation pressure looked even");
        assertThat(task.getStatus()).isEqualTo(ProductionTaskStatus.DONE);
        assertThat(((JLabel) named(root, "production.status")).getText())
                .contains("labor, input cost, and field result saved");
    }

    @Test
    @DisplayName("regeneration requires a clear impact confirmation")
    void confirmsTaskRegeneration() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        ProductionTask task = new ProductionTask(4L, 2L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Zinnia", "Bed A",
                1, true, "Direct sow");
        task.setId(19L);
        when(service.listTasks(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(task));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Regenerate production work", true);
        ProductionStudioTab tab = new ProductionStudioTab(service, dialogs);
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JTable table = (JTable) named(root, "production.taskTable");
        JButton regenerate = (JButton) named(root, "production.regenerateTasks");

        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            regenerate.doClick();
        });

        verify(service).regenerateTasks(4L, 2L);
        assertThat(((JLabel) named(root, "production.status")).getText())
                .contains("Open generated work was rebuilt");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("planting edits visibly invalidate an earlier schedule preview")
    void invalidatesChangedPlantingPreview() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        GrowingTemplate template = template(3L, "Cosmos production", "Cosmos");
        when(service.listTemplates()).thenReturn(List.of(template));
        ProductionStudioService.PlantingDraft draft = new ProductionStudioService.PlantingDraft(
                Year.now().getValue(), 3L, template.getName(), "Cosmos", "Sensation",
                "Bed A", 50, 1, LocalDate.now(), null, LocalDate.now().plusDays(80),
                LocalDate.now().plusDays(100), 200, 170, 510, false, "Inside window");
        when(service.draftPlantings(anyLong(), anyInt(), anyString(),
                org.mockito.ArgumentMatchers.anyDouble(), anyInt())).thenReturn(List.of(draft));
        when(service.plantingFingerprint(List.of(draft))).thenReturn("preview-token");
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JButton preview = (JButton) named(root, "production.draftPlantings");
        JTable table = (JTable) named(root, "production.draftTable");
        JTextField bed = (JTextField) named(root, "production.plantingBed");

        SwingUtilities.invokeAndWait(preview::doClick);
        assertThat(table.getRowCount()).isEqualTo(1);
        SwingUtilities.invokeAndWait(() -> bed.setText("Bed B"));

        assertThat(table.getRowCount()).isZero();
        assertThat(((JLabel) named(root, "production.status")).getText())
                .contains("inputs changed", "preview the schedule again");
    }

    @Test
    @DisplayName("handoff statuses match the selected external workflow")
    void scopesHandoffStatuses() throws Exception {
        ProductionStudioTab tab = new ProductionStudioTab(mock(ProductionStudioService.class),
                new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        JComboBox<?> type = (JComboBox<?>) named(root, "production.handoffType");
        JComboBox<?> status = (JComboBox<?>) named(root, "production.handoffStatus");

        assertThat(comboItems(status)).contains("ACCEPTED", "FAILED", "CANCELLED")
                .doesNotContain("PAID");
        SwingUtilities.invokeAndWait(() -> type.setSelectedItem("PAYMENT"));
        assertThat(comboItems(status)).contains("PAID", "FAILED", "CANCELLED")
                .doesNotContain("ACCEPTED");
        SwingUtilities.invokeAndWait(() -> type.setSelectedItem("MARKETPLACE"));
        assertThat(comboItems(status)).contains("DRAFT", "READY", "SENT", "FAILED", "CANCELLED")
                .doesNotContain("ACCEPTED", "PAID");
    }

    @Test
    @DisplayName("selection-aware actions remain read-only for a viewer")
    void keepsSelectionActionsRoleSafe() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        when(service.listTemplates()).thenReturn(List.of(
                template(3L, "Cosmos production", "Cosmos")));
        when(service.listRecipes()).thenReturn(List.of(recipe(8L, "Market bouquet")));
        ProductionTask task = new ProductionTask(4L, 3L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Cosmos", "Bed A",
                1, true, "Direct sow");
        task.setId(19L);
        when(service.listTasks(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(task));
        ProductionStudioTab tab = new ProductionStudioTab(
                service, new SwingDialogHarness().safelyCancelUnscripted());
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(false);
            ((JTable) named(root, "production.templateTable"))
                    .setRowSelectionInterval(0, 0);
            ((JTable) named(root, "production.recipeTable"))
                    .setRowSelectionInterval(0, 0);
            ((JTable) named(root, "production.taskTable"))
                    .setRowSelectionInterval(0, 0);
        });

        assertThat(((JButton) named(root, "production.loadTemplate")).isEnabled()).isTrue();
        assertThat(((JButton) named(root, "production.loadRecipe")).isEnabled()).isTrue();
        assertThat(((JButton) named(root, "production.draftPlantings")).isEnabled()).isTrue();
        assertThat(((JButton) named(root, "production.deleteTemplate")).isEnabled()).isFalse();
        assertThat(((JButton) named(root, "production.deleteRecipe")).isEnabled()).isFalse();
        assertThat(((JButton) named(root, "production.commitPlantings")).isEnabled()).isFalse();
        assertThat(((JButton) named(root, "production.markTaskDone")).isEnabled()).isFalse();
        assertThat(((JButton) named(root, "production.regenerateTasks")).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("reviews plan impact before creating duplicateable blocks and work")
    void confirmsPlantingCommit() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        GrowingTemplate template = template(3L, "Cosmos production", "Cosmos");
        when(service.listTemplates()).thenReturn(List.of(template));
        ProductionStudioService.PlantingDraft draft = new ProductionStudioService.PlantingDraft(
                Year.now().getValue(), 3L, template.getName(), "Cosmos", "Sensation",
                "Bed A", 50, 1, LocalDate.now(), null, LocalDate.now().plusDays(80),
                LocalDate.now().plusDays(100), 200, 170, 510, false, "Inside window");
        when(service.draftPlantings(anyLong(), anyInt(), anyString(),
                org.mockito.ArgumentMatchers.anyDouble(), anyInt())).thenReturn(List.of(draft));
        when(service.plantingFingerprint(List.of(draft))).thenReturn("preview-token");
        SeasonPlanEntry plan = new SeasonPlanEntry(Year.now().getValue(), "Cosmos", "Sensation",
                "Bed A", draft.sowDate(), null, draft.harvestStart(), draft.harvestEnd(),
                50, 510, SeasonPlanStatus.PLANNED, "");
        plan.setId(31L);
        ProductionTask task = new ProductionTask(31L, 3L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Cosmos", "Bed A",
                1, true, "Sow");
        when(service.commitPlantings(anyLong(), anyInt(), anyString(),
                org.mockito.ArgumentMatchers.anyDouble(), anyInt(),
                org.mockito.ArgumentMatchers.eq("preview-token"))).thenReturn(List.of(
                new ProductionStudioService.CommittedPlanting(plan, List.of(task))));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Commit production plan", true);
        ProductionStudioTab tab = new ProductionStudioTab(service, dialogs);
        JComponent root = tab.getUIComponent();
        SwingUtilities.invokeAndWait(tab::initialize);
        awaitRefresh(root);
        JButton commit = (JButton) named(root, "production.commitPlantings");

        SwingUtilities.invokeAndWait(commit::doClick);

        verify(service).commitPlantings(
                3L, Year.now().getValue(), "Bed A", 50, 1, "preview-token");
        assertThat(((JLabel) named(root, "production.status")).getText())
                .isEqualTo("Committed 1 planting block(s) and 1 production task(s).");
        dialogs.assertComplete();
    }

    private static void awaitRefresh(JComponent root) {
        SwingUserHarness.on(root).awaitText("Production Studio refreshed.");
    }

    private static GrowingTemplate template(Long id, String name, String crop) {
        GrowingTemplate template = new GrowingTemplate(name, crop, "", 9, 4,
                -42, null, 90, 21, 14, 3, 85, 14, 10, true, "");
        template.setId(id);
        return template;
    }

    private static FloralRecipe recipe(Long id, String name) {
        FloralRecipe recipe = new FloralRecipe(name, "Bouquet", 35,
                12, 25, 15, 10, "");
        recipe.setId(id);
        recipe.addIngredient(new FloralRecipeIngredient("Zinnia", 5, 10, 9, ""));
        return recipe;
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

    private static List<String> comboItems(JComboBox<?> combo) {
        return java.util.stream.IntStream.range(0, combo.getItemCount())
                .mapToObj(index -> String.valueOf(combo.getItemAt(index)))
                .toList();
    }
}

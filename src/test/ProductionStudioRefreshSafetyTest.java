package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.service.ProductionStudioService;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionStudioRefreshSafetyTest {
    private static final int YEAR = Year.now().getValue();

    @Test
    void initialLoadRunsOffEdtAndKeepsEditsMadeWhileDataArrives() throws Exception {
        ProductionStudioService service = service();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.listTemplates()).thenAnswer(invocation -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });
        Fixture fixture = build(service, new SwingDialogHarness(), false);
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.user().type("Source / station", "Measured at the north bed")
                    .type("Labor cost per hour ($)", "42.50");
            assertThat(fixture.tab().applicationExitWarning()).contains("unsaved frost");
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("Unsaved frost or task drafts were kept");
        assertThat(fixture.user().fieldValue("Source / station"))
                .isEqualTo("Measured at the north bed");
        assertThat(fixture.user().fieldValue("Labor cost per hour ($)")).isEqualTo("42.50");
    }

    @Test
    void refreshPreservesFrostAndSelectedTaskDraftAcrossRowReordering() throws Exception {
        ProductionStudioService service = service();
        ProductionTask first = task(19L, "Bed A");
        ProductionTask other = task(20L, "Bed B");
        AtomicReference<List<ProductionTask>> rows = new AtomicReference<>(List.of(first, other));
        when(service.listTasks(any(), any())).thenAnswer(invocation -> rows.get());
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        fixture.user().type("Source / station", "Unsaved station")
                .type("Labor cost per hour ($)", "39.25");
        work(fixture).selectRow("production.taskTable", 0)
                .type("Crew minutes", "32")
                .type("Input cost ($)", "4.50")
                .type("Field result or observation", "Compost before rain");
        rows.set(List.of(other, first));
        fixture.user().click("Refresh studio").awaitText("Unsaved frost or task drafts were kept");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("32");
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("Compost before rain");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isTrue();
        fixture.user().selectTab("Frost & crop templates");
        assertThat(fixture.user().fieldValue("Source / station")).isEqualTo("Unsaved station");
        assertThat(fixture.user().fieldValue("Labor cost per hour ($)")).isEqualTo("39.25");
    }

    @Test
    void invalidRefreshFilterIsContainedWithoutClearingDataOrDrafts() throws Exception {
        ProductionStudioService service = service();
        when(service.listTasks(any(), any())).thenReturn(List.of(task(19L, "Bed A")));
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        fixture.user().type("Season year", "unfinished")
                .type("Source / station", "Keep this draft").click("Refresh studio")
                .awaitText("Refresh paused");
        assertThat(fixture.user().fieldValue("Source / station")).isEqualTo("Keep this draft");
        assertThat(fixture.user().tableRows("production.taskTable")).isEqualTo(1);
        verify(service, times(1)).listTasks(any(), any());
    }

    @Test
    void failedRefreshPreservesViewsAndDraftsAndCanBeRetried() throws Exception {
        ProductionStudioService service = service();
        when(service.listTasks(any(), any())).thenReturn(List.of(task(19L, "Bed A")));
        AtomicBoolean fail = new AtomicBoolean();
        when(service.listHandoffs()).thenAnswer(invocation -> {
            if (fail.get()) {
                throw new IllegalStateException("Database is temporarily busy");
            }
            return List.of();
        });
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Crew minutes", "45");
        fail.set(true);
        fixture.user().click("Refresh studio").awaitText("Could not refresh Production Studio");
        assertThat(fixture.user().tableRows("production.taskTable")).isEqualTo(1);
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("45");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
        fail.set(false);
        fixture.user().click("Refresh studio").awaitText("Unsaved frost or task drafts were kept");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("45");
    }

    @Test
    void lateResponseCannotApplyDataForEditedFilters() throws Exception {
        ProductionStudioService service = service();
        when(service.listTasks(any(), any())).thenReturn(List.of(task(19L, "Bed A")));
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.listTasks(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(task(20L, "Different bed"));
        });
        fixture.user().click("Refresh studio");
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.user().type("Season year", Integer.toString(YEAR + 1));
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("Filters changed while loading");
        assertThat(fixture.user().tableValue("production.taskTable", 0, 0)).isEqualTo(19L);
    }

    @Test
    void taskDraftsFollowTaskIdentityWhenSwitchingRowsAndFilters() throws Exception {
        ProductionStudioService service = service();
        when(service.listTasks(any(), any())).thenReturn(
                List.of(task(19L, "Bed A"), task(20L, "Bed B")));
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Field result or observation", "First bed draft")
                .selectRow("production.taskTable", 1)
                .type("Field result or observation", "Second bed draft")
                .selectRow("production.taskTable", 0);
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("First bed draft");
        fixture.user().select("Show tasks", "Done");
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("First bed draft");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
        fixture.user().select("Show tasks", "Open")
                .selectRow("production.taskTable", 1);
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("Second bed draft");
    }

    @Test
    void changedTaskContextKeepsDraftButRequiresReviewBeforeSaving() throws Exception {
        ProductionStudioService service = service();
        AtomicReference<ProductionTask> task = new AtomicReference<>(task(19L, "Bed A"));
        when(service.listTasks(any(), any())).thenAnswer(invocation -> List.of(task.get()));
        SwingDialogHarness dialogs = new SwingDialogHarness().confirm("Discard task draft", true);
        Fixture fixture = build(service, dialogs, true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Crew minutes", "27");
        task.set(task(19L, "Changed bed"));
        fixture.user().click("Refresh studio").awaitText("saved task changed");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("27");
        assertThat(fixture.user().text("production.selectedTaskSummary"))
                .contains("Task #19", "Bed A", "Saved task changed");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
        fixture.user().click("Discard task draft");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("0");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isTrue();
        verify(service, never()).recordTaskActuals(anyLong(), any());
        dialogs.assertComplete();
    }

    @Test
    void missingTaskKeepsItsDraftWithoutAttachingItToAnotherTask() throws Exception {
        ProductionStudioService service = service();
        AtomicReference<List<ProductionTask>> rows = new AtomicReference<>(
                List.of(task(19L, "Bed A"), task(20L, "Bed B")));
        when(service.listTasks(any(), any())).thenAnswer(invocation -> rows.get());
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Field result or observation", "A removed task still has my notes");
        rows.set(List.of(task(20L, "Bed B")));
        fixture.user().click("Refresh studio").awaitText("no longer in this view");
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("A removed task still has my notes");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
        assertThat(fixture.user().text("production.selectedTaskSummary"))
                .contains("Task #19", "Bed A");
        fixture.user().selectRow("production.taskTable", 0);
        assertThat(fixture.user().fieldValue("Field result or observation")).isEmpty();
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved");
    }

    @Test
    void frostReloadNeedsExplicitDiscardAndClearsTheExitWarningAfterSuccess() throws Exception {
        ProductionStudioService service = service();
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Reload frost anchors", false).confirm("Reload frost anchors", true);
        Fixture fixture = build(service, dialogs, true);
        fixture.user().type("Source / station", "Unsaved station")
                .click("Reload frost anchors");
        assertThat(fixture.user().fieldValue("Source / station")).isEqualTo("Unsaved station");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved");
        fixture.user().click("Reload frost anchors").awaitText("Loaded saved frost anchors");
        assertThat(fixture.user().fieldValue("Source / station")).isEqualTo("Farm station");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        dialogs.assertComplete();
    }

    @Test
    void retainedDraftForARemovedTaskCanBeExplicitlyDiscardedFromAnotherTask() throws Exception {
        ProductionStudioService service = service();
        AtomicReference<List<ProductionTask>> rows = new AtomicReference<>(
                List.of(task(19L, "Bed A"), task(20L, "Bed B")));
        when(service.listTasks(any(), any())).thenAnswer(invocation -> rows.get());
        SwingDialogHarness dialogs = new SwingDialogHarness().confirm("Discard task draft", true);
        Fixture fixture = build(service, dialogs, true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Field result or observation", "Removed task draft");
        rows.set(List.of(task(20L, "Bed B")));
        fixture.user().click("Refresh studio").awaitText("no longer in this view")
                .selectRow("production.taskTable", 0).click("Discard task draft");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        dialogs.assertComplete();
    }

    @Test
    void asyncTaskSaveCapturesTheReviewedTaskAndKeepsTheUiResponsive() throws Exception {
        ProductionStudioService service = service();
        ProductionTask task = task(19L, "Bed A");
        when(service.listTasks(any(), any())).thenReturn(List.of(task));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.recordTaskActuals(anyLong(), any())).thenAnswer(invocation -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            assertThat((Long) invocation.getArgument(0)).isEqualTo(19L);
            ProductionStudioService.TaskActuals actuals = invocation.getArgument(1);
            assertThat(actuals.actualMinutes()).isEqualTo(38);
            assertThat(actuals.fieldObservation()).isEqualTo("Captured crew note");
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            task.recordActuals(actuals.actualMinutes(), actuals.inputCost(),
                    actuals.fieldObservation(), Instant.now());
            return task;
        });
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Crew minutes", "38")
                .type("Field result or observation", "Captured crew note")
                .click("Save actuals + done");
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
            assertThat(fixture.user().isFieldEditable("Crew minutes")).isFalse();
            assertThat(fixture.tab().applicationExitWarning()).contains("saving task actuals");
            fixture.user().selectTab("Design recipes").type("Recipe name", "Still responsive");
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("field result saved");
        assertThat(fixture.user().fieldValue("Recipe name")).isEqualTo("Still responsive");
        verify(service, times(1)).recordTaskActuals(anyLong(), any());
    }

    @Test
    void failedTaskSaveKeepsTheExactDraftForReviewAndRetry() throws Exception {
        ProductionStudioService service = service();
        when(service.listTasks(any(), any())).thenReturn(List.of(task(19L, "Bed A")));
        when(service.recordTaskActuals(anyLong(), any()))
                .thenThrow(new IllegalStateException("Task was changed by another worker"));
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0)
                .type("Crew minutes", "49")
                .type("Input cost ($)", "8.25")
                .type("Field result or observation", "Keep the crew note")
                .click("Save actuals + done").awaitText("Task actuals could not be saved");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("49");
        assertThat(fixture.user().fieldValue("Input cost ($)")).isEqualTo("8.25");
        assertThat(fixture.user().fieldValue("Field result or observation"))
                .isEqualTo("Keep the crew note");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
    }

    @Test
    void successfulSaveWithFailedTaskReadbackRequiresRefreshBeforeAnotherWrite() throws Exception {
        ProductionStudioService service = service();
        ProductionTask task = task(19L, "Bed A");
        AtomicBoolean saved = new AtomicBoolean();
        AtomicBoolean readsAvailable = new AtomicBoolean(true);
        when(service.listTasks(any(), any())).thenAnswer(invocation -> {
            if (!readsAvailable.get()) {
                throw new IllegalStateException("Task read temporarily unavailable");
            }
            return List.of(task);
        });
        when(service.recordTaskActuals(anyLong(), any())).thenAnswer(invocation -> {
            ProductionStudioService.TaskActuals actuals = invocation.getArgument(1);
            task.recordActuals(actuals.actualMinutes(), actuals.inputCost(),
                    actuals.fieldObservation(), Instant.now());
            saved.set(true);
            readsAvailable.set(false);
            return task;
        });
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).select("Show tasks", "All").selectRow("production.taskTable", 0)
                .type("Crew minutes", "37").click("Save actuals + done")
                .awaitText("do not repeat the saved action");
        assertThat(saved.get()).isTrue();
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
        assertThat(fixture.user().isButtonEnabled("Reopen selected")).isFalse();
        assertThat(fixture.user().text("production.selectedTaskSummary"))
                .contains("Refresh studio to review");
        readsAvailable.set(true);
        fixture.user().click("Refresh studio").awaitText("Production Studio refreshed.");
        assertThat(fixture.user().fieldValue("Crew minutes")).isEqualTo("37");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isTrue();
        verify(service, times(1)).recordTaskActuals(anyLong(), any());
    }

    @Test
    void frostEditsMadeDuringExplicitReloadAreNeverOverwritten() throws Exception {
        ProductionStudioService service = service();
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.findSeasonSettings(YEAR)).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(settings());
        });
        fixture.user().click("Reload frost anchors");
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.user().type("Source / station", "Newer observation");
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("Your newer frost edits were kept");
        assertThat(fixture.user().fieldValue("Source / station")).isEqualTo("Newer observation");
    }

    @Test
    void unrelatedEditorActionCannotMakeAnInterruptedTaskRefreshWritable() throws Exception {
        ProductionStudioService service = service();
        FloralRecipe recipe = new FloralRecipe("Market bouquet", "Bouquet", 35,
                12, 25, 15, 10, "");
        recipe.setId(7L);
        when(service.listRecipes()).thenReturn(List.of(recipe));
        when(service.listTasks(any(), any())).thenReturn(List.of(task(19L, "Bed A")));
        Fixture fixture = build(service, new SwingDialogHarness(), true);
        work(fixture).selectRow("production.taskTable", 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.listTasks(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(task(19L, "Bed A"));
        });
        fixture.user().click("Refresh studio");
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.user().selectTab("Design recipes").selectRow("production.recipeTable", 0)
                    .click("Load selected");
            work(fixture);
            assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isFalse();
            assertThat(fixture.user().text("production.selectedTaskSummary"))
                    .contains("Refresh studio to review");
        } finally {
            release.countDown();
        }
        fixture.user().click("Refresh studio").awaitText("Production Studio refreshed.");
        assertThat(fixture.user().isButtonEnabled("Save actuals + done")).isTrue();
    }

    private static ProductionStudioService service() {
        ProductionStudioService service = mock(ProductionStudioService.class);
        when(service.findSeasonSettings(YEAR)).thenReturn(Optional.of(settings()));
        return service;
    }

    private static FarmSeasonSettings settings() {
        return new FarmSeasonSettings(YEAR, LocalDate.of(YEAR, 4, 18),
                LocalDate.of(YEAR, 10, 12), "Farm station", 25);
    }

    private static ProductionTask task(Long id, String bed) {
        ProductionTask task = new ProductionTask(4L, 3L, LocalDate.now(),
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO, "Zinnia", bed,
                1, true, "Prepare bed");
        task.setId(id);
        return task;
    }

    private static Fixture build(ProductionStudioService service, SwingDialogHarness dialogs,
                                 boolean await) throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ProductionStudioTab tab = new ProductionStudioTab(service, dialogs);
            JComponent panel = tab.getUIComponent();
            fixture.set(new Fixture(tab, SwingUserHarness.on(panel)));
            tab.initialize();
        });
        if (await) {
            fixture.get().user().awaitText("Production Studio refreshed.");
        }
        return fixture.get();
    }

    private static SwingUserHarness work(Fixture fixture) {
        return fixture.user().selectTab("Plantings & work").selectTab("Field work");
    }

    private record Fixture(ProductionStudioTab tab, SwingUserHarness user) {
    }
}

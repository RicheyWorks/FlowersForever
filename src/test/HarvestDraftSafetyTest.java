package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.service.HarvestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarvestDraftSafetyTest {
    @Test
    void configuredUnitSeedsQuickAndBatchEntryWithoutCreatingAnUnsavedDraft()
            throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Clear form");
        Fixture fixture = build(service(), dialogs, "each");

        assertThat(fixture.user().fieldValue("Harvest unit")).isEqualTo("each");
        fixture.user().selectTab("Batch entry");
        assertThat(fixture.user().fieldValue("Batch default unit")).isEqualTo("each");
        fixture.user().selectTab("Quick entry");
        assertThat(fixture.tab().applicationExitWarning()).isNull();

        fixture.user().type("Harvest crop or variety", "Potatoes")
                .type("Harvest unit", "lbs");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
        entryAction(fixture, "Clear form");
        assertThat(fixture.user().fieldValue("Harvest unit")).isEqualTo("each");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        dialogs.assertComplete();

        fixture.user().selectTab("Batch entry").click("Use sample lines");
        assertThat(fixture.user().fieldValue("Batch harvest lines"))
                .contains("Example crop").doesNotContain("Rose");
    }

    @Test
    void pristineAndUneditedLoadedFormsDoNotWarnOnExit() throws Exception {
        Fixture fixture = build(service(), new SwingDialogHarness());
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        fixture.user().selectRow("harvest.history.table", 0).click("Load selected");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        fixture.user().type("Harvest notes", "An unfinished correction");
        assertThat(fixture.tab().applicationExitWarning()).contains("harvest #31");
        fixture.user().type("Harvest notes", "Morning cut");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
    }

    @Test
    void cancellingLoadKeepsCompleteNewHarvestAndBatch() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness().chooseForm(
                "Replace harvest draft?", "Keep draft", form -> form.expectText(
                        "Unsaved changes for the new harvest").expectText("load harvest #32"));
        Fixture fixture = build(service(), dialogs);
        enterCompleteDraft(fixture);
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24")
                .selectTab("Quick entry").selectRow("harvest.history.table", 1)
                .click("Load selected");
        assertCompleteDraft(fixture);
        assertThat(fixture.user().isButtonEnabled("Add harvest")).isTrue();
        assertThat(fixture.tab().applicationExitWarning())
                .contains("unsaved quick-entry", "unsaved batch harvest lines");
        dialogs.assertComplete();
    }

    @Test
    void approvingLoadReplacesOnlyQuickEntryAndSetsCleanBaseline() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Load harvest");
        Fixture fixture = build(service(), dialogs);
        enterCompleteDraft(fixture);
        fixture.user().selectRow("harvest.history.table", 1).click("Load selected");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Dahlia");
        assertThat(fixture.user().fieldValue("Harvest storage location")).isEqualTo("Cooler B");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        dialogs.assertComplete();
    }

    @Test
    void doubleClickCannotReplaceEditedTargetWhenReplacementIsCancelled() throws Exception {
        HarvestService service = service();
        when(service.update(eq(31L), any())).thenAnswer(call -> call.getArgument(1));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Keep draft");
        Fixture fixture = build(service, dialogs);
        fixture.user().selectRow("harvest.history.table", 0).click("Load selected")
                .type("Harvest quantity", "29");
        doubleClick(fixture, 1);
        assertThat(fixture.user().fieldValue("Harvest quantity")).isEqualTo("29");
        fixture.user().click("Save edit").awaitText("Harvest updated · inventory corrected");
        verify(service).update(eq(31L), any());
        verify(service, never()).update(eq(32L), any());
        dialogs.assertComplete();
    }

    @Test
    void duplicateRequiresConsentAndRemainsAnUnsavedNewHarvest() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Keep draft")
                .choose("Replace harvest draft?", "Copy harvest");
        Fixture fixture = build(service(), dialogs);
        enterCompleteDraft(fixture);
        fixture.user().selectRow("harvest.history.table", 1);
        entryAction(fixture, "Duplicate selected as today");
        assertCompleteDraft(fixture);
        entryAction(fixture, "Duplicate selected as today");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Dahlia");
        assertThat(fixture.user().fieldValue("Harvest lot code")).isEmpty();
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
        dialogs.assertComplete();
    }

    @Test
    void clearRequiresConsentAndDoesNotDiscardBatchLines() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Keep draft")
                .choose("Replace harvest draft?", "Clear form");
        Fixture fixture = build(service(), dialogs);
        enterCompleteDraft(fixture);
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24")
                .selectTab("Quick entry");
        entryAction(fixture, "Clear form");
        assertCompleteDraft(fixture);
        entryAction(fixture, "Clear form");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEmpty();
        assertThat(fixture.tab().applicationExitWarning())
                .contains("unsaved batch").doesNotContain("quick-entry");
        dialogs.assertComplete();
    }

    @Test
    void sampleLinesRequireConsentBeforeReplacingEnteredBatch() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace batch harvest lines?", "Keep batch")
                .choose("Replace batch harvest lines?", "Use sample lines");
        Fixture fixture = build(service(), dialogs);
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24")
                .type("Batch harvest notes", "Keep the stems cool")
                .click("Use sample lines");
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEqualTo("Peony,24");
        fixture.user().click("Use sample lines");
        assertThat(fixture.user().fieldValue("Batch harvest lines")).contains("Nootka Rose,40");
        assertThat(fixture.user().fieldValue("Batch harvest notes")).isEqualTo("Keep the stems cool");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved batch");
        dialogs.assertComplete();
    }

    @Test
    void droppedTextStagesHarvestLinesAndShowsPreviewWithoutLogging() throws Exception {
        HarvestService service = service();
        Fixture fixture = build(service, new SwingDialogHarness());
        fixture.user().selectTab("Batch entry");
        AtomicReference<Boolean> imported = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> imported.set(fixture.tab().importBatchDrop(
                new StringSelection("Crop,Quantity,Unit,Bed\nPotatoes,24,lbs,Field A\nApples,8,bins,Block 2"))));

        assertThat(imported.get()).isTrue();
        assertThat(fixture.user().fieldValue("Batch harvest lines"))
                .startsWith("# Crop,Quantity,Unit,Bed")
                .contains("Potatoes,24,lbs,Field A", "Apples,8,bins,Block 2");
        assertThat(fixture.user().isButtonEnabled("Log batch harvest")).isTrue();
        assertThat(fixture.host().status).contains("batch draft", "Review the preview");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved batch harvest lines");
        verify(service, never()).addBatch(anyList());
    }

    @Test
    void droppedPlainTextReaderStagesTheSameBatchDraft() throws Exception {
        Fixture fixture = build(service(), new SwingDialogHarness());
        fixture.user().selectTab("Batch entry");
        DataFlavor plainText = new DataFlavor("text/plain;class=java.io.Reader");
        Transferable transfer = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{plainText};
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return plainText.equals(flavor);
            }
            @Override public Object getTransferData(DataFlavor flavor)
                    throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return new StringReader("Carrots,10,bunches,East Field");
            }
        };
        AtomicReference<Boolean> imported = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> imported.set(fixture.tab().importBatchDrop(transfer)));

        assertThat(imported.get()).isTrue();
        assertThat(fixture.user().fieldValue("Batch harvest lines"))
                .isEqualTo("Carrots,10,bunches,East Field");
        assertThat(fixture.user().isButtonEnabled("Log batch harvest")).isTrue();
    }

    @Test
    void droppedFileReplacesBatchOnlyAfterConsentAndKeepsContext(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("morning.csv");
        Files.writeString(file, "\uFEFFCrop,Qty,Unit,Bed\nRoses,12,stems,North Bed\n");
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace batch harvest lines?", "Keep batch")
                .choose("Replace batch harvest lines?", "Use dropped lines");
        Fixture fixture = build(service(), dialogs);
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24")
                .type("Batch harvest notes", "Cool before loading");
        AtomicReference<Boolean> first = new AtomicReference<>();
        AtomicReference<Boolean> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> first.set(fixture.tab().importBatchDrop(
                fileTransfer(file.toFile()))));
        assertThat(first.get()).isFalse();
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEqualTo("Peony,24");

        SwingUtilities.invokeAndWait(() -> second.set(fixture.tab().importBatchDrop(
                fileTransfer(file.toFile()))));
        assertThat(second.get()).isTrue();
        assertThat(fixture.user().fieldValue("Batch harvest lines"))
                .startsWith("# Crop,Qty,Unit,Bed").contains("Roses,12,stems,North Bed");
        assertThat(fixture.user().fieldValue("Batch harvest notes"))
                .isEqualTo("Cool before loading");
        assertThat(fixture.user().isButtonEnabled("Log batch harvest")).isTrue();
        dialogs.assertComplete();
    }

    @Test
    void unsupportedFileKeepsExistingBatchAndReportsReason(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("invoice.pdf");
        Files.writeString(file, "not a harvest list");
        Fixture fixture = build(service(), new SwingDialogHarness());
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24");
        AtomicReference<Boolean> imported = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> imported.set(fixture.tab().importBatchDrop(
                fileTransfer(file.toFile()))));

        assertThat(imported.get()).isFalse();
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEqualTo("Peony,24");
        assertThat(fixture.host().status).contains("batch draft kept", ".csv or .txt");
    }

    @Test
    void viewerCannotDropBatchLinesAndNormalTextImportStillWorks() throws Exception {
        Fixture fixture = build(service(), new SwingDialogHarness());
        fixture.user().selectTab("Batch entry");
        AtomicReference<Boolean> pasted = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JTextArea area = (javax.swing.JTextArea) findAccessible(
                    fixture.root(), "Batch harvest lines");
            TransferHandler handler = area.getTransferHandler();
            pasted.set(handler.importData(new TransferHandler.TransferSupport(
                    area, new StringSelection("Peony,24"))));
        });
        assertThat(pasted.get()).isTrue();
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEqualTo("Peony,24");
        SwingUtilities.invokeAndWait(() -> fixture.tab().applyRolePermissions(false));
        AtomicReference<Boolean> imported = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> imported.set(fixture.tab().importBatchDrop(
                new StringSelection("Apples,8"))));
        assertThat(imported.get()).isFalse();
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEqualTo("Peony,24");
    }

    @Test
    void failedSaveKeepsFullDraftAndStillRequiresDiscardConsent() throws Exception {
        HarvestService service = service();
        when(service.add(any())).thenThrow(new IllegalStateException("Database is busy"));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Keep draft");
        Fixture fixture = build(service, dialogs);
        enterCompleteDraft(fixture);
        fixture.user().click("Add harvest").awaitText("Harvest not logged · retry");
        entryAction(fixture, "Clear form");
        assertCompleteDraft(fixture);
        assertThat(fixture.tab().hasPendingSave()).isFalse();
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
        verify(service, times(1)).add(any());
    }

    @Test
    void pendingSingleSaveRejectsDoubleClickAndQueuedReplacementActions() throws Exception {
        HarvestService service = service();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.add(any())).thenAnswer(call -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return call.getArgument(0);
        });
        Fixture fixture = build(service, new SwingDialogHarness());
        enterCompleteDraft(fixture);
        fixture.user().click("Add harvest");
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.tab().hasPendingSave()).isTrue();
            assertThat(fixture.tab().applicationExitWarning()).contains("save still running");
            doubleClick(fixture, 1);
            queuedEntryAction(fixture, "Clear form");
            queuedEntryAction(fixture, "Duplicate selected as today");
            assertCompleteDraft(fixture);
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("Saved · context kept for next cut");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Peony");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        verify(service, times(1)).add(any());
    }

    @Test
    void pendingBatchSaveKeepsIndependentQuickEntryAndClearsOnlyCommittedBatch() throws Exception {
        HarvestService service = service();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.addBatch(anyList())).thenAnswer(call -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return call.getArgument(0);
        });
        Fixture fixture = build(service, new SwingDialogHarness());
        enterCompleteDraft(fixture);
        fixture.user().selectTab("Batch entry").type("Batch harvest lines", "Peony,24")
                .click("Log batch harvest");
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            doubleClick(fixture, 1);
            assertThat(fixture.tab().applicationExitWarning()).contains("batch harvest save still running");
            fixture.user().selectTab("Quick entry");
            assertCompleteDraft(fixture);
        } finally {
            release.countDown();
        }
        fixture.user().selectTab("Batch entry").awaitText("1 harvest entry logged");
        assertThat(fixture.user().fieldValue("Batch harvest lines")).isEmpty();
        assertThat(fixture.tab().applicationExitWarning())
                .contains("unsaved quick-entry").doesNotContain("unsaved batch");
    }

    @Test
    void pendingDeleteKeepsIndependentDraftAndWarnsAboutTheRunningReversal() throws Exception {
        HarvestService service = service();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(service).delete(32L);
        Fixture fixture = build(service, new SwingDialogHarness().confirm("Delete harvest?", true));
        enterCompleteDraft(fixture);
        fixture.user().selectRow("harvest.history.table", 1);
        entryAction(fixture, "Delete selected harvest");
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            doubleClick(fixture, 0);
            queuedEntryAction(fixture, "Clear form");
            assertCompleteDraft(fixture);
            assertThat(fixture.tab().applicationExitWarning()).contains("deletion still running");
        } finally {
            release.countDown();
        }
        fixture.user().awaitText("Harvest #32 deleted · inventory reversed");
        assertCompleteDraft(fixture);
        verify(service, times(1)).delete(32L);
    }

    @Test
    void refreshCannotReplayOldCropPresetOverTheTypedDraft() throws Exception {
        Fixture fixture = build(service(), new SwingDialogHarness());
        fixture.user().select("Recent crop presets", "Rose")
                .type("Harvest crop or variety", "Peony");
        SwingUtilities.invokeAndWait(fixture.tab()::refreshData);
        fixture.user().awaitText("2 entries");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Peony");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
    }

    @Test
    void changedTargetDuringConfirmationCannotReplaceDraftWithStaleRow() throws Exception {
        AtomicReference<Fixture> current = new AtomicReference<>();
        FarmDialogs dialogs = new CallbackDialogs(() -> {
            JTable table = history(current.get());
            ((DefaultTableModel) table.getModel()).setValueAt("Changed elsewhere", 1, 2);
        });
        Fixture fixture = build(service(), dialogs);
        current.set(fixture);
        enterCompleteDraft(fixture);
        fixture.user().selectRow("harvest.history.table", 1).click("Load selected");
        assertCompleteDraft(fixture);
        assertThat(fixture.host().status).contains("changed or disappeared");
    }

    @Test
    void successfulSaveWithFailedHostRefreshLeavesCleanRetainedContext() throws Exception {
        HarvestService service = service();
        when(service.add(any())).thenAnswer(call -> call.getArgument(0));
        Fixture fixture = build(service, new SwingDialogHarness());
        fixture.host().failRefresh = true;
        enterCompleteDraft(fixture);
        fixture.user().click("Add harvest").awaitText("Saved · context kept for next cut");
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        assertThat(fixture.host().status).contains("Harvest logged", "Refresh harvest history manually");
        verify(service, times(1)).add(any());
        fixture.user().type("Harvest notes", "Next bucket is ready");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
    }

    @Test
    void failedSaveOfUnchangedRetainedSetupIsStillAnUnfinishedHarvest() throws Exception {
        HarvestService service = service();
        when(service.add(any())).thenAnswer(call -> call.getArgument(0))
                .thenThrow(new IllegalStateException("Database is busy"));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Replace harvest draft?", "Keep draft");
        Fixture fixture = build(service, dialogs);
        enterCompleteDraft(fixture);
        fixture.user().click("Add harvest").awaitText("Saved · context kept for next cut")
                .awaitButtonEnabled("Add harvest", true);
        assertThat(fixture.tab().applicationExitWarning()).isNull();
        fixture.user().click("Add harvest").awaitText("Harvest not logged · retry");
        assertThat(fixture.tab().applicationExitWarning()).contains("unsaved quick-entry");
        entryAction(fixture, "Clear form");
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Peony");
        assertThat(fixture.user().fieldValue("Harvest quantity")).isEqualTo("1");
        dialogs.assertComplete();
        verify(service, times(2)).add(any());
    }

    @Test
    void deletingEditedTargetNamesItsDraftLossAndCancellationKeepsIt() throws Exception {
        SwingDialogHarness dialogs = new SwingDialogHarness().confirmForm("Delete harvest?", false,
                form -> form.expectText("unfinished quick-entry edits").expectText("discarded"));
        HarvestService service = service();
        Fixture fixture = build(service, dialogs);
        fixture.user().selectRow("harvest.history.table", 0).click("Load selected")
                .type("Harvest quantity", "29");
        entryAction(fixture, "Delete selected harvest");
        assertThat(fixture.user().fieldValue("Harvest quantity")).isEqualTo("29");
        assertThat(fixture.tab().applicationExitWarning()).contains("harvest #31");
        verify(service, never()).delete(any());
        dialogs.assertComplete();
    }

    private static HarvestService service() {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry rose = new HarvestEntry(LocalDate.now(), "Rose", 12, "stems",
                "Bed A", "Morning cut", "LOT-31", HarvestGrade.SELECT, 2, "Cooler A");
        rose.setId(31L);
        HarvestEntry dahlia = new HarvestEntry(LocalDate.now(), "Dahlia", 18, "stems",
                "Bed B", "Evening cut", "LOT-32", HarvestGrade.STANDARD, 1, "Cooler B");
        dahlia.setId(32L);
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of(rose, dahlia));
        when(service.totalsByCrop()).thenReturn(Map.of("Rose", 12.0, "Dahlia", 18.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Rose", 12.0, "Dahlia", 18.0));
        return service;
    }

    private static Fixture build(HarvestService service, FarmDialogs dialogs) throws Exception {
        return build(service, dialogs, "stems");
    }

    private static Fixture build(HarvestService service, FarmDialogs dialogs,
                                 String defaultUnit) throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Host host = new Host();
            HarvestLogTab tab = new HarvestLogTab(service, host, dialogs);
            tab.setDefaultUnit(defaultUnit);
            JComponent panel = tab.getUIComponent();
            fixture.set(new Fixture(tab, panel, SwingUserHarness.on(panel), host));
            tab.initialize();
        });
        fixture.get().user().awaitText("2 entries");
        return fixture.get();
    }

    private static void enterCompleteDraft(Fixture fixture) {
        fixture.user().type("Harvest crop or variety", "Peony")
                .type("Harvest quantity", "24")
                .type("Harvest unit", "bunches")
                .type("Harvest bed or field", "Tunnel 4")
                .type("Harvest notes", "Shade before packing")
                .type("Harvest lot code", "PEONY-TEST")
                .select("Harvest quality grade", "Select")
                .type("Harvest loss or cull quantity", "3")
                .type("Harvest storage location", "Cooler D");
    }

    private static void assertCompleteDraft(Fixture fixture) {
        assertThat(fixture.user().fieldValue("Harvest crop or variety")).isEqualTo("Peony");
        assertThat(fixture.user().fieldValue("Harvest quantity")).isEqualTo("24");
        assertThat(fixture.user().fieldValue("Harvest unit")).isEqualTo("bunches");
        assertThat(fixture.user().fieldValue("Harvest bed or field")).isEqualTo("Tunnel 4");
        assertThat(fixture.user().fieldValue("Harvest notes")).isEqualTo("Shade before packing");
        assertThat(fixture.user().fieldValue("Harvest lot code")).isEqualTo("PEONY-TEST");
        assertThat(fixture.user().fieldValue("Harvest loss or cull quantity")).isEqualTo("3");
        assertThat(fixture.user().fieldValue("Harvest storage location")).isEqualTo("Cooler D");
    }

    private static void entryAction(Fixture fixture, String action) {
        fixture.user().select("More harvest entry actions", action).click("Run entry action");
    }

    private static void queuedEntryAction(Fixture fixture, String action) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            @SuppressWarnings("unchecked")
            JComboBox<String> choices = (JComboBox<String>) named(fixture.root(), "harvest.entryTools");
            choices.setSelectedItem(action);
            AbstractButton button = button(fixture.root(), "Run entry action");
            if (button == null) {
                button = button(fixture.root(), "Deleting harvest…");
            }
            for (var listener : button.getActionListeners()) {
                listener.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, "queued"));
            }
        });
    }

    private static void doubleClick(Fixture fixture, int row) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = history(fixture);
            table.setRowSelectionInterval(row, row);
            MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, 5, 5, 2, false);
            for (var listener : table.getMouseListeners()) {
                listener.mouseClicked(event);
            }
        });
    }

    private static JTable history(Fixture fixture) {
        return (JTable) named(fixture.root(), "harvest.history.table");
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = named(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Component findAccessible(Component root, String accessibleName) {
        if (root instanceof javax.accessibility.Accessible accessible
                && accessible.getAccessibleContext() != null
                && accessibleName.equals(
                accessible.getAccessibleContext().getAccessibleName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = findAccessible(child, accessibleName);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Transferable fileTransfer(File file) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }
            @Override public Object getTransferData(DataFlavor flavor)
                    throws UnsupportedFlavorException, IOException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return List.of(file);
            }
        };
    }

    private static AbstractButton button(Component root, String label) {
        if (root instanceof AbstractButton button && label.equals(button.getText())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                AbstractButton match = button(child, label);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private record Fixture(HarvestLogTab tab, JComponent root, SwingUserHarness user, Host host) { }

    private static final class Host implements TabHost {
        private volatile String status;
        private boolean failRefresh;
        @Override public void refreshAll() {
            if (failRefresh) {
                throw new IllegalStateException("Another workspace could not refresh");
            }
        }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }

    private static final class CallbackDialogs implements FarmDialogs {
        private final Runnable callback;
        private CallbackDialogs(Runnable callback) { this.callback = callback; }
        @Override public int showOption(Component parent, Object message, String title,
                                         int optionType, int messageType, Icon icon,
                                         Object[] options, Object initialValue) {
            assertThat(initialValue).isEqualTo("Keep draft");
            callback.run();
            return 1;
        }
        @Override public int showConfirm(Component parent, Object message, String title,
                                          int optionType, int messageType) {
            throw new AssertionError("Unexpected confirmation");
        }
        @Override public void showMessage(Component parent, Object message, String title, int messageType) {
            throw new AssertionError("Unexpected message");
        }
    }
}

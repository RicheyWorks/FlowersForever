package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.model.CannabisCultivationBatch;
import com.flowerfarm.model.CannabisCultivationStage;
import com.flowerfarm.service.CannabisCultivationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Cannabis cultivation planning notebook")
class CannabisCultivationTabTest {

    @Test
    void explainsScopeAndRoutesToGeneralRecordsWithoutLosingDraft() throws Exception {
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        RecordingHost host = new RecordingHost();
        CannabisCultivationTab tab = new CannabisCultivationTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);

        assertThat(tab.getTabTitle()).isEqualTo("Cultivation");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .anySatisfy(text -> assertThat(text).contains("does not provide regulated tracking"));
        onEdt(() -> field(root, "Batch name").setText("House A"));
        assertThat(tab.applicationExitWarning()).contains("unsaved batch planning draft");

        onEdt(() -> {
            button(root, "Open Harvest Log").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open soil recipes").doClick();
        });
        assertThat(host.selected).containsExactly("Harvest Log", "Customers & Orders",
                "Field science");
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
    }

    @Test
    void failedCreateKeepsCompleteDraftForRetryAndThenSavesOnce() throws Exception {
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        when(service.listAll()).thenReturn(List.of());
        when(service.create(any(CannabisCultivationBatch.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    CannabisCultivationBatch saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        CannabisCultivationTab tab = new CannabisCultivationTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        fillValidDraft(root);

        onEdt(() -> button(root, "Save batch").doClick());
        await(() -> feedback(root).contains("Not saved · complete planning draft kept"));
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
        assertThat(field(root, "Growing area").getText()).isEqualTo("Room 1");
        assertThat(tab.applicationExitWarning()).contains("unsaved batch planning draft");

        onEdt(() -> button(root, "Save batch").doClick());
        await(() -> feedback(root).contains("Batch saved · House A")
                && !tab.hasPendingSave());
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, org.mockito.Mockito.times(2)).create(any(CannabisCultivationBatch.class));
    }

    @Test
    void editsSelectedBatchAndProtectsChangedDraftOnSelection() throws Exception {
        CannabisCultivationBatch first = saved(11L, "House A");
        CannabisCultivationBatch second = saved(12L, "House B");
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        when(service.listAll()).thenReturn(List.of(first, second));
        when(service.update(eq(11L), any(CannabisCultivationBatch.class)))
                .thenAnswer(invocation -> {
                    CannabisCultivationBatch saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        CannabisCultivationTab tab = new CannabisCultivationTab(
                service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
        onEdt(() -> field(root, "Plant count").setText("25"));
        onEdt(() -> table(root).setRowSelectionInterval(1, 1));
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
        assertThat(field(root, "Plant count").getText()).isEqualTo("25");
        assertThat(table(root).getSelectedRow()).isZero();

        onEdt(() -> button(root, "Save batch").doClick());
        await(() -> feedback(root).contains("Batch saved · House A"));
        org.mockito.ArgumentCaptor<CannabisCultivationBatch> draft =
                org.mockito.ArgumentCaptor.forClass(CannabisCultivationBatch.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getPlantCount()).isEqualTo(25);
    }

    @Test
    void dragAndClickMoveOnlyTheSavedPlanningStage() throws Exception {
        AtomicReference<CannabisCultivationBatch> stored = new AtomicReference<>(
                saved(21L, "House A"));
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        when(service.listAll()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(CannabisCultivationStage.class)))
                .thenAnswer(invocation -> {
                    CannabisCultivationBatch updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated.snapshot();
                });
        RecordingHost host = new RecordingHost();
        CannabisCultivationTab tab = new CannabisCultivationTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton flowering = named(root, "cultivation.stageTarget.FLOWERING", JButton.class);
        JButton harvested = named(root, "cultivation.stageTarget.HARVESTED", JButton.class);
        assertThat(named(root, "cultivation.stageTargets", JComponent.class)
                .getComponentCount()).isEqualTo(CannabisCultivationStage.values().length);
        assertThat(flowering.getToolTipText()).contains("no harvest or stock record");

        Transferable batch = dragPayload(root);
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(flowering, batch);
        assertThat(onEdt(() -> flowering.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> flowering.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == CannabisCultivationStage.FLOWERING);
        assertThat(stored.get().getArea()).isEqualTo("Room 1");
        assertThat(stored.get().getPlantCount()).isEqualTo(20);
        assertThat(stored.get().getPlannedHarvestDate())
                .isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
        assertThat(host.lastStatus).contains("planning stage Flowering");

        await(() -> harvested.isEnabled()
                && table(root).getValueAt(0, 4)
                == CannabisCultivationStage.FLOWERING);
        onEdt(() -> { harvested.doClick(); });
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == CannabisCultivationStage.HARVESTED);
        verify(service, org.mockito.Mockito.times(2))
                .updateStage(eq(21L), any(CannabisCultivationStage.class));
        verify(service, never()).update(eq(21L), any(CannabisCultivationBatch.class));
    }

    @Test
    void stageDropRejectsForeignStaleDirtyReadOnlyAndInFlightPayloads() throws Exception {
        CannabisCultivationBatch saved = saved(31L, "House A");
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        when(service.listAll()).thenReturn(List.of(saved));
        CannabisCultivationTab tab = new CannabisCultivationTab(service,
                new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton target = named(root, "cultivation.stageTarget.FLOWERING", JButton.class);
        JButton sameStage = named(root, "cultivation.stageTarget.VEGETATIVE", JButton.class);
        Transferable initial = dragPayload(root);
        assertThat(onEdt(() -> sameStage.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(sameStage, initial)))).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target,
                        new StringSelection("not a saved batch"))))).isFalse();

        CannabisCultivationTab other = new CannabisCultivationTab(service,
                new RecordingHost());
        JComponent otherRoot = onEdt(other::getUIComponent);
        onEdt(other::initialize);
        await(() -> table(otherRoot).getRowCount() == 1);
        onEdt(() -> table(otherRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(otherRoot);
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, foreign)))).isFalse();

        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 2
                && button(root, "Refresh batches").isEnabled());
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, initial)))).isFalse();

        Transferable current = dragPayload(root);
        onEdt(() -> ReflectionTestUtils.setField(tab, "saving", true));
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        onEdt(() -> ReflectionTestUtils.setField(tab, "saving", false));
        onEdt(() -> field(root, "Batch name").setText("Unsaved edit"));
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().importData(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        assertThat(field(root, "Batch name").getText()).isEqualTo("Unsaved edit");

        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(target.isEnabled()).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        verify(service, never()).updateStage(any(), any());
    }

    @Test
    void failedStageMoveLeavesSavedBatchAndOpenFormIntact() throws Exception {
        CannabisCultivationBatch saved = saved(41L, "House A");
        CannabisCultivationService service = mock(CannabisCultivationService.class);
        when(service.listAll()).thenReturn(List.of(saved));
        when(service.updateStage(41L, CannabisCultivationStage.FLOWERING))
                .thenThrow(new IllegalStateException("store busy"));
        CannabisCultivationTab tab = new CannabisCultivationTab(service,
                new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton target = named(root, "cultivation.stageTarget.FLOWERING", JButton.class);

        onEdt(() -> { target.doClick(); });
        await(() -> !tab.hasPendingSave()
                && feedback(root).contains("Planning stage was not changed"));
        assertThat(saved.getStage()).isEqualTo(CannabisCultivationStage.VEGETATIVE);
        assertThat(table(root).getValueAt(0, 4))
                .isEqualTo(CannabisCultivationStage.VEGETATIVE);
        assertThat(field(root, "Batch name").getText()).isEqualTo("House A");
        assertThat(tab.applicationExitWarning()).isNull();
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("cultivation-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static CannabisCultivationBatch saved(Long id, String name) {
        CannabisCultivationBatch batch = new CannabisCultivationBatch(name, "Cultivar A",
                "Room 1", 20, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1),
                CannabisCultivationStage.VEGETATIVE, "Planning notes");
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }

    private static void fillValidDraft(Component root) throws Exception {
        onEdt(() -> {
            field(root, "Batch name").setText("House A");
            field(root, "Growing area").setText("Room 1");
            field(root, "Plant count").setText("20");
            field(root, "Start date").setText("2026-09-01");
            field(root, "Planned harvest date").setText("2026-12-01");
        });
    }

    private static String feedback(Component root) {
        return descendants(root, JLabel.class).stream()
                .filter(label -> "cultivation.feedback".equals(label.getName()))
                .findFirst().orElseThrow().getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return descendants(root, JTable.class).stream()
                .filter(table -> "cultivation.batchTable".equals(table.getName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(item -> name.equals(item.getName())).findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(15);
        }
        assertThat(onEdt(condition::getAsBoolean)).isTrue();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> {
            action.run();
            return null;
        });
    }

    private static final class RecordingHost implements TabHost {
        private final List<String> selected = new ArrayList<>();
        private String lastStatus = "";

        @Override public void refreshAll() {
        }

        @Override public void selectTab(String tabTitle) {
            selected.add(tabTitle);
        }

        @Override public void setStatus(String message) {
            lastStatus = message;
        }

        @Override public void runTrendAnalysis() {
        }
    }
}

package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.NurseryBatch;
import com.flowerfarm.model.NurseryBatchStage;
import com.flowerfarm.model.NurseryPlantForm;
import com.flowerfarm.model.NurserySalesChannel;
import com.flowerfarm.service.NurseryBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Nursery growing batch planning")
class NurseryGrowingBatchTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "nursery-batch");

    @Test
    void livePlantFieldsAndSharedLinksFitCompactScrollableWorkspace() throws Exception {
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring lavender")));
        RecordingHost host = new RecordingHost();
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Growing batches");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Growing batches")
                .anySatisfy(text -> assertThat(text).contains("do not add inventory"));
        for (String name : List.of("Batch name", "Plant name", "Cultivar",
                "Growing area", "Started date", "Planned ready date", "Starting count")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "nurseryBatch.form").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Plant form");
        assertThat(combo(root, "nurseryBatch.channel").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Intended sales channel");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Ready");
        assertThat(table(root).getColumnName(5)).isEqualTo("Started count");

        onEdt(() -> field(root, "Plant name").setText("lavender starts"));
        assertThat(tab.applicationExitWarning()).contains("unsaved nursery batch draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders");

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "nurseryBatch.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JComponent targets = named(root, "nurseryBatch.stageTargets", JComponent.class);
            assertThat(targets.getWidth()).isLessThanOrEqualTo(scroll.getViewport().getWidth());
            assertThat(descendants(targets, JButton.class)).hasSize(
                    NurseryBatchStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "nursery-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "nursery-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftForRetry() throws Exception {
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.create(any(NurseryBatch.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    NurseryBatch saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Batch name").setText("Spring lavender");
            field(root, "Plant name").setText("Lavender");
            field(root, "Cultivar").setText("Hidcote");
            field(root, "Growing area").setText("Greenhouse 2");
            field(root, "Started date").setText("2026-02-01");
            field(root, "Planned ready date").setText("2026-05-15");
            field(root, "Starting count").setText("240");
            combo(root, "nurseryBatch.form").setSelectedItem(NurseryPlantForm.STARTS_OR_PLUGS);
            combo(root, "nurseryBatch.channel").setSelectedItem(NurserySalesChannel.RETAIL);
            combo(root, "nurseryBatch.stage").setSelectedItem(NurseryBatchStage.GROWING);
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planned ready date").getText()).isEqualTo("2026-05-15");
        assertThat(tab.applicationExitWarning()).contains("unsaved nursery batch draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("Spring lavender"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<NurseryBatch> draft =
                org.mockito.ArgumentCaptor.forClass(NurseryBatch.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getPlantName()).isEqualTo("Lavender");
        assertThat(draft.getValue().getStartingCount()).isEqualTo(240);
        assertThat(draft.getValue().getPlannedReadyDate())
                .isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(draft.getValue().getForm()).isEqualTo(NurseryPlantForm.STARTS_OR_PLUGS);
    }

    @Test
    void invalidReadyDateProtectsEditedBatch() throws Exception {
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "Spring lavender"), saved(12L, "Summer rosemary")));
        when(service.update(eq(11L), any(NurseryBatch.class)))
                .thenAnswer(invocation -> {
                    NurseryBatch saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(
                service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned ready date").setText("2026-01-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Batch name").getText()).isEqualTo("Spring lavender");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned ready date cannot precede started date");
        verify(service, never()).update(anyLong(), any());

        onEdt(() -> {
            field(root, "Planned ready date").setText("2026-06-01");
            field(root, "Starting count").setText("two dozen");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("whole number");
        verify(service, never()).update(anyLong(), any());

        onEdt(() -> {
            field(root, "Starting count").setText("1000001");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("between 1 and 1,000,000");
        verify(service, never()).update(anyLong(), any());

        onEdt(() -> {
            field(root, "Starting count").setText("240");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<NurseryBatch> draft =
                org.mockito.ArgumentCaptor.forClass(NurseryBatch.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getPlannedReadyDate())
                .isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void viewerCanReviewButCannotSaveBatches() throws Exception {
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring lavender")));
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Batch name").getText()).isEqualTo("Spring lavender");
        assertThat(field(root, "Batch name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New growing batch").isEnabled()).isFalse();
        assertThat(button(root, "Refresh batches").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
    }

    @Test
    void draggingSavedBatchToStageChangesOnlyStageAndKeepsClickFallback() throws Exception {
        AtomicReference<NurseryBatch> stored = new AtomicReference<>(
                saved(21L, "Spring lavender"));
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(NurseryBatchStage.class)))
                .thenAnswer(invocation -> {
                    NurseryBatch original = stored.get();
                    NurseryBatch updated = new NurseryBatch(original.getBatchName(),
                            original.getPlantName(), original.getCultivar(), original.getForm(),
                            original.getGrowingArea(), original.getStartedDate(),
                            original.getPlannedReadyDate(), original.getStartingCount(),
                            original.getChannel(), invocation.getArgument(1),
                            original.getNotes());
                    ReflectionTestUtils.setField(updated, "id", 21L);
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        JButton ready = named(root, "nurseryBatch.stageTarget.READY_FOR_SALE", JButton.class);
        JButton complete = named(root, "nurseryBatch.stageTarget.COMPLETE", JButton.class);
        assertThat(ready.getToolTipText()).contains("Stage change saves immediately");

        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(ready.isEnabled()).isTrue();
        Transferable batch = dragPayload(root);
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(ready, batch);
        assertThat(onEdt(() -> ready.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> ready.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == NurseryBatchStage.READY_FOR_SALE);
        assertThat(stored.get().getPlantName()).isEqualTo("Lavender");
        assertThat(stored.get().getStartingCount()).isEqualTo(240);
        assertThat(stored.get().getGrowingArea()).isEqualTo("Greenhouse 2");
        assertThat(host.lastStatus).contains("moved to Ready for sale");

        await(() -> complete.isEnabled()
                && "Ready for sale".equals(table(root).getValueAt(0, 7)));
        onEdt(() -> { complete.doClick(); });
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == NurseryBatchStage.COMPLETE);
        verify(service, times(2)).updateStage(eq(21L), any(NurseryBatchStage.class));
    }

    @Test
    void stageDropRejectsDirtyReadOnlyStaleAndOtherNotebookPayloads() throws Exception {
        NurseryBatch saved = saved(31L, "Spring lavender");
        NurseryBatchService service = mock(NurseryBatchService.class);
        when(service.list()).thenReturn(List.of(saved));
        NurseryGrowingBatchTab tab = new NurseryGrowingBatchTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton target = named(root, "nurseryBatch.stageTarget.READY_FOR_SALE", JButton.class);
        Transferable initial = dragPayload(root);

        NurseryGrowingBatchTab other = new NurseryGrowingBatchTab(service, new RecordingHost());
        JComponent otherRoot = onEdt(other::getUIComponent);
        onEdt(other::initialize);
        await(() -> table(otherRoot).getRowCount() == 1);
        onEdt(() -> table(otherRoot).setRowSelectionInterval(0, 0));
        Transferable otherBatch = dragPayload(otherRoot);
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, otherBatch)))).isFalse();

        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 2
                && button(root, "Refresh batches").isEnabled());
        // A refreshed list invalidates even a payload with the same batch id and object.
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, initial)))).isFalse();

        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Batch name").setText("Unsaved name"));
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().importData(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        assertThat(field(root, "Batch name").getText()).isEqualTo("Unsaved name");

        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(target.isEnabled()).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, current)))).isFalse();
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("nursery-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static NurseryBatch saved(Long id, String name) {
        NurseryBatch batch = new NurseryBatch(name, "Lavender", "Hidcote",
                NurseryPlantForm.STARTS_OR_PLUGS, "Greenhouse 2",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 15), 240,
                NurserySalesChannel.RETAIL, NurseryBatchStage.GROWING, "Planning note");
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }

    private static void savePreview(JComponent root, String fileName)
            throws java.io.IOException {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", PREVIEWS.resolve(fileName).toFile());
    }

    private static String feedback(Component root) {
        return named(root, "nurseryBatch.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(item -> name.equals(item.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JComboBox<?> combo(Component root, String name) {
        return named(root, name, JComboBox.class);
    }

    private static JButton button(Component root, String title) {
        return descendants(root, JButton.class).stream()
                .filter(item -> title.equals(item.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "nurseryBatch.planTable", JTable.class);
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
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

        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { selected.add(tabTitle); }
        @Override public void setStatus(String message) { lastStatus = message; }
        @Override public void runTrendAnalysis() { }
    }
}

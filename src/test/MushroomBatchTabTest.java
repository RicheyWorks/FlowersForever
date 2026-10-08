package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.MushroomBatchPlan;
import com.flowerfarm.model.MushroomBatchStage;
import com.flowerfarm.service.MushroomBatchPlanService;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
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

@DisplayName("Mushroom batch planning")
class MushroomBatchTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "mushroom-batches");

    @Test
    void screenExplainsPlanScopeAndOffersEditableBatchAndSharedTools() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        MushroomBatchPlanService service = mock(MushroomBatchPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring oyster 1")));
        RecordingHost host = new RecordingHost();
        MushroomBatchTab tab = new MushroomBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Mushroom batches");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Mushroom batches");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("not a completed event"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("actual yields, stock, or sales"))).isTrue();
        for (String name : List.of("Batch name", "Mushroom species", "Growing method",
                "Substrate", "Growing area", "Intended channel",
                "Planned inoculation date", "Planned fruiting start",
                "Planned fruiting end")) assertThat(field(root, name)).isNotNull();
        assertThat(combo(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Fruiting start");
        onEdt(() -> {
            field(root, "Substrate").setText("Hardwood sawdust");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Substrate").getText()).isEqualTo("Hardwood sawdust");
        assertThat(tab.applicationExitWarning()).contains("unsaved mushroom batch plan draft");
        onEdt(() -> {
            field(root, "Substrate").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        SwingUserHarness.on(root).layoutAt(920, 1300);
        onEdt(() -> {
            JScrollPane scroll = named(root, "mushroomBatch.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "mushroomBatch.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(MushroomBatchStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "mushroom-batches-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void invalidDatesAndFailedSavePreserveAllPlanningFieldsForRetry() throws Exception {
        MushroomBatchPlanService service = mock(MushroomBatchPlanService.class);
        when(service.create(any(MushroomBatchPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    MushroomBatchPlan result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        MushroomBatchTab tab = new MushroomBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a batch name");
        onEdt(() -> {
            field(root, "Batch name").setText("Shiitake logs 2");
            field(root, "Mushroom species").setText("Shiitake");
            field(root, "Growing method").setText("Logs");
            field(root, "Substrate").setText("Hardwood");
            field(root, "Growing area").setText("Shade tunnel");
            field(root, "Intended channel").setText("CSA");
            field(root, "Planned inoculation date").setText("2027-06-01");
            field(root, "Planned fruiting start").setText("2027-05-20");
            field(root, "Planned fruiting end").setText("2027-06-10");
            combo(root).setSelectedItem(MushroomBatchStage.INCUBATING);
            notes(root).setText("Keep shaded");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned inoculation date cannot be after planned fruiting start");
        onEdt(() -> {
            field(root, "Planned inoculation date").setText("2027-03-10");
            field(root, "Planned fruiting start").setText("");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned fruiting end requires a start date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned fruiting start").setText("2027-05-20");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Substrate").getText()).isEqualTo("Hardwood");
        assertThat(tab.applicationExitWarning()).contains("unsaved mushroom batch plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("Shiitake logs 2"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<MushroomBatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(MushroomBatchPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getMushroomSpecies()).isEqualTo("Shiitake");
        assertThat(draft.getValue().getGrowingMethod()).isEqualTo("Logs");
        assertThat(draft.getValue().getSubstrate()).isEqualTo("Hardwood");
        assertThat(draft.getValue().getGrowingSite()).isEqualTo("Shade tunnel");
        assertThat(draft.getValue().getPlannedInoculationDate())
                .isEqualTo(LocalDate.of(2027, 3, 10));
        assertThat(draft.getValue().getPlannedFruitingEnd())
                .isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(draft.getValue().getIntendedChannel()).isEqualTo("CSA");
        assertThat(draft.getValue().getStage()).isEqualTo(MushroomBatchStage.INCUBATING);
        assertThat(draft.getValue().getNotes()).isEqualTo("Keep shaded");
    }

    @Test
    void nextBatchClonesStableFieldsButClearsIdentityDatesStageAndNotes() throws Exception {
        MushroomBatchPlanService service = mock(MushroomBatchPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring oyster 1")));
        when(service.create(any(MushroomBatchPlan.class))).thenAnswer(invocation -> {
            MushroomBatchPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        MushroomBatchTab tab = new MushroomBatchTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Batch name").setText("Unfinished edit");
            button(root, "Plan next batch").doClick();
        });
        assertThat(field(root, "Batch name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Batch name").setText("Spring oyster 1");
            button(root, "Plan next batch").doClick();
        });
        assertThat(field(root, "Batch name").getText()).isEmpty();
        assertThat(field(root, "Mushroom species").getText()).isEqualTo("Oyster");
        assertThat(field(root, "Growing method").getText()).isEqualTo("Bags");
        assertThat(field(root, "Substrate").getText()).isEqualTo("Pasteurized straw");
        assertThat(field(root, "Growing area").getText()).isEqualTo("Fruiting room 2");
        assertThat(field(root, "Intended channel").getText()).isEqualTo("Farmers market");
        for (String name : List.of("Planned inoculation date", "Planned fruiting start",
                "Planned fruiting end")) assertThat(field(root, name).getText()).isEmpty();
        assertThat(combo(root).getSelectedItem()).isEqualTo(MushroomBatchStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved mushroom batch plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a batch name");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Batch name").setText("Summer oyster 2");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("Summer oyster 2"));
        org.mockito.ArgumentCaptor<MushroomBatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(MushroomBatchPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getBatchName()).isEqualTo("Summer oyster 2");
        assertThat(draft.getValue().getMushroomSpecies()).isEqualTo("Oyster");
        assertThat(draft.getValue().getSubstrate()).isEqualTo("Pasteurized straw");
        assertThat(draft.getValue().getPlannedInoculationDate()).isNull();
        assertThat(draft.getValue().getPlannedFruitingStart()).isNull();
        assertThat(draft.getValue().getStage()).isEqualTo(MushroomBatchStage.PLANNED);
    }

    @Test
    void savedBatchCanBeEditedWithoutCreatingAnotherBatch() throws Exception {
        MushroomBatchPlanService service = mock(MushroomBatchPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring oyster 1")));
        when(service.update(eq(11L), any(MushroomBatchPlan.class))).thenAnswer(invocation -> {
            MushroomBatchPlan result = invocation.getArgument(1);
            ReflectionTestUtils.setField(result, "id", 11L);
            return result;
        });
        RecordingHost host = new RecordingHost();
        MushroomBatchTab tab = new MushroomBatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Growing area").setText("Fruiting room 3");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<MushroomBatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(MushroomBatchPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getGrowingSite()).isEqualTo("Fruiting room 3");
        verify(service, never()).create(any());
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<MushroomBatchPlan> stored = new AtomicReference<>(
                saved(21L, "Spring oyster 1"));
        MushroomBatchPlanService service = mock(MushroomBatchPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(MushroomBatchStage.class)))
                .thenAnswer(invocation -> {
                    MushroomBatchPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        MushroomBatchTab tab = new MushroomBatchTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton inoculation = named(root,
                "mushroomBatch.stageTarget.INOCULATION_WINDOW", JButton.class);
        JButton incubating = named(root,
                "mushroomBatch.stageTarget.INCUBATING", JButton.class);
        assertThat(inoculation.getToolTipText()).contains("Stage change saves immediately");
        onEdt(() -> field(root, "Substrate").setText("Unsaved substrate"));
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> field(root, "Substrate").setText("Pasteurized straw"));
        Transferable payload = dragPayload(root);
        MushroomBatchTab foreignTab = new MushroomBatchTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> inoculation.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(inoculation, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(inoculation, payload);
        assertThat(onEdt(() -> inoculation.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> inoculation.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == MushroomBatchStage.INOCULATION_WINDOW);
        assertThat(stored.get().getSubstrate()).isEqualTo("Pasteurized straw");
        await(() -> "Inoculation window".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh batches").isEnabled());
        onEdt((Runnable) incubating::doClick);
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == MushroomBatchStage.INCUBATING);
        verify(service, times(2)).updateStage(eq(21L), any(MushroomBatchStage.class));
        await(() -> "Incubating".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh batches").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh batches").isEnabled());
        assertThat(onEdt(() -> inoculation.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(inoculation, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(inoculation.isEnabled()).isFalse();
        assertThat(button(root, "Plan next batch").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "Refresh batches").isEnabled()).isTrue();
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
        verify(service, never()).updateStage(anyLong(), eq(MushroomBatchStage.FRUITING_WINDOW));
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("mushroom-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static MushroomBatchPlan saved(Long id, String name) {
        MushroomBatchPlan plan = new MushroomBatchPlan(name, "Oyster", "Bags",
                "Pasteurized straw", "Fruiting room 2",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1),
                LocalDate.of(2027, 4, 10), "Farmers market",
                MushroomBatchStage.PLANNED, "Check humidity");
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private static void savePreview(JComponent root, String fileName)
            throws java.io.IOException {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { root.printAll(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", PREVIEWS.resolve(fileName).toFile());
    }

    private static String feedback(Component root) {
        return named(root, "mushroomBatch.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "mushroomBatch.notes", JTextArea.class);
    }

    private static JComboBox<?> combo(Component root) {
        return named(root, "mushroomBatch.stage", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "mushroomBatch.batchTable", JTable.class);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) found.addAll(descendants(child, type));
        }
        return found;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onEdt(condition::getAsBoolean)) return;
            Thread.sleep(15);
        }
        assertThat(onEdt(condition::getAsBoolean)).isTrue();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); }
            catch (Exception exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> { action.run(); return null; });
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

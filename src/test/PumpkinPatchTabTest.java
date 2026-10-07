package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.PumpkinPatchPlan;
import com.flowerfarm.model.PumpkinPatchStage;
import com.flowerfarm.model.PumpkinSalesMode;
import com.flowerfarm.service.PumpkinPatchPlanService;
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
import java.math.BigDecimal;
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

@DisplayName("Pumpkin patch planning")
class PumpkinPatchTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "pumpkin-patches");

    @Test
    void screenShowsSalesModesPlanningScopeAndEditablePatch() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        PumpkinPatchPlanService service = mock(PumpkinPatchPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "South field")));
        RecordingHost host = new RecordingHost();
        PumpkinPatchTab tab = new PumpkinPatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Pumpkin patches");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Pumpkin patches");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("plans, not bookings"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("completed harvests, stock, or sales"))).isTrue();
        for (String name : List.of("Patch name", "Variety", "Acres",
                "Planned planting date", "Planned picking start", "Planned picking end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(visitorNotes(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Visitor area notes");
        assertThat(method(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planned sales mode");
        assertThat(stage(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(3)).isEqualTo("Sales mode");
        onEdt(() -> {
            field(root, "Variety").setText("Jack Be Little");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Variety").getText()).isEqualTo("Jack Be Little");
        assertThat(tab.applicationExitWarning()).contains("unsaved pumpkin patch plan draft");
        onEdt(() -> {
            field(root, "Variety").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        SwingUserHarness.on(root).layoutAt(920, 1320);
        onEdt(() -> {
            JScrollPane scroll = named(root, "pumpkinPatch.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "pumpkinPatch.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(PumpkinPatchStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "pumpkin-patches-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void acreageAndDatesValidateWhileFailedSaveKeepsCompleteDraftForRetry() throws Exception {
        PumpkinPatchPlanService service = mock(PumpkinPatchPlanService.class);
        when(service.create(any(PumpkinPatchPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    PumpkinPatchPlan result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        PumpkinPatchTab tab = new PumpkinPatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a patch name");
        onEdt(() -> {
            field(root, "Patch name").setText("West field");
            field(root, "Variety").setText("Howden");
            field(root, "Acres").setText("2.555");
            field(root, "Planned picking start").setText("2027-10-01");
            field(root, "Planned picking end").setText("2027-10-15");
            field(root, "Planned planting date").setText("2027-05-10");
            visitorNotes(root).setText("North gate parking");
            method(root).setSelectedItem(PumpkinSalesMode.FARM_STAND);
            stage(root).setSelectedItem(PumpkinPatchStage.PLANTING_WINDOW);
            notes(root).setText("Prepare wagon path");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("at most two decimal places");
        onEdt(() -> {
            field(root, "Acres").setText("2.50");
            field(root, "Planned planting date").setText("2027-10-10");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned planting date cannot be after");
        onEdt(() -> {
            field(root, "Planned planting date").setText("2027-05-10");
            field(root, "Planned picking start").setText("");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned picking end requires a start date");
        onEdt(() -> {
            field(root, "Planned picking start").setText("2027-10-20");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned picking end cannot precede start");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned picking start").setText("2027-10-01");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Variety").getText()).isEqualTo("Howden");
        assertThat(tab.applicationExitWarning()).contains("unsaved pumpkin patch plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("West field"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<PumpkinPatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(PumpkinPatchPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getVariety()).isEqualTo("Howden");
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("2.50");
        assertThat(draft.getValue().getSalesMode()).isEqualTo(PumpkinSalesMode.FARM_STAND);
        assertThat(draft.getValue().getPlannedPlantingDate())
                .isEqualTo(LocalDate.of(2027, 5, 10));
        assertThat(draft.getValue().getPlannedPickingStart())
                .isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(draft.getValue().getPlannedPickingEnd())
                .isEqualTo(LocalDate.of(2027, 10, 15));
        assertThat(draft.getValue().getVisitorAreaNotes()).isEqualTo("North gate parking");
        assertThat(draft.getValue().getStage()).isEqualTo(PumpkinPatchStage.PLANTING_WINDOW);
        assertThat(draft.getValue().getNotes()).isEqualTo("Prepare wagon path");
    }

    @Test
    void nextSeasonCarriesStablePatchDetailsButClearsDatesStageAndNotes() throws Exception {
        PumpkinPatchPlanService service = mock(PumpkinPatchPlanService.class);
        PumpkinPatchPlan previous = saved(11L, "South field");
        previous.setStage(PumpkinPatchStage.PICKING_WINDOW);
        when(service.list()).thenReturn(List.of(previous));
        when(service.create(any(PumpkinPatchPlan.class))).thenAnswer(invocation -> {
            PumpkinPatchPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        PumpkinPatchTab tab = new PumpkinPatchTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Patch name").setText("Unfinished edit");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Patch name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Patch name").setText("South field");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Patch name").getText()).isEqualTo("South field");
        assertThat(field(root, "Variety").getText()).isEqualTo("Howden");
        assertThat(field(root, "Acres").getText()).isEqualTo("3.25");
        assertThat(method(root).getSelectedItem()).isEqualTo(PumpkinSalesMode.PICK_YOUR_OWN);
        assertThat(visitorNotes(root).getText()).isEqualTo("North gate parking");
        assertThat(field(root, "Planned planting date").getText()).isEmpty();
        assertThat(field(root, "Planned picking start").getText()).isEmpty();
        assertThat(field(root, "Planned picking end").getText()).isEmpty();
        assertThat(stage(root).getSelectedItem()).isEqualTo(PumpkinPatchStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved pumpkin patch plan draft");
        onEdt(() -> {
            field(root, "Planned planting date").setText("2028-05-08");
            field(root, "Planned picking start").setText("2028-10-03");
            field(root, "Planned picking end").setText("2028-10-20");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<PumpkinPatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(PumpkinPatchPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getPatchName()).isEqualTo("South field");
        assertThat(draft.getValue().getSalesMode()).isEqualTo(PumpkinSalesMode.PICK_YOUR_OWN);
        assertThat(draft.getValue().getPlannedPlantingDate())
                .isEqualTo(LocalDate.of(2028, 5, 8));
        assertThat(draft.getValue().getPlannedPickingStart())
                .isEqualTo(LocalDate.of(2028, 10, 3));
        assertThat(draft.getValue().getNotes()).isEmpty();
        assertThat(draft.getValue().getVisitorAreaNotes()).isEqualTo("North gate parking");
        assertThat(draft.getValue().getStage()).isEqualTo(PumpkinPatchStage.PLANNED);
    }

    @Test
    void savedPatchCanBeEditedWithoutCreatingAnotherPatch() throws Exception {
        PumpkinPatchPlanService service = mock(PumpkinPatchPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "South field")));
        when(service.update(eq(11L), any(PumpkinPatchPlan.class))).thenAnswer(invocation -> {
            PumpkinPatchPlan result = invocation.getArgument(1);
            ReflectionTestUtils.setField(result, "id", 11L);
            return result;
        });
        RecordingHost host = new RecordingHost();
        PumpkinPatchTab tab = new PumpkinPatchTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            method(root).setSelectedItem(PumpkinSalesMode.MIXED);
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<PumpkinPatchPlan> draft =
                org.mockito.ArgumentCaptor.forClass(PumpkinPatchPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getSalesMode()).isEqualTo(PumpkinSalesMode.MIXED);
        verify(service, never()).create(any());
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<PumpkinPatchPlan> stored = new AtomicReference<>(
                saved(21L, "South field"));
        PumpkinPatchPlanService service = mock(PumpkinPatchPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(PumpkinPatchStage.class)))
                .thenAnswer(invocation -> {
                    PumpkinPatchPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        PumpkinPatchTab tab = new PumpkinPatchTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton growing = named(root, "pumpkinPatch.stageTarget.GROWING", JButton.class);
        JButton prep = named(root, "pumpkinPatch.stageTarget.PLANTING_WINDOW", JButton.class);
        assertThat(growing.getToolTipText()).contains("Stage change saves immediately");
        onEdt(() -> field(root, "Variety").setText("Unsaved variety"));
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> field(root, "Variety").setText("Howden"));
        Transferable payload = dragPayload(root);
        PumpkinPatchTab foreignTab = new PumpkinPatchTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(growing, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(growing, payload);
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> growing.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == PumpkinPatchStage.GROWING);
        assertThat(stored.get().getSalesMode()).isEqualTo(PumpkinSalesMode.PICK_YOUR_OWN);
        await(() -> "Growing".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh patches").isEnabled());
        onEdt((Runnable) prep::doClick);
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == PumpkinPatchStage.PLANTING_WINDOW);
        verify(service, times(2)).updateStage(eq(21L), any(PumpkinPatchStage.class));
        await(() -> "Planting window".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh patches").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh patches").isEnabled());
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(growing, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(growing.isEnabled()).isFalse();
        assertThat(button(root, "Plan next season").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "Refresh patches").isEnabled()).isTrue();
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
        verify(service, never()).updateStage(anyLong(), eq(PumpkinPatchStage.PICKING_WINDOW));
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("pumpkin-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static PumpkinPatchPlan saved(Long id, String name) {
        PumpkinPatchPlan plan = new PumpkinPatchPlan(name, "Howden",
                new BigDecimal("3.25"), LocalDate.of(2027, 5, 10),
                LocalDate.of(2027, 9, 20), LocalDate.of(2027, 10, 20),
                PumpkinSalesMode.PICK_YOUR_OWN, "North gate parking",
                PumpkinPatchStage.PLANNED, "Review signs");
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
        return named(root, "pumpkinPatch.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "pumpkinPatch.notes", JTextArea.class);
    }

    private static JTextArea visitorNotes(Component root) {
        return named(root, "pumpkinPatch.visitorAreaNotes", JTextArea.class);
    }

    private static JComboBox<?> method(Component root) {
        return named(root, "pumpkinPatch.salesMode", JComboBox.class);
    }

    private static JComboBox<?> stage(Component root) {
        return named(root, "pumpkinPatch.stage", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "pumpkinPatch.patchTable", JTable.class);
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

package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.CranberryBedPlan;
import com.flowerfarm.model.CranberryBedStage;
import com.flowerfarm.model.CranberryHarvestMethod;
import com.flowerfarm.service.CranberryBedPlanService;
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

@DisplayName("Cranberry bed planning")
class CranberryBedTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "cranberry-beds");

    @Test
    void screenShowsHarvestMethodPlanScopeAndEditableBed() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        CranberryBedPlanService service = mock(CranberryBedPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North bog bed")));
        RecordingHost host = new RecordingHost();
        CranberryBedTab tab = new CranberryBedTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Cranberry beds");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Cranberry beds");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not log a completed harvest"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("actual yield, stock, or sales"))).isTrue();
        for (String name : List.of("Bed name", "Variety", "Acres",
                "Planned harvest start", "Planned harvest end", "Intended channel")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(method(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planned harvest method");
        assertThat(stage(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(3)).isEqualTo("Harvest method");
        onEdt(() -> {
            field(root, "Variety").setText("Ben Lear");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Variety").getText()).isEqualTo("Ben Lear");
        assertThat(tab.applicationExitWarning()).contains("unsaved cranberry bed plan draft");
        onEdt(() -> {
            field(root, "Variety").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        SwingUserHarness.on(root).layoutAt(920, 1220);
        onEdt(() -> {
            JScrollPane scroll = named(root, "cranberryBed.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "cranberryBed.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(CranberryBedStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "cranberry-beds-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void acreageAndDatesValidateWhileFailedSaveKeepsCompleteDraftForRetry() throws Exception {
        CranberryBedPlanService service = mock(CranberryBedPlanService.class);
        when(service.create(any(CranberryBedPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    CranberryBedPlan result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        CranberryBedTab tab = new CranberryBedTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a bed name");
        onEdt(() -> {
            field(root, "Bed name").setText("West bog bed");
            field(root, "Variety").setText("Stevens");
            field(root, "Acres").setText("2.555");
            field(root, "Planned harvest start").setText("2027-10-01");
            field(root, "Planned harvest end").setText("2027-10-15");
            field(root, "Intended channel").setText("Processing");
            method(root).setSelectedItem(CranberryHarvestMethod.DRY);
            stage(root).setSelectedItem(CranberryBedStage.HARVEST_PREP);
            notes(root).setText("Check access lanes");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("at most two decimal places");
        onEdt(() -> {
            field(root, "Acres").setText("2.50");
            field(root, "Planned harvest start").setText("");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned harvest end requires a start date");
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2027-10-20");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2027-10-01");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Variety").getText()).isEqualTo("Stevens");
        assertThat(tab.applicationExitWarning()).contains("unsaved cranberry bed plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("West bog bed"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<CranberryBedPlan> draft =
                org.mockito.ArgumentCaptor.forClass(CranberryBedPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getVariety()).isEqualTo("Stevens");
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("2.50");
        assertThat(draft.getValue().getHarvestMethod()).isEqualTo(CranberryHarvestMethod.DRY);
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 10, 15));
        assertThat(draft.getValue().getIntendedChannel()).isEqualTo("Processing");
        assertThat(draft.getValue().getStage()).isEqualTo(CranberryBedStage.HARVEST_PREP);
        assertThat(draft.getValue().getNotes()).isEqualTo("Check access lanes");
    }

    @Test
    void nextSeasonCarriesBedVarietyMethodAndChannelButClearsDatesAndNotes() throws Exception {
        CranberryBedPlanService service = mock(CranberryBedPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North bog bed")));
        when(service.create(any(CranberryBedPlan.class))).thenAnswer(invocation -> {
            CranberryBedPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        CranberryBedTab tab = new CranberryBedTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Bed name").setText("Unfinished edit");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Bed name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Bed name").setText("North bog bed");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Bed name").getText()).isEqualTo("North bog bed");
        assertThat(field(root, "Variety").getText()).isEqualTo("Stevens");
        assertThat(field(root, "Acres").getText()).isEqualTo("3.25");
        assertThat(method(root).getSelectedItem()).isEqualTo(CranberryHarvestMethod.WET);
        assertThat(field(root, "Intended channel").getText()).isEqualTo("Processing");
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(stage(root).getSelectedItem()).isEqualTo(CranberryBedStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved cranberry bed plan draft");
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2028-10-03");
            field(root, "Planned harvest end").setText("2028-10-20");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<CranberryBedPlan> draft =
                org.mockito.ArgumentCaptor.forClass(CranberryBedPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getBedName()).isEqualTo("North bog bed");
        assertThat(draft.getValue().getHarvestMethod()).isEqualTo(CranberryHarvestMethod.WET);
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2028, 10, 3));
        assertThat(draft.getValue().getNotes()).isEmpty();
        assertThat(draft.getValue().getStage()).isEqualTo(CranberryBedStage.PLANNED);
    }

    @Test
    void savedBedCanBeEditedWithoutCreatingAnotherBed() throws Exception {
        CranberryBedPlanService service = mock(CranberryBedPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North bog bed")));
        when(service.update(eq(11L), any(CranberryBedPlan.class))).thenAnswer(invocation -> {
            CranberryBedPlan result = invocation.getArgument(1);
            ReflectionTestUtils.setField(result, "id", 11L);
            return result;
        });
        RecordingHost host = new RecordingHost();
        CranberryBedTab tab = new CranberryBedTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            method(root).setSelectedItem(CranberryHarvestMethod.MIXED);
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<CranberryBedPlan> draft =
                org.mockito.ArgumentCaptor.forClass(CranberryBedPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getHarvestMethod()).isEqualTo(CranberryHarvestMethod.MIXED);
        verify(service, never()).create(any());
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<CranberryBedPlan> stored = new AtomicReference<>(
                saved(21L, "North bog bed"));
        CranberryBedPlanService service = mock(CranberryBedPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(CranberryBedStage.class)))
                .thenAnswer(invocation -> {
                    CranberryBedPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        CranberryBedTab tab = new CranberryBedTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton growing = named(root, "cranberryBed.stageTarget.GROWING", JButton.class);
        JButton prep = named(root, "cranberryBed.stageTarget.HARVEST_PREP", JButton.class);
        assertThat(growing.getToolTipText()).contains("Stage change saves immediately");
        onEdt(() -> field(root, "Variety").setText("Unsaved variety"));
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> field(root, "Variety").setText("Stevens"));
        Transferable payload = dragPayload(root);
        CranberryBedTab foreignTab = new CranberryBedTab(service, new RecordingHost());
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
                && stored.get().getStage() == CranberryBedStage.GROWING);
        assertThat(stored.get().getHarvestMethod()).isEqualTo(CranberryHarvestMethod.WET);
        await(() -> "Growing".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh beds").isEnabled());
        onEdt((Runnable) prep::doClick);
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == CranberryBedStage.HARVEST_PREP);
        verify(service, times(2)).updateStage(eq(21L), any(CranberryBedStage.class));
        await(() -> "Harvest prep".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh beds").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh beds").isEnabled());
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(growing, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(growing.isEnabled()).isFalse();
        assertThat(button(root, "Plan next season").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "Refresh beds").isEnabled()).isTrue();
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
        verify(service, never()).updateStage(anyLong(), eq(CranberryBedStage.HARVEST_WINDOW));
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("cranberry-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static CranberryBedPlan saved(Long id, String name) {
        CranberryBedPlan plan = new CranberryBedPlan(name, "Stevens",
                new BigDecimal("3.25"), CranberryHarvestMethod.WET,
                LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 15),
                "Processing", CranberryBedStage.PLANNED, "Check water levels");
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
        return named(root, "cranberryBed.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "cranberryBed.notes", JTextArea.class);
    }

    private static JComboBox<?> method(Component root) {
        return named(root, "cranberryBed.harvestMethod", JComboBox.class);
    }

    private static JComboBox<?> stage(Component root) {
        return named(root, "cranberryBed.stage", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "cranberryBed.bedTable", JTable.class);
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

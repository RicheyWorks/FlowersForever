package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.VegetablePlantingPlan;
import com.flowerfarm.model.VegetablePlantingStage;
import com.flowerfarm.service.VegetablePlantingPlanService;
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

@DisplayName("Vegetable planting planning")
class VegetablePlantingTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "vegetable-plantings");

    @Test
    void vegetablePlantingsShowScopeSharedLinksStagesAndScrollableForm() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        RecordingHost host = new RecordingHost();
        VegetablePlantingTab tab = new VegetablePlantingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Vegetable plantings");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Vegetable plantings");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual harvests"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("grower-entered"))).isTrue();
        for (String name : List.of("Field or bed", "Vegetable crop", "Variety",
                "Previous crop", "Acres", "Succession number", "Intended channel",
                "Planned sowing date", "Planned transplant date", "Planned harvest start",
                "Planned harvest end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "vegetablePlanting.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(3)).isEqualTo("Succession");

        onEdt(() -> {
            field(root, "Vegetable crop").setText("Carrots");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Vegetable crop").getText()).isEqualTo("Carrots");
        assertThat(tab.applicationExitWarning()).contains("unsaved vegetable planting plan draft");
        onEdt(() -> {
            field(root, "Vegetable crop").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        assertThat(field(root, "Field or bed").getText()).isEqualTo("North field");

        SwingUserHarness.on(root).layoutAt(920, 650);
        onEdt(() -> {
            JScrollPane scroll = named(root, "vegetablePlanting.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "vegetablePlanting.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(VegetablePlantingStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "vegetable-plantings-compact.png");
                SwingUserHarness.on(root).layoutAt(920, 1360);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "vegetable-plantings-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsAllPlantingDetailsAndRetrySavesThem() throws Exception {
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.create(any(VegetablePlantingPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    VegetablePlantingPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        VegetablePlantingTab tab = new VegetablePlantingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Field or bed").setText("North field");
            field(root, "Vegetable crop").setText("Onions");
            field(root, "Variety").setText("Yellow storage");
            field(root, "Previous crop").setText("Peas");
            field(root, "Acres").setText("3.250");
            field(root, "Succession number").setText("2");
            field(root, "Intended channel").setText("Fresh market");
            field(root, "Planned sowing date").setText("2027-02-01");
            field(root, "Planned transplant date").setText("2027-04-15");
            field(root, "Planned harvest start").setText("2027-09-01");
            field(root, "Planned harvest end").setText("2027-09-20");
            combo(root, "vegetablePlanting.stage").setSelectedItem(VegetablePlantingStage.GROWING);
            notes(root).setText("Review irrigation plan");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Previous crop").getText()).isEqualTo("Peas");
        assertThat(field(root, "Planned transplant date").getText()).isEqualTo("2027-04-15");
        assertThat(tab.applicationExitWarning()).contains("unsaved vegetable planting plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North field"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<VegetablePlantingPlan> draft =
                org.mockito.ArgumentCaptor.forClass(VegetablePlantingPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getVegetableCrop()).isEqualTo("Onions");
        assertThat(draft.getValue().getVariety()).isEqualTo("Yellow storage");
        assertThat(draft.getValue().getPreviousCrop()).isEqualTo("Peas");
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("3.25");
        assertThat(draft.getValue().getSuccessionNumber()).isEqualTo(2);
        assertThat(draft.getValue().getPlannedSowingDate())
                .isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(draft.getValue().getPlannedTransplantDate())
                .isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 9, 20));
        assertThat(draft.getValue().getIntendedChannel()).isEqualTo("Fresh market");
        assertThat(draft.getValue().getStage()).isEqualTo(VegetablePlantingStage.GROWING);
        assertThat(draft.getValue().getNotes()).isEqualTo("Review irrigation plan");
    }

    @Test
    void invalidDatesAndSelectionProtectDraftThenSavedEditsPersist() throws Exception {
        AtomicReference<VegetablePlantingPlan> stored = new AtomicReference<>(
                saved(11L, "North field"));
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get(),
                saved(12L, "South bed")));
        when(service.update(eq(11L), any(VegetablePlantingPlan.class))).thenAnswer(invocation -> {
            VegetablePlantingPlan updated = invocation.getArgument(1);
            ReflectionTestUtils.setField(updated, "id", 11L);
            stored.set(updated.snapshot());
            return updated.snapshot();
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        VegetablePlantingTab tab = new VegetablePlantingTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned harvest start").setText("2027-10-01");
            field(root, "Planned harvest end").setText("2027-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Field or bed").getText()).isEqualTo("North field");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned harvest end").setText("2027-10-15");
            field(root, "Planned sowing date").setText("2027-10-10");
            field(root, "Succession number").setText("0");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Succession number must be greater than zero");
        onEdt(() -> {
            field(root, "Succession number").setText("3");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned sowing date cannot be after planned harvest start");
        onEdt(() -> {
            field(root, "Planned sowing date").setText("2027-04-20");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned sowing date cannot be after planned transplant date");
        onEdt(() -> {
            field(root, "Planned sowing date").setText("2027-02-10");
            field(root, "Planned transplant date").setText("2027-10-10");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned transplant date cannot be after planned harvest start");
        onEdt(() -> {
            field(root, "Planned transplant date").setText("2027-04-20");
            field(root, "Previous crop").setText("Updated previous crop");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave()
                && "Updated previous crop".equals(stored.get().getPreviousCrop()));
        assertThat(stored.get().getSuccessionNumber()).isEqualTo(3);
        assertThat(stored.get().getPlannedTransplantDate())
                .isEqualTo(LocalDate.of(2027, 4, 20));
        verify(service).update(eq(11L), any(VegetablePlantingPlan.class));
        verify(service, never()).create(any());
    }

    @Test
    void nextSuccessionCarriesCropAndChannelThenClearsDatesAndNotes() throws Exception {
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        when(service.create(any(VegetablePlantingPlan.class))).thenAnswer(invocation -> {
            VegetablePlantingPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        VegetablePlantingTab tab = new VegetablePlantingTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Field or bed").setText("Unfinished edit");
            button(root, "Plan next succession").doClick();
        });
        assertThat(field(root, "Field or bed").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        verify(service, never()).create(any());

        onEdt(() -> {
            field(root, "Field or bed").setText("North field");
            button(root, "Plan next succession").doClick();
        });
        assertThat(field(root, "Field or bed").getText()).isEqualTo("North field");
        assertThat(field(root, "Vegetable crop").getText()).isEqualTo("Onions");
        assertThat(field(root, "Variety").getText()).isEqualTo("Yellow storage");
        assertThat(field(root, "Previous crop").getText()).isEqualTo("Peas");
        assertThat(field(root, "Acres").getText()).isEqualTo("3.25");
        assertThat(field(root, "Succession number").getText()).isEqualTo("3");
        assertThat(field(root, "Intended channel").getText()).isEqualTo("Fresh market");
        assertThat(field(root, "Planned sowing date").getText()).isEmpty();
        assertThat(field(root, "Planned transplant date").getText()).isEmpty();
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(combo(root, "vegetablePlanting.stage").getSelectedItem())
                .isEqualTo(VegetablePlantingStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved vegetable planting plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a planned harvest start date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2028-09-10");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<VegetablePlantingPlan> draft =
                org.mockito.ArgumentCaptor.forClass(VegetablePlantingPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getFieldOrBed()).isEqualTo("North field");
        assertThat(draft.getValue().getVegetableCrop()).isEqualTo("Onions");
        assertThat(draft.getValue().getPreviousCrop()).isEqualTo("Peas");
        assertThat(draft.getValue().getSuccessionNumber()).isEqualTo(3);
        assertThat(draft.getValue().getPlannedSowingDate()).isNull();
        assertThat(draft.getValue().getPlannedTransplantDate()).isNull();
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2028, 9, 10));
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<VegetablePlantingPlan> stored = new AtomicReference<>(
                saved(21L, "North field"));
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(VegetablePlantingStage.class)))
                .thenAnswer(invocation -> {
                    VegetablePlantingPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        VegetablePlantingTab tab = new VegetablePlantingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton sown = named(root,
                "vegetablePlanting.stageTarget.SOWN", JButton.class);
        JButton growing = named(root,
                "vegetablePlanting.stageTarget.GROWING", JButton.class);
        assertThat(sown.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        VegetablePlantingTab foreignTab = new VegetablePlantingTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> sown.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(sown, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(sown, payload);
        assertThat(onEdt(() -> sown.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> sown.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == VegetablePlantingStage.SOWN);
        assertThat(stored.get().getVegetableCrop()).isEqualTo("Onions");
        assertThat(stored.get().getSuccessionNumber()).isEqualTo(2);
        assertThat(host.lastStatus).contains("moved to Sown");

        await(() -> growing.isEnabled()
                && "Sown".equals(table(root).getValueAt(0, 6)));
        onEdt((Runnable) growing::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == VegetablePlantingStage.GROWING);
        verify(service, times(2)).updateStage(eq(21L), any(VegetablePlantingStage.class));

        await(() -> "Growing".equals(table(root).getValueAt(0, 6))
                && button(root, "Refresh plantings").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh plantings").isEnabled());
        assertThat(onEdt(() -> sown.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(sown, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Field or bed").setText("Unsaved field name"));
        assertThat(onEdt(() -> sown.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(sown, current)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(sown.isEnabled()).isFalse();
        assertThat(onEdt(() -> sown.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(sown, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveMoveOrPlanNextSuccession() throws Exception {
        VegetablePlantingPlanService service = mock(VegetablePlantingPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        VegetablePlantingTab tab = new VegetablePlantingTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Field or bed").getText()).isEqualTo("North field");
        assertThat(field(root, "Field or bed").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New vegetable planting plan").isEnabled()).isFalse();
        assertThat(button(root, "Plan next succession").isEnabled()).isFalse();
        assertThat(button(root, "Refresh plantings").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("vegetable-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static VegetablePlantingPlan saved(Long id, String fieldOrBed) {
        VegetablePlantingPlan plan = new VegetablePlantingPlan(fieldOrBed, "Onions",
                "Yellow storage", "Peas", new BigDecimal("3.25"), 2,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 4, 15),
                LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 20),
                "Fresh market", VegetablePlantingStage.PLANNED,
                "Review irrigation plan");
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
        return named(root, "vegetablePlanting.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "vegetablePlanting.notes", JTextArea.class);
    }

    private static JComboBox<?> combo(Component root, String name) {
        return named(root, name, JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "vegetablePlanting.plantingTable", JTable.class);
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
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
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
